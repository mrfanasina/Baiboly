package com.fa.baiboly.models;

public class Verse {
    private int id;
    private int number;
    private String text;

    private String title;

    public Verse(int id, int number, String text, String title) {
        this.id = id;
        this.number = number;
        this.text = text;
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public int getNumber() {
        return number;
    }

    public String getText() {
        return text;
    }

    public void setId(int id) {
        this.id = id;
    }
    public int getId() {
        return id;
    }
}