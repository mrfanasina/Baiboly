package com.fa.baiboly.data.fihirana;

import android.content.Context;
import android.database.Cursor;

import com.fa.baiboly.models.Song;
import com.fa.baiboly.models.SongVerse;

import java.util.ArrayList;
import java.util.List;

public class FihiranaService {

    private final FihiranaRepository repository;

    public FihiranaService(Context context) {
        this.repository = new FihiranaRepository(context);
    }

    // =========================
    // 🎵 ALL SONGS
    // =========================
    public List<Song> getAllSongs() {

        List<Song> list = new ArrayList<>();

        Cursor c = repository.getAllSongs();

        if (c != null && c.moveToFirst()) {
            do {
                list.add(new Song(
                        c.getString(0),
                        c.getInt(1),
                        c.getString(2),
                        c.getString(3)
                ));
            } while (c.moveToNext());

            c.close();
        }

        return list;
    }

    // =========================
    // 🎵 BY CATEGORY
    // =========================
    public List<Song> getSongsByCategory(String category) {

        List<Song> list = new ArrayList<>();

        Cursor c = repository.getSongsByCategory(category.toLowerCase());

        if (c != null && c.moveToFirst()) {
            do {
                list.add(new Song(
                        c.getString(0),
                        c.getInt(1),
                        c.getString(2),
                        c.getString(3)
                ));
            } while (c.moveToNext());

            c.close();
        }

        return list;
    }

    // =========================
    // 🎶 VERSES
    // =========================
    public Song getSongById(String id) {
        Cursor c = repository.getSongById();
        if (c != null && c.moveToFirst()) {
            int laharana =  c.getInt(1);
            String sokajy =  c.getString(2);
            String lohateny =  c.getString(3);

            Song song = new Song(id, laharana, sokajy, lohateny);
            return song;
        }
        return null;
    }
    public List<SongVerse> getSongVerses(String songId) {

        List<SongVerse> list = new ArrayList<>();

        Cursor c = repository.getSongVerses(songId);

        if (c != null && c.moveToFirst()) {
            do {
                int andininy = c.getInt(0);
                String tononkira = c.getString(1);
                Boolean fiverenany = c.getColumnIndexOrThrow("fiverenany") == 1;

                SongVerse songVerse = new SongVerse(songId, andininy, tononkira, fiverenany);
                list.add(songVerse);
            } while (c.moveToNext());

            c.close();
        }

        return list;
    }

    // =========================
    // 👤 AUTHORS
    // =========================
    public List<String> getAuthors(String songId) {

        List<String> list = new ArrayList<>();

        Cursor c = repository.getAuthors(songId);

        if (c != null && c.moveToFirst()) {
            do {
                list.add(c.getString(0));
            } while (c.moveToNext());

            c.close();
        }

        return list;
    }
    // =========================
// 📂 CATEGORIES
// =========================
    public List<String> getCategories() {

        List<String> list = new ArrayList<>();

        Cursor c = repository.getCategories();

        if (c != null && c.moveToFirst()) {
            do {
                list.add(c.getString(0).toUpperCase());
            } while (c.moveToNext());

            c.close();
        }

        return list;
    }

    public Song getSongByNumberAndCategory(int number, String category) {

        Cursor c = repository.getSongByNumberAndCategory(number, category.toLowerCase());

        if (c != null && c.moveToFirst()) {

            Song song = new Song(
                    c.getString(0),
                    c.getInt(1),
                    c.getString(2),
                    c.getString(3)
            );

            c.close();
            return song;
        }

        return null;
    }
    public int getSongCountByCategory(String category) {
        Cursor c = repository.getSongsByCategory(category.toLowerCase());
        int count = 0;
        if (c != null) {
            count = c.getCount();
            c.close();
        }
        return count;
    }


    // =========================
    // 🔍 SEARCH
    // =========================
    public List<Song> search(String query) {

        List<Song> list = new ArrayList<>();

        Cursor c = repository.search(query);

        if (c != null && c.moveToFirst()) {
            do {
                String firstVerse = c.getColumnCount() > 4 ? c.getString(4) : null;
                list.add(new Song(
                        c.getString(0),
                        c.getInt(1),
                        c.getString(2),
                        c.getString(3),
                        firstVerse
                ));
            } while (c.moveToNext());

            c.close();
        }

        return list;
    }
}