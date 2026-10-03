package com.fa.baiboly.models;

public class VerseMark {

    private int id;
    private String bookShortName;
    private int chapter;
    private int verseNumber;
    private String type;   // "highlight" ou "favorite"
    private String color;  // hex, null si favorite
    private long createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getBookShortName() { return bookShortName; }
    public void setBookShortName(String bookShortName) { this.bookShortName = bookShortName; }

    public int getChapter() { return chapter; }
    public void setChapter(int chapter) { this.chapter = chapter; }

    public int getVerseNumber() { return verseNumber; }
    public void setVerseNumber(int verseNumber) { this.verseNumber = verseNumber; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}