package com.fa.baiboly.data.fanekena;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.fa.baiboly.data.DatabaseHelper;

public class FanekenaRepository {
    private final DatabaseHelper dbHelper;

    public FanekenaRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    // =========================
    // ALL
    // =========================
    public Cursor getAllFanekena() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT id, laharana, sokajy, lohateny " +
                        "FROM songs " +
                        "ORDER BY laharana",
                null
        );
    }

    // =========================
    // BY CODE (language mg)
    // =========================
    public Cursor getByCode(String code) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT id, text " +
                        "FROM creeds " +
                        "WHERE code=? " +
                        "AND language='mg'",
                new String[]{code}
        );
    }

    public Cursor getCode() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT DISTINCT code FROM creeds",
                null
        );
    }

    /**
     * Tous les credos en malgache (une ligne par code) avec leur texte,
     * pour afficher un extrait dans la liste.
     */
    public Cursor getAllWithText() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT code, text " +
                        "FROM creeds " +
                        "WHERE language='mg' " +
                        "GROUP BY code",
                null
        );
    }

    // =========================
    // 🔍 SEARCH (pas de FTS ici, table petite -> LIKE direct)
    // =========================
    public Cursor search(String query) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String safe = query.replace("%", "").replace("_", "").trim();
        if (safe.isEmpty()) return null;

        String pattern = "%" + safe + "%";

        return db.rawQuery(
                "SELECT id, code, text " +
                        "FROM creeds " +
                        "WHERE language='mg' " +
                        "AND (text LIKE ? OR code LIKE ?) " +
                        "LIMIT 50",
                new String[]{pattern, pattern}
        );
    }
}