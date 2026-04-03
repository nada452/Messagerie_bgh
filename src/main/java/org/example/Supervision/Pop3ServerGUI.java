package org.example.Supervision;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Pop3ServerGUI extends JFrame {

    // ─── Constantes & Styles ─────────────────────────────────────────────────
    private static final int    PORT       = 110; // Port standard POP3
    private static final String TITLE      = "Supervision — Serveur POP3";
    private static final Color  BG         = new Color(15, 17, 23);
    private static final Color  BG2        = new Color(24, 28, 37);
    private static final Color  BORDER     = new Color(42, 48, 69);
    private static final Color  GREEN      = new Color(57, 211, 83);
    private static final Color  RED        = new Color(224, 82, 82);
    private static final Color  YELLOW     = new Color(224, 168, 68);
    private static final Color  TEXT_DIM   = new Color(107, 114, 128);
    private static final Color  TEXT_HI    = new Color(240, 242, 245);
    private static final Font   MONO       = new Font("Monospaced", Font.PLAIN, 12);
    private static final Font   MONO_BOLD  = new Font("Monospaced", Font.BOLD, 12);

    private ServerSocket serverSocket;
    private Thread serverThread;
    private ExecutorService pool;
    private volatile boolean running = false;

    private JTextPane logPane;
    private StyledDocument logDoc;
    private JButton btnStart, btnStop, btnClear;
    private JLabel lblStatus, lblClients;

    private Style styleClient, styleServer, styleInfo, styleError, styleTime;
    private AtomicInteger clientCount = new AtomicInteger(0);

    public Pop3ServerGUI() {
        super(TITLE);
        initUI();
        log("info", "Interface POP3 prête. Port: " + PORT);
    }

    // ─── Interface Graphique ────────────────────────────────────────────────
    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(820, 600);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        // Barre du haut
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG2);
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        topBar.add(new JLabel("  ▣  POP3 SERVER — port " + PORT), BorderLayout.WEST);

        btnStart = makeButton("▶ Démarrer", GREEN, BG);
        btnStop = makeButton("■ Arrêter", RED, BG);
        btnStop.setEnabled(false);
        btnClear = makeButton("⌫ Effacer", TEXT_DIM, BG2);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btns.setBackground(BG2);
        btns.add(btnClear); btns.add(btnStop); btns.add(btnStart);
        topBar.add(btns, BorderLayout.EAST);

        // Zone de Log
        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(BG);
        logDoc = logPane.getStyledDocument();
        JScrollPane scroll = new JScrollPane(logPane);
        scroll.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));

        // Barre de Status
        JPanel status = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 6));
        status.setBackground(BG2);
        lblStatus = new JLabel("● ARRÊTÉ");
        lblStatus.setForeground(RED);
        lblClients = new JLabel("Clients: 0");
        lblClients.setForeground(TEXT_DIM);
        status.add(lblStatus); status.add(new JSeparator(JSeparator.VERTICAL));
        status.add(lblClients);

        add(topBar, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        // Events
        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());
        btnClear.addActionListener(e -> {
            try { logDoc.remove(0, logDoc.getLength()); } catch (Exception ex) {}
        });
    }

    // ─── Logging ───────────────────────────────────────────────────────────
    void log(String type, String msg) {
        SwingUtilities.invokeLater(() -> {
            ensureStyles();
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            try {
                logDoc.insertString(logDoc.getLength(), "[" + time + "] ", styleTime);
                Style s = styleInfo; String prefix = "[INFO] ";
                if (type.equals("client")) { s = styleClient; prefix = "Client → "; }
                if (type.equals("server")) { s = styleServer; prefix = "Serveur→ "; }
                if (type.equals("error"))  { s = styleError;  prefix = "[ERR]  "; }
                logDoc.insertString(logDoc.getLength(), prefix + msg + "\n", s);
                logPane.setCaretPosition(logDoc.getLength());
            } catch (Exception e) {}
        });
    }

    void logExchange(String addr, String dir, String msg) {
        log(dir.equals("←") ? "client" : "server", addr + " : " + msg);
    }

    private void ensureStyles() {
        if (styleClient != null) return;
        StyleContext sc = StyleContext.getDefaultStyleContext();
        styleClient = logDoc.addStyle("client", null); StyleConstants.setForeground(styleClient, GREEN); StyleConstants.setFontFamily(styleClient, "Monospaced");
        styleServer = logDoc.addStyle("server", null); StyleConstants.setForeground(styleServer, TEXT_HI); StyleConstants.setFontFamily(styleServer, "Monospaced");
        styleInfo = logDoc.addStyle("info", null); StyleConstants.setForeground(styleInfo, YELLOW); StyleConstants.setFontFamily(styleInfo, "Monospaced");
        styleError = logDoc.addStyle("error", null); StyleConstants.setForeground(styleError, RED); StyleConstants.setFontFamily(styleError, "Monospaced");
        styleTime = logDoc.addStyle("time", null); StyleConstants.setForeground(styleTime, TEXT_DIM); StyleConstants.setFontFamily(styleTime, "Monospaced");
    }

    // ─── Serveur ────────────────────────────────────────────────────────────
    private void startServer() {
        try {
            serverSocket = new ServerSocket(PORT);
            pool = Executors.newFixedThreadPool(10);
            running = true;
            btnStart.setEnabled(false); btnStop.setEnabled(true);
            lblStatus.setText("● EN COURS"); lblStatus.setForeground(GREEN);
            log("info", "Serveur POP3 démarré sur le port " + PORT);

            serverThread = new Thread(() -> {
                while (running) {
                    try {
                        Socket s = serverSocket.accept();
                        clientCount.incrementAndGet();
                        SwingUtilities.invokeLater(() -> lblClients.setText("Clients: " + clientCount.get()));
                        pool.execute(new Pop3Session(s, this));
                    } catch (IOException e) { if(running) log("error", "Erreur accept: " + e.getMessage()); }
                }
            });
            serverThread.start();
        } catch (IOException e) {
            log("error", "Impossible de démarrer (port occupé ?): " + e.getMessage());
        }
    }

    private void stopServer() {
        running = false;
        try { if(serverSocket != null) serverSocket.close(); if(pool != null) pool.shutdownNow(); } catch (Exception e) {}
        btnStart.setEnabled(true); btnStop.setEnabled(false);
        lblStatus.setText("● ARRÊTÉ"); lblStatus.setForeground(RED);
        log("info", "Serveur POP3 arrêté.");
    }

    void clientLeft() {
        SwingUtilities.invokeLater(() -> lblClients.setText("Clients: " + clientCount.decrementAndGet()));
    }

    private JButton makeButton(String t, Color f, Color b) {
        JButton btn = new JButton(t); btn.setFont(MONO_BOLD); btn.setForeground(f); btn.setBackground(b);
        btn.setFocusPainted(false); btn.setBorder(BorderFactory.createLineBorder(BORDER));
        return btn;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Pop3ServerGUI().setVisible(true));
    }
}

// ════════════════════════════════════════════════════════════════════════════
// SESSION POP3 (Gestion d'un client)
// ════════════════════════════════════════════════════════════════════════════
class Pop3Session implements Runnable {
    private Socket socket;
    private Pop3ServerGUI gui;
    private BufferedReader in;
    private PrintWriter out;
    private String addr;

    // État de la session
    private String currentUser = null;
    private boolean authenticated = false;

    // Liste des emails chargés depuis le disque
    private List<File> emails = new ArrayList<>();
    private List<Boolean> deletionFlags = new ArrayList<>();

    public Pop3Session(Socket socket, Pop3ServerGUI gui) {
        this.socket = socket;
        this.gui = gui;
        this.addr = socket.getInetAddress().getHostAddress();
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            send("+OK POP3 Server Ready");
            String line;

            while ((line = in.readLine()) != null) {
                gui.logExchange(addr, "←", line);

                // Traitement de la commande
                String cmd = line.toUpperCase();

                if (cmd.startsWith("USER")) {
                    handleUser(line.substring(4).trim());
                } else if (cmd.startsWith("PASS")) {
                    handlePass(line.substring(4).trim());
                } else if (cmd.startsWith("STAT")) {
                    handleStat();
                } else if (cmd.startsWith("LIST")) {
                    handleList(line.substring(4).trim());
                } else if (cmd.startsWith("RETR")) {
                    handleRetr(line.substring(4).trim());
                } else if (cmd.startsWith("DELE")) {
                    handleDele(line.substring(4).trim());
                } else if (cmd.startsWith("QUIT")) {
                    handleQuit();
                    break; // Fin de la session
                } else {
                    send("-ERR Command not implemented");
                }
            }
        } catch (Exception e) {
            gui.log("error", "Session error: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (Exception e) {}
            gui.clientLeft();
            gui.log("info", "Client déconnecté: " + addr);
        }
    }

    // --- Commandes POP3 ---

    private void handleUser(String user) {
        this.currentUser = user;
        send("+OK User accepted");
    }

    private void handlePass(String pass) {
        // Ici, on devrait vérifier le mot de passe via RMI (Phase 4)
        // Pour l'instant, on accepte tout pour tester l'interface
        this.authenticated = true;
        send("+OK Authenticated. Mailbox locked.");

        // CHARGEMENT DES EMAILS ICI
        loadEmails();
    }

    private void loadEmails() {
        emails.clear();
        deletionFlags.clear();

        if (currentUser == null) return;

        // Gestion du nom de dossier : "user" ou "user@domain" -> on prend "user"
        String folderName = currentUser.contains("@") ? currentUser.split("@")[0] : currentUser;
        File userDir = new File("mailserver/" + folderName);

        gui.log("info", "Chargement emails depuis: " + userDir.getAbsolutePath());

        if (userDir.exists() && userDir.isDirectory()) {
            File[] files = userDir.listFiles();
            if (files != null) {
                // Trier par nom de fichier pour avoir un ordre chronologique
                Arrays.sort(files);
                for (File f : files) {
                    emails.add(f);
                    deletionFlags.add(false);
                }
                gui.log("info", emails.size() + " email(s) chargé(s).");
            }
        } else {
            gui.log("error", "Dossier utilisateur introuvable.");
        }
    }

    private void handleStat() {
        if (!authenticated) { send("-ERR Not authenticated"); return; }

        long totalSize = 0;
        int count = 0;
        for (int i = 0; i < emails.size(); i++) {
            if (!deletionFlags.get(i)) {
                count++;
                totalSize += emails.get(i).length();
            }
        }
        send("+OK " + count + " " + totalSize);
    }

    private void handleList(String arg) {
        if (!authenticated) { send("-ERR Authentication required"); return; }

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
            send("+OK " + count + " messages (" + totalSize + " octets)");
            for (int i = 0; i < emails.size(); i++) {
                if (!deletionFlags.get(i)) {
                    send((i + 1) + " " + emails.get(i).length());
                }
            }
            send(".");
        } else {
            // Lister un message spécifique
            try {
                int idx = Integer.parseInt(arg.trim()) - 1;
                if (idx < 0 || idx >= emails.size() || deletionFlags.get(idx)) {
                    send("-ERR No such message");
                    return;
                }
                send("+OK " + (idx + 1) + " " + emails.get(idx).length());
            } catch (NumberFormatException e) {
                send("-ERR Invalid argument");
            }
        }
    }

    private void handleRetr(String arg) {
        if (!authenticated) { send("-ERR Not authenticated"); return; }

        try {
            int idx = Integer.parseInt(arg.trim()) - 1;
            if (idx < 0 || idx >= emails.size() || deletionFlags.get(idx)) {
                send("-ERR No such message");
                return;
            }

            File f = emails.get(idx);
            send("+OK " + f.length() + " octets");

            // Lecture et envoi du fichier
            try (BufferedReader fr = new BufferedReader(new FileReader(f))) {
                String l;
                while ((l = fr.readLine()) != null) {
                    // POP3 nécessite un point au début d'une ligne si la ligne commence par un point
                    // (Dot-stuffing), mais pour simplifier, on envoie tel quel ici.
                    out.println(l);
                }
            }
            send(".");

        } catch (Exception e) {
            send("-ERR Error reading message");
        }
    }

    private void handleDele(String arg) {
        if (!authenticated) { send("-ERR Not authenticated"); return; }

        try {
            int idx = Integer.parseInt(arg.trim()) - 1;
            if (idx < 0 || idx >= emails.size()) {
                send("-ERR No such message");
                return;
            }

            if (deletionFlags.get(idx)) {
                send("-ERR Message already deleted");
            } else {
                deletionFlags.set(idx, true); // Marquer pour suppression
                send("+OK Message marked for deletion");
            }
        } catch (NumberFormatException e) {
            send("-ERR Invalid argument");
        }
    }

    private void handleQuit() {
        // Effectuer les suppressions réelles sur le disque
        for (int i = 0; i < emails.size(); i++) {
            if (deletionFlags.get(i)) {
                emails.get(i).delete();
                gui.log("info", "Email supprimé: " + emails.get(i).getName());
            }
        }
        send("+OK Goodbye");
    }

    private void send(String m) {
        out.println(m);
        gui.logExchange(addr, "→", m);
    }
}