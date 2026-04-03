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

public class ImapServerGUI extends JFrame {

    // ─── Constantes & Styles ─────────────────────────────────────────────────
    private static final int    PORT       = 143; // Port IMAP standard
    private static final String TITLE      = "Supervision — Serveur IMAP";
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
    private AtomicInteger clientCount = new AtomicInteger(0); // Compteur

    private JTextPane logPane;
    private StyledDocument logDoc;
    private JButton btnStart, btnStop, btnClear;
    private JLabel lblStatus, lblClients; // Labels de status

    private Style styleClient, styleServer, styleInfo, styleError, styleTime;

    public ImapServerGUI() {
        super(TITLE);
        initUI();
        log("info", "Interface IMAP prête. Port: " + PORT);
    }

    // ─── Interface Graphique ────────────────────────────────────────────────
    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(820, 600);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        // 1. BARRE DU HAAUT (Titre + Boutons)
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG2);
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        topBar.add(new JLabel("  ▣  IMAP SERVER — port " + PORT), BorderLayout.WEST);

        btnStart = makeButton("▶ Démarrer", GREEN, BG);
        btnStop = makeButton("■ Arrêter", RED, BG);
        btnStop.setEnabled(false);
        btnClear = makeButton("⌫ Effacer", TEXT_DIM, BG2);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btns.setBackground(BG2);
        btns.add(btnClear);
        btns.add(btnStop);
        btns.add(btnStart);
        topBar.add(btns, BorderLayout.EAST);

        // 2. ZONE DE LOG
        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(BG);
        logDoc = logPane.getStyledDocument();
        JScrollPane scroll = new JScrollPane(logPane);
        scroll.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));

        // 3. BARRE DE STATUS BAS (Correction ici)
        JPanel status = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 6));
        status.setBackground(BG2);
        status.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));

        lblStatus = new JLabel("● ARRÊTÉ");
        lblStatus.setForeground(RED);

        lblClients = new JLabel("Clients: 0"); // Ajout du label clients
        lblClients.setForeground(TEXT_DIM);

        status.add(lblStatus);
        status.add(new JSeparator(JSeparator.VERTICAL));
        status.add(lblClients);

        // Assemblage
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
            pool = Executors.newFixedThreadPool(20);
            running = true;
            btnStart.setEnabled(false); btnStop.setEnabled(true);
            lblStatus.setText("● EN COURS"); lblStatus.setForeground(GREEN);
            log("info", "Serveur IMAP démarré sur le port " + PORT);

            serverThread = new Thread(() -> {
                while (running) {
                    try {
                        Socket s = serverSocket.accept();
                        // Mise à jour compteur
                        int count = clientCount.incrementAndGet();
                        SwingUtilities.invokeLater(() -> lblClients.setText("Clients: " + count));

                        pool.execute(new ImapSession(s, this));
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
        log("info", "Serveur IMAP arrêté.");
    }

    // Méthode appelée quand un client part
    void clientLeft() {
        SwingUtilities.invokeLater(() -> {
            int count = clientCount.decrementAndGet();
            lblClients.setText("Clients: " + Math.max(0, count));
        });
    }

    private JButton makeButton(String t, Color f, Color b) {
        JButton btn = new JButton(t); btn.setFont(MONO_BOLD); btn.setForeground(f); btn.setBackground(b);
        btn.setFocusPainted(false); btn.setBorder(BorderFactory.createLineBorder(BORDER));
        btn.setOpaque(true); // Important pour que la couleur de fond s'affiche sur certains OS
        return btn;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ImapServerGUI().setVisible(true));
    }
}

// ════════════════════════════════════════════════════════════════════════════
// IMAP SESSION
// ════════════════════════════════════════════════════════════════════════════
class ImapSession implements Runnable {

    private enum State { NOT_AUTHENTICATED, AUTHENTICATED, SELECTED, LOGOUT }

    private Socket socket;
    private ImapServerGUI gui;
    private BufferedReader in;
    private PrintWriter out;
    private String addr;

    private State state = State.NOT_AUTHENTICATED;
    private String currentUser;
    private File currentFolder;
    private List<File> emails = new ArrayList<>();

    public ImapSession(Socket socket, ImapServerGUI gui) {
        this.socket = socket;
        this.gui = gui;
        this.addr = socket.getInetAddress().getHostAddress();
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            send("* OK IMAP4rev1 Service Ready");

            String line;
            while ((line = in.readLine()) != null && state != State.LOGOUT) {
                gui.logExchange(addr, "←", line);

                String[] parts = line.split(" ", 3);
                if (parts.length < 2) continue;

                String tag = parts[0];
                String cmd = parts[1].toUpperCase();
                String args = parts.length > 2 ? parts[2] : "";

                switch (cmd) {
                    case "CAPABILITY": handleCapability(tag); break;
                    case "LOGIN": handleLogin(tag, args); break;
                    case "SELECT": handleSelect(tag, args); break;
                    case "FETCH": handleFetch(tag, args); break;
                    case "LOGOUT": handleLogout(tag); break;
                    case "NOOP": send(tag + " OK NOOP completed"); break;
                    default: send(tag + " BAD Command unknown");
                }
            }
        } catch (Exception e) {
            gui.log("error", "IMAP Session error: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (Exception e) {}
            gui.clientLeft(); // On prévient la GUI qu'on part
            gui.log("info", "Client déconnecté: " + addr);
        }
    }

    private void handleCapability(String tag) {
        send("* CAPABILITY IMAP4rev1 LOGIN");
        send(tag + " OK CAPABILITY completed");
    }

    private void handleLogin(String tag, String args) {
        if (state != State.NOT_AUTHENTICATED) { send(tag + " BAD Already logged in"); return; }

        String[] creds = args.split(" ");
        if (creds.length >= 2) {
            currentUser = creds[0];
            state = State.AUTHENTICATED;
            gui.log("info", "Utilisateur authentifié : " + currentUser);
            send(tag + " OK LOGIN completed");
        } else {
            send(tag + " BAD Invalid arguments");
        }
    }

    private void handleSelect(String tag, String folderName) {
        if (state != State.AUTHENTICATED && state != State.SELECTED) {
            send(tag + " NO Not authenticated"); return;
        }

        String folder = folderName.replace("\"", "");
        if (folder.equalsIgnoreCase("INBOX")) {
            String folderPath = "mailserver/" + (currentUser.contains("@") ? currentUser.split("@")[0] : currentUser);
            currentFolder = new File(folderPath);

            if (currentFolder.exists() && currentFolder.isDirectory()) {
                state = State.SELECTED;
                loadEmails();

                send("* " + emails.size() + " EXISTS");
                send("* 0 RECENT");
                send("* FLAGS (\\Seen \\Deleted)");
                send("* OK [PERMANENTFLAGS (\\Seen \\Deleted)]");
                send(tag + " OK [READ-WRITE] SELECT completed");
                gui.log("info", "Dossier INBOX sélectionné (" + emails.size() + " messages)");
            } else {
                send(tag + " NO Folder not found");
                gui.log("error", "Dossier introuvable : " + folderPath);
            }
        } else {
            send(tag + " NO Folder not supported");
        }
    }

    private void loadEmails() {
        emails.clear();
        File[] files = currentFolder.listFiles();
        if (files != null) {
            Arrays.sort(files);
            emails.addAll(Arrays.asList(files));
        }
    }

    private void handleFetch(String tag, String args) {
        if (state != State.SELECTED) { send(tag + " NO No folder selected"); return; }

        try {
            String[] parts = args.split(" ", 2);
            int msgNum = Integer.parseInt(parts[0]) - 1;

            if (msgNum >= 0 && msgNum < emails.size()) {
                File f = emails.get(msgNum);
                long size = f.length();

                send("* " + (msgNum + 1) + " FETCH (BODY[] {" + size + "})");
                try (BufferedReader fr = new BufferedReader(new FileReader(f))) {
                    String l; while ((l = fr.readLine()) != null) out.println(l);
                }
                send(")");
                send(tag + " OK FETCH completed");
            } else {
                send(tag + " NO Invalid message number");
            }
        } catch (Exception e) {
            send(tag + " BAD Error parsing FETCH command");
        }
    }

    private void handleLogout(String tag) {
        send("* BYE IMAP server shutting down");
        send(tag + " OK LOGOUT completed");
        state = State.LOGOUT;
    }

    private void send(String m) {
        out.println(m);
        gui.logExchange(addr, "→", m);
    }
}
