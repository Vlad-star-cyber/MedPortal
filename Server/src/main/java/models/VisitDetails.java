package models;

import java.io.Serializable;
import java.time.LocalDateTime;

public class VisitDetails implements Serializable {
    private long id;
    private long appointmentId;
    private String complaints;
    private String examination;
    private String diagnosisCode;
    private String diagnosisText;
    private String prescription;
    private String notes;
    private LocalDateTime createdAt;

    public VisitDetails() {
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

    public String getComplaints() {
        return complaints;
    }

    public void setComplaints(String c) {
        this.complaints = c;
    }

    public String getExamination() {
        return examination;
    }

    public void setExamination(String e) {
        this.examination = e;
    }

    public String getDiagnosisCode() {
        return diagnosisCode;
    }

    public void setDiagnosisCode(String c) {
        this.diagnosisCode = c;
    }

    public String getDiagnosisText() {
        return diagnosisText;
    }

    public void setDiagnosisText(String t) {
        this.diagnosisText = t;
    }

    public String getPrescription() {
        return prescription;
    }

    public void setPrescription(String p) {
        this.prescription = p;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String n) {
        this.notes = n;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime t) {
        this.createdAt = t;
    }

    @Override
    public String toString() {
        return "VisitDetails{id=" + id + ", appointmentId=" + appointmentId + ", diagnosis=" + diagnosisCode + "}";
    }
}