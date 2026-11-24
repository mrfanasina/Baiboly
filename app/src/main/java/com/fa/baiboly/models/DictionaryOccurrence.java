package com.fa.baiboly.models;

/**
 * Un verset où une entrée du dictionnaire apparaît, avec le nom court du
 * livre (ex. « Gen ») pour ouvrir directement la lecture.
 */
public class DictionaryOccurrence {

    private final int bookNumber;
    private final String bookName;
    private final int chapter;
    private final int verse;

    public DictionaryOccurrence(int bookNumber, String bookName, int chapter, int verse) {
        this.bookNumber = bookNumber;
        this.bookName = bookName;
        this.chapter = chapter;
        this.verse = verse;
    }

    public int getBookNumber() {
        return bookNumber;
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

    /** Référence comprise par ReadingParser : « Gen 1:1 ». */
    public String getReference() {
        return bookName + " " + chapter + ":" + verse;
    }
}
