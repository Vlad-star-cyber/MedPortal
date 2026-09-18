package models;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

public class ScheduleSlot implements Serializable {
    private long id;
    private long doctorId;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean booked;
    private boolean blocked;

    public ScheduleSlot() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(long did) {
        this.doctorId = did;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public void setSlotDate(LocalDate d) {
        this.slotDate = d;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime t) {
        this.startTime = t;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime t) {
        this.endTime = t;
    }

    public boolean isBooked() {
        return booked;
    }

    public void setBooked(boolean b) {
        this.booked = b;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean b) {
        this.blocked = b;
    }

    @Override
    public String toString() {
        return "Slot{id=" + id + ", doctorId=" + doctorId + ", date=" + slotDate + ", start=" + startTime + "}";
    }
}