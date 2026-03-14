package org.example.web;


public class Htmltemplates {
    static final String CSS = """
        @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600&family=IBM+Plex+Sans:wght@300;400;500&display=swap');

        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

        :root {
            --bg:        #0f1117;
            --bg2:       #181c25;
            --bg3:       #1e2330;
            --border:    #2a3045;
            --accent:    #39d353;
            --accent2:   #1a8f30;
            --text:      #d4d8e0;
            --text-dim:  #6b7280;
            --text-hi:   #f0f2f5;
            --danger:    #e05252;
            --warn:      #e0a844;
            --mono:      'IBM Plex Mono', monospace;
            --sans:      'IBM Plex Sans', sans-serif;
        }

        html, body {
            height: 100%;
            background: var(--bg);
            color: var(--text);
            font-family: var(--sans);
            font-size: 14px;
            line-height: 1.6;
        }

        a { color: var(--accent); text-decoration: none; }
        a:hover { text-decoration: underline; }

        input, textarea, select {
            background: var(--bg3);
            border: 1px solid var(--border);
            color: var(--text-hi);
            font-family: var(--mono);
            font-size: 13px;
            padding: 8px 12px;
            border-radius: 3px;
            outline: none;
            width: 100%;
            transition: border-color .15s;
        }
        input:focus, textarea:focus {
            border-color: var(--accent);
            box-shadow: 0 0 0 2px rgba(57,211,83,.1);
        }

        button, .btn {
            font-family: var(--mono);
            font-size: 12px;
            font-weight: 600;
            letter-spacing: .05em;
            text-transform: uppercase;
            padding: 8px 18px;
            border-radius: 3px;
            border: none;
            cursor: pointer;
            transition: all .15s;
            display: inline-flex;
            align-items: center;
            gap: 6px;
        }
        .btn-primary {
            background: var(--accent);
            color: #0f1117;
        }
        .btn-primary:hover { background: #4ae868; transform: translateY(-1px); }
        .btn-ghost {
            background: transparent;
            color: var(--text-dim);
            border: 1px solid var(--border);
        }
        .btn-ghost:hover { border-color: var(--text-dim); color: var(--text); }
        .btn-danger {
            background: transparent;
            color: var(--danger);
            border: 1px solid rgba(224,82,82,.3);
            font-size: 11px;
            padding: 4px 10px;
        }
        .btn-danger:hover { background: rgba(224,82,82,.1); }

        .tag {
            display: inline-block;
            font-family: var(--mono);
            font-size: 10px;
            font-weight: 600;
            letter-spacing: .08em;
            text-transform: uppercase;
            padding: 2px 7px;
            border-radius: 2px;
            background: var(--bg3);
            border: 1px solid var(--border);
            color: var(--text-dim);
        }
        .tag-new {
            background: rgba(57,211,83,.12);
            border-color: rgba(57,211,83,.3);
            color: var(--accent);
        }

        .flash-error {
            background: rgba(224,82,82,.1);
            border: 1px solid rgba(224,82,82,.3);
            color: #f08080;
            padding: 10px 16px;
            border-radius: 3px;
            font-family: var(--mono);
            font-size: 12px;
            margin-bottom: 20px;
        }
        .flash-success {
            background: rgba(57,211,83,.08);
            border: 1px solid rgba(57,211,83,.25);
            color: var(--accent);
            padding: 10px 16px;
            border-radius: 3px;
            font-family: var(--mono);
            font-size: 12px;
            margin-bottom: 20px;
        }

        /* Scrollbar */
        ::-webkit-scrollbar { width: 6px; height: 6px; }
        ::-webkit-scrollbar-track { background: var(--bg); }
        ::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }
        ::-webkit-scrollbar-thumb:hover { background: var(--text-dim); }
        """;

    // ─── Layout principal (après login) ─────────────────────────────────────
    static String layout(String username, String content) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>WebMail — %s</title>
                <style>
                %s

                .app { display: flex; height: 100vh; overflow: hidden; }

                /* Sidebar */
                .sidebar {
                    width: 220px;
                    min-width: 220px;
                    background: var(--bg2);
                    border-right: 1px solid var(--border);
                    display: flex;
                    flex-direction: column;
                    padding: 0;
                }
                .sidebar-logo {
                    padding: 20px 20px 16px;
                    border-bottom: 1px solid var(--border);
                }
                .sidebar-logo .logo-text {
                    font-family: var(--mono);
                    font-size: 16px;
                    font-weight: 600;
                    color: var(--accent);
                    letter-spacing: .05em;
                }
                .sidebar-logo .logo-sub {
                    font-family: var(--mono);
                    font-size: 10px;
                    color: var(--text-dim);
                    margin-top: 2px;
                }
                .sidebar-user {
                    padding: 14px 20px;
                    border-bottom: 1px solid var(--border);
                    display: flex;
                    align-items: center;
                    gap: 10px;
                }
                .user-avatar {
                    width: 28px; height: 28px;
                    background: var(--accent2);
                    border-radius: 50%%;
                    display: flex; align-items: center; justify-content: center;
                    font-family: var(--mono);
                    font-size: 11px;
                    font-weight: 600;
                    color: #0f1117;
                    flex-shrink: 0;
                }
                .user-name {
                    font-family: var(--mono);
                    font-size: 12px;
                    color: var(--text-hi);
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;
                }
                .sidebar-nav {
                    padding: 12px 0;
                    flex: 1;
                }
                .nav-section {
                    padding: 6px 20px 4px;
                    font-family: var(--mono);
                    font-size: 9px;
                    letter-spacing: .12em;
                    text-transform: uppercase;
                    color: var(--text-dim);
                }
                .nav-item {
                    display: flex;
                    align-items: center;
                    gap: 10px;
                    padding: 8px 20px;
                    color: var(--text-dim);
                    font-family: var(--mono);
                    font-size: 12px;
                    cursor: pointer;
                    transition: all .1s;
                    text-decoration: none;
                    border-left: 2px solid transparent;
                }
                .nav-item:hover {
                    color: var(--text);
                    background: var(--bg3);
                    text-decoration: none;
                }
                .nav-item.active {
                    color: var(--accent);
                    border-left-color: var(--accent);
                    background: rgba(57,211,83,.06);
                }
                .nav-icon { font-size: 13px; width: 16px; text-align: center; }
                .sidebar-footer {
                    padding: 14px 20px;
                    border-top: 1px solid var(--border);
                }

                /* Main content */
                .main { flex: 1; overflow-y: auto; display: flex; flex-direction: column; }
                .topbar {
                    padding: 14px 28px;
                    border-bottom: 1px solid var(--border);
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    background: var(--bg2);
                    position: sticky; top: 0; z-index: 10;
                }
                .topbar-title {
                    font-family: var(--mono);
                    font-size: 13px;
                    font-weight: 600;
                    color: var(--text-hi);
                    letter-spacing: .03em;
                }
                .topbar-meta {
                    font-family: var(--mono);
                    font-size: 11px;
                    color: var(--text-dim);
                }
                .content { padding: 24px 28px; flex: 1; }
                </style>
            </head>
            <body>
            <div class="app">
                <aside class="sidebar">
                    <div class="sidebar-logo">
                        <div class="logo-text">▣ WEBMAIL</div>
                        <div class="logo-sub">v1.0 — TP Systèmes Distribués</div>
                    </div>
                    <div class="sidebar-user">
                        <div class="user-avatar">%s</div>
                        <div class="user-name">%s</div>
                    </div>
                    <nav class="sidebar-nav">
                        <div class="nav-section">Boîtes</div>
                        <a href="/inbox" class="nav-item active">
                            <span class="nav-icon">▤</span> INBOX
                        </a>
                        <div class="nav-section" style="margin-top:12px">Actions</div>
                        <a href="/send" class="nav-item">
                            <span class="nav-icon">↗</span> Nouveau message
                        </a>
                    </nav>
                    <div class="sidebar-footer">
                        <a href="/logout" class="btn btn-ghost" style="width:100%%;justify-content:center">
                            ⏻ Déconnexion
                        </a>
                    </div>
                </aside>
                <main class="main">
                    %s
                </main>
            </div>
            </body>
            </html>
            """.formatted(username, CSS,
                username.substring(0, 1).toUpperCase(),
                username,
                content);
    }

    // ─── Page login ─────────────────────────────────────────────────────────
    static String loginPage(String error) {
        String errorHtml = error != null
                ? "<div class='flash-error'>⚠ " + error + "</div>"
                : "";
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>WebMail — Connexion</title>
                <style>
                %s
                body { display: flex; align-items: center; justify-content: center; min-height: 100vh; }
                .login-wrap {
                    width: 100%%; max-width: 380px;
                    padding: 0 20px;
                }
                .login-header {
                    text-align: center;
                    margin-bottom: 36px;
                }
                .login-logo {
                    font-family: var(--mono);
                    font-size: 28px;
                    font-weight: 600;
                    color: var(--accent);
                    letter-spacing: .06em;
                    margin-bottom: 6px;
                }
                .login-sub {
                    font-family: var(--mono);
                    font-size: 11px;
                    color: var(--text-dim);
                    letter-spacing: .1em;
                    text-transform: uppercase;
                }
                .login-card {
                    background: var(--bg2);
                    border: 1px solid var(--border);
                    border-radius: 6px;
                    padding: 28px;
                }
                .field { margin-bottom: 16px; }
                .field label {
                    display: block;
                    font-family: var(--mono);
                    font-size: 10px;
                    letter-spacing: .1em;
                    text-transform: uppercase;
                    color: var(--text-dim);
                    margin-bottom: 6px;
                }
                .login-btn {
                    width: 100%%;
                    margin-top: 8px;
                    padding: 11px;
                    font-size: 12px;
                    justify-content: center;
                }
                .login-hint {
                    margin-top: 20px;
                    padding: 12px 16px;
                    background: var(--bg3);
                    border: 1px solid var(--border);
                    border-radius: 3px;
                    font-family: var(--mono);
                    font-size: 11px;
                    color: var(--text-dim);
                    line-height: 1.8;
                }
                .login-hint strong { color: var(--text); }
                </style>
            </head>
            <body>
            <div class="login-wrap">
                <div class="login-header">
                    <div class="login-logo">▣ WEBMAIL</div>
                    <div class="login-sub">Système de messagerie distribué</div>
                </div>
                %s
                <div class="login-card">
                    <form method="POST" action="/login">
                        <div class="field">
                            <label>Identifiant</label>
                            <input type="text" name="username" placeholder="user1" required autofocus>
                        </div>
                        <div class="field">
                            <label>Mot de passe</label>
                            <input type="password" name="password" placeholder="••••••" required>
                        </div>
                        <button type="submit" class="btn btn-primary login-btn">
                            → Connexion
                        </button>
                    </form>
                </div>
                <div class="login-hint">
                    <strong>Comptes de test :</strong><br>
                    user1 / pass1 &nbsp;·&nbsp; user2 / pass2 &nbsp;·&nbsp; student / nada
                </div>
            </div>
            </body>
            </html>
            """.formatted(CSS, errorHtml);
    }

    // ─── Inbox ───────────────────────────────────────────────────────────────
    static String inboxPage(java.util.List<Pop3client.Email> emails,
                            String username, String flash) {
        String flashHtml = flash != null
                ? "<div class='flash-success'>✓ " + flash + "</div>"
                : "";

        StringBuilder rows = new StringBuilder();
        if (emails.isEmpty()) {
            rows.append("""
                <div style="text-align:center;padding:60px 0;color:var(--text-dim);
                            font-family:var(--mono);font-size:13px;">
                    ▢ Aucun message dans cette boîte
                </div>
                """);
        } else {
            for (Pop3client.Email e : emails) {
                String fromShort = e.from.length() > 30
                        ? e.from.substring(0, 28) + "…" : e.from;
                String subjShort = e.subject.length() > 50
                        ? e.subject.substring(0, 48) + "…" : e.subject;
                rows.append("""
                    <div class="mail-row" onclick="location.href='/message?n=%d'">
                        <div class="mail-from">%s</div>
                        <div class="mail-subject">%s</div>
                        <div class="mail-date">%s</div>
                        <form method="POST" action="/delete"
                              onsubmit="event.stopPropagation()" style="margin:0">
                            <input type="hidden" name="n" value="%d">
                            <button type="submit" class="btn btn-danger"
                                onclick="event.stopPropagation()">✕</button>
                        </form>
                    </div>
                    """.formatted(
                        e.number,
                        escHtml(fromShort),
                        escHtml(subjShort),
                        escHtml(e.date.length() > 20 ? e.date.substring(0, 20) : e.date),
                        e.number
                ));
            }
        }

        String content = """
            <div class="topbar">
                <span class="topbar-title">▤ INBOX</span>
                <span class="topbar-meta">%d message(s) — %s</span>
            </div>
            <div class="content">
                %s
                <div style="display:flex;align-items:center;
                            justify-content:space-between;margin-bottom:16px;">
                    <span style="font-family:var(--mono);font-size:11px;
                                 color:var(--text-dim);">
                        Récupéré via POP3 · port 110
                    </span>
                    <a href="/send" class="btn btn-primary">↗ Nouveau</a>
                </div>
                <div class="mail-list">
                    <div class="mail-list-header">
                        <span>Expéditeur</span>
                        <span>Sujet</span>
                        <span>Date</span>
                        <span></span>
                    </div>
                    %s
                </div>
            </div>
            <style>
            .mail-list { border: 1px solid var(--border); border-radius: 4px; overflow: hidden; }
            .mail-list-header {
                display: grid;
                grid-template-columns: 220px 1fr 160px 60px;
                padding: 8px 16px;
                background: var(--bg3);
                border-bottom: 1px solid var(--border);
                font-family: var(--mono);
                font-size: 10px;
                letter-spacing: .1em;
                text-transform: uppercase;
                color: var(--text-dim);
            }
            .mail-row {
                display: grid;
                grid-template-columns: 220px 1fr 160px 60px;
                align-items: center;
                padding: 12px 16px;
                border-bottom: 1px solid var(--border);
                cursor: pointer;
                transition: background .1s;
                gap: 8px;
            }
            .mail-row:last-child { border-bottom: none; }
            .mail-row:hover { background: var(--bg3); }
            .mail-from {
                font-family: var(--mono);
                font-size: 12px;
                color: var(--text-hi);
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
            }
            .mail-subject {
                font-size: 13px;
                color: var(--text);
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
            }
            .mail-date {
                font-family: var(--mono);
                font-size: 11px;
                color: var(--text-dim);
            }
            </style>
            """.formatted(emails.size(), username, flashHtml, rows.toString());

        return layout(username, content);
    }

    // ─── Lire un message ─────────────────────────────────────────────────────
    static String messagePage(Pop3client.Email email, String username) {
        String content = """
            <div class="topbar">
                <span class="topbar-title">▤ MESSAGE #%d</span>
                <span class="topbar-meta">
                    <a href="/inbox" style="color:var(--text-dim)">← Retour inbox</a>
                </span>
            </div>
            <div class="content">
                <div class="msg-card">
                    <div class="msg-header">
                        <div class="msg-header-row">
                            <span class="msg-label">De</span>
                            <span class="msg-value">%s</span>
                        </div>
                        <div class="msg-header-row">
                            <span class="msg-label">À</span>
                            <span class="msg-value">%s</span>
                        </div>
                        <div class="msg-header-row">
                            <span class="msg-label">Sujet</span>
                            <span class="msg-value msg-subject">%s</span>
                        </div>
                        <div class="msg-header-row">
                            <span class="msg-label">Date</span>
                            <span class="msg-value">%s</span>
                        </div>
                    </div>
                    <div class="msg-body">%s</div>
                    <div class="msg-actions">
                        <a href="/inbox" class="btn btn-ghost">← Retour</a>
                        <form method="POST" action="/delete" style="display:inline">
                            <input type="hidden" name="n" value="%d">
                            <button type="submit" class="btn btn-danger">✕ Supprimer</button>
                        </form>
                    </div>
                </div>
            </div>
            <style>
            .msg-card {
                background: var(--bg2);
                border: 1px solid var(--border);
                border-radius: 4px;
                overflow: hidden;
                max-width: 760px;
            }
            .msg-header {
                padding: 20px 24px;
                border-bottom: 1px solid var(--border);
                background: var(--bg3);
            }
            .msg-header-row {
                display: flex;
                gap: 16px;
                padding: 4px 0;
                font-family: var(--mono);
                font-size: 12px;
            }
            .msg-label {
                width: 50px;
                color: var(--text-dim);
                flex-shrink: 0;
                text-transform: uppercase;
                font-size: 10px;
                letter-spacing: .08em;
                padding-top: 2px;
            }
            .msg-value { color: var(--text-hi); }
            .msg-subject { font-size: 14px; font-weight: 500; color: #fff; }
            .msg-body {
                padding: 24px;
                font-family: var(--mono);
                font-size: 13px;
                line-height: 1.8;
                color: var(--text);
                white-space: pre-wrap;
                min-height: 120px;
            }
            .msg-actions {
                padding: 14px 24px;
                border-top: 1px solid var(--border);
                display: flex;
                gap: 10px;
                align-items: center;
            }
            </style>
            """.formatted(
                email.number,
                escHtml(email.from),
                escHtml(email.to),
                escHtml(email.subject),
                escHtml(email.date),
                escHtml(email.body),
                email.number
        );
        return layout(username, content);
    }

    // ─── Composer un message ─────────────────────────────────────────────────
    static String composePage(String username, String error, String success) {
        String flashHtml = "";
        if (error   != null) flashHtml = "<div class='flash-error'>⚠ "   + error   + "</div>";
        if (success != null) flashHtml = "<div class='flash-success'>✓ " + success + "</div>";

        String content = """
            <div class="topbar">
                <span class="topbar-title">↗ NOUVEAU MESSAGE</span>
                <span class="topbar-meta">via SMTP · port 2525</span>
            </div>
            <div class="content">
                %s
                <div class="compose-card">
                    <form method="POST" action="/send">
                        <div class="compose-field">
                            <label>De</label>
                            <input type="text" value="%s@example.com" readonly
                                   style="color:var(--text-dim);cursor:not-allowed">
                        </div>
                        <div class="compose-field">
                            <label>À <span style="color:var(--danger)">*</span></label>
                            <input type="text" name="to" placeholder="destinataire@example.com" required>
                        </div>
                        <div class="compose-field">
                            <label>Sujet</label>
                            <input type="text" name="subject" placeholder="(sans sujet)">
                        </div>
                        <div class="compose-field">
                            <label>Message <span style="color:var(--danger)">*</span></label>
                            <textarea name="body" rows="10"
                                      placeholder="Écrivez votre message ici..." required></textarea>
                        </div>
                        <div style="display:flex;gap:10px;margin-top:8px;">
                            <button type="submit" class="btn btn-primary">↗ Envoyer</button>
                            <a href="/inbox" class="btn btn-ghost">✕ Annuler</a>
                        </div>
                    </form>
                </div>
            </div>
            <style>
            .compose-card {
                background: var(--bg2);
                border: 1px solid var(--border);
                border-radius: 4px;
                padding: 24px;
                max-width: 700px;
            }
            .compose-field { margin-bottom: 16px; }
            .compose-field label {
                display: block;
                font-family: var(--mono);
                font-size: 10px;
                letter-spacing: .1em;
                text-transform: uppercase;
                color: var(--text-dim);
                margin-bottom: 6px;
            }
            textarea { resize: vertical; min-height: 160px; line-height: 1.7; }
            </style>
            """.formatted(flashHtml, username);

        return layout(username, content);
    }

    // ─── Utilitaire HTML escape ──────────────────────────────────────────────
    static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;")
                .replace(">","&gt;").replace("\"","&quot;");
    }
}
