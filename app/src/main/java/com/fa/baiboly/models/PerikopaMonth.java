package com.fa.baiboly.models;


import java.util.List;

public class PerikopaMonth {

    private int id;
    private String year;
    private String monthName;
    private String theme;
    private List<PerikopaDay> days;

    public PerikopaMonth() {}

    public PerikopaMonth(int id, String year, String monthName, String theme, List<PerikopaDay> days) {
        this.id = id;
        this.year = year;
        this.monthName = monthName;
        this.theme = theme;
        this.days = days;
    }

    public int getId() {
        return id;
    }

    public String getYear() {
        return year;
    }

    public String getMonthName() {
        return monthName;
    }

    public String getTheme() {
        return theme;
    }

    public List<PerikopaDay> getDays() {
        return days;
    }

    public void setDays(List<PerikopaDay> days) {
        this.days = days;
    }
}