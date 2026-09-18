package models;

import java.io.Serializable;
import java.time.LocalDateTime;

public class User implements Serializable {
    private long id;
    private String login;
    private String passwordHash;
    private String salt;
    private Role role;
    private boolean blocked;
    private LocalDateTime createdAt;

    public User() {
    }

    public User(long id, String login, String passwordHash, String salt,
                Role role, boolean blocked, LocalDateTime createdAt) {
        this.id = id;
        this.login = login;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role;
        this.blocked = blocked;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String l) {
        this.login = l;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String h) {
        this.passwordHash = h;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String s) {
        this.salt = s;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role r) {
        this.role = r;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean b) {
        this.blocked = b;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime t) {
        this.createdAt = t;
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", login='" + login + "', role=" + role + "}";
    }
}