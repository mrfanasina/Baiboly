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

    /**
     * true si la base contient la colonne titre (versions recentes).
     * Les anciennes bases installees n'en ont pas encore : on retombe
     * alors sur le nom deduit du code.
     */
    private boolean hasTitreColumn(SQLiteDatabase db) {
        Cursor c = db.rawQuery("PRAGMA table_info(creeds)", null);
        try {
            int nameIndex = c.getColumnIndex("name");
            while (c.moveToNext()) {
                if ("titre".equalsIgnoreCase(c.getString(nameIndex))) return true;
            }
        } finally {
            c.close();
        }
        return false;
    }

    /** Expression SQL de la colonne titre (NULL si elle n'existe pas). */
    private String titreColumn(SQLiteDatabase db) {
        return hasTitreColumn(db) ? "titre" : "NULL AS titre";
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
                "SELECT id, text, " + titreColumn(db) +
                        " FROM creeds " +
                        "WHERE code=? " +
                        "AND language='mg'",
                new String[]{code}
        );
    }

    /**
     * Codes des credos, fanekem-pinoana (v1, v2, …) en premier, puis les
     * autres dans l'ordre d'insertion (id) de la base.
     */
    public Cursor getCode() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT code FROM creeds " +
                        "GROUP BY code " +
                        "ORDER BY CASE WHEN LOWER(code) GLOB 'v[0-9]*' THEN 0 ELSE 1 END, " +
                        "MIN(id)",
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
                "SELECT code, text, " + titreColumn(db) +
                        " FROM creeds " +
                        "WHERE language='mg' " +
                        "GROUP BY code",
                null
        );
    }

    /**
     * Titre de chaque credo malgache (une ligne par code), pour la liste.
     */
    public Cursor getAllTitles() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT code, " + titreColumn(db) +
                        " FROM creeds " +
                        "WHERE language='mg' " +
                        "GROUP BY code",
                null
        );
    }

    // =========================
    // SEARCH (pas de FTS ici, table petite -> LIKE direct)
    // =========================
    public Cursor search(String query) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String safe = query.replace("%", "").replace("_", "").trim();
        if (safe.isEmpty()) return null;

        String pattern = "%" + safe + "%";

        return db.rawQuery(
                "SELECT id, code, text, " + titreColumn(db) +
                        " FROM creeds " +
                        "WHERE language='mg' " +
                        "AND (text LIKE ? OR code LIKE ?) " +
                        "LIMIT 50",
                new String[]{pattern, pattern}
        );
    }
}
