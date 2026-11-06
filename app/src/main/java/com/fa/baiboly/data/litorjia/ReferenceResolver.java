package com.fa.baiboly.data.litorjia;

import android.content.Context;
import android.database.Cursor;

import com.fa.baiboly.data.bible.BibleRepository;
import com.fa.baiboly.data.fihirana.FihiranaRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Référence → contenu et validation (§6.1) : un noeud ne stocke que des
 * références, résolues ici contre la base principale de l'application
 * (chants : songs/verses ; Bible : bible_verses/bible_books).
 */
public class ReferenceResolver {

    private final BibleRepository bible;
    private final FihiranaRepository fihirana;

    public ReferenceResolver(Context context) {
        bible = new BibleRepository(context);
        fihirana = new FihiranaRepository(context);
    }

    // =========================================================
    // Bible
    // =========================================================

    /** Livre reconnu ? Renvoie le numéro canonique 1..66 sinon null. */
    public Integer bookNumber(String rawBook) {
        return bible.resolveBookNumber(rawBook);
    }

    /** Un verset existe-t-il ? "13b" est traité comme 13. */
    public boolean verseExists(int bookNumber, int chapter, String verseStr) {
        if (bookNumber <= 0 || chapter <= 0) return false;
        String v = verseStr == null ? "" : verseStr.replaceAll("[^0-9]", "");
        if (v.isEmpty()) return true; // plage ouverte : on valide la forme
        int n;
        try { n = Integer.parseInt(v); } catch (NumberFormatException e) { return false; }
        if (n <= 0) return false;
        Cursor c = bible.getVerses(bookNumber, chapter);
        boolean ok = false;
        if (c != null) {
            while (c.moveToNext()) {
                if (c.getInt(0) == n) { ok = true; break; }
            }
            c.close();
        }
        return ok;
    }

    /** Validation d'une référence biblique : livre + chapitre (+ bornes). */
    public boolean refValid(ProgramNode.BibleRef r) {
        if (r == null || r.book == null || r.book.isEmpty()) return false;
        Integer bn = r.bookNumber != null ? r.bookNumber : bookNumber(r.book);
        if (bn == null) return false;
        if (r.chapter == null || r.chapter.isEmpty()) return false;
        int chap;
        try { chap = Integer.parseInt(r.chapter.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return false; }
        int maxChap = chapterCount(bn);
        if (chap <= 0 || chap > maxChap) return false;
        if (r.from != null && !r.from.isEmpty()) {
            if (!verseExists(bn, chap, r.from)) return false;
            if (r.to != null && !r.to.isEmpty() && !verseExists(bn, chap, r.to)) return false;
        }
        return true;
    }

    private int chapterCount(int bookNumber) {
        Cursor c = bible.getChapters(bookNumber);
        int last = 0;
        if (c != null) {
            while (c.moveToNext()) {
                int chap = c.getInt(0);
                if (chap > last) last = chap;
            }
            c.close();
        }
        return last;
    }

    // =========================================================
    // Hymnes
    // =========================================================

    public static class SongInfo {
        public String id;
        public String book;      // sokajy (FFPM, FIF, FF…)
        public int number;
        public String title;
        public int stanzaCount;  // nombre de couplets
    }

    /** Chant existant dans la base ? (recueil + numéro) */
    public SongInfo findSong(String book, Integer number) {
        if (book == null || number == null) return null;
        SongInfo info = null;
        Cursor c = fihirana.getSongByNumberAndCategory(number, book);
        if (c != null && c.moveToFirst()) {
            info = new SongInfo();
            info.id = c.getString(c.getColumnIndexOrThrow("id"));
            info.book = book;
            info.number = number;
            info.title = c.getString(c.getColumnIndexOrThrow("lohateny"));
            Cursor v = fihirana.getSongVerses(info.id);
            if (v != null) {
                int max = 0;
                while (v.moveToNext()) {
                    try {
                        int and = v.getInt(0); // andininy
                        if (and > max) max = and;
                    } catch (Exception ignored) {}
                }
                info.stanzaCount = max;
                v.close();
            }
        }
        if (c != null) c.close();
        return info;
    }

    /** Recueils disponibles (sokajy) pour les éditeurs de chants. */
    public List<String> songBooks() {
        List<String> out = new ArrayList<>();
        Cursor c = fihirana.getCategories();
        if (c != null) {
            while (c.moveToNext()) {
                String s = c.getString(0);
                if (s != null && !s.trim().isEmpty()) out.add(s.trim());
            }
            c.close();
        }
        return out;
    }

    // =========================================================
    // Chaine d'un chant / texte biblique pour l'affichage
    // =========================================================

    /** Couplets d'un chant dans l'ordre, pour l'écran de consultation. */
    public List<int[]> songVerseNumbers(String songId) {
        List<int[]> out = new ArrayList<>();
        Cursor c = fihirana.getSongVerses(songId);
        if (c != null) {
            while (c.moveToNext()) {
                try { out.add(new int[]{Integer.parseInt(c.getString(0))}); }
                catch (NumberFormatException ignored) {}
            }
            c.close();
        }
        return out;
    }

    /** Texte des versets d'un chapitre (consultation). */
    public List<int[]> chapterVerses(int bookNumber, int chapter) {
        List<int[]> out = new ArrayList<>();
        Cursor c = bible.getVerses(bookNumber, chapter);
        if (c != null) {
            while (c.moveToNext()) out.add(new int[]{c.getInt(0)});
            c.close();
        }
        return out;
    }
}
