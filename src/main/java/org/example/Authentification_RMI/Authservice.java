package org.example.Authentification_RMI;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Authservice extends Remote {
    // Vérifie si le couple user/pass est correct
    boolean authenticate(String username, String password) throws RemoteException;

    // Crée un nouvel utilisateur
    boolean createUser(String username, String password) throws RemoteException;

    // Supprime un utilisateur
    boolean deleteUser(String username) throws RemoteException;

    // Vérifie si un utilisateur existe
    boolean userExists(String username) throws RemoteException;
}