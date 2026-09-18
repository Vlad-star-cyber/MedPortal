package models;

import java.io.Serializable;

public class Doctor implements Serializable {
    private long id;
    private long userId;
    private String fullName;
    private Specialization specialization;
    private String officeNumber;
    private String education;
    private String about;
    private int experienceYears;
    private boolean active;
    private int price;

    public Doctor() {}

    public int getPrice() { return price; }
    public void setPrice(int price) { this.price = price; }
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getUserId() { return userId; }
    public void setUserId(long uid) { this.userId = uid; }
    public String getFullName() { return fullName; }
    public void setFullName(String n) { this.fullName = n; }
    public Specialization getSpecialization() { return specialization; }
    public void setSpecialization(Specialization s) { this.specialization = s; }
    public String getOfficeNumber() { return officeNumber; }
    public void setOfficeNumber(String o) { this.officeNumber = o; }
    public String getEducation() { return education; }
    public void setEducation(String e) { this.education = e; }
    public String getAbout() { return about; }
    public void setAbout(String a) { this.about = a; }
    public int getExperienceYears() { return experienceYears; }
    public void setExperienceYears(int y) { this.experienceYears = y; }
    public boolean isActive() { return active; }
    public void setActive(boolean a) { this.active = a; }

    @Override public String toString() {
        return "Doctor{id=" + id + ", fullName='" + fullName + "', spec=" + specialization + "}";
    }
}