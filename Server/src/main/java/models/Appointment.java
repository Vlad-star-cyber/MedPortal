package models;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Appointment implements Serializable {
    private long id;
    private long patientId;
    private long doctorId;
    private long slotId;
    private LocalDateTime appointmentDatetime;
    private AppointmentStatus status;
    private String reason;
    private LocalDateTime createdAt;

    private String patientName;
    private String doctorName;
    private String specialization;

    private String diagnosis;
    private String prescription;

    public Appointment() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getPatientId() { return patientId; }
    public void setPatientId(long pid) { this.patientId = pid; }
    public long getDoctorId() { return doctorId; }
    public void setDoctorId(long did) { this.doctorId = did; }
    public long getSlotId() { return slotId; }
    public void setSlotId(long sid) { this.slotId = sid; }
    public LocalDateTime getAppointmentDatetime() { return appointmentDatetime; }
    public void setAppointmentDatetime(LocalDateTime dt) { this.appointmentDatetime = dt; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus s){ this.status = s; }
    public String getReason() { return reason; }
    public void setReason(String r) { this.reason = r; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String n) { this.patientName = n; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String n) { this.doctorName = n; }
    public String getSpecialization() { return specialization; }
    public void setSpecialization(String s) { this.specialization = s; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public String getPrescription() { return prescription; }
    public void setPrescription(String prescription) { this.prescription = prescription; }

    @Override public String toString() {
        return "Appointment{id=" + id + ", patient=" + patientId + ", doctor=" + doctorId + ", status=" + status + "}";
    }
}