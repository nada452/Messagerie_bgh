package org.example.web;

import java.io.*;
import java.net.*;
import java.util.*;
public class Imapclient {



        private final String host;
        private final int    port;
        private int tagCounter = 1;



        public static class Email {
            public int     number;
            public String  from;
            public String  to;
            public String  subject;
            public String  date;
            public String  body;
            public boolean seen;    // flag \Seen

            public Email(int number) {
                this.number  = number;
                this.subject = "(no subject)";
                this.from    = "";
                this.to      = "";
                this.date    = "";
                this.body    = "";
                this.seen    = false;
            }

            public void parseRaw(String raw) {
                String[] lines = raw.split("\r?\n");
                boolean inBody = false;
                StringBuilder bodyBuilder = new StringBuilder();

                for (String line : lines) {
                    if (!inBody) {
                        if (line.isEmpty()) { inBody = true; continue; }
                        String upper = line.toUpperCase();
                        if      (upper.startsWith("FROM:"))    from    = line.substring(5).trim();
                        else if (upper.startsWith("TO:"))      to      = line.substring(3).trim();
                        else if (upper.startsWith("SUBJECT:")) subject = line.substring(8).trim();
                        else if (upper.startsWith("DATE:"))    date    = line.substring(5).trim();
                    } else {
                        bodyBuilder.append(line).append("\n");
                    }
                }
                body = bodyBuilder.toString().trim();
            }
        }

        // ─── Connexion / session interne ──────────────────────────────────────

        /** Représente une session IMAP ouverte (socket + streams). */
        private static class Session implements Closeable {
            final Socket       socket;
            final BufferedReader in;
            final PrintWriter    out;

            Session(String host, int port) throws IOException {
                socket = new Socket(host, port);
                socket.setSoTimeout(5000); // timeout 5s
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            }

            @Override
            public void close() {
                try { socket.close(); } catch (IOException e) { /* ignore */ }
            }
        }

        public Imapclient(String host, int port) {
            this.host = host;
            this.port = port;
        }

        private String nextTag() {
            return "w" + (tagCounter++);
        }

        // ─── API publique ─────────────────────────────────────────────────────

        /**
         * Récupère tous les emails de la boîte INBOX.
         * Utilise FETCH BODY[] pour lire le contenu complet.
         * Les emails restent sur le serveur (contrairement à POP3).
         */
        public List<Email> fetchEmails(String username, String password) throws IOException {
            List<Email> emails = new ArrayList<>();

            try (Session s = new Session(host, port)) {
                readGreeting(s);
                login(s, username, password);

                // SELECT INBOX → récupère le nombre de messages
                int count = selectInbox(s);

                // FETCH 1:N BODY[] pour lire tous les messages
                if (count > 0) {
                    emails = fetchAll(s, count);
                }

                logout(s);
            }

            return emails;
        }

        /**
         * Récupère uniquement les en-têtes (plus rapide, sans télécharger les corps).
         * Utilise FETCH 1:N BODY[HEADER].
         */
        public List<Email> fetchHeaders(String username, String password) throws IOException {
            List<Email> emails = new ArrayList<>();

            try (Session s = new Session(host, port)) {
                readGreeting(s);
                login(s, username, password);
                int count = selectInbox(s);

                if (count > 0) {
                    emails = fetchHeadersOnly(s, count);
                }

                logout(s);
            }

            return emails;
        }

        /**
         * Lit un seul message complet.
         * @param msgNum numéro IMAP (commence à 1)
         */
        public Email fetchOne(String username, String password, int msgNum) throws IOException {
            try (Session s = new Session(host, port)) {
                readGreeting(s);
                login(s, username, password);
                selectInbox(s);

                Email email = fetchSingle(s, msgNum);

                // Marquer comme lu
                markSeen(s, msgNum);

                logout(s);
                return email;
            }
        }

        /**
         * Marque un message comme lu (\Seen) ou non-lu (-\Seen).
         */
        public void setSeen(String username, String password, int msgNum, boolean seen)
                throws IOException {
            try (Session s = new Session(host, port)) {
                readGreeting(s);
                login(s, username, password);
                selectInbox(s);

                String tag = nextTag();
                String op  = seen ? "+FLAGS" : "-FLAGS";
                s.out.println(tag + " STORE " + msgNum + " " + op + " (\\Seen)");
                readUntilTagged(s, tag);

                logout(s);
            }
        }

        /**
         * Supprime un message (STORE \Deleted + EXPUNGE).
         * Contrairement à POP3, la suppression est immédiate et précise.
         */
        public boolean deleteEmail(String username, String password, int msgNum)
                throws IOException {
            try (Session s = new Session(host, port)) {
                readGreeting(s);
                login(s, username, password);
                selectInbox(s);

                // Marquer \Deleted
                String tag1 = nextTag();
                s.out.println(tag1 + " STORE " + msgNum + " +FLAGS (\\Deleted)");
                String resp1 = readUntilTagged(s, tag1);

                if (!resp1.contains("OK")) return false;

                // EXPUNGE pour supprimer physiquement
                String tag2 = nextTag();
                s.out.println(tag2 + " EXPUNGE");
                readUntilTagged(s, tag2);

                logout(s);
                return true;
            }
        }

        /**
         * Recherche des messages selon un critère IMAP.
         * @param criteria ex: "ALL", "UNSEEN", "FROM user1", "SUBJECT test"
         * @return liste des numéros de messages correspondants
         */
        public List<Integer> search(String username, String password, String criteria)
                throws IOException {
            List<Integer> results = new ArrayList<>();

            try (Session s = new Session(host, port)) {
                readGreeting(s);
                login(s, username, password);
                selectInbox(s);

                String tag = nextTag();
                s.out.println(tag + " SEARCH " + criteria);

                String line;
                while ((line = s.in.readLine()) != null) {
                    if (line.startsWith("* SEARCH")) {
                        // "* SEARCH 1 3 5" → extraire les numéros
                        String[] parts = line.split("\\s+");
                        for (int i = 2; i < parts.length; i++) {
                            try { results.add(Integer.parseInt(parts[i])); }
                            catch (NumberFormatException e) { /* ignorer */ }
                        }
                    }
                    if (line.startsWith(tag)) break;
                }

                logout(s);
            }

            return results;
        }

        /**
         * Recherche et retourne les emails complets correspondant aux critères.
         */
        public List<Email> searchEmails(String username, String password, String criteria)
                throws IOException {
            List<Email> results = new ArrayList<>();

            try (Session s = new Session(host, port)) {
                readGreeting(s);
                login(s, username, password);
                selectInbox(s);

                // SEARCH pour obtenir les numéros
                String tag1 = nextTag();
                s.out.println(tag1 + " SEARCH " + criteria);

                List<Integer> nums = new ArrayList<>();
                String line;
                while ((line = s.in.readLine()) != null) {
                    if (line.startsWith("* SEARCH")) {
                        String[] parts = line.split("\\s+");
                        for (int i = 2; i < parts.length; i++) {
                            try { nums.add(Integer.parseInt(parts[i])); }
                            catch (NumberFormatException e) { /* ignorer */ }
                        }
                    }
                    if (line.startsWith(tag1)) break;
                }

                // FETCH chaque message trouvé
                for (int n : nums) {
                    Email e = fetchSingle(s, n);
                    if (e != null) results.add(e);
                }

                logout(s);
            }

            return results;
        }

        // ─── Méthodes IMAP internes ──────────────────────────────────────────

        private void readGreeting(Session s) throws IOException {
            s.in.readLine(); // * OK ... Server Ready
        }

        private void login(Session s, String username, String password) throws IOException {
            String tag = nextTag();
            s.out.println(tag + " LOGIN " + username + " " + password);
            String resp = readUntilTagged(s, tag);
            if (!resp.contains("OK")) {
                throw new IOException("IMAP LOGIN failed: " + resp);
            }
        }

        private int selectInbox(Session s) throws IOException {
            String tag = nextTag();
            s.out.println(tag + " SELECT INBOX");

            int count = 0;
            String line;
            while ((line = s.in.readLine()) != null) {
                // "* 3 EXISTS" → 3 messages
                if (line.matches("\\* \\d+ EXISTS")) {
                    count = Integer.parseInt(line.split("\\s+")[1]);
                }
                if (line.startsWith(tag)) break;
            }
            return count;
        }

        private List<Email> fetchAll(Session s, int count) throws IOException {
            List<Email> emails = new ArrayList<>();
            String tag = nextTag();
            s.out.println(tag + " FETCH 1:" + count + " BODY[]");

            StringBuilder current = new StringBuilder();
            int currentNum = 0;
            boolean inLiteral = false;
            int literalRemaining = 0;

            String line;
            while ((line = s.in.readLine()) != null) {
                if (line.startsWith(tag)) break;

                // Détecter "* N FETCH (...{SIZE}"
                if (line.matches("\\* \\d+ FETCH .*")) {
                    String[] parts = line.split("\\s+");
                    currentNum = Integer.parseInt(parts[1]);
                    current    = new StringBuilder();

                    // Taille du littéral : {N}
                    if (line.endsWith("}")) {
                        int open  = line.lastIndexOf('{');
                        literalRemaining = Integer.parseInt(
                                line.substring(open + 1, line.length() - 1));
                        inLiteral = true;
                    }
                    continue;
                }

                if (inLiteral) {
                    current.append(line).append("\n");
                    literalRemaining -= (line.length() + 1);
                    if (literalRemaining <= 0) {
                        inLiteral = false;
                        Email email = new Email(currentNum);
                        email.parseRaw(current.toString());
                        emails.add(email);
                    }
                    continue;
                }

                // Ligne ")" = fin du FETCH
                if (line.equals(")") && currentNum > 0 && current.length() > 0) {
                    Email email = new Email(currentNum);
                    email.parseRaw(current.toString());
                    emails.add(email);
                    currentNum = 0;
                    current    = new StringBuilder();
                } else if (currentNum > 0) {
                    current.append(line).append("\n");
                }
            }

            return emails;
        }

        private List<Email> fetchHeadersOnly(Session s, int count) throws IOException {
            List<Email> emails = new ArrayList<>();
            String tag = nextTag();
            s.out.println(tag + " FETCH 1:" + count + " BODY[HEADER]");

            StringBuilder current = new StringBuilder();
            int currentNum = 0;

            String line;
            while ((line = s.in.readLine()) != null) {
                if (line.startsWith(tag)) break;

                if (line.matches("\\* \\d+ FETCH .*")) {
                    currentNum = Integer.parseInt(line.split("\\s+")[1]);
                    current    = new StringBuilder();
                    continue;
                }

                if (line.equals(")") && currentNum > 0) {
                    Email email = new Email(currentNum);
                    email.parseRaw(current.toString());
                    emails.add(email);
                    currentNum = 0;
                    current    = new StringBuilder();
                } else if (currentNum > 0 && !line.startsWith("{")) {
                    current.append(line).append("\n");
                }
            }

            return emails;
        }

        private Email fetchSingle(Session s, int msgNum) throws IOException {
            String tag = nextTag();
            s.out.println(tag + " FETCH " + msgNum + " BODY[]");

            StringBuilder content = new StringBuilder();
            boolean collecting = false;

            String line;
            while ((line = s.in.readLine()) != null) {
                if (line.startsWith(tag)) break;

                if (line.matches("\\* " + msgNum + " FETCH .*")) {
                    collecting = true; continue;
                }
                if (collecting && line.equals(")")) { collecting = false; continue; }
                if (collecting && !line.startsWith("{")) {
                    content.append(line).append("\n");
                }
            }

            if (content.length() == 0) return null;
            Email email = new Email(msgNum);
            email.parseRaw(content.toString());
            return email;
        }

        private void markSeen(Session s, int msgNum) throws IOException {
            String tag = nextTag();
            s.out.println(tag + " STORE " + msgNum + " +FLAGS (\\Seen)");
            readUntilTagged(s, tag);
        }

        private void logout(Session s) {
            try {
                String tag = nextTag();
                s.out.println(tag + " LOGOUT");
                s.in.readLine(); // * BYE
                s.in.readLine(); // tag OK
            } catch (IOException e) { /* ignore */ }
        }

        /**
         * Lit les lignes jusqu'à trouver la ligne taguée (réponse finale).
         * @return la ligne taguée (ex: "w3 OK FETCH completed")
         */
        private String readUntilTagged(Session s, String tag) throws IOException {
            String line;
            while ((line = s.in.readLine()) != null) {
                if (line.startsWith(tag)) return line;
            }
            return "";
        }
    }
