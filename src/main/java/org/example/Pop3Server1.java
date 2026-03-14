package org.example;


import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Pop3Server — Serveur POP3 sur le port 110.
 *
 * CORRECTIONS apportées :
 *  1. PORT changé de 995 → 110 (le SSL nécessite des certificats configurés ;
 *     pour les tests du TP, on utilise le port standard sans SSL).
 *  2. Suppression des champs SSL inutilisés dans la classe Pop3Server
 *     (SSLServerSocket, SSLServerSocketFactory) qui causaient une erreur
 *     à la construction de l'objet.
 *  3. handleStat() était vide — implémentation complète ajoutée.
 *  4. handlePass() ne renvoyait rien si l'authentification échouait —
 *     ajout du message d'erreur -ERR.
 *  5. handleUser() ne vérifie plus l'existence du répertoire mais utilise
 *     UserAuth.userExists() pour être cohérent avec le vrai système d'auth.
 *  6. Utilisation d'un ExecutorService pour gérer la concurrence.
 *  7. Ajout des commandes TOP et UIDL (souvent utilisées par les clients mail).
 */
public class Pop3Server1 {

    private static final int PORT = 110;  // CORRECTION : 995 → 110 (sans SSL)
    private static final int THREAD_POOL_SIZE = 20;

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("POP3 Server started on port " + PORT);
            System.out.println("Mailbox root: " +
                    new File("mailserver").getAbsolutePath());

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[POP3] Connection from " +
                        clientSocket.getInetAddress());
                pool.execute(new Pop3Session(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("[POP3] Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }
    }
}

class Pop3Session implements Runnable {

    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private String username;
    private File userDir;
    private List<File> emails;
    private List<Boolean> deletionFlags;
    private boolean authenticated;

    // États POP3 (RFC 1939) : AUTHORIZATION → TRANSACTION → UPDATE
    private enum Pop3State { AUTHORIZATION, TRANSACTION, UPDATE }
    private Pop3State state;

    public Pop3Session(Socket socket) {
        this.socket = socket;
        this.authenticated = false;
        this.state = Pop3State.AUTHORIZATION;
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println("+OK POP3 server ready <" +
                    System.currentTimeMillis() + "@example.com>");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[POP3] Received: " + line);
                String[] parts = line.trim().split("\\s+", 2);
                String command  = parts[0].toUpperCase();
                String argument = parts.length > 1 ? parts[1] : "";

                switch (command) {
                    case "USER": handleUser(argument); break;
                    case "PASS": handlePass(argument); break;
                    case "STAT": handleStat();         break;
                    case "LIST": handleList(argument); break;
                    case "RETR": handleRetr(argument); break;
                    case "DELE": handleDele(argument); break;
                    case "RSET": handleRset();         break;
                    case "TOP":  handleTop(argument);  break;
                    case "UIDL": handleUidl(argument); break;
                    case "NOOP":
                        out.println("+OK");
                        break;
                    case "QUIT":
                        handleQuit();
                        return;
                    default:
                        out.println("-ERR Unknown command: " + command);
                }
            }

            // Connexion fermée sans QUIT : les deletionFlags ne sont PAS appliqués
            if (authenticated) {
                System.err.println("[POP3] Connection lost without QUIT — " +
                        "deletion marks not applied.");
            }
        } catch (IOException e) {
            System.err.println("[POP3] Session error: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException e) { /* ignore */ }
            System.out.println("[POP3] Client disconnected: " + socket.getInetAddress());
        }
    }

    // ─── Phase AUTHORIZATION ────────────────────────────────────────────────

    private void handleUser(String arg) {
        if (state != Pop3State.AUTHORIZATION) {
            out.println("-ERR Already authenticated");
            return;
        }
        // CORRECTION : utiliser UserAuth.userExists() au lieu de tester le répertoire
        if (UserAuth.userExists(arg)) {
            username = arg;
            userDir  = new File("mailserver/" + arg);
            out.println("+OK User accepted, send password");
        } else {
            out.println("-ERR User not found");
        }
    }

    private void handlePass(String arg) {
        if (state != Pop3State.AUTHORIZATION || username == null) {
            out.println("-ERR Send USER first");
            return;
        }
        if (!UserAuth.authenticate(username, arg)) {
            // CORRECTION : message d'erreur manquant dans l'original
            out.println("-ERR Invalid password");
            username = null; // reset pour permettre un nouvel essai
            return;
        }

        // Authentification réussie : charger les emails
        authenticated = true;
        state = Pop3State.TRANSACTION;

        emails = new ArrayList<>();
        deletionFlags = new ArrayList<>();

        // Créer le répertoire si inexistant (première connexion)
        if (!userDir.exists()) userDir.mkdirs();

        File[] files = userDir.listFiles(File::isFile);
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File f : files) {
                emails.add(f);
                deletionFlags.add(false);
            }
        }

        long totalSize = emails.stream().mapToLong(File::length).sum();
        out.println("+OK " + emails.size() + " messages (" + totalSize + " octets)");
    }

    // ─── Phase TRANSACTION ──────────────────────────────────────────────────

    private void handleStat() {
        if (!authenticated) { out.println("-ERR Authentication required"); return; }

        // CORRECTION : implémentation complète (était vide dans l'original)
        long totalSize = 0;
        int count = 0;
        for (int i = 0; i < emails.size(); i++) {
            if (!deletionFlags.get(i)) {
                count++;
                totalSize += emails.get(i).length();
            }
        }
        out.println("+OK " + count + " " + totalSize);
    }

    private void handleList(String arg) {
        if (!authenticated) { out.println("-ERR Authentication required"); return; }

        if (arg.isEmpty()) {
            // Lister tous les messages non supprimés
            long totalSize = 0;
            int count = 0;
            for (int i = 0; i < emails.size(); i++) {
                if (!deletionFlags.get(i)) {
                    count++;
                    totalSize += emails.get(i).length();
                }
            }
            out.println("+OK " + count + " messages (" + totalSize + " octets)");
            for (int i = 0; i < emails.size(); i++) {
                if (!deletionFlags.get(i)) {
                    out.println((i + 1) + " " + emails.get(i).length());
                }
            }
            out.println(".");
        } else {
            // Lister un message spécifique
            try {
                int idx = Integer.parseInt(arg.trim()) - 1;
                if (idx < 0 || idx >= emails.size() || deletionFlags.get(idx)) {
                    out.println("-ERR No such message");
                    return;
                }
                out.println("+OK " + (idx + 1) + " " + emails.get(idx).length());
            } catch (NumberFormatException e) {
                out.println("-ERR Invalid argument");
            }
        }
    }

    private void handleRetr(String arg) {
        if (!authenticated) { out.println("-ERR Authentication required"); return; }
        try {
            int idx = Integer.parseInt(arg.trim()) - 1;
            if (idx < 0 || idx >= emails.size()) {
                out.println("-ERR No such message"); return;
            }
            if (deletionFlags.get(idx)) {
                out.println("-ERR Message already deleted"); return;
            }
            File emailFile = emails.get(idx);
            out.println("+OK " + emailFile.length() + " octets");
            try (BufferedReader reader = new BufferedReader(new FileReader(emailFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Byte stuffing : ligne commençant par "." → ".."
                    if (line.startsWith(".")) out.println("." + line);
                    else out.println(line);
                }
            }
            out.println(".");
        } catch (NumberFormatException e) {
            out.println("-ERR Invalid message number");
        } catch (IOException e) {
            out.println("-ERR Error reading message");
        }
    }

    private void handleDele(String arg) {
        if (!authenticated) { out.println("-ERR Authentication required"); return; }
        try {
            int idx = Integer.parseInt(arg.trim()) - 1;
            if (idx < 0 || idx >= emails.size()) {
                out.println("-ERR No such message"); return;
            }
            if (deletionFlags.get(idx)) {
                out.println("-ERR Message already marked for deletion"); return;
            }
            deletionFlags.set(idx, true);
            out.println("+OK Message " + (idx + 1) + " marked for deletion");
        } catch (NumberFormatException e) {
            out.println("-ERR Invalid message number");
        }
    }

    private void handleRset() {
        if (!authenticated) { out.println("-ERR Authentication required"); return; }
        for (int i = 0; i < deletionFlags.size(); i++) deletionFlags.set(i, false);
        out.println("+OK Deletion marks cleared");
    }

    /**
     * TOP n l — Renvoie les l premières lignes du corps du message n.
     * Commande utile pour les clients mail légers (Thunderbird l'utilise).
     */
    private void handleTop(String arg) {
        if (!authenticated) { out.println("-ERR Authentication required"); return; }
        try {
            String[] parts = arg.trim().split("\\s+");
            int idx   = Integer.parseInt(parts[0]) - 1;
            int lines = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

            if (idx < 0 || idx >= emails.size() || deletionFlags.get(idx)) {
                out.println("-ERR No such message"); return;
            }
            out.println("+OK Top of message follows");
            boolean inBody = false;
            int bodyLines = 0;
            try (BufferedReader reader = new BufferedReader(new FileReader(emails.get(idx)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!inBody) {
                        out.println(line);
                        if (line.isEmpty()) inBody = true; // fin des headers
                    } else {
                        if (bodyLines >= lines) break;
                        out.println(line);
                        bodyLines++;
                    }
                }
            }
            out.println(".");
        } catch (Exception e) {
            out.println("-ERR Error in TOP command");
        }
    }

    /**
     * UIDL — Identifiant unique basé sur le nom de fichier.
     */
    private void handleUidl(String arg) {
        if (!authenticated) { out.println("-ERR Authentication required"); return; }
        if (arg.isEmpty()) {
            out.println("+OK Unique-ID listing follows");
            for (int i = 0; i < emails.size(); i++) {
                if (!deletionFlags.get(i)) {
                    out.println((i + 1) + " " + emails.get(i).getName().replace(".txt", ""));
                }
            }
            out.println(".");
        } else {
            try {
                int idx = Integer.parseInt(arg.trim()) - 1;
                if (idx < 0 || idx >= emails.size() || deletionFlags.get(idx)) {
                    out.println("-ERR No such message"); return;
                }
                out.println("+OK " + (idx + 1) + " " +
                        emails.get(idx).getName().replace(".txt", ""));
            } catch (NumberFormatException e) {
                out.println("-ERR Invalid argument");
            }
        }
    }

    // ─── Phase UPDATE (QUIT) ────────────────────────────────────────────────

    private void handleQuit() {
        state = Pop3State.UPDATE;
        // Supprimer les fichiers marqués (en partant de la fin pour ne pas
        // décaler les indices)
        for (int i = deletionFlags.size() - 1; i >= 0; i--) {
            if (deletionFlags.get(i)) {
                File f = emails.get(i);
                if (f.delete()) {
                    System.out.println("[POP3] Deleted: " + f.getName());
                } else {
                    System.err.println("[POP3] Could not delete: " + f.getName());
                }
            }
        }
        out.println("+OK POP3 server signing off");
    }
}