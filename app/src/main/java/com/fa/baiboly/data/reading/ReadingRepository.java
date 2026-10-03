package com.fa.baiboly.data.reading;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.fa.baiboly.data.DatabaseHelper;

public class ReadingRepository {

    private final DatabaseHelper dbHelper;

    public ReadingRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public Cursor getReadingForDay(int year, int month, int day) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT rd.verse " +
                        "FROM reading_days rd " +
                        "JOIN reading_months rm ON rm.id = rd.month_id " +
                        "WHERE rm.year=? AND rm.month_number=? AND rd.day=?",
                new String[]{
                        String.valueOf(year),
                        String.valueOf(month),
                        String.valueOf(day)
                }
        );
    }

    // Tous les jours d'un mois avec leur verset
    public Cursor getReadingsForMonth(int year, int month) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT rd.day, rd.verse " +
                        "FROM reading_days rd " +
                        "JOIN reading_months rm ON rm.id = rd.month_id " +
                        "WHERE rm.year=? AND rm.month_number=?",
                new String[]{
                        String.valueOf(year),
                        String.valueOf(month)
                }
        );
    }

    // Tous les jours d'une année avec leur verset
    public Cursor getReadingsForYear(int year) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT rm.month_number, rd.day, rd.verse " +
                        "FROM reading_days rd " +
                        "JOIN reading_months rm ON rm.id = rd.month_id " +
                        "WHERE rm.year=?",
                new String[]{
                        String.valueOf(year)
                }
        );
    }
}