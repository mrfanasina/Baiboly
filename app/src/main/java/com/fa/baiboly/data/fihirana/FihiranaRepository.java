package com.fa.baiboly.data.fihirana;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.fa.baiboly.data.DatabaseHelper;

public class FihiranaRepository {

    private final DatabaseHelper dbHelper;

    public FihiranaRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    // =========================
    // 🎵 ALL SONGS
    // =========================
    public Cursor getAllSongs() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT id, laharana, sokajy, lohateny " +
                        "FROM songs " +
                        "ORDER BY laharana",
                null
        );
    }

    // =========================
    // 🎵 SONG BY CATEGORY
    // =========================
    public Cursor getSongsByCategory(String category) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT id, laharana, sokajy, lohateny " +
                        "FROM songs " +
                        "WHERE sokajy=? " +
                        "ORDER BY laharana",
                new String[]{category}
        );
    }
    // =========================
// 🎵 SONG BY NUMBER + CATEGORY
// =========================
    public Cursor getSongByNumberAndCategory(int number, String category) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT id, laharana, sokajy, lohateny " +
                        "FROM songs " +
                        "WHERE laharana = ? AND sokajy = ?",
                new String[]{
                        String.valueOf(number),
                        category
                }
        );
    }

    // =========================
    // 🎶 VERSES OF SONG
    // =========================
    public Cursor getSongVerses(String songId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT andininy, tononkira, fiverenany " +
                        "FROM verses " +
                        "WHERE song_id=? " +
                        "ORDER BY andininy",
                new String[]{songId}
        );
    }

    // =========================
    // 👤 AUTHORS
    // =========================
    public Cursor getAuthors(String songId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT a.name " +
                        "FROM authors a " +
                        "JOIN song_authors sa ON a.id = sa.author_id " +
                        "WHERE sa.song_id=?",
                new String[]{songId}
        );
    }
    // =========================
// 📂 CATEGORIES (types chants)
// =========================
    public Cursor getCategories() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT DISTINCT sokajy FROM songs ORDER BY sokajy",
                null
        );
    }

// =====================================================
// 🔍 SEARCH (FTS5 + fallback LIKE si module indisponible)
// =====================================================

    public Cursor search(String query) {

        String likeSafe = query.replace("%", "").replace("_", "").trim();

        if (likeSafe.isEmpty()) return null;

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // 1) Tentative FTS5 — inclut un extrait du premier couplet correspondant
        if (isFts5Available(db)) {
            try {
                String ftsQuery = buildFtsQuery(likeSafe);

                String sql = "SELECT s.id, s.laharana, s.sokajy, s.lohateny, " +
                        "(SELECT v2.tononkira FROM verses v2 " +
                        "  WHERE v2.song_id = s.id " +
                        "  AND v2.tononkira IS NOT NULL " +
                        "  LIMIT 1) AS first_verse " +
                        "FROM songs s " +
                        "JOIN verses_fts f ON f.rowid IN (SELECT id FROM verses WHERE song_id = s.id) " +
                        "WHERE verses_fts MATCH ? " +
                        "GROUP BY s.id " +
                        "ORDER BY s.laharana";

                return db.rawQuery(sql, new String[]{ftsQuery});

            } catch (Exception e) {
                Log.w("SEARCH", "FTS5 indisponible (chants), fallback LIKE : " + e.getMessage());
            }
        }

        // 2) Fallback universel : LIKE sur le texte des couplets ET le titre du chant
        String sql = "SELECT DISTINCT s.id, s.laharana, s.sokajy, s.lohateny, " +
                "(SELECT v2.tononkira FROM verses v2 " +
                "  WHERE v2.song_id = s.id " +
                "  AND v2.tononkira IS NOT NULL " +
                "  LIMIT 1) AS first_verse " +
                "FROM songs s " +
                "LEFT JOIN verses v ON v.song_id = s.id " +
                "WHERE v.tononkira LIKE ? OR s.lohateny LIKE ? " +
                "ORDER BY s.laharana " +
                "LIMIT 100";

        String pattern = "%" + likeSafe + "%";
        return db.rawQuery(sql, new String[]{pattern, pattern});
    }

    /**
     * Vérifie une fois si FTS5 est compilé dans le SQLite du device.
     * Mis en cache pour éviter de refaire le test à chaque recherche.
     */
    private Boolean fts5AvailableCache = null;

    private boolean isFts5Available(SQLiteDatabase db) {
        if (fts5AvailableCache != null) return fts5AvailableCache;

        try {
            db.rawQuery(
                    "SELECT 1 FROM verses_fts WHERE verses_fts MATCH 'test' LIMIT 0", null
            ).close();
            fts5AvailableCache = true;
        } catch (Exception e) {
            fts5AvailableCache = false;
        }

        return fts5AvailableCache;
    }

    /**
     * Construit une requête FTS5 avec wildcard préfixe sur chaque mot.
     */
    private String buildFtsQuery(String raw) {
        String cleanInput = raw.replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        String[] words = cleanInput.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(word).append("*");
            }
        }
        return sb.toString();
    }
    public Cursor getSongById() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.rawQuery(
                "SELECT id, laharana, sokajy, lohateny  FROM songs ",null
        );
    }
}