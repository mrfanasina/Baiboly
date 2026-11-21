package com.fa.baiboly.data.dictionary;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.fa.baiboly.data.DatabaseHelper;
import com.fa.baiboly.models.DictionaryEntry;
import com.fa.baiboly.models.DictionaryLink;
import com.fa.baiboly.models.DictionaryOccurrence;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Accès au dictionnaire biblique stocké dans baiboly_mg.db
 * (tables bible_dictionary, dictionary_links, dictionary_aliases,
 * annotation_dictionary).
 *
 * Une seule instance par processus : le cache des liens par verset évite de
 * relire la base à chaque re-rendu d'une page du lecteur.
 */
public final class DictionaryService {

    private static final String SELECT_ENTRY =
            "SELECT id, headword, headword_norm, qualifier, definition " +
                    "FROM bible_dictionary ";

    private static volatile DictionaryService instance;

    private final DatabaseHelper dbHelper;

    private Integer entryCount;

    /**
     * Cache borné des liens d'un verset (accès le plus récent en dernier).
     * 4096 versets couvrent largement plusieurs livres sans gonfler la mémoire.
     */
    private final LinkedHashMap<Integer, List<DictionaryLink>> verseCache =
            new LinkedHashMap<Integer, List<DictionaryLink>>(512, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<Integer, List<DictionaryLink>> eldest) {
                    return size() > 4096;
                }
            };

    private DictionaryService(Context context) {
        this.dbHelper = new DatabaseHelper(context.getApplicationContext());
    }

    public static DictionaryService get(Context context) {
        if (instance == null) {
            synchronized (DictionaryService.class) {
                if (instance == null) {
                    instance = new DictionaryService(context);
                }
            }
        }
        return instance;
    }

    // =====================================================
    // RECHERCHE / LECTURE DES ENTRÉES
    // =====================================================

    /** Toutes les entrées, triées par ordre alphabétique. */
    public List<DictionaryEntry> getAll() {
        List<DictionaryEntry> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(SELECT_ENTRY + "ORDER BY headword_norm COLLATE NOCASE", null);
        readEntries(c, list);
        return list;
    }

    /**
     * Recherche par mot (forme normalisée ou affichée), les correspondances
     * exactes puis les préfixes d'abord.
     */
    public List<DictionaryEntry> search(String query, int limit) {
        List<DictionaryEntry> list = new ArrayList<>();
        String q = normalize(query);
        if (q.isEmpty()) {
            return list;
        }

        String escaped = escapeLike(q);
        String contains = "%" + escaped + "%";
        String prefix = escaped + "%";

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                SELECT_ENTRY +
                        "WHERE headword_norm LIKE ? ESCAPE '\\' " +
                        "OR LOWER(headword) LIKE ? ESCAPE '\\' " +
                        "ORDER BY CASE " +
                        "WHEN headword_norm = ? THEN 0 " +
                        "WHEN headword_norm LIKE ? ESCAPE '\\' THEN 1 " +
                        "ELSE 2 END, headword_norm COLLATE NOCASE " +
                        "LIMIT ?",
                new String[]{contains, contains, q, prefix, String.valueOf(limit)});
        readEntries(c, list);
        return list;
    }

    public DictionaryEntry getById(int id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(SELECT_ENTRY + "WHERE id = ?",
                new String[]{String.valueOf(id)});
        try {
            if (c.moveToFirst()) {
                return entryFrom(c);
            }
            return null;
        } finally {
            c.close();
        }
    }

    /**
     * Identifiant de la fiche correspondant exactement à un mot (en-tête ou
     * alias), ou {@code null}. Sert aux renvois « Jereo Andriamanitra » des
     * définitions.
     */
    public Integer findIdByHeadword(String raw) {
        String norm = normalize(raw);
        if (norm.isEmpty()) {
            return null;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id FROM bible_dictionary WHERE headword_norm = ? LIMIT 1",
                new String[]{norm});
        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
        } finally {
            c.close();
        }

        c = db.rawQuery(
                "SELECT dict_id FROM dictionary_aliases WHERE alias_norm = ? LIMIT 1",
                new String[]{norm});
        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
        } finally {
            c.close();
        }

        // Repli pour les en-têtes qualifiés : « Eloy » doit retrouver
        // « ELOY (Andriamanitra). ». On ne lie que si une seule fiche
        // correspond, sinon « Heroda » (plusieurs fiches) resterait ambigu.
        String escaped = escapeLike(norm);
        c = db.rawQuery(
                "SELECT id FROM bible_dictionary " +
                        "WHERE headword_norm LIKE ? ESCAPE '\\' " +
                        "OR headword_norm LIKE ? ESCAPE '\\' " +
                        "GROUP BY id LIMIT 2",
                new String[]{escaped + " %", escaped + " (%"});
        try {
            if (c.moveToFirst()) {
                int id = c.getInt(0);
                if (!c.moveToNext()) {
                    return id;
                }
            }
        } finally {
            c.close();
        }
        return null;
    }

    /**
     * Mot du jour : un tirage stable pour la date courante, décalé par
     * {@code offset} quand l'utilisateur demande un autre mot.
     */
    public DictionaryEntry getDailyWord(int offset) {
        int total = count();
        if (total <= 0) {
            return null;
        }

        Calendar calendar = Calendar.getInstance();
        long day = calendar.get(Calendar.YEAR) * 1000L
                + calendar.get(Calendar.DAY_OF_YEAR);
        long seed = day * 31L + offset * 7919L;
        int index = Math.floorMod(new Random(seed).nextInt(), total);

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                SELECT_ENTRY + "ORDER BY id LIMIT 1 OFFSET ?",
                new String[]{String.valueOf(index)});
        try {
            if (c.moveToFirst()) {
                return entryFrom(c);
            }
            return null;
        } finally {
            c.close();
        }
    }

    public synchronized int count() {
        if (entryCount != null) {
            return entryCount;
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM bible_dictionary", null);
        try {
            entryCount = c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
        return entryCount;
    }

    // =====================================================
    // LIENS MOT <-> VERSETS
    // =====================================================

    /** Occurrences du mot dans un verset (positions prêtes pour le rendu). */
    public synchronized List<DictionaryLink> getLinksForVerse(int verseId) {
        List<DictionaryLink> cached = verseCache.get(verseId);
        if (cached != null) {
            return cached;
        }

        List<DictionaryLink> links = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT dict_id, verse_id, book_number, chapter, verse, " +
                        "start_position, end_position, word " +
                        "FROM dictionary_links WHERE verse_id = ? " +
                        "ORDER BY start_position",
                new String[]{String.valueOf(verseId)});
        try {
            while (c.moveToNext()) {
                links.add(new DictionaryLink(
                        c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3),
                        c.getInt(4), c.getInt(5), c.getInt(6), c.getString(7)));
            }
        } finally {
            c.close();
        }

        verseCache.put(verseId, links);
        return links;
    }

    /** Versets où l'entrée apparaît (dédoublonnés), dans l'ordre canonique. */
    public List<DictionaryOccurrence> getOccurrences(int dictId, int limit) {
        List<DictionaryOccurrence> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT l.book_number, b.mg, l.chapter, l.verse " +
                        "FROM dictionary_links l " +
                        "JOIN bible_books b ON b.numero = l.book_number " +
                        "WHERE l.dict_id = ? " +
                        "GROUP BY l.book_number, l.chapter, l.verse " +
                        "ORDER BY l.book_number, l.chapter, l.verse " +
                        "LIMIT ?",
                new String[]{String.valueOf(dictId), String.valueOf(limit)});
        try {
            while (c.moveToNext()) {
                list.add(new DictionaryOccurrence(
                        c.getInt(0), c.getString(1), c.getInt(2), c.getInt(3)));
            }
        } finally {
            c.close();
        }
        return list;
    }

    /** Nombre total de versets où l'entrée apparaît. */
    public int countOccurrences(int dictId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM (" +
                        "SELECT 1 FROM dictionary_links " +
                        "WHERE dict_id = ? " +
                        "GROUP BY book_number, chapter, verse)",
                new String[]{String.valueOf(dictId)});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    // =====================================================
    // OUTILS
    // =====================================================

    private static void readEntries(Cursor c, List<DictionaryEntry> out) {
        if (c == null) return;
        try {
            while (c.moveToNext()) {
                out.add(entryFrom(c));
            }
        } finally {
            c.close();
        }
    }

    private static DictionaryEntry entryFrom(Cursor c) {
        return new DictionaryEntry(
                c.getInt(0),
                c.getString(1),
                c.getString(2),
                c.isNull(3) ? null : c.getString(3),
                c.getString(4));
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase()
                .replace('\u2019', '\'')
                .replaceAll("\\s+", " ");
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
