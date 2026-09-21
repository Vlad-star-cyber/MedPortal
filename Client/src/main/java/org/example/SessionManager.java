package org.example;

import org.example.common.UserRole;

public class SessionManager {
    private static SessionManager instance;
    private String token;
    private long userId;
    private UserRole role;
    private String login;

    private SessionManager() {}

    public static synchronized SessionManager getInstance() {
        if (instance == null) instance = new SessionManager();
        return instance;
    }

    public void setSession(String token, long userId, UserRole role, String login) {
        this.token = token;
        this.userId = userId;
        this.role = role;
        this.login = login;
    }

    public String getToken() { return token; }
    public long getUserId() { return userId; }
    public UserRole getRole() { return role; }
    public String getLogin() { return login; }

    public void clear() {
        token = null;
        userId = 0;
        role = null;
        login = null;
    }

    public boolean isLoggedIn() {
        return token != null && !token.isEmpty();
    }
}