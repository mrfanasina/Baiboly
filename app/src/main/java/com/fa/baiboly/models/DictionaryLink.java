package com.fa.baiboly.models;

/**
 * Lien entre une entrée du dictionnaire et une occurrence de mot dans un
 * verset. Les positions sont des index de caractères dans le texte du verset
 * tel qu'il est stocké dans bible_verses.
 */
public class DictionaryLink {

    private final int dictId;
    private final int verseId;
    private final int bookNumber;
    private final int chapter;
    private final int verse;
    private final int startPosition;
    private final int endPosition;
    private final String word;

    public DictionaryLink(int dictId, int verseId, int bookNumber, int chapter,
                          int verse, int startPosition, int endPosition, String word) {
        this.dictId = dictId;
        this.verseId = verseId;
        this.bookNumber = bookNumber;
        this.chapter = chapter;
        this.verse = verse;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.word = word;
    }

    public int getDictId() {
        return dictId;
    }

    public int getVerseId() {
        return verseId;
    }

    public int getBookNumber() {
        return bookNumber;
    }

    public int getChapter() {
        return chapter;
    }

    public int getVerse() {
        return verse;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public int getEndPosition() {
        return endPosition;
    }

    public String getWord() {
        return word;
    }
}
