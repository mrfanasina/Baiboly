package com.fa.baiboly.models;

public class LiturgicalSeason {

    private int id;
    private String year;
    private String season;
    private String startDate; // yyyy-MM-dd
    private String endDate;   // yyyy-MM-dd

    public LiturgicalSeason() {}

    public LiturgicalSeason(int id, String year, String season, String startDate, String endDate) {
        this.id = id;
        this.year = year;
        this.season = season;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public int getId() {
        return id;
    }

    public String getYear() {
        return year;
    }

    public String getSeason() {
        return season;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }
}