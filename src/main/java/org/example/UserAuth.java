package org.example;

import java.util.HashMap;
import java.util.Map;

public class UserAuth {

    private static Map<String, String> users = new HashMap<>();

    static {
        users.put("user1", "pass1");
        users.put("user2", "pass2");
        users.put("student", "nada");
    }

    public static boolean authenticate(String username, String password){
        return users.containsKey(username) && users.get(username).equals(password);
    }

    public static boolean userExists(String username) {
        return username != null && users.containsKey(username);
    }
}
