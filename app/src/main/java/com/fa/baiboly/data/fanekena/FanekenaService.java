package com.fa.baiboly.data.fanekena;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.fa.baiboly.models.Fanekena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FanekenaService {
    private final FanekenaRepository repository;

    public FanekenaService(Context context) {
        this.repository = new FanekenaRepository(context);
    }

    public List<String> getCodes() {

        List<String> list = new ArrayList<>();

        Cursor c = repository.getCode();

        if (c != null && c.moveToFirst()) {
            do {
                list.add(c.getString(0).toUpperCase());
            } while (c.moveToNext());

            c.close();
        }

        return list;
    }

    /**
     * Map code -> texte complet, pour construire des extraits dans la liste.
     */
    public Map<String, String> getTexts() {
        Map<String, String> map = new LinkedHashMap<>();

        Cursor c = repository.getAllWithText();

        if (c != null) {
            try {
                while (c.moveToNext()) {
                    String code = c.getString(0).toUpperCase();
                    if (!map.containsKey(code)) {
                        map.put(code, c.getString(1));
                    }
                }
            } finally {
                c.close();
            }
        }

        return map;
    }

    public Fanekena getByCode(String code) {
        code = code.toLowerCase();
        Cursor c = repository.getByCode(code);

        Log.d("DEBUG", "Cursor count = " + (c != null ? c.getCount() : -1));

        if (c != null && c.moveToFirst()) {
            int id = c.getInt(0);
            String text = c.getString(1);

            Log.d("DEBUG", "FOUND: " + text);

            return new Fanekena(id, code, text);
        }

        Log.d("DEBUG", "NOT FOUND for code = " + code);

        return null;
    }

    // =========================
    // 🔍 SEARCH
    // =========================
    public List<Fanekena> search(String query) {

        List<Fanekena> list = new ArrayList<>();

        Cursor c = repository.search(query);

        if (c != null) {
            try {
                while (c.moveToNext()) {
                    int id = c.getInt(0);
                    String code = c.getString(1);
                    String text = c.getString(2);
                    list.add(new Fanekena(id, code, text));
                }
            } finally {
                c.close();
            }
        }

        return list;
    }
}