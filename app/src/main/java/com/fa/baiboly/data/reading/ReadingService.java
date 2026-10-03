package com.fa.baiboly.data.reading;

import android.content.Context;
import android.database.Cursor;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class ReadingService {

    private final ReadingRepository repository;

    public ReadingService(Context context) {
        this.repository = new ReadingRepository(context);
    }

    // =========================
    // 📅 Lecture du jour
    // =========================
    public String getTodayReading() {

        Calendar calendar = Calendar.getInstance();

        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int monthNumber = calendar.get(Calendar.MONTH) + 1;
        int year = calendar.get(Calendar.YEAR);

        Cursor cursor = repository.getReadingForDay(year, monthNumber, day);

        String result = "Tsy misy vakiteny androany";

        if (cursor != null && cursor.moveToFirst()) {
            result = cursor.getString(0);
            cursor.close();
        }

        return result;
    }

    // =========================
    // 📅 Tous les jours d'un mois
    // =========================
    public Map<Integer, String> getAllReadingsForMonth(int year, int month) {

        Map<Integer, String> readings = new HashMap<>();

        Cursor cursor = repository.getReadingsForMonth(year, month);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                int day = cursor.getInt(0);
                String verse = cursor.getString(1);
                if (verse != null && !verse.trim().isEmpty()) {
                    readings.put(day, verse);
                }
            } while (cursor.moveToNext());
            cursor.close();
        }

        return readings;
    }

    // =========================
    // 📅 Tous les jours d'une année
    // =========================
    public Map<String, String> getAllReadingsForYear(int year) {

        Map<String, String> readings = new HashMap<>();

        Cursor cursor = repository.getReadingsForYear(year);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                int month = cursor.getInt(0);
                int day = cursor.getInt(1);
                String verse = cursor.getString(2);
                if (verse != null && !verse.trim().isEmpty()) {
                    String dateKey = year + "-" + (month < 10 ? "0" : "") + month + "-" + (day < 10 ? "0" : "") + day;
                    readings.put(dateKey, verse);
                }
            } while (cursor.moveToNext());
            cursor.close();
        }

        return readings;
    }
}