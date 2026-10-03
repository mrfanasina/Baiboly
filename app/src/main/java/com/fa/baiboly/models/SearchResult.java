package com.fa.baiboly.models;

public class SearchResult {

    public int verseId;
    public String bookName;
    public int chapter;
    public int verse;
    public String text;

    public SearchResult(int id, String bookName, int chapter, int verse, String text) {
        this.verseId = id;
        this.bookName = bookName;
        this.chapter = chapter;
        this.verse = verse;
        this.text = text;
    }

    public int getVerseId() {
        return verseId;
    }

    public String getBookName() {
        return bookName;
    }

    public int getChapter() {
        return chapter;
    }

    public int getVerse() {
        return verse;
    }

    public String getText() {
        return text;
    }
}
