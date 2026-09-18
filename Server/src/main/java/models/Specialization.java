package models;

import java.io.Serializable;

public class Specialization implements Serializable {
    private int id;
    private String name;

    public Specialization() {
    }

    public Specialization(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String n) {
        this.name = n;
    }

    @Override
    public String toString() {
        return "Specialization{id=" + id + ", name='" + name + "'}";
    }
}