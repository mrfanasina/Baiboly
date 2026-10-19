package com.fa.baiboly.models;

import java.io.Serializable;

public class Chapter implements Serializable {
    private int bookId;
    private int number;
    private int verseCount;

    public Chapter(int bookId, int number, int verseCount) {
        this.bookId = bookId;
        this.number = number;
        this.verseCount = verseCount;
    }

    // Getters
    public int getBookId() { return bookId; }
    public int getChapterNumber() { return number; }
    public int getVerseCount() { return verseCount; }
}