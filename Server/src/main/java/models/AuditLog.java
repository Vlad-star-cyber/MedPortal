package models;

import java.io.Serializable;
import java.time.LocalDateTime;

public class AuditLog implements Serializable {
    private long id;
    private long userId;
    private String userLogin;
    private String action;
    private String entityType;
    private String entityId;
    private String details;
    private String level;
    private LocalDateTime timestamp;

    public AuditLog() {}

    public AuditLog(long userId, String userLogin, String action,
                    String entityType, String entityId,
                    String details, String level) {
        this.userId = userId;
        this.userLogin = userLogin;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.level = level;
        this.timestamp = LocalDateTime.now();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getUserId() { return userId; }
    public void setUserId(long uid) { this.userId = uid; }
    public String getUserLogin() { return userLogin; }
    public void setUserLogin(String l) { this.userLogin = l; }
    public String getAction() { return action; }
    public void setAction(String a) { this.action = a; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String t) { this.entityType = t; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String eid) { this.entityId = eid; }
    public String getDetails() { return details; }
    public void setDetails(String d) { this.details = d; }
    public String getLevel() { return level; }
    public void setLevel(String l) { this.level = l; }
    public LocalDateTime getTimestamp(){ return timestamp; }
    public void setTimestamp(LocalDateTime t) { this.timestamp = t; }

    @Override public String toString() {
        return "AuditLog{" + level + " | " + action + " | " + userLogin + " | " + details + "}";
    }
}