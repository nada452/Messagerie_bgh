package org.example.web;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class Sessionmanager {

    private static final long TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

    // sessionId → Session
    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public static class Session {
        public final String id;
        public final String username;
        public final String password; // nécessaire pour les appels POP3/IMAP
        private long lastAccess;

        public Session(String username, String password) {
            this.id       = UUID.randomUUID().toString();
            this.username = username;
            this.password = password;
            this.lastAccess = System.currentTimeMillis();
        }

        public void touch() { this.lastAccess = System.currentTimeMillis(); }
        public boolean isExpired() {
            return System.currentTimeMillis() - lastAccess > TIMEOUT_MS;
        }
    }

    /** Crée une nouvelle session et la retourne. */
    public static Session create(String username, String password) {
        Session s = new Session(username, password);
        sessions.put(s.id, s);
        return s;
    }

    /** Récupère une session par son ID (null si inexistante ou expirée). */
    public static Session get(String sessionId) {
        if (sessionId == null) return null;
        Session s = sessions.get(sessionId);
        if (s == null || s.isExpired()) {
            sessions.remove(sessionId);
            return null;
        }
        s.touch();
        return s;
    }

    /** Invalide une session (logout). */
    public static void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    /** Extrait le sessionId depuis l'en-tête Cookie. */
    public static String extractSessionId(String cookieHeader) {
        if (cookieHeader == null) return null;
        for (String part : cookieHeader.split(";")) {
            part = part.trim();
            if (part.startsWith("session=")) {
                return part.substring("session=".length());
            }
        }
        return null;
    }
}