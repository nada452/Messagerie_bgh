package org.example.web;

import com.sun.net.httpserver.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class Basehandler implements HttpHandler {

    // ─── Réponses HTTP ──────────────────────────────────────────────────────

    protected void sendHtml(HttpExchange ex, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    protected void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.getResponseBody().close();
    }

    // ─── Session ────────────────────────────────────────────────────────────

    protected Sessionmanager.Session getSession(HttpExchange ex) {
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        String sid = Sessionmanager.extractSessionId(cookie);
        return Sessionmanager.get(sid);
    }

    protected void setSessionCookie(HttpExchange ex, String sessionId) {
        ex.getResponseHeaders().set("Set-Cookie",
                "session=" + sessionId + "; Path=/; HttpOnly");
    }

    protected void clearSessionCookie(HttpExchange ex) {
        ex.getResponseHeaders().set("Set-Cookie",
                "session=; Path=/; Max-Age=0; HttpOnly");
    }

    // ─── Parsing ────────────────────────────────────────────────────────────

    protected Map<String, String> parseForm(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return parseQuery(body);
    }

    protected Map<String, String> parseQuery(String query) {
        Map<String, String> map = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(urlDecode(kv[0]), urlDecode(kv[1]));
            }
        }
        return map;
    }

    protected String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) { return s; }
    }

    protected String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
