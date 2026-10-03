package com.fa.baiboly.data.perikopa;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.fa.baiboly.data.DatabaseHelper;

import java.util.HashMap;
import java.util.Map;

public class PerikopaRepository {

    private final DatabaseHelper dbHelper;

    public PerikopaRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    // 📅 Tous les versets d’un jour
    public Cursor getPerikopaForDate(String date) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT d.name, s.season, r.verse " +
                        "FROM perikopa_days d " +
                        "LEFT JOIN liturgical_seasons s ON s.id = d.season_id " +
                        "LEFT JOIN perikopa_readings r ON r.day_id = d.id " +
                        "WHERE d.date = ?",
                new String[]{date}
        );
    }

    public Cursor getLohahevitra(String month, int year) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT theme FROM perikopa_months WHERE month_name = ? AND year = ?",
                new String[]{month, String.valueOf(year)}
        );
    }

    // Prochaine date de perikopa à partir d'une date donnée
    public Cursor getNextPerikopaDate(String fromDate) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT d.date, d.name, s.season " +
                        "FROM perikopa_days d " +
                        "LEFT JOIN liturgical_seasons s ON s.id = d.season_id " +
                        "WHERE d.date >= ? " +
                        "ORDER BY d.date ASC LIMIT 1",
                new String[]{fromDate}
        );
    }

    // Tous les jours de perikopa d'un mois (date, name, season)
    public Cursor getPerikopaDaysForMonth(String yearMonth) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT d.date, d.name, s.season " +
                        "FROM perikopa_days d " +
                        "LEFT JOIN liturgical_seasons s ON s.id = d.season_id " +
                        "WHERE d.date LIKE ?",
                new String[]{yearMonth + "%"}
        );
    }

    // Get all lohahevitra (themes) for a year
    public Map<String, String> getAllLohahevitraForYear(int year) {
        Map<String, String> result = new HashMap<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String[] malagasyMonths = {
            "Janoary", "Febroary", "Martsa", "Aprily", "May", "Jona",
            "Jolay", "Aogositra", "Septambra", "Oktobra", "Novambra", "Desambra"
        };

        for (int m = 1; m <= 12; m++) {
            Cursor cursor = db.rawQuery(
                "SELECT theme FROM perikopa_months WHERE month_name = ? AND year = ?",
                new String[]{malagasyMonths[m - 1], String.valueOf(year)}
            );
            if (cursor != null && cursor.moveToFirst()) {
                String theme = cursor.getString(0);
                if (theme != null && !theme.isEmpty()) {
                    result.put(year + "-" + (m < 10 ? "0" : "") + m, theme);
                }
                cursor.close();
            }
        }
        return result;
    }
}