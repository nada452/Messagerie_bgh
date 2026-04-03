package org.example.clients;

import org.example.web.Imapclient;
import org.example.web.Imapclient.Email;
import java.util.List;
import java.util.Scanner;


public class ImapClientApp {

    private static final String  HOST = "localhost";
    private static final int     PORT = 143;
    private static final Scanner sc   = new Scanner(System.in);

    private static String      username;
    private static String      password;
    private static List<Email> emails;

    public static void main(String[] args) {
        printBanner();

        if (!login()) return;

        loadEmails();

        while (true) {
            printMenu();
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1": listEmails();   break;
                case "2": readEmail();    break;
                case "3": markSeen();     break;
                case "4": markUnseen();   break;
                case "5": searchEmails(); break;
                case "6": deleteEmail();  break;
                case "7": loadEmails();   break;
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
        System.out.println("  ║       CLIENT IMAP — port " + PORT + "          ║");
        System.out.println("  ║   Serveur : " + HOST + "                   ║");
        System.out.println("  ╚══════════════════════════════════════╝");
        System.out.println();
    }

    static void printMenu() {
        int total  = emails == null ? 0 : emails.size();
        long unseen = emails == null ? 0 : emails.stream().filter(e -> !e.seen).count();
        System.out.println();
        System.out.println("  ┌────────────────────────────────────────────────┐");
        System.out.printf ("  │  INBOX de %s — %d msg (%d non lu(s))%n",
                username, total, unseen);
        System.out.println("  ├────────────────────────────────────────────────┤");
        System.out.println("  │  1. Lister les messages                        │");
        System.out.println("  │  2. Lire un message                            │");
        System.out.println("  │  3. Marquer comme lu     (STORE +FLAGS \\Seen) │");
        System.out.println("  │  4. Marquer comme non-lu (STORE -FLAGS \\Seen) │");
        System.out.println("  │  5. Rechercher           (SEARCH)              │");
        System.out.println("  │  6. Supprimer            (EXPUNGE)             │");
        System.out.println("  │  7. Rafraîchir                                 │");
        System.out.println("  │  0. Quitter                                    │");
        System.out.println("  └────────────────────────────────────────────────┘");
        System.out.print("  Choix : ");
    }

    // ─── Auth ─────────────────────────────────────────────────────────────

    static boolean login() {
        System.out.println("  --- Connexion IMAP ---");
        System.out.print("  Identifiant : ");
        username = sc.nextLine().trim();
        System.out.print("  Mot de passe : ");
        password = sc.nextLine().trim();

        System.out.println("  [→] Connexion à ImapServer:" + PORT + "...");
        try {
            emails = new Imapclient(HOST, PORT).fetchHeaders(username, password);
            System.out.println("  [✓] Authentifié — " + emails.size() + " message(s)\n");
            return true;
        } catch (Exception e) {
            System.out.println("  [✗] Échec : " + e.getMessage());
            System.out.println("      Vérifiez que ImapServer est démarré sur le port " + PORT);
            return false;
        }
    }

    // ─── Actions ──────────────────────────────────────────────────────────

    static void loadEmails() {
        System.out.println("\n  [→] Récupération des en-têtes (FETCH BODY[HEADER])...");
        try {
            emails = new Imapclient(HOST, PORT).fetchHeaders(username, password);
            long unseen = emails.stream().filter(e -> !e.seen).count();
            System.out.println("  [✓] " + emails.size() + " message(s), "
                    + unseen + " non lu(s)\n");
        } catch (Exception e) {
            System.out.println("  [✗] Erreur : " + e.getMessage() + "\n");
        }
    }

    static void listEmails() {
        System.out.println();
        if (emails.isEmpty()) {
            System.out.println("  Aucun message.\n"); return;
        }
        System.out.println("  ┌────┬────┬──────────────────────────────┬──────────────────────────────┐");
        System.out.printf ("  │ %-2s │ %-2s │ %-28s │ %-28s │%n", "N°", "Lu", "Expéditeur", "Sujet");
        System.out.println("  ├────┼────┼──────────────────────────────┼──────────────────────────────┤");
        for (Email e : emails) {
            String lu   = e.seen ? "✓" : "●";
            String from = truncate(e.from,    28);
            String subj = truncate(e.subject, 28);
            System.out.printf("  │ %-2d │ %-2s │ %-28s │ %-28s │%n",
                    e.number, lu, from, subj);
        }
        System.out.println("  └────┴────┴──────────────────────────────┴──────────────────────────────┘");
        System.out.println("  Légende : ● = non lu   ✓ = lu");
        System.out.println();
    }

    static void readEmail() {
        System.out.print("\n  Numéro du message à lire : ");
        try {
            int n = Integer.parseInt(sc.nextLine().trim());
            System.out.println("  [→] Lecture du message (FETCH " + n + " BODY[])...");
            Email email = new Imapclient(HOST, PORT).fetchOne(username, password, n);
            if (email == null) {
                System.out.println("  [!] Message introuvable.\n"); return;
            }
            System.out.println();
            System.out.println("  ╔══════════════════════════════════════════════════╗");
            System.out.println("  ║  MESSAGE #" + n + "  [automatiquement marqué lu]");
            System.out.println("  ╠══════════════════════════════════════════════════╣");
            System.out.println("  ║  De    : " + email.from);
            System.out.println("  ║  À     : " + email.to);
            System.out.println("  ║  Sujet : " + email.subject);
            System.out.println("  ║  Date  : " + email.date);
            System.out.println("  ╠══════════════════════════════════════════════════╣");
            System.out.println("  ║  Corps :");
            System.out.println();
            for (String line : email.body.split("\n")) {
                System.out.println("      " + line);
            }
            System.out.println();
            System.out.println("  ╚══════════════════════════════════════════════════╝");

            // Mettre à jour le flag seen dans la liste locale
            emails.stream().filter(e -> e.number == n)
                    .forEach(e -> e.seen = true);
            System.out.println();
        } catch (Exception e) {
            System.out.println("  [✗] Erreur : " + e.getMessage() + "\n");
        }
    }

    static void markSeen() {
        System.out.print("\n  Numéro du message à marquer lu : ");
        try {
            int n = Integer.parseInt(sc.nextLine().trim());
            System.out.println("  [→] STORE " + n + " +FLAGS (\\Seen)");
            new Imapclient(HOST, PORT).setSeen(username, password, n, true);
            emails.stream().filter(e -> e.number == n).forEach(e -> e.seen = true);
            System.out.println("  [✓] Message #" + n + " marqué comme lu.\n");
        } catch (Exception e) {
            System.out.println("  [✗] Erreur : " + e.getMessage() + "\n");
        }
    }

    static void markUnseen() {
        System.out.print("\n  Numéro du message à marquer non-lu : ");
        try {
            int n = Integer.parseInt(sc.nextLine().trim());
            System.out.println("  [→] STORE " + n + " -FLAGS (\\Seen)");
            new Imapclient(HOST, PORT).setSeen(username, password, n, false);
            emails.stream().filter(e -> e.number == n).forEach(e -> e.seen = false);
            System.out.println("  [✓] Message #" + n + " marqué comme non-lu.\n");
        } catch (Exception e) {
            System.out.println("  [✗] Erreur : " + e.getMessage() + "\n");
        }
    }

    static void searchEmails() {
        System.out.println("\n  --- Recherche IMAP (SEARCH) ---");
        System.out.println("  Critères disponibles :");
        System.out.println("    1. Tous les messages        (ALL)");
        System.out.println("    2. Non lus                  (UNSEEN)");
        System.out.println("    3. Lus                      (SEEN)");
        System.out.println("    4. Par expéditeur           (FROM)");
        System.out.println("    5. Par sujet                (SUBJECT)");
        System.out.print("  Critère : ");

        String criteria;
        switch (sc.nextLine().trim()) {
            case "1": criteria = "ALL";    break;
            case "2": criteria = "UNSEEN"; break;
            case "3": criteria = "SEEN";   break;
            case "4":
                System.out.print("  Expéditeur contient : ");
                criteria = "FROM " + sc.nextLine().trim();
                break;
            case "5":
                System.out.print("  Sujet contient : ");
                criteria = "SUBJECT " + sc.nextLine().trim();
                break;
            default:
                System.out.println("  [!] Critère invalide.\n"); return;
        }

        System.out.println("  [→] SEARCH " + criteria + "...");
        try {
            List<Email> results = new Imapclient(HOST, PORT)
                    .searchEmails(username, password, criteria);

            System.out.println("  [✓] " + results.size() + " résultat(s) :\n");
            if (results.isEmpty()) {
                System.out.println("  Aucun message ne correspond.\n");
            } else {
                for (Email e : results) {
                    System.out.printf("  #%-2d  [%s]  %-30s  %s%n",
                            e.number,
                            e.seen ? "lu  " : "NEUF",
                            truncate(e.from, 30),
                            truncate(e.subject, 35));
                }
                System.out.println();
            }
        } catch (Exception e) {
            System.out.println("  [✗] Erreur : " + e.getMessage() + "\n");
        }
    }

    static void deleteEmail() {
        System.out.print("\n  Numéro du message à supprimer : ");
        try {
            int n = Integer.parseInt(sc.nextLine().trim());
            System.out.print("  Confirmer ? (o/n) : ");
            if (!sc.nextLine().trim().equalsIgnoreCase("o")) {
                System.out.println("  Annulé.\n"); return;
            }
            System.out.println("  [→] STORE " + n + " +FLAGS (\\Deleted) + EXPUNGE...");
            boolean ok = new Imapclient(HOST, PORT).deleteEmail(username, password, n);
            if (ok) {
                System.out.println("  [✓] Message #" + n + " supprimé.\n");
                loadEmails();
            } else {
                System.out.println("  [✗] Suppression échouée.\n");
            }
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