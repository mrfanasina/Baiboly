package com.fa.baiboly.data.perikopa;

import android.content.Context;
import android.database.Cursor;
import android.os.Build;

import com.fa.baiboly.data.bible.BibleService;
import com.fa.baiboly.models.PerikopaDay;
import com.fa.baiboly.models.Reading;
import com.fa.baiboly.data.parser.ReadingParser;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PerikopaService {

    private final PerikopaRepository repository;
    private ReadingParser readingParser;

    private BibleService bibleService;
    public PerikopaService(Context context) {
        this.repository = new PerikopaRepository(context);
        this.readingParser =  new ReadingParser();
        this.bibleService = new BibleService(context);
    }

    // =========================
    // PERIKOPA TODAY
    // =========================
    public PerikopaDay getTodayPerikopa() {

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());

        return getPerikopaByDate(today);
    }

    // =========================
    // PERIKOPA NEXT (today or nearest future)
    // =========================
    public PerikopaDay getNextPerikopa() {

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());

        Cursor cursor = repository.getNextPerikopaDate(today);

        PerikopaDay day = new PerikopaDay();
        day.setVerses(new ArrayList<>());

        if (cursor != null && cursor.moveToFirst()) {
            day.setDate(cursor.getString(0));
            day.setName(cursor.getString(1));
            day.setSeason(cursor.getString(2));

            // Load verses for this date
            Cursor versesCursor = repository.getPerikopaForDate(day.getDate());
            if (versesCursor != null && versesCursor.moveToFirst()) {
                do {
                    String verse = versesCursor.getString(2);
                    if (verse != null) {
                        day.getVerses().add(verse);
                    }
                } while (versesCursor.moveToNext());
                versesCursor.close();
            }

            cursor.close();
        } else {
            day.setName("Tsy misy perikopa");
            day.setSeason("");
        }

        return day;
    }
    public String getTodayLohahevitra() {

        String theme = "";

        int year;
        int month;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDate now = LocalDate.now();
            year = now.getYear();
            month = now.getMonthValue();
        } else {
            java.util.Calendar c = java.util.Calendar.getInstance();
            year = c.get(java.util.Calendar.YEAR);
            month = c.get(java.util.Calendar.MONTH) + 1;
        }

        String monthName = mapMonth(month);

        Cursor c = repository.getLohahevitra(monthName, year);

        if (c != null && c.moveToFirst()) {
            theme = c.getString(0);
            c.close();
        }

        return theme;
    }

    // Get all lohahevitra (themes) for a year
    public Map<String, String> getAllLohahevitraForYear(int year) {
        return repository.getAllLohahevitraForYear(year);
    }

    // =========================
    // PERIKOPA BY DATE
    // =========================
    public PerikopaDay getPerikopaByDate(String date) {

        Cursor cursor = repository.getPerikopaForDate(date);

        PerikopaDay day = new PerikopaDay();
        day.setDate(date);
        day.setVerses(new ArrayList<>());

        if (cursor != null && cursor.moveToFirst()) {

            // 🧠 metadata (répétée mais identique sur toutes les lignes)
            day.setName(cursor.getString(0));
            day.setSeason(cursor.getString(1));

            // 📖 versets (multi lignes)
            do {
                String verse = cursor.getString(2);

                if (verse != null) {
                    day.getVerses().add(verse);
                }

            } while (cursor.moveToNext());

            cursor.close();

        } else {
            // fallback propre
            day.setName("Tsy misy perikopa");
            day.setSeason("");
        }

        return day;
    }

    public PerikopaDay sortPerikopa(PerikopaDay day)  {
        if (day == null || day.getVerses() == null) return day;

        List<Reading> readings = new ArrayList<>();
        readingParser.setService(bibleService);
        for (String verse : day.getVerses()) {

            if (verse == null || verse.trim().isEmpty()) continue;
            Reading reading = readingParser.parse(verse);
            if (reading != null) {
                readings.add(reading);
            }
        }

        // =========================
        // 2. SORT BOOK ORDER (DB ID)
        // =========================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            readings.sort(Comparator.comparingInt(a -> a.getBook().getId())
            );
        }
        // =========================
        // 3. REBUILD STRING LIST
        // =========================
        List<String> sortedVerses = new ArrayList<>();

        for (Reading r : readings) {
            sortedVerses.add(r.toString());
        }

        // =========================
        // 4. NEW PERIKOPA
        // =========================
        PerikopaDay sorted = new PerikopaDay();
        sorted.setDate(day.getDate());
        sorted.setName(day.getName());
        sorted.setSeason(day.getSeason());
        sorted.setVerses(sortedVerses);

        return sorted;
    }

    //Malagasy month
    private String mapMonth(int month) {

        switch (month) {
            case 1: return "Janoary";
            case 2: return "Febroary";
            case 3: return "Martsa";
            case 4: return "Aprily";
            case 5: return "May";
            case 6: return "Jona";
            case 7: return "Jolay";
            case 8: return "Aogositra";
            case 9: return "Septambra";
            case 10: return "Oktobra";
            case 11: return "Novambra";
            case 12: return "Desambra";
            default: return "None";
        }
    }

    // =========================
    // 📅 Tous les jours de perikopa d'une année
    // =========================
    public List<PerikopaDay> getAllPerikopaForYear(int year) {

        List<PerikopaDay> days = new ArrayList<>();

        for (int month = 1; month <= 12; month++) {
            String yearMonth = year + "-" + (month < 10 ? "0" : "") + month;
            Cursor cursor = repository.getPerikopaDaysForMonth(yearMonth);

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String date = cursor.getString(0);
                    String name = cursor.getString(1);
                    String season = cursor.getString(2);

                    PerikopaDay perikopaDay = new PerikopaDay();
                    perikopaDay.setDate(date);
                    perikopaDay.setName(name);
                    perikopaDay.setSeason(season);
                    perikopaDay.setVerses(new ArrayList<>());

                    Cursor versesCursor = repository.getPerikopaForDate(date);
                    if (versesCursor != null && versesCursor.moveToFirst()) {
                        do {
                            String verse = versesCursor.getString(2);
                            if (verse != null) {
                                perikopaDay.getVerses().add(verse);
                            }
                        } while (versesCursor.moveToNext());
                        versesCursor.close();
                    }

                    days.add(perikopaDay);
                } while (cursor.moveToNext());
                cursor.close();
            }
        }

        return days;
    }

    // =========================
    // 📅 Tous les jours de perikopa d'un mois
    // =========================
    public Map<Integer, PerikopaDay> getAllPerikopaForMonth(int year, int month) {

        Map<Integer, PerikopaDay> days = new HashMap<>();

        String yearMonth = year + "-" + (month < 10 ? "0" : "") + month;

        Cursor cursor = repository.getPerikopaDaysForMonth(yearMonth);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                String date = cursor.getString(0); // yyyy-MM-dd
                String name = cursor.getString(1);
                String season = cursor.getString(2);

                // Extraire le jour depuis la date
                int day = Integer.parseInt(date.substring(date.lastIndexOf("-") + 1));

                PerikopaDay perikopaDay = new PerikopaDay();
                perikopaDay.setDate(date);
                perikopaDay.setName(name);
                perikopaDay.setSeason(season);
                perikopaDay.setVerses(new ArrayList<>());

                // Charger les versets pour ce jour
                Cursor versesCursor = repository.getPerikopaForDate(date);
                if (versesCursor != null && versesCursor.moveToFirst()) {
                    do {
                        String verse = versesCursor.getString(2);
                        if (verse != null) {
                            perikopaDay.getVerses().add(verse);
                        }
                    } while (versesCursor.moveToNext());
                    versesCursor.close();
                }

                days.put(day, perikopaDay);
            } while (cursor.moveToNext());
            cursor.close();
        }

        return days;
    }
}