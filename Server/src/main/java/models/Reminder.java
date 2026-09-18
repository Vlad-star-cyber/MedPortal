package models;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Reminder implements Serializable {
    private long id;
    private long appointmentId;
    private LocalDateTime scheduledSendTime;
    private int intervalMinutes;
    private String channel;
    private ReminderStatus status;
    private boolean enabled;
    private LocalDateTime actualSendTime;

    private String patientEmail;
    private String patientName;
    private String doctorName;
    private String specialization;
    private LocalDateTime appointmentDatetime;

    public Reminder() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(long aid) {
        this.appointmentId = aid;
    }

    public LocalDateTime getScheduledSendTime() {
        return scheduledSendTime;
    }

    public void setScheduledSendTime(LocalDateTime t) {
        this.scheduledSendTime = t;
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    public void setIntervalMinutes(int m) {
        this.intervalMinutes = m;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String c) {
        this.channel = c;
    }

    public ReminderStatus getStatus() {
        return status;
    }

    public void setStatus(ReminderStatus s) {
        this.status = s;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean e) {
        this.enabled = e;
    }

    public LocalDateTime getActualSendTime() {
        return actualSendTime;
    }

    public void setActualSendTime(LocalDateTime t) {
        this.actualSendTime = t;
    }

    public String getPatientEmail() {
        return patientEmail;
    }

    public void setPatientEmail(String e) {
        this.patientEmail = e;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String n) {
        this.patientName = n;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String n) {
        this.doctorName = n;
    }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String s) {
        this.specialization = s;
    }

    public LocalDateTime getAppointmentDatetime() {
        return appointmentDatetime;
    }

    public void setAppointmentDatetime(LocalDateTime dt) {
        this.appointmentDatetime = dt;
    }

    @Override
    public String toString() {
        return "Reminder{id=" + id + ", appointmentId=" + appointmentId + ", status=" + status + "}";
    }
}