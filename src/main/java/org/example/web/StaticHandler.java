package org.example.web;

import com.sun.net.httpserver.*;
import java.io.*;
import java.util.*;

// ════════════════════════════════════════════════════════════════════════════
// StaticHandler — Page d'accueil (redirige vers /inbox ou /login)
// ════════════════════════════════════════════════════════════════════════════
class StaticHandler extends Basehandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        Sessionmanager.Session session = getSession(ex);
        if (session != null) redirect(ex, "/inbox");
        else redirect(ex, "/login");
    }
}

// ════════════════════════════════════════════════════════════════════════════
// LoginHandler — GET /login (formulaire) · POST /login (traitement)
// ════════════════════════════════════════════════════════════════════════════
class LoginHandler extends Basehandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        if ("GET".equals(ex.getRequestMethod())) {
            // Si déjà connecté, rediriger directement
            if (getSession(ex) != null) { redirect(ex, "/inbox"); return; }
            sendHtml(ex, 200, Htmltemplates.loginPage(null));

        } else if ("POST".equals(ex.getRequestMethod())) {
            Map<String, String> form = parseForm(ex);
            String username = form.getOrDefault("username", "").trim();
            String password = form.getOrDefault("password", "").trim();

            // Authentification : on tente une connexion POP3 réelle
            // C'est ainsi que l'interface Web délègue l'auth au service existant
            boolean ok = false;
            String errorMsg = null;

            if (username.isEmpty() || password.isEmpty()) {
                errorMsg = "Identifiant et mot de passe requis.";
            } else {
                // ÉTAPE 1 : vérification locale via UserAuth (toujours disponible)
                if (!org.example.UserAuth.authenticate(username, password)) {
                    errorMsg = "Identifiants incorrects.";
                } else {
                    ok = true;
                    // ÉTAPE 2 : créer le répertoire mailbox si absent
                    java.io.File userDir = new java.io.File("mailserver/" + username);
                    if (!userDir.exists()) userDir.mkdirs();
                }
            }

            if (ok) {
                Sessionmanager.Session session =
                        Sessionmanager.create(username, password);
                setSessionCookie(ex, session.id);
                redirect(ex, "/inbox");
            } else {
                sendHtml(ex, 401, Htmltemplates.loginPage(errorMsg));
            }
        }
    }
}
// ════════════════════════════════════════════════════════════════════════════
// LogoutHandler — GET /logout
// ════════════════════════════════════════════════════════════════════════════
class LogoutHandler extends Basehandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String cookie  = ex.getRequestHeaders().getFirst("Cookie");
        String sid     = Sessionmanager.extractSessionId(cookie);
        Sessionmanager.remove(sid);
        clearSessionCookie(ex);
        redirect(ex, "/login");
    }
}

// ════════════════════════════════════════════════════════════════════════════
// InboxHandler — GET /inbox  (liste des messages via POP3)
// ════════════════════════════════════════════════════════════════════════════
class InboxHandler extends Basehandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        Sessionmanager.Session session = getSession(ex);
        if (session == null) { redirect(ex, "/login"); return; }

        // Paramètre flash (après envoi ou suppression)
        String query = ex.getRequestURI().getQuery();
        Map<String, String> params = parseQuery(query);
        String flash = params.get("flash");

        List<Pop3client.Email> emails = new ArrayList<>();
        String error = null;

        try {
            Pop3client client = new Pop3client(WebServer.MAIL_HOST, WebServer.POP3_PORT);
            emails = client.fetchEmails(session.username, session.password);
        } catch (IOException e) {
            error = "Impossible de contacter le serveur pop3 : " + e.getMessage();
        }

        String html = Htmltemplates.inboxPage(emails, session.username,
                error != null ? null : flash);

        // Afficher l'erreur si besoin
        if (error != null) {
            html = html.replace("</div>\n                <div style=\"display:flex",
                    "<div class='flash-error'>⚠ " + escapeHtml(error) +
                            "</div></div>\n                <div style=\"display:flex");
        }

        sendHtml(ex, 200, html);
    }
}

// ════════════════════════════════════════════════════════════════════════════
// MessageHandler — GET /message?n=1 (lecture d'un message via POP3)
// ════════════════════════════════════════════════════════════════════════════
class MessageHandler extends Basehandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        Sessionmanager.Session session = getSession(ex);
        if (session == null) { redirect(ex, "/login"); return; }

        String query = ex.getRequestURI().getQuery();
        Map<String, String> params = parseQuery(query);

        int msgNum = 1;
        try { msgNum = Integer.parseInt(params.getOrDefault("n", "1")); }
        catch (NumberFormatException e) { /* garder 1 */ }

        try {
            Pop3client client = new Pop3client(WebServer.MAIL_HOST, WebServer.POP3_PORT);
            List<Pop3client.Email> emails =
                    client.fetchEmails(session.username, session.password);

            // Trouver le message par son numéro
            Pop3client.Email email = null;
            for (Pop3client.Email e : emails) {
                if (e.number == msgNum) { email = e; break; }
            }

            if (email == null) {
                redirect(ex, "/inbox?flash=Message+introuvable"); return;
            }
            sendHtml(ex, 200,
                    Htmltemplates.messagePage(email, session.username));

        } catch (IOException e) {
            redirect(ex, "/inbox");
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// SendHandler — GET /send (formulaire) · POST /send (envoi via SMTP)
// ════════════════════════════════════════════════════════════════════════════
class SendHandler extends Basehandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        Sessionmanager.Session session = getSession(ex);
        if (session == null) { redirect(ex, "/login"); return; }

        if ("GET".equals(ex.getRequestMethod())) {
            sendHtml(ex, 200,
                    Htmltemplates.composePage(session.username, null, null));

        } else if ("POST".equals(ex.getRequestMethod())) {
            Map<String, String> form = parseForm(ex);
            String to      = form.getOrDefault("to", "").trim();
            String subject = form.getOrDefault("subject", "(sans sujet)").trim();
            String body    = form.getOrDefault("body", "").trim();

            if (to.isEmpty() || body.isEmpty()) {
                sendHtml(ex, 400, Htmltemplates.composePage(
                        session.username,
                        "Le destinataire et le corps du message sont requis.", null));
                return;
            }

            String from = session.username + "@example.com";
            try {
                Smtpclient smtp = new Smtpclient(WebServer.MAIL_HOST, WebServer.SMTP_PORT);
                smtp.sendEmail(from, to, subject, body);
                // Succès → retour inbox avec message flash
                redirect(ex, "/inbox?flash=Message+envoy%C3%A9+avec+succ%C3%A8s+%E2%86%97");
            } catch (IOException e) {
                sendHtml(ex, 500, Htmltemplates.composePage(
                        session.username,
                        "Erreur SMTP : " + e.getMessage(), null));
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// DeleteHandler — POST /delete  (suppression via POP3 DELE + QUIT)
// ════════════════════════════════════════════════════════════════════════════
class DeleteHandler extends Basehandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        Sessionmanager.Session session = getSession(ex);
        if (session == null) { redirect(ex, "/login"); return; }

        Map<String, String> form = parseForm(ex);
        int msgNum = 1;
        try { msgNum = Integer.parseInt(form.getOrDefault("n", "1")); }
        catch (NumberFormatException e) { /* garder 1 */ }

        try {
            Pop3client client = new Pop3client(WebServer.MAIL_HOST, WebServer.POP3_PORT);
            boolean ok = client.deleteEmail(session.username, session.password, msgNum);
            if (ok) redirect(ex, "/inbox?flash=Message+supprim%C3%A9");
            else    redirect(ex, "/inbox?flash=Erreur+suppression");
        } catch (IOException e) {
            redirect(ex, "/inbox");
        }
    }
}