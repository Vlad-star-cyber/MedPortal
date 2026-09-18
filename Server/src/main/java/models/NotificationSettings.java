package models;

import java.io.Serializable;

public class NotificationSettings implements Serializable {
    private long id;
    private long userId;
    private boolean channelEmail;
    private boolean channelPush;
    private boolean channelSms;
    private boolean interval24h;
    private boolean interval2h;
    private boolean interval30min;
    private boolean quietHoursEnabled;
    private String quietFrom;   // "22:00"
    private String quietTo;     // "08:00"

    public NotificationSettings() {
        this.channelEmail = true;
        this.interval24h  = true;
        this.interval2h   = true;
        this.quietHoursEnabled = true;
        this.quietFrom = "22:00";
        this.quietTo   = "08:00";
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getUserId() { return userId; }
    public void setUserId(long uid) { this.userId = uid; }
    public boolean isChannelEmail()  { return channelEmail; }
    public void setChannelEmail(boolean b) { this.channelEmail = b; }
    public boolean isChannelPush() { return channelPush; }
    public void setChannelPush(boolean b)  { this.channelPush = b; }
    public boolean isChannelSms() { return channelSms; }
    public void setChannelSms(boolean b) { this.channelSms = b; }
    public boolean isInterval24h() { return interval24h; }
    public void setInterval24h(boolean b) { this.interval24h = b; }
    public boolean isInterval2h() { return interval2h; }
    public void setInterval2h(boolean b) { this.interval2h = b; }
    public boolean isInterval30min() { return interval30min; }
    public void setInterval30min(boolean b){ this.interval30min = b; }
    public boolean isQuietHoursEnabled() { return quietHoursEnabled; }
    public void setQuietHoursEnabled(boolean b){ this.quietHoursEnabled = b; }
    public String getQuietFrom() { return quietFrom; }
    public void setQuietFrom(String s) { this.quietFrom = s; }
    public String getQuietTo() { return quietTo; }
    public void setQuietTo(String s) { this.quietTo = s; }
}