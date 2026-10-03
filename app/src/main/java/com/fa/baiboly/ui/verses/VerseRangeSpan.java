package com.fa.baiboly.ui.verses;

/**
 * Span invisible posé sur l'intégralité d'un verset (numéro + texte).
 * Sert uniquement à retrouver le chapitre/numéro de verset au moment du tap.
 */
class VerseRangeSpan {

    private final int chapter;
    private final int verseNumber;

    VerseRangeSpan(int chapter, int verseNumber) {
        this.chapter = chapter;
        this.verseNumber = verseNumber;
    }

    int getChapter() {
        return chapter;
    }

    int getVerseNumber() {
        return verseNumber;
    }
}