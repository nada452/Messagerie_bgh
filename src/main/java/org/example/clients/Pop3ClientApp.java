package org.example.clients;

import org.example.web.Pop3client;
import org.example.web.Pop3client.Email;
import java.util.List;
import java.util.Scanner;

public class Pop3ClientApp {

    private static final String  HOST = "localhost";
    private static final int     PORT = 110;
    private static final Scanner sc   = new Scanner(System.in);

    private static String       username;
    private static String       password;
    private static List<Email>  emails;

    public static void main(String[] args) {
        printBanner();

        // Authentification
        if (!login()) return;

        // Charger les emails
        loadEmails();

        // Menu principal
        while (true) {
            printMenu();
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1": listEmails();    break;
                case "2": readEmail();     break;
                case "3": deleteEmail();   break;
                case "4": loadEmails();    break;
                case "0":
                    System.out.println("\n  Au revoir !");
                    return;
                default:
                    System.out.println("  [!] Choix invalide.");
            }
        }
    }

    // ─── Bannière & menu ──────────────────────────────────────────────────

    static void printBanner() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════╗");
        System.out.println("  ║       CLIENT POP3 — port " + PORT + "          ║");
        System.out.println("  ║   Serveur : " + HOST + "                   ║");
        System.out.println("  ╚══════════════════════════════════════╝");
        System.out.println();
    }

    static void printMenu() {
        int total = emails == null ? 0 : emails.size();
        System.out.println("  ┌──────────────────────────────────────────┐");
        System.out.printf ("  │  Boîte de %s (%d message(s))%n", username, total);
        System.out.println("  ├──────────────────────────────────────────┤");
        System.out.println("  │  1. Lister les messages                  │");
        System.out.println("  │  2. Lire un message                      │");
        System.out.println("  │  3. Supprimer un message                 │");
        System.out.println("  │  4. Rafraîchir                           │");
        System.out.println("  │  0. Quitter                              │");
        System.out.println("  └──────────────────────────────────────────┘");
        System.out.print("  Choix : ");
    }

    // ─── Auth ─────────────────────────────────────────────────────────────

    static boolean login() {
        System.out.println("  --- Connexion POP3 ---");
        System.out.print("  Identifiant : ");
        username = sc.nextLine().trim();
        System.out.print("  Mot de passe : ");
        password = sc.nextLine().trim();

        System.out.println("  [→] Connexion à Pop3Server:" + PORT + "...");
        try {
            Pop3client client = new Pop3client(HOST, PORT);
            emails = client.fetchEmails(username, password);
            System.out.println("  [✓] Authentifié — " + emails.size() + " message(s)\n");
            return true;
        } catch (Exception e) {
            System.out.println("  [✗] Échec : " + e.getMessage());
            return false;
        }
    }

    // ─── Actions ──────────────────────────────────────────────────────────

    static void loadEmails() {
        System.out.println("\n  [→] Récupération des messages...");
        try {
            emails = new Pop3client(HOST, PORT).fetchEmails(username, password);
            System.out.println("  [✓] " + emails.size() + " message(s) chargé(s)\n");
        } catch (Exception e) {
            System.out.println("  [✗] Erreur : " + e.getMessage() + "\n");
        }
    }

    static void listEmails() {
        System.out.println();
        if (emails.isEmpty()) {
            System.out.println("  Aucun message.\n"); return;
        }
        System.out.println("  ┌────┬──────────────────────────────┬────────────────────────────────┬──────────────────────┐");
        System.out.printf ("  │ %-2s │ %-28s │ %-30s │ %-20s │%n", "N°", "Expéditeur", "Sujet", "Date");
        System.out.println("  ├────┼──────────────────────────────┼────────────────────────────────┼──────────────────────┤");
        for (Email e : emails) {
            String from  = truncate(e.from,    28);
            String subj  = truncate(e.subject, 30);
            String date  = truncate(e.date,    20);
            System.out.printf("  │ %-2d │ %-28s │ %-30s │ %-20s │%n",
                    e.number, from, subj, date);
        }
        System.out.println("  └────┴──────────────────────────────┴────────────────────────────────┴──────────────────────┘");
        System.out.println();
    }

    static void readEmail() {
        System.out.print("\n  Numéro du message à lire : ");
        try {
            int n = Integer.parseInt(sc.nextLine().trim());
            Email email = emails.stream()
                    .filter(e -> e.number == n).findFirst().orElse(null);
            if (email == null) {
                System.out.println("  [!] Message introuvable.\n"); return;
            }
            System.out.println();
            System.out.println("  ┌─────────────────────────────────────────────┐");
            System.out.println("  │  MESSAGE #" + n);
            System.out.println("  ├─────────────────────────────────────────────┤");
            System.out.println("  │  De      : " + email.from);
            System.out.println("  │  À       : " + email.to);
            System.out.println("  │  Sujet   : " + email.subject);
            System.out.println("  │  Date    : " + email.date);
            System.out.println("  ├─────────────────────────────────────────────┤");
            System.out.println("  │  Corps :");
            System.out.println();
            for (String line : email.body.split("\n")) {
                System.out.println("  " + line);
            }
            System.out.println();
            System.out.println("  └─────────────────────────────────────────────┘");
            System.out.println();
        } catch (NumberFormatException e) {
            System.out.println("  [!] Numéro invalide.\n");
        }
    }

    static void deleteEmail() {
        System.out.print("\n  Numéro du message à supprimer : ");
        try {
            int n = Integer.parseInt(sc.nextLine().trim());
            System.out.print("  Confirmer la suppression ? (o/n) : ");
            if (!sc.nextLine().trim().equalsIgnoreCase("o")) {
                System.out.println("  Annulé.\n"); return;
            }
            System.out.println("  [→] Suppression en cours (DELE + QUIT)...");
            new Pop3client(HOST, PORT).deleteEmail(username, password, n);
            System.out.println("  [✓] Message #" + n + " supprimé.\n");
            loadEmails(); // Rafraîchir la liste
        } catch (Exception e) {
            System.out.println("  [✗] Erreur : " + e.getMessage() + "\n");
        }
    }

    // ─── Utilitaire ───────────────────────────────────────────────────────

    static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}