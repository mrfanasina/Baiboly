package com.fa.baiboly.data.cache;

import android.content.Context;
import android.content.SharedPreferences;

public class MofonainaCache {

    private static final String PREF = "mofonaina_cache";
    private static final String KEY_HTML = "html";
    private static final String KEY_DATE = "date";

    private SharedPreferences prefs;

    public MofonainaCache(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public void save(String html, String date) {
        prefs.edit()
                .putString(KEY_HTML, html)
                .putString(KEY_DATE, date)
                .apply();
    }

    public String getHtml() {
        return prefs.getString(KEY_HTML, null);
    }

    public String getDate() {
        return prefs.getString(KEY_DATE, null);
    }
}