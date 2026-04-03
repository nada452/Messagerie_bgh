package org.example.clients;

import org.example.web.Smtpclient;
import java.util.Scanner;

public class SmtpClientApp {

    private static final String HOST = "localhost";
    private static final int    PORT = 2525;
    private static final Scanner sc  = new Scanner(System.in);

    public static void main(String[] args) {
        printBanner();

        while (true) {
            printMenu();
            String choice = sc.nextLine().trim();

            switch (choice) {
                case "1": sendEmail();   break;
                case "2": sendMultiple(); break;
                case "0":
                    System.out.println("\n  Au revoir !");
                    return;
                default:
                    System.out.println("  [!] Choix invalide.");
            }
        }
    }

    // ─── Menu ─────────────────────────────────────────────────────────────

    static void printBanner() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════╗");
        System.out.println("  ║       CLIENT SMTP — port " + PORT + "         ║");
        System.out.println("  ║   Serveur : " + HOST + "                   ║");
        System.out.println("  ╚══════════════════════════════════════╝");
        System.out.println();
    }

    static void printMenu() {
        System.out.println("  ┌─────────────────────────────────┐");
        System.out.println("  │  1. Envoyer un email            │");
        System.out.println("  │  2. Envoyer plusieurs emails    │");
        System.out.println("  │  0. Quitter                     │");
        System.out.println("  └─────────────────────────────────┘");
        System.out.print("  Choix : ");
    }

    // ─── Actions ──────────────────────────────────────────────────────────

    static void sendEmail() {
        System.out.println("\n  --- Nouvel email ---");
        System.out.print("  De (ex: user1@example.com) : ");
        String from = sc.nextLine().trim();

        System.out.print("  À  (ex: user2@example.com) : ");
        String to = sc.nextLine().trim();

        System.out.print("  Sujet : ");
        String subject = sc.nextLine().trim();
        if (subject.isEmpty()) subject = "(sans sujet)";

        System.out.println("  Message (terminer par une ligne vide) :");
        StringBuilder body = new StringBuilder();
        String line;
        while (!(line = sc.nextLine()).isEmpty()) {
            body.append(line).append("\n");
        }

        System.out.println("\n  [→] Connexion à SmtpServer:" + PORT + "...");
        try {
            Smtpclient client = new Smtpclient(HOST, PORT);
            client.sendEmail(from, to, subject, body.toString());
            System.out.println("  [✓] Email envoyé avec succès !");
            System.out.println("      De      : " + from);
            System.out.println("      À       : " + to);
            System.out.println("      Sujet   : " + subject);
        } catch (Exception e) {
            System.out.println("  [✗] Erreur : " + e.getMessage());
            System.out.println("      Vérifiez que SmtpServer est démarré.");
        }
        System.out.println();
    }

    static void sendMultiple() {
        System.out.println("\n  --- Envoi multiple ---");
        System.out.print("  De : ");
        String from = sc.nextLine().trim();
        System.out.print("  Destinataires séparés par virgule : ");
        String[] tos = sc.nextLine().split(",");
        System.out.print("  Sujet : ");
        String subject = sc.nextLine().trim();
        System.out.println("  Message (ligne vide pour terminer) :");
        StringBuilder body = new StringBuilder();
        String line;
        while (!(line = sc.nextLine()).isEmpty()) body.append(line).append("\n");

        Smtpclient client = new Smtpclient(HOST, PORT);
        int ok = 0, fail = 0;
        for (String to : tos) {
            to = to.trim();
            try {
                client.sendEmail(from, to, subject, body.toString());
                System.out.println("  [✓] Envoyé à " + to);
                ok++;
            } catch (Exception e) {
                System.out.println("  [✗] Échec pour " + to + " : " + e.getMessage());
                fail++;
            }
        }
        System.out.println("\n  Résultat : " + ok + " succès, " + fail + " échec(s)\n");
    }
}