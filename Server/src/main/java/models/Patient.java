package models;

import java.io.Serializable;
import java.time.LocalDate;

public class Patient implements Serializable {
    private long id;
    private long userId;
    private String fullName;
    private String phone;
    private String email;
    private LocalDate dateOfBirth;
    private String policy;
    private String allergy;
    private String bloodType;

    public Patient() {
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String n) {
        this.fullName = n;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String p) {
        this.phone = p;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String e) {
        this.email = e;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate d) {
        this.dateOfBirth = d;
    }

    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String p) {
        this.policy = p;
    }

    public String getAllergy() {
        return allergy;
    }

    public void setAllergy(String a) {
        this.allergy = a;
    }

    public String getBloodType() {
        return bloodType;
    }

    public void setBloodType(String bt) {
        this.bloodType = bt;
    }

    @Override
    public String toString() {
        return "Patient{id=" + id + ", fullName='" + fullName + "'}";
    }
}
