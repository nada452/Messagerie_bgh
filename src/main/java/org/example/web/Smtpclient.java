package org.example.web;

import java.io.*;
import java.net.*;

/**
 * SmtpClient — Client SMTP utilisé par le WebServer pour envoyer des emails
 * via le SmtpServer.
 *
 * Flux : WebServer → SmtpClient → SmtpServer(port 2525) → fichiers
 */
public class Smtpclient {

    private final String host;
    private final int    port;

    public Smtpclient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Envoie un email via SMTP.
     * @param from    Adresse expéditeur (ex: user1@example.com)
     * @param to      Adresse destinataire
     * @param subject Sujet
     * @param body    Corps du message
     * @throws IOException si la connexion échoue ou si le serveur refuse
     */
    public void sendEmail(String from, String to, String subject, String body)
            throws IOException {

        try (Socket socket = new Socket(host, port);
             BufferedReader in  = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()));
             PrintWriter    out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream()), true)) {

            expect(in, "220"); // greeting

            out.println("EHLO webmail.example.com");
            expect(in, "250");

            out.println("MAIL FROM: <" + from + ">");
            expect(in, "250");

            out.println("RCPT TO: <" + to + ">");
            expect(in, "250");

            out.println("DATA");
            expect(in, "354");

            // En-têtes RFC 5322
            out.println("From: " + from);
            out.println("To: "   + to);
            out.println("Subject: " + subject);
            out.println("MIME-Version: 1.0");
            out.println("Content-Type: text/plain; charset=UTF-8");
            out.println();

            // Corps : byte-stuffing des lignes commençant par "."
            for (String line : body.split("\r?\n")) {
                if (line.startsWith(".")) out.println("." + line);
                else out.println(line);
            }
            out.println(".");
            expect(in, "250");

            out.println("QUIT");
            // Pas besoin de lire le 221
        }
    }

    private void expect(BufferedReader in, String code) throws IOException {
        String line = in.readLine();
        if (line == null || !line.startsWith(code)) {
            throw new IOException("Expected " + code + " but got: " + line);
        }
        // EHLO peut renvoyer plusieurs lignes "250-..."
        while (line != null && line.startsWith(code + "-")) {
            line = in.readLine();
        }
    }
}