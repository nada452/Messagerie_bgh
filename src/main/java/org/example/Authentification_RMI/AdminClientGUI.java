package org.example.Authentification_RMI;



import javax.swing.*;
import java.awt.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class AdminClientGUI extends JFrame {

    private JTextField userField;
    private JPasswordField passField;
    private JButton btnAdd, btnDelete;
    private Authservice authStub;

    public AdminClientGUI() {
        super("Administration des Utilisateurs (RMI)");
        setSize(400, 200);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(3, 2, 10, 10));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Composants
        add(new JLabel("Nom d'utilisateur :"));
        userField = new JTextField();
        add(userField);

        add(new JLabel("Mot de passe :"));
        passField = new JPasswordField();
        add(passField);

        btnAdd = new JButton("Ajouter");
        btnDelete = new JButton("Supprimer");
        add(btnAdd);
        add(btnDelete);

        // Connexion RMI
        connectToRMI();

        // Actions
        btnAdd.addActionListener(e -> addUser());
        btnDelete.addActionListener(e -> deleteUser());

        setVisible(true);
    }

    private void connectToRMI() {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            authStub = (Authservice) registry.lookup("rmi://localhost/Authservice");
            System.out.println("Connecté au serveur RMI.");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Impossible de se connecter au serveur RMI: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addUser() {
        String user = userField.getText();
        String pass = new String(passField.getPassword());

        if (user.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Remplissez tous les champs !");
            return;
        }

        try {
            if (authStub.createUser(user, pass)) {
                JOptionPane.showMessageDialog(this, "Utilisateur ajouté avec succès !");
            } else {
                JOptionPane.showMessageDialog(this, "Erreur : L'utilisateur existe déjà.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteUser() {
        String user = userField.getText();
        try {
            if (authStub.deleteUser(user)) {
                JOptionPane.showMessageDialog(this, "Utilisateur supprimé.");
            } else {
                JOptionPane.showMessageDialog(this, "Erreur : Utilisateur introuvable.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AdminClientGUI());
    }
}
