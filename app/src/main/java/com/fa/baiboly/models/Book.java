package com.fa.baiboly.models;

import java.io.Serializable;

public class Book implements Serializable {

    private int id;
    private String longName;
    private String shortName;
    private String color;

    // Constructeur
    public Book(int id, String color, String shortName, String longName) {
        this.id = id;
        this.color = color;
        this.longName = longName;
        this.shortName = shortName;
    }

    // --- GETTERS ---

    public int getId() {
        return id;
    }

    public String getLongName() {
        return longName;
    }

    public String getShortName() {
        return shortName;
    }

    public String getColor() {
        return color;
    }

    // --- SETTERS ---

    public void setId(int id) {
        this.id = id;
    }

    public void setLongName(String longName) {
        this.longName = longName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public void setColor(String color) {
        this.color = color;
    }
}