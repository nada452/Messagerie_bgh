package org.example.Supervision;



import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;



public class SmtpServerGUI extends JFrame {

    // ─── Constantes ───────────────────────────────────────────────────────
    private static final int    PORT       = 2525;
    private static final String TITLE      = "Supervision — Serveur SMTP";
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

    // ─── État du serveur ──────────────────────────────────────────────────
    private ServerSocket        serverSocket;
    private Thread              serverThread;
    private ExecutorService     pool;
    private volatile boolean    running    = false;
    private AtomicInteger       clientCount = new AtomicInteger(0);
    private AtomicInteger       msgCount    = new AtomicInteger(0);

    // ─── Composants GUI ───────────────────────────────────────────────────
    private JTextPane   logPane;
    private StyledDocument logDoc;
    private JButton     btnStart, btnStop, btnClear;
    private JLabel      lblStatus, lblClients, lblMessages, lblPort;
    private JScrollPane scrollPane;

    // ─── Styles de texte ──────────────────────────────────────────────────
    private Style styleClient, styleServer, styleInfo, styleError, styleTime;

    public SmtpServerGUI() {
        super(TITLE);
        initStyles();
        initUI();
        log("info", "Interface de supervision SMTP prête.");
        log("info", "Cliquez sur [Démarrer] pour lancer le serveur sur le port " + PORT + ".");
    }

    // ─── Construction de l'interface ──────────────────────────────────────

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(820, 600);
        setMinimumSize(new Dimension(700, 500));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout(0, 0));

        add(buildTopBar(),    BorderLayout.NORTH);
        add(buildLogPanel(),  BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    /** Barre supérieure : titre + boutons */
    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG2);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        bar.setPreferredSize(new Dimension(0, 54));

        // Titre
        JLabel title = new JLabel("  ▣  SMTP SERVER — port " + PORT);
        title.setFont(new Font("Monospaced", Font.BOLD, 14));
        title.setForeground(GREEN);
        bar.add(title, BorderLayout.WEST);

        // Boutons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnPanel.setBackground(BG2);

        btnStart = makeButton("▶  Démarrer", GREEN,   BG);
        btnStop  = makeButton("■  Arrêter",  RED,     BG);
        btnClear = makeButton("⌫  Effacer",  TEXT_DIM, BG2);

        btnStop.setEnabled(false);

        btnStart.addActionListener(e -> startServer());
        btnStop .addActionListener(e -> stopServer());
        btnClear.addActionListener(e -> clearLog());

        btnPanel.add(btnClear);
        btnPanel.add(btnStop);
        btnPanel.add(btnStart);
        bar.add(btnPanel, BorderLayout.EAST);
        return bar;
    }

    /** Zone de log principale */
    private JScrollPane buildLogPanel() {
        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(BG);
        logPane.setCaretColor(GREEN);
        logDoc = logPane.getStyledDocument();

        scrollPane = new JScrollPane(logPane);
        scrollPane.setBackground(BG);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        scrollPane.getViewport().setBackground(BG);
        scrollPane.getVerticalScrollBar().setBackground(BG2);
        return scrollPane;
    }

    /** Barre de statut en bas */
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 6));
        bar.setBackground(BG2);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));

        lblStatus   = makeStatusLabel("● ARRÊTÉ", RED);
        lblPort     = makeStatusLabel("Port : " + PORT, TEXT_DIM);
        lblClients  = makeStatusLabel("Clients : 0", TEXT_DIM);
        lblMessages = makeStatusLabel("Emails : 0", TEXT_DIM);

        bar.add(lblStatus);
        bar.add(sep());
        bar.add(lblPort);
        bar.add(sep());
        bar.add(lblClients);
        bar.add(sep());
        bar.add(lblMessages);
        return bar;
    }

    // ─── Styles de texte ──────────────────────────────────────────────────

    private void initStyles() {
        // Les styles sont initialisés après la création du logPane
    }

    private void ensureStyles() {
        if (styleClient != null) return;
        StyleContext sc = StyleContext.getDefaultStyleContext();

        styleClient = logDoc.addStyle("client", null);
        StyleConstants.setForeground(styleClient, GREEN);
        StyleConstants.setFontFamily(styleClient, "Monospaced");
        StyleConstants.setFontSize(styleClient, 12);

        styleServer = logDoc.addStyle("server", null);
        StyleConstants.setForeground(styleServer, TEXT_HI);
        StyleConstants.setFontFamily(styleServer, "Monospaced");
        StyleConstants.setFontSize(styleServer, 12);

        styleInfo = logDoc.addStyle("info", null);
        StyleConstants.setForeground(styleInfo, YELLOW);
        StyleConstants.setFontFamily(styleInfo, "Monospaced");
        StyleConstants.setFontSize(styleInfo, 12);

        styleError = logDoc.addStyle("error", null);
        StyleConstants.setForeground(styleError, RED);
        StyleConstants.setFontFamily(styleError, "Monospaced");
        StyleConstants.setBold(styleError, true);
        StyleConstants.setFontSize(styleError, 12);

        styleTime = logDoc.addStyle("time", null);
        StyleConstants.setForeground(styleTime, TEXT_DIM);
        StyleConstants.setFontFamily(styleTime, "Monospaced");
        StyleConstants.setFontSize(styleTime, 11);
    }

    // ─── Logging ──────────────────────────────────────────────────────────

    void log(String type, String message) {
        SwingUtilities.invokeLater(() -> {
            ensureStyles();
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            try {
                logDoc.insertString(logDoc.getLength(),
                        "[" + time + "] ", styleTime);

                Style style;
                String prefix;
                switch (type) {
                    case "client": style = styleClient; prefix = "Client → "; break;
                    case "server": style = styleServer; prefix = "Serveur→ "; break;
                    case "error":  style = styleError;  prefix = "[ERREUR]  "; break;
                    default:       style = styleInfo;   prefix = "[INFO]    "; break;
                }
                logDoc.insertString(logDoc.getLength(), prefix + message + "\n", style);

                // Auto-scroll vers le bas
                logPane.setCaretPosition(logDoc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    void logExchange(String clientAddr, String direction, String message) {
        if (direction.equals("←")) {
            log("client", clientAddr + " : " + message);
        } else {
            log("server", clientAddr + " : " + message);
        }
    }

    private void clearLog() {
        try {
            logDoc.remove(0, logDoc.getLength());
            log("info", "Journal effacé.");
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    // ─── Contrôle du serveur ──────────────────────────────────────────────

    private void startServer() {
        if (running) return;
        try {
            serverSocket = new ServerSocket(PORT);
            pool         = Executors.newFixedThreadPool(20);
            running      = true;

            // Mise à jour GUI
            btnStart.setEnabled(false);
            btnStop .setEnabled(true);
            lblStatus.setText("● EN COURS");
            lblStatus.setForeground(GREEN);
            clientCount.set(0);
            msgCount.set(0);
            updateStats();

            log("info", "Serveur SMTP démarré sur le port " + PORT + ".");

            // Thread d'acceptation des connexions
            serverThread = new Thread(() -> {
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        int count = clientCount.incrementAndGet();
                        SwingUtilities.invokeLater(() ->
                                lblClients.setText("Clients : " + count));

                        String addr = clientSocket.getInetAddress()
                                .getHostAddress();
                        log("info", "Nouvelle connexion de " + addr +
                                " (total actif : " + count + ")");

                        pool.execute(new SmtpSessionGUI(
                                clientSocket, addr, this));
                    } catch (IOException e) {
                        if (running) log("error",
                                "Erreur acceptation : " + e.getMessage());
                    }
                }
            }, "smtp-acceptor");
            serverThread.setDaemon(true);
            serverThread.start();

        } catch (IOException e) {
            log("error", "Impossible de démarrer : " + e.getMessage());
            running = false;
        }
    }

    private void stopServer() {
        if (!running) return;
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
            if (pool != null) pool.shutdownNow();
        } catch (IOException e) {
            log("error", "Erreur à l'arrêt : " + e.getMessage());
        }
        btnStart.setEnabled(true);
        btnStop .setEnabled(false);
        lblStatus.setText("● ARRÊTÉ");
        lblStatus.setForeground(RED);
        log("info", "Serveur SMTP arrêté.");
    }

    void onEmailStored() {
        int count = msgCount.incrementAndGet();
        SwingUtilities.invokeLater(() ->
                lblMessages.setText("Emails : " + count));
    }

    void onClientDisconnected() {
        int count = clientCount.decrementAndGet();
        SwingUtilities.invokeLater(() ->
                lblClients.setText("Clients : " + Math.max(0, count)));
    }

    private void updateStats() {
        lblClients .setText("Clients : " + clientCount.get());
        lblMessages.setText("Emails : "  + msgCount.get());
    }

    // ─── Utilitaires GUI ──────────────────────────────────────────────────

    private JButton makeButton(String text, Color fg, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(MONO_BOLD);
        btn.setForeground(fg);
        btn.setBackground(bg);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JLabel makeStatusLabel(String text, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(MONO);
        lbl.setForeground(color);
        return lbl;
    }

    private JSeparator sep() {
        JSeparator s = new JSeparator(JSeparator.VERTICAL);
        s.setPreferredSize(new Dimension(1, 14));
        s.setForeground(BORDER);
        return s;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new SmtpServerGUI().setVisible(true);
        });
    }
}

// ════════════════════════════════════════════════════════════════════════════
// SmtpSessionGUI — Session SMTP avec logging vers la GUI
// ════════════════════════════════════════════════════════════════════════════
class SmtpSessionGUI implements Runnable {

    private final Socket        socket;
    private final String        addr;
    private final SmtpServerGUI gui;
    private BufferedReader      in;
    private PrintWriter         out;

    private enum State { CONNECTED, HELO_RECEIVED, MAIL_FROM_SET, RCPT_TO_SET, DATA_RECEIVING }
    private State         state     = State.CONNECTED;
    private String        sender;
    private java.util.List<String> recipients = new java.util.ArrayList<>();
    private StringBuilder dataBuffer = new StringBuilder();

    SmtpSessionGUI(Socket socket, String addr, SmtpServerGUI gui) {
        this.socket = socket;
        this.addr   = addr;
        this.gui    = gui;
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            send("220 smtp.example.com ESMTP Service Ready");

            String line;
            while ((line = in.readLine()) != null) {
                gui.logExchange(addr, "←", line);

                if (state == State.DATA_RECEIVING) {
                    if (line.equals(".")) {
                        storeEmail(dataBuffer.toString());
                        dataBuffer.setLength(0);
                        recipients.clear();
                        state = State.HELO_RECEIVED;
                        send("250 OK: Message accepted for delivery");
                        gui.onEmailStored();
                    } else {
                        dataBuffer.append(line.startsWith("..") ?
                                line.substring(1) : line).append("\r\n");
                    }
                    continue;
                }

                // Nettoyer caractères de contrôle telnet (IAC, séquences d'échappement)
                line = line.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "").trim();
                if (line.isEmpty()) continue;

                String cmd = line.split(" ")[0].toUpperCase();
                String arg = line.contains(" ") ?
                        line.substring(line.indexOf(' ')).trim() : "";

                // Etat CONNECTED : seuls HELO/EHLO/NOOP/QUIT autorises
                if (state == State.CONNECTED
                        && !cmd.equals("HELO") && !cmd.equals("EHLO")
                        && !cmd.equals("NOOP") && !cmd.equals("QUIT")) {
                    send("503 Bad sequence of commands — send HELO first");
                    continue;
                }

                switch (cmd) {
                    case "HELO": case "EHLO":
                        state = State.HELO_RECEIVED;
                        sender = null; recipients.clear();
                        send("250 smtp.example.com Hello " + arg);
                        break;
                    case "MAIL":
                        if (state != State.HELO_RECEIVED) {
                            send("503 Bad sequence of commands"); break;
                        }
                        sender = extractEmail(arg.substring(arg.indexOf(':') + 1));
                        state  = State.MAIL_FROM_SET;
                        send("250 OK");
                        break;
                    case "RCPT":
                        if (state != State.MAIL_FROM_SET && state != State.RCPT_TO_SET) {
                            send("503 Bad sequence of commands"); break;
                        }
                        String rcpt = extractEmail(arg.substring(arg.indexOf(':') + 1));
                        new java.io.File("mailserver/" + rcpt.split("@")[0]).mkdirs();
                        recipients.add(rcpt);
                        state = State.RCPT_TO_SET;
                        send("250 OK");

                    case "DATA":
                        if (state != State.RCPT_TO_SET){
                            send("503 bad sequance of commands need to RCPT first");
                            break;}
                        state = State.DATA_RECEIVING;
                        send("354 Start mail input; end with <CRLF>.<CRLF>");
                        break;

                    case "RSET":
                        state = State.HELO_RECEIVED;
                        sender = null; recipients.clear();
                        send("250 OK");
                        break;
                    case "NOOP": send("250 OK"); break;
                    case "QUIT":
                        send("221 smtp.example.com Bye");
                        return;
                    default:
                        send("500 Command unrecognized");
                }
            }
        } catch (IOException e) {
            gui.log("error", addr + " : " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException e) {}
            gui.onClientDisconnected();
            gui.log("info", "Client déconnecté : " + addr);
        }
    }

    private void send(String msg) {
        out.println(msg);
        gui.logExchange(addr, "→", msg);
    }

    private void storeEmail(String body) {
        String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
                .format(new java.util.Date());
        String uid = ts + "_" + (System.nanoTime() % 100000);

        for (String rcpt : recipients) {
            String username = rcpt.split("@")[0];
            java.io.File dir = new java.io.File("mailserver/" + username);
            if (!dir.exists()) dir.mkdirs();
            java.io.File f = new java.io.File(dir, uid + ".txt");
            try (java.io.PrintWriter w = new java.io.PrintWriter(
                    new java.io.FileWriter(f))) {
                w.println("From: " + sender);
                w.println("To: " + String.join(", ", recipients));
                w.println("Date: " + new java.text.SimpleDateFormat(
                        "EEE, dd MMM yyyy HH:mm:ss Z").format(new java.util.Date()));
                w.println("Subject: (no subject)");
                w.println();
                w.print(body);
                gui.log("info", "Email stocké → mailserver/" + username + "/" + uid + ".txt");
            } catch (IOException e) {
                gui.log("error", "Stockage échoué : " + e.getMessage());
            }
        }
    }

    private String extractEmail(String s) {
        return s.replaceAll("[<>\\s]", "");
    }
}