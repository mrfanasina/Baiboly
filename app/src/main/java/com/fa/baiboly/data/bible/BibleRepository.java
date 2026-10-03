package com.fa.baiboly.data.bible;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.fa.baiboly.data.DatabaseHelper;
import com.fa.baiboly.models.Book;

import java.util.List;

public class BibleRepository {

    private final DatabaseHelper dbHelper;
    public BibleRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    // =========================
    // 📚 BOOKS (CORRIGÉ)
    // =========================
    public Cursor getBooks(String testament, String lang) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT " +
                        "b.numero AS id, " +
                        "b." + lang + " AS short_name, " +
                        "f." + lang + " AS long_name, " +
                        "CAST(s.color AS TEXT) AS color " +
                        "FROM bible_books b " +
                        "JOIN bible_sections s ON b.section_id = s.id " +
                        "LEFT JOIN bible_books_full f ON b.numero = f.numero " +
                        "WHERE s.testament=? " +
                        "ORDER BY b.numero",
                new String[]{testament}
        );
    }

    /**
     * Tous les chapitres de la Bible avec leur livre, dans l'ordre
     * canonique (Genèse 1 -> Apocalypse 22). Sert au swipe continu
     * entre tous les chapitres dans VersesActivity.
     */
    public Cursor getAllChaptersOrdered() {

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT b.numero AS book_id, " +
                        "b.mg AS short_name, " +
                        "v.chapter AS chapter " +
                        "FROM bible_verses v " +
                        "JOIN bible_books b ON v.book_number = b.numero " +
                        "GROUP BY b.numero, v.chapter " +
                        "ORDER BY b.numero, v.chapter",
                null
        );
    }

    // =========================
    // 📖 CHAPTERS
    // =========================
    public Cursor getChapters(int bookId) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT chapter, COUNT(verse) " +
                        "FROM bible_verses " +
                        "WHERE book_number=? " +
                        "GROUP BY chapter " +
                        "ORDER BY chapter",
                new String[]{String.valueOf(bookId)}
        );
    }


    // =========================
    // 📜 VERSES
    // =========================
    public Cursor getVerses(int bookId, int chapter) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT verse, text " +
                        "FROM bible_verses " +
                        "WHERE book_number=? AND chapter=? " +
                        "ORDER BY verse",
                new String[]{
                        String.valueOf(bookId),
                        String.valueOf(chapter)
                }
        );
    }


    public Cursor getBookById(int id) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT " +
                        "b.numero AS id, " +
                        "b.mg AS short_name, " +
                        "f.mg AS long_name, " +
                        "CAST(s.color AS TEXT) AS color " +
                        "FROM bible_books b " +
                        "JOIN bible_sections s ON b.section_id = s.id " +
                        "LEFT JOIN bible_books_full f ON b.numero = f.numero " +
                        "WHERE b.numero=?",
                new String[]{String.valueOf(id)}
        );
    }
    /**
     * Résout un nom/acronyme de livre vers son numéro canonique.
     *
     * Priorité au parser centralisé (BibleRefParser) : correspondance
     * EXACTE d'abord, donc "Jak" -> Jakoba (59) et JAMAIS "Mpanjaka"
     * comme le faisait l'ancien LIKE "%jak%". Le LIKE SQL n'est consulté
     * qu'en dernier recours pour un alias absent de la table statique.
     */
    public Integer resolveBookNumber(String input) {

        if (input == null) return null;

        Log.d("BOOK_RESOLVE", "input=" + input);

        // 1) Parser centralisé : alias exacts (acronymes) puis préfixe unique
        Integer parsed = com.fa.baiboly.data.parser.BibleRefParser.resolveBookNumber(input);
        if (parsed != null) {
            Log.d("BOOK_RESOLVE", "parser result=" + parsed);
            return parsed;
        }

        // 2) Dernier recours : LIKE sur la base (alias hors table statique)
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String clean = input
                .toLowerCase()
                .replaceAll("\\s+", "")
                .replace(",", "");

        if (clean.isEmpty()) return null;

        Cursor c = db.rawQuery(
                "SELECT numero FROM bible_books " +
                        "WHERE LOWER(REPLACE(mg,' ','')) LIKE ? " +

                        "UNION " +

                        "SELECT numero FROM bible_books_full " +
                        "WHERE LOWER(REPLACE(mg,' ','')) LIKE ? " +

                        "LIMIT 1",
                new String[]{
                        clean + "%",
                        clean + "%"
                }
        );

        Integer result = null;

        if (c != null && c.moveToFirst()) {
            result = c.getInt(0);
        }

        if (c != null) c.close();

        Log.d("BOOK_RESOLVE", "like result=" + result);

        return result;
    }
    // =========================
    // 📖 RANGE VERSES
    // =========================

    public Cursor getVersesFromReading(
            String bookShort,
            int startChapter,
            int startVerse,
            int endChapter,
            int endVerse
    ) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Integer bookNumber = resolveBookNumber(bookShort);

        Log.d("BIBLE_DEBUG",
                "book=" + bookShort +
                        " → bookNumber=" + bookNumber +
                        " start=" + startChapter + ":" + startVerse +
                        " end=" + endChapter + ":" + endVerse);

        if (bookNumber == null) {
            return null;
        }

        boolean isChapterOnly = (startVerse <= 0 && endVerse <= 0);

        // =========================
        // SAME CHAPTER
        // =========================
        if (startChapter == endChapter) {

            // Chapitre entier
            if (isChapterOnly) {

                return db.rawQuery(
                        "SELECT id, chapter, verse, text, title " +
                                "FROM bible_verses " +
                                "WHERE book_number=? " +
                                "AND chapter=? " +
                                "ORDER BY verse",
                        new String[]{
                                String.valueOf(bookNumber),
                                String.valueOf(startChapter)
                        }
                );
            }

            return db.rawQuery(
                    "SELECT id, chapter, verse, text, title " +
                            "FROM bible_verses " +
                            "WHERE book_number=? " +
                            "AND chapter=? " +
                            "AND verse BETWEEN ? AND ? " +
                            "ORDER BY verse",
                    new String[]{
                            String.valueOf(bookNumber),
                            String.valueOf(startChapter),
                            String.valueOf(startVerse),
                            String.valueOf(endVerse)
                    }
            );
        }

        // =========================
        // MULTI CHAPTER
        // =========================

        if (startVerse <= 0) startVerse = 1;
        if (endVerse <= 0) endVerse = 999;

        return db.rawQuery(
                "SELECT id, chapter, verse, text, title " +
                        "FROM bible_verses " +
                        "WHERE book_number=? " +
                        "AND ( " +

                        "(chapter = ? AND verse >= ?) " +

                        "OR (chapter > ? AND chapter < ?) " +

                        "OR (chapter = ? AND verse <= ?) " +

                        ") " +
                        "ORDER BY chapter ASC, verse ASC",
                new String[]{

                        String.valueOf(bookNumber),

                        // start
                        String.valueOf(startChapter),
                        String.valueOf(startVerse),

                        // middle
                        String.valueOf(startChapter),
                        String.valueOf(endChapter),

                        // end
                        String.valueOf(endChapter),
                        String.valueOf(endVerse)
                }
        );
    }
    // =========================
    // 🔍 SEARCH
    // =========================
    public Cursor search(String query) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT id, text FROM bible_fts WHERE text MATCH ?",
                new String[]{query}
        );
    }


    // =====================================================
    // 🔍 SEARCH (FULL-TEXT SEARCH FTS5)
    // =====================================================

    /**
     * Recherche ultra-rapide utilisant la table virtuelle FTS5.
     * @param query Le texte saisi par l'utilisateur
     * @param lang La langue pour le nom du livre (mg, fr, en)
     */
// =====================================================
// 🔍 SEARCH (FTS5 + fallback LIKE si module indisponible)
// =====================================================

    /**
     * Recherche dans les versets. Utilise FTS5 si dispo sur le device,
     * sinon retombe automatiquement sur LIKE (compatible tous devices).
     */
    public Cursor searchVerses(String query, String lang) {

        String likeSafe = query.replace("%", "").replace("_", "").trim();

        if (likeSafe.isEmpty()) return null;

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // 1) Tentative FTS5
        if (isFts5Available()) {
            try {
                String ftsQuery = buildFtsQuery(likeSafe);

                String sql = "SELECT v.id AS _id, v.book_number, v.chapter, v.verse, v.text, " +
                        "b." + lang + " AS book_name " +
                        "FROM bible_verses v " +
                        "JOIN bible_fts f ON v.id = f.rowid " +
                        "JOIN bible_books b ON v.book_number = b.numero " +
                        "WHERE f.text MATCH ? " +
                        "ORDER BY rank LIMIT 100";

                return db.rawQuery(sql, new String[]{ftsQuery});

            } catch (Exception e) {
                Log.w("SEARCH", "FTS5 indisponible sur ce device, fallback LIKE : " + e.getMessage());
            }
        }

        // 2) Fallback universel : LIKE
        String sql = "SELECT v.id AS _id, v.book_number, v.chapter, v.verse, v.text, " +
                "b." + lang + " AS book_name " +
                "FROM bible_verses v " +
                "JOIN bible_books b ON v.book_number = b.numero " +
                "WHERE v.text LIKE ? " +
                "ORDER BY v.book_number, v.chapter, v.verse LIMIT 100";

        return db.rawQuery(sql, new String[]{"%" + likeSafe + "%"});
    }

    /**
     * Vérifie une fois si FTS5 est compilé dans le SQLite du device.
     * Mis en cache pour ne pas refaire le test à chaque recherche.
     */
    private Boolean fts5AvailableCache = null;

    private boolean isFts5Available() {
        if (fts5AvailableCache != null) return fts5AvailableCache;

        try {
            dbHelper.getReadableDatabase().rawQuery(
                    "SELECT 1 FROM bible_fts WHERE bible_fts MATCH 'test' LIMIT 0", null
            ).close();
            fts5AvailableCache = true;
        } catch (Exception e) {
            fts5AvailableCache = false;
        }

        return fts5AvailableCache;
    }

    /**
     * Construit une requête FTS5 avec wildcard préfixe sur chaque mot
     * (ex: "Dieu amour" -> "Dieu* amour*").
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
    /**
     * Transforme une recherche simple en recherche par préfixe FTS5.
     * Permet de trouver "Fahafatesana" en tapant juste "Fahafa".
     */
    private String prepareFtsQuery(String input) {
        if (input == null || input.trim().isEmpty()) return "";

        // Supprimer les caractères spéciaux qui pourraient faire crash SQLite
        String cleanInput = input.replaceAll("[^a-zA-Z0-9\\s]", " ");

        String[] words = cleanInput.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                sb.append(word).append("* ");
            }
        }
        return sb.toString().trim();
    }

    // =========================
    // 📝 ANNOTATIONS
    // =========================
    public Cursor getAnnotationsForVerse(int verseId) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        return db.rawQuery(
                "SELECT " +
                        "id, " +
                        "word, " +
                        "annotation, " +
                        "start_position, " +
                        "end_position " +
                        "FROM bible_annotations " +
                        "WHERE verse_id=? " +
                        "ORDER BY start_position ASC",
                new String[]{
                        String.valueOf(verseId)
                }
        );
    }
    public Cursor getBooksByIds(List<Integer> ids) {

        if (ids == null || ids.isEmpty()) {
            return null;
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        StringBuilder placeholders = new StringBuilder();

        String[] args = new String[ids.size()];

        for (int i = 0; i < ids.size(); i++) {

            if (i > 0) placeholders.append(",");

            placeholders.append("?");

            args[i] = String.valueOf(ids.get(i));
        }

        String sql =
                "SELECT * FROM bible_books " +
                        "WHERE id IN (" + placeholders + ")";

        return db.rawQuery(sql, args);
    }
}