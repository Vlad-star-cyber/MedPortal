package models;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Session implements Serializable {
    private long id;
    private long userId;
    private String token;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String ipAddress;

    public Session() {
    }

    public Session(long userId, String token, String ipAddress) {
        this.userId = userId;
        this.token = token;
        this.ipAddress = ipAddress;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = createdAt.plusHours(24);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long uid) {
        this.userId = uid;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String t) {
        this.token = t;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime t) {
        this.createdAt = t;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime t) {
        this.expiresAt = t;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ip) {
        this.ipAddress = ip;
    }
}