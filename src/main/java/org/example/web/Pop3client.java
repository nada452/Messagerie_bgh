package org.example.web;

import java.io.*;
import java.net.*;
import java.util.*;


public class Pop3client {

    private final String host;
    private final int port;

    public Pop3client(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static class Email {
        public int    number;
        public String from;
        public String to;
        public String subject;
        public String date;
        public String body;
        public String raw;

        public Email(int number, String raw) {
            this.number  = number;
            this.raw     = raw;
            this.subject = "(no subject)";
            this.from    = "";
            this.to      = "";
            this.date    = "";
            this.body    = "";
            parseRaw(raw);
        }

        private void parseRaw(String raw) {
            String[] lines = raw.split("\r?\n");
            boolean inBody = false;
            StringBuilder bodyBuilder = new StringBuilder();

            for (String line : lines) {
                if (!inBody) {
                    if (line.isEmpty()) { inBody = true; continue; }
                    String upper = line.toUpperCase();
                    if (upper.startsWith("FROM:"))    from    = line.substring(5).trim();
                    else if (upper.startsWith("TO:")) to      = line.substring(3).trim();
                    else if (upper.startsWith("SUBJECT:")) subject = line.substring(8).trim();
                    else if (upper.startsWith("DATE:"))    date    = line.substring(5).trim();
                } else {
                    bodyBuilder.append(line).append("\n");
                }
            }
            body = bodyBuilder.toString().trim();
        }
    }



    /** Récupère tous les emails d'un utilisateur. */
    public List<Email> fetchEmails(String username, String password) throws IOException {
        List<Email> emails = new ArrayList<>();

        try (Socket socket = new Socket(host, port);
             BufferedReader in  = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()));
             PrintWriter    out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream()), true)) {

            readLine(in); // +OK greeting

            // Authentification
            out.println("USER " + username);
            String resp = readLine(in);
            if (!resp.startsWith("+OK")) throw new IOException("USER rejected: " + resp);

            out.println("PASS " + password);
            resp = readLine(in);
            if (!resp.startsWith("+OK")) throw new IOException("PASS rejected: " + resp);

            // Nombre de messages
            out.println("STAT");
            resp = readLine(in); // +OK n totalSize
            int count = 0;
            if (resp.startsWith("+OK")) {
                String[] parts = resp.split("\\s+");
                if (parts.length >= 2) count = Integer.parseInt(parts[1]);
            }

            // Récupérer chaque message
            for (int i = 1; i <= count; i++) {
                out.println("RETR " + i);
                String retrResp = readLine(in);
                if (!retrResp.startsWith("+OK")) continue;

                StringBuilder raw = new StringBuilder();
                String line;
                while (!(line = readLine(in)).equals(".")) {
                    // Byte unstuffing
                    if (line.startsWith("..")) line = line.substring(1);
                    raw.append(line).append("\r\n");
                }
                emails.add(new Email(i, raw.toString()));
            }

            out.println("QUIT");
            readLine(in); // +OK signing off
        }

        return emails;
    }

    /** Supprime un email (par son numéro POP3). */
    public boolean deleteEmail(String username, String password, int msgNumber)
            throws IOException {
        try (Socket socket = new Socket(host, port);
             BufferedReader in  = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()));
             PrintWriter    out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream()), true)) {

            readLine(in); // greeting

            out.println("USER " + username);
            String resp = readLine(in);
            if (!resp.startsWith("+OK")) return false;

            out.println("PASS " + password);
            resp = readLine(in);
            if (!resp.startsWith("+OK")) return false;

            out.println("DELE " + msgNumber);
            resp = readLine(in);
            boolean ok = resp.startsWith("+OK");

            out.println("QUIT");
            readLine(in);
            return ok;
        }
    }

    private String readLine(BufferedReader in) throws IOException {
        String line = in.readLine();
        return line != null ? line : "";
    }
}