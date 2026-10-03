package com.fa.baiboly.data.search;

import android.content.Context;

import com.fa.baiboly.data.bible.BibleService;
import com.fa.baiboly.data.fanekena.FanekenaService;
import com.fa.baiboly.data.fihirana.FihiranaService;
import com.fa.baiboly.models.Fanekena;
import com.fa.baiboly.models.GlobalSearchResult;
import com.fa.baiboly.models.SearchResult;
import com.fa.baiboly.models.Song;

import java.util.ArrayList;
import java.util.List;

public class GlobalSearchService {

    private final BibleService bibleService;
    private final FihiranaService fihiranaService;
    private final FanekenaService fanekenaService;

    public GlobalSearchService(Context context) {
        this.bibleService = new BibleService(context);
        this.fihiranaService = new FihiranaService(context);
        this.fanekenaService = new FanekenaService(context);
    }

    public List<GlobalSearchResult> searchAll(String query, String lang) {
        List<GlobalSearchResult> results = new ArrayList<>();

        if (query == null || query.trim().length() < 2) return results;

        // --- Versets ---
        List<SearchResult> verses = bibleService.searchVerses(query, lang);
        for (SearchResult v : verses) {
            String ref = v.getBookName() + " " + v.getChapter() + ":" + v.getVerse();
            results.add(new GlobalSearchResult(
                    GlobalSearchResult.Type.VERSET, ref, v.getText(), ref, null, v
            ));
        }

        // --- Chants ---
        List<Song> songs = fihiranaService.search(query);
        for (Song s : songs) {
            String ref = "HIRA #" + s.getNumber();
            // Affiche la catégorie + un extrait du premier couplet si disponible
            String categoryLabel = s.getCategory() != null ? s.getCategory().toUpperCase() : "";
            String verseSnippet = s.getFirstVerse();
            if (verseSnippet != null && verseSnippet.trim().length() > 0) {
                // Tronquer le couplet à 120 caractères
                verseSnippet = verseSnippet.trim();
                if (verseSnippet.length() > 120) {
                    verseSnippet = verseSnippet.substring(0, 120) + "\u2026";
                }
            }
            String snippet = verseSnippet != null && !verseSnippet.isEmpty()
                    ? verseSnippet
                    : categoryLabel;
            results.add(new GlobalSearchResult(
                    GlobalSearchResult.Type.CHANT,
                    s.getTitle(),
                    snippet,
                    ref,
                    s.getCategory(), // catégorie brute -> sert de clé de filtre
                    s
            ));
        }

        // --- Fanekena (credos) ---
        List<Fanekena> fanekenas = fanekenaService.search(query);
        for (Fanekena f : fanekenas) {
            String ref = "FANEKENA " + f.getCode().toUpperCase();
            String text = f.getText() != null ? f.getText() : "";
            String snippet = text.length() > 140 ? text.substring(0, 140) + "…" : text;

            results.add(new GlobalSearchResult(
                    GlobalSearchResult.Type.FANEKENA,
                    f.getCode().toUpperCase(),
                    snippet,
                    ref,
                    null,
                    f
            ));
        }

        return results;
    }
}