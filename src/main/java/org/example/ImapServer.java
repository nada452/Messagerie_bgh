package org.example;

import org.example.Authentification_RMI.Authservice;

import java.io.*;
import java.net.*;
import java.rmi.registry.*;
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
    private List<File> emails =new ArrayList<>();
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
        if (state != ImapState.NON_AUTHENTICATED) {
            send(tag + " BAD Already logged in");
            return;
        }

        // Format attendu : LOGIN user password
        String[] parts = args.split(" ", 2);
        if (parts.length < 2) {
            send(tag + " BAD Invalid arguments");
            return;
        }

        String user = parts[0];
        String pass = parts[1];

        // --- DEBUT INTEGRATION RMI ---
        boolean isAuthValid = false;
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            Authservice authStub = (Authservice) registry.lookup("rmi://localhost/Authservice");
            isAuthValid = authStub.authenticate(user, pass);
        } catch (Exception e) {
            e.printStackTrace();
            send(tag + " NO Internal Server Error (RMI Unavailable)");
            return;
        }
        // --- FIN INTEGRATION RMI ---

        if (isAuthValid) {
            this.username=user;
            state = ImapState.AUTHENTICATED;

            send(tag + " OK LOGIN completed");
        } else {
            send(tag + " NO LOGIN failed (Invalid credentials)");
        }
    }

    private void handleSelect(String tag, String folderName) {
        // 1. Sécurité : On entoure tout le code d'un try-catch
        try {
            if (state != ImapState.AUTHENTICATED && state != ImapState.SELECTED) {
                send(tag + " NO Not authenticated");
                return;
            }

            String folder = folderName.replace("\"", "");

            if (folder.equalsIgnoreCase("INBOX")) {

                // 2. Sécurité : Si currentUser est null, on arrête

                if (username == null) {
                    send(tag + " NO User session error");
                    return;
                }

                // 3. Calcul du chemin

                if (username.contains("@")) {
                    username = username.split("@")[0];
                }

                File userDir = new File("mailserver/" + username);

                // 4. Affichage Console pour DEBOGAGE (Regarde ta console Java !)
                System.out.println("--- DEBUG SELECT ---");
                System.out.println("User: " + username);
                System.out.println("Chemin cherché: " + userDir.getAbsolutePath());
                System.out.println("Existe ? " + userDir.exists());
                System.out.println("--------------------");

                if (userDir.exists() && userDir.isDirectory()) {
                    state = ImapState.SELECTED;
                    userDir = userDir;

                    // 5. Chargement des emails avec sécurité
                    loadEmails();

                    // 6. Réponses IMAP
                    send("* " + emails.size() + " EXISTS");
                    send("* 0 RECENT");
                    send("* FLAGS (\\Seen \\Deleted)");
                    send(tag + " OK [READ-WRITE] SELECT completed");
                } else {
                    send(tag + " NO Folder not found");
                }
            } else {
                send(tag + " NO Folder not supported");
            }

        } catch (Exception e) {
            // Si une erreur inconnue arrive, on l'affiche et on prévient le client
            e.printStackTrace();
            send(tag + " NO Internal Server Error");
        }
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
        // SOLUTION : On réinitialise la liste à chaque fois pour éviter le "null"
        emails = new ArrayList<>();

        if (userDir == null) return;

        File[] files = userDir.listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                emails.add(f);
            }
        }
    }
    private void send (String message){
        System.out.println(message);
    }
}
