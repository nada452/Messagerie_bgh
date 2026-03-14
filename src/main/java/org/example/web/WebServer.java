package org.example.web;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;


public class WebServer {

    public static final int PORT = 8080;

    // Adresses des serveurs de messagerie
    public static final String MAIL_HOST  = "localhost";
    public static final int    SMTP_PORT  = 2525;
    public static final int    POP3_PORT  = 110;
    public static final int    IMAP_PORT  = 143;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Routes de l'application
        server.createContext("/",          new StaticHandler());
        server.createContext("/login",     new LoginHandler());
        server.createContext("/logout",    new LogoutHandler());
        server.createContext("/inbox",     new InboxHandler());
        server.createContext("/message",   new MessageHandler());
        server.createContext("/send",      new SendHandler());
        server.createContext("/delete",    new DeleteHandler());

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("===========================================");
        System.out.println("  WebMail Server démarré sur le port " + PORT);
        System.out.println("  Accès : http://localhost:" + PORT);
        System.out.println("===========================================");
    }
}

