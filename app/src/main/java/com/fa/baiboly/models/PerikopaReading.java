package com.fa.baiboly.models;

public class PerikopaReading {

    private int id;
    private int dayId;
    private String verse;

    public PerikopaReading() {}

    public PerikopaReading(int id, int dayId, String verse) {
        this.id = id;
        this.dayId = dayId;
        this.verse = verse;
    }

    public int getId() {
        return id;
    }

    public int getDayId() {
        return dayId;
    }

    public String getVerse() {
        return verse;
    }
}