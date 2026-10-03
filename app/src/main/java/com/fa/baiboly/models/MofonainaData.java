package com.fa.baiboly.models;

import java.util.List;

public class MofonainaData {

    private String title;

    private Reading verseOfDay;

    private Reading bibleReading1;

    private Song song1;
    private Song song2;

    private String bibleText;
    private String reflection;
    private String question;
    private String date;
    private List<ReflectionSection> sections;
    private String author;

    public MofonainaData() {}

    // getters and setters

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Reading getVerseOfDay() {
        return verseOfDay;
    }

    public void setVerseOfDay(Reading verseOfDay) {
        this.verseOfDay = verseOfDay;
    }

    public Reading getBibleReading1() {
        return bibleReading1;
    }

    public void setBibleReading1(Reading bibleReading1) {
        this.bibleReading1 = bibleReading1;
    }

    public Song getSong1() {
        return song1;
    }

    public void setSong1(Song song1) {
        this.song1 = song1;
    }

    public Song getSong2() {
        return song2;
    }

    public void setSong2(Song song2) {
        this.song2 = song2;
    }

    public String getBibleText() {
        return bibleText;
    }

    public void setBibleText(String bibleText) {
        this.bibleText = bibleText;
    }

    public String getReflection() {
        return reflection;
    }

    public void setReflection(String reflection) {
        this.reflection = reflection;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public List<ReflectionSection> getSections() {
        return sections;
    }

    public void setSections(List<ReflectionSection> sections) {
        this.sections = sections;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }


}