package com.fa.baiboly.models;

import java.io.Serializable;

public class Song implements Serializable {

    private String id;
    private int number;
    private String category;
    private String title;
    private String firstVerse;

    public Song(String id, int number, String category, String title) {
        this.id = id;
        this.number = number;
        this.category = category;
        this.title = title;
    }

    public Song(String id, int number, String category, String title, String firstVerse) {
        this(id, number, category, title);
        this.firstVerse = firstVerse;
    }

    public String getId() {
        return id;
    }

    public int getNumber() {
        return number;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getFirstVerse() {
        return firstVerse;
    }

    public void setFirstVerse(String firstVerse) {
        this.firstVerse = firstVerse;
    }

}