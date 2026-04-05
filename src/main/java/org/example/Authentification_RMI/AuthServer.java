package org.example.Authentification_RMI;



import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class AuthServer {
    public static void main(String[] args) {
        try {
            // 1. Créer le registre RMI sur le port 1099
            Registry registry = LocateRegistry.createRegistry(1099);

            // 2. Créer l'instance du service
            Authservice authService = new AuthServiceImpl();

            // 3. Lier le service au nom "AuthService"
            registry.rebind("rmi://localhost/Authservice", authService);

            System.out.println("🟢 Serveur d'authentification RMI démarré sur le port 1099.");
            System.out.println("   Service lié : rmi://localhost/Authservice");

        } catch (Exception e) {
            System.err.println("Erreur serveur RMI : " + e.getMessage());
            e.printStackTrace();
        }
    }
}
