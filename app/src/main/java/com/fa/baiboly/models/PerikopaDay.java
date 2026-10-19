package com.fa.baiboly.models;

import java.util.List;

public class PerikopaDay {

    private String date;        // yyyy-MM-dd
    private String name;        // ex: "Dimanche 3 après Pâques"
    private String season;     // ex: "Temps pascal"
    private List<String> verses;

    public PerikopaDay() {}

    public PerikopaDay(String date, String name, String season, List<String> verses) {
        this.date = date;
        this.name = name;
        this.season = season;
        this.verses = verses;
    }

    // =========================
    // GETTERS
    // =========================
    public String getDate() {
        return date;
    }

    public String getName() {
        return name;
    }

    public String getSeason() {
        return season;
    }

    public List<String> getVerses() {
        return verses;
    }

    // =========================
    // SETTERS
    // =========================
    public void setDate(String date) {
        this.date = date;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public void setVerses(List<String> verses) {
        this.verses = verses;
    }
}