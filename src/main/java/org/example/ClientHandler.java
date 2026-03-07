package org.example;

import java.io.IOException;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket clientSocket;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            System.out.println("Handling client: " + clientSocket.getInetAddress());

            // Créer une session SMTP pour ce client
            SmtpSession session = new SmtpSession(clientSocket);
            session.run(); // Attention : on peut aussi utiliser session.start() si SmtpSession extends Thread

        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // ignore
            }
            System.out.println("Client disconnected: " + clientSocket.getInetAddress());
        }
    }
}
