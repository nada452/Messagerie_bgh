package org.example.Authentification_RMI;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;

public class AuthServiceImpl extends UnicastRemoteObject implements Authservice {

    private static final String USER_FILE = "users.xml";
    private Properties users;

    public AuthServiceImpl() throws RemoteException {
        super();
        users = new Properties();
        loadUsers();
    }

    // Charger les utilisateurs depuis le fichier XML
    private void loadUsers() {
        File f = new File(USER_FILE);
        if (f.exists()) {
            try (FileInputStream fis = new FileInputStream(f)) {
                users.loadFromXML(fis);
                System.out.println("Utilisateurs chargés : " + users.keySet());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Sauvegarder les utilisateurs dans le fichier XML
    private void saveUsers() {
        try (FileOutputStream fos = new FileOutputStream(USER_FILE)) {
            users.storeToXML(fos, "Liste des utilisateurs");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean authenticate(String username, String password) throws RemoteException {
        if (username == null || password == null) return false;
        String storedPass = users.getProperty(username);
        return password.equals(storedPass);
    }

    @Override
    public boolean createUser(String username, String password) throws RemoteException {
        if (users.containsKey(username)) {
            return false; // Existe déjà
        }
        users.setProperty(username, password);
        saveUsers();
        System.out.println("Utilisateur créé : " + username);
        return true;
    }

    @Override
    public boolean deleteUser(String username) throws RemoteException {
        if (!users.containsKey(username)) {
            return false; // N'existe pas
        }
        users.remove(username);
        saveUsers();
        System.out.println("Utilisateur supprimé : " + username);
        return true;
    }

    @Override
    public boolean userExists(String username) throws RemoteException {
        return users.containsKey(username);
    }
}