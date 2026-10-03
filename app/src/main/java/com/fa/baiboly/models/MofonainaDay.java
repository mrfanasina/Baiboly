package com.fa.baiboly.models;

/**
 * Represents a single day's devotional entry from mofonaina.json
 * (Aina sy Fahasalamana - Mofonaina isan'andro)
 */
public class MofonainaDay {

    private String id;
    private String dateIso;
    private int dayIndex;
    private String dayNameMg;
    private String dayNameEn;
    private String monthMg;
    private String monthFr;
    private int year;
    private String bibleReference;
    private String themeSlogan;
    private String meditation;
    private String messageToday;
    private String prayer;

    public MofonainaDay() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDateIso() { return dateIso; }
    public void setDateIso(String dateIso) { this.dateIso = dateIso; }

    public int getDayIndex() { return dayIndex; }
    public void setDayIndex(int dayIndex) { this.dayIndex = dayIndex; }

    public String getDayNameMg() { return dayNameMg; }
    public void setDayNameMg(String dayNameMg) { this.dayNameMg = dayNameMg; }

    public String getDayNameEn() { return dayNameEn; }
    public void setDayNameEn(String dayNameEn) { this.dayNameEn = dayNameEn; }

    public String getMonthMg() { return monthMg; }
    public void setMonthMg(String monthMg) { this.monthMg = monthMg; }

    public String getMonthFr() { return monthFr; }
    public void setMonthFr(String monthFr) { this.monthFr = monthFr; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public String getBibleReference() { return bibleReference; }
    public void setBibleReference(String bibleReference) { this.bibleReference = bibleReference; }

    public String getThemeSlogan() { return themeSlogan; }
    public void setThemeSlogan(String themeSlogan) { this.themeSlogan = themeSlogan; }

    public String getMeditation() { return meditation; }
    public void setMeditation(String meditation) { this.meditation = meditation; }

    public String getMessageToday() { return messageToday; }
    public void setMessageToday(String messageToday) { this.messageToday = messageToday; }

    public String getPrayer() { return prayer; }
    public void setPrayer(String prayer) { this.prayer = prayer; }

    /**
     * Returns a formatted date display string in Malagasy
     * e.g. "ALAKAMISY 03 Septambra 2026"
     */
    public String getFormattedDate() {
        return dayNameMg + " " + String.format("%02d", getDayOfMonth()) + " " + monthMg + " " + year;
    }

    /**
     * Extracts day of month from dateIso (e.g. "2026-09-03" -> 3)
     */
    public int getDayOfMonth() {
        if (dateIso != null && dateIso.length() >= 10) {
            try {
                return Integer.parseInt(dateIso.substring(8, 10));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
