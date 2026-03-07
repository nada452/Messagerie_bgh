package org.example;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ImapServer {
    private static final int PORT = 143; // Port standard IMAP

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(10);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("IMAP Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("IMAP connection from " + clientSocket.getInetAddress());
                pool.execute(new ImapSession(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
class ImapSession implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private enum ImapState {
        NON_AUTHENTICATED,
        AUTHENTICATED,
        SELECTED,
        LOGOUT
    }

    private ImapState state;
    private String username;
    private File userDir;
    private List<File> emails;
    private boolean[] seenFlags; // Pour gérer les messages lus/non lus
    private String selectedMailbox;

    public ImapSession(Socket socket) {
        this.socket = socket;
        this.state = ImapState.NON_AUTHENTICATED;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Envoi du greeting IMAP
            out.println("* OK IMAP4rev1 Service Ready");

            String line;
            while ((line = in.readLine()) != null && state != ImapState.LOGOUT) {
                System.out.println("IMAP Received: " + line);

                // Parse command with tag
                String[] parts = line.split(" ", 3);
                if (parts.length < 2) {
                    out.println("* BAD Invalid command");
                    continue;
                }

                String tag = parts[0];
                String command = parts[1].toUpperCase();
                String args = parts.length > 2 ? parts[2] : "";

                processCommand(tag, command, args);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException e) {}
        }
    }

    private void processCommand(String tag, String command, String args) {
        System.out.println("Current state: " + state);
        System.out.println("Processing: " + command);
        switch (state) {
            case NON_AUTHENTICATED:
                handleNonAuthenticated(tag, command, args);
                break;
            case AUTHENTICATED:
                handleAuthenticated(tag, command, args);
                break;
            case SELECTED:
                handleSelected(tag, command, args);
                break;
        }
    }

    private void handleNonAuthenticated(String tag, String command, String args) {
        switch (command) {
            case "LOGIN":
                handleLogin(tag, args);
                break;
            case "CAPABILITY":
                out.println("* CAPABILITY IMAP4rev1 LOGIN");
                out.println(tag + " OK CAPABILITY completed");
                break;
            case "LOGOUT":
                handleLogout(tag);
                break;
            default:
                out.println(tag + " BAD Command not allowed in current state");
        }
    }

    private void handleAuthenticated(String tag, String command, String args) {
        switch (command) {
            case "SELECT":
                handleSelect(tag, args);
                break;
            case "LIST":
                handleList(tag, args);
                break;
            case "LOGOUT":
                handleLogout(tag);
                break;
            case "CAPABILITY":
                out.println("* CAPABILITY IMAP4rev1");
                out.println(tag + " OK CAPABILITY completed");
                break;
            default:
                out.println(tag + " BAD Command not allowed in current state");
        }
    }

    private void handleSelected(String tag, String command, String args) {
        switch (command) {
            case "FETCH":
                handleFetch(tag, args);
                break;
            case "STORE":
                handleStore(tag, args);
                break;
            case "SEARCH":
                handleSearch(tag, args);
                break;
            case "CLOSE":
                handleClose(tag);
                break;
            case "LOGOUT":
                handleLogout(tag);
                break;
            default:
                out.println(tag + " BAD Command not allowed in current state");
        }
    }

    private void handleLogin(String tag, String args) {
        // Format: LOGIN username password
        String[] parts = args.split(" ");
        if (parts.length < 2) {
            out.println(tag + " BAD Invalid arguments");
            return;
        }

        String username = parts[0];
        // Note: Dans une vraie implémentation, vérifier le mot de passe

        File dir = new File("mailserver/" + username);
        if (dir.exists() && dir.isDirectory()) {
            this.username = username;
            this.userDir = dir;
            loadEmails();
            state = ImapState.AUTHENTICATED;
            out.println(tag + " OK LOGIN completed");
        } else {
            out.println(tag + " NO Invalid username or password");
        }
    }

    private void handleSelect(String tag, String args) {
        // Format: SELECT INBOX
        if (!args.equalsIgnoreCase("INBOX")) {
            out.println(tag + " NO Mailbox doesn't exist");
            return;
        }

        this.selectedMailbox = args;
        state = ImapState.SELECTED;

        // Réponses requises pour SELECT
        out.println("* " + emails.size() + " EXISTS");
        out.println("* 0 RECENT");
        out.println("* FLAGS (\\Seen \\Answered \\Flagged \\Deleted \\Draft)");
        out.println("* OK [PERMANENTFLAGS (\\Seen \\Deleted)] Permanent flags");
        out.println(tag + " OK [READ-WRITE] SELECT completed");
    }

    private void handleFetch(String tag, String args) {
        // Format: FETCH 1 BODY[]
        try {
            String[] parts = args.split(" ");
            int msgNum = Integer.parseInt(parts[0]) - 1;

            if (msgNum < 0 || msgNum >= emails.size()) {
                out.println(tag + " NO Message doesn't exist");
                return;
            }

            File email = emails.get(msgNum);

            // Marquer comme lu
            seenFlags[msgNum] = true;

            // Envoyer le message
            out.println("* " + (msgNum + 1) + " FETCH (FLAGS (\\Seen) BODY[] {"
                    + email.length() + "}");

            // Lire et envoyer le contenu du fichier
            try (BufferedReader reader = new BufferedReader(new FileReader(email))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.println(line);
                }
            }

            out.println(")");
            out.println(tag + " OK FETCH completed");

        } catch (Exception e) {
            out.println(tag + " BAD Invalid arguments");
        }
    }

    private void handleStore(String tag, String args) {
        // Format: STORE 1 +FLAGS (\Seen)
        try {
            String[] parts = args.split(" ");
            int msgNum = Integer.parseInt(parts[0]) - 1;
            String operation = parts[1]; // +FLAGS, -FLAGS, FLAGS

            if (msgNum < 0 || msgNum >= emails.size()) {
                out.println(tag + " NO Message doesn't exist");
                return;
            }

            // Extraire les flags (simplifié)
            if (operation.contains("\\Seen")) {
                seenFlags[msgNum] = true;
            }

            out.println("* " + (msgNum + 1) + " FETCH FLAGS (\\Seen)");
            out.println(tag + " OK STORE completed");

        } catch (Exception e) {
            out.println(tag + " BAD Invalid arguments");
        }
    }

    private void handleSearch(String tag, String args) {
        // Format: SEARCH FROM "test"
        // Implémentation simplifiée
        List<Integer> matching = new ArrayList<>();

        for (int i = 0; i < emails.size(); i++) {
            // Si pas marqué pour suppression et rechercher dans le contenu
            matching.add(i + 1);
        }

        // Envoyer les résultats
        out.print("* SEARCH");
        for (int msgNum : matching) {
            out.print(" " + msgNum);
        }
        out.println();
        out.println(tag + " OK SEARCH completed");
    }

    private void handleClose(String tag) {
        // Supprimer les messages marqués \Deleted
        for (int i = emails.size() - 1; i >= 0; i--) {
            // Note: Dans une vraie implémentation, vérifier le flag Deleted
            emails.remove(i);
        }

        state = ImapState.AUTHENTICATED;
        selectedMailbox = null;
        out.println(tag + " OK CLOSE completed");
    }

    private void handleList(String tag, String args) {
        // Format: LIST "" "*"
        out.println("* LIST (\\HasNoChildren) \"/\" INBOX");
        out.println(tag + " OK LIST completed");
    }

    private void handleLogout(String tag) {
        out.println("* BYE IMAP4rev1 Server logging out");
        out.println(tag + " OK LOGOUT completed");
        state = ImapState.LOGOUT;
    }

    private void handleRset(String tag) {
        // Réinitialiser les flags de suppression
        // Note: Implémentation selon besoin
        out.println(tag + " OK RSET completed");
    }

    private void loadEmails() {
        File[] files = userDir.listFiles();
        if (files != null) {
            emails = new ArrayList<>(Arrays.asList(files));
            seenFlags = new boolean[emails.size()];
            // Trier par date (du plus récent au plus ancien)
            emails.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        } else {
            emails = new ArrayList<>();
            seenFlags = new boolean[0];
        }
    }
}
