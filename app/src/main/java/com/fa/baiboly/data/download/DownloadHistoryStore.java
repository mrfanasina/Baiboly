package com.fa.baiboly.data.download;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.fa.baiboly.models.DownloadEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Journal (historique) des téléchargements effectués :
 *
 * - alimente la section "Voasintona farany" de l'écran Downloads
 * - évitera les re-téléchargements inutiles quand la future
 *   importation de données (perikopa/mofonaina) sera branchée.
 */
public class DownloadHistoryStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "download_center.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE = "downloads";

    public static final String COL_ID = "id";
    public static final String COL_TYPE = "type";
    public static final String COL_LABEL = "label";
    public static final String COL_URL = "url";
    public static final String COL_SIZE = "size_bytes";
    public static final String COL_TIMESTAMP = "timestamp";

    public DownloadHistoryStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TYPE + " TEXT, " +
                COL_LABEL + " TEXT, " +
                COL_URL + " TEXT, " +
                COL_SIZE + " INTEGER, " +
                COL_TIMESTAMP + " INTEGER);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public void add(DownloadEntry entry, long size, long timestamp) {
        ContentValues values = new ContentValues();
        values.put(COL_TYPE, entry.getType());
        values.put(COL_LABEL, entry.getTitle());
        values.put(COL_URL, entry.getUrl());
        values.put(COL_SIZE, size);
        values.put(COL_TIMESTAMP, timestamp);
        getWritableDatabase().insert(TABLE, null, values);
    }

    public boolean hasUrl(String url) {
        // Les entrées de secours ont une url null → un bind null sadd
        // "the bind value at index 1 is null" = CRASH dès l'ouverture
        // de l'écran. Entrée sans url = jamais téléchargée.
        if (url == null || url.isEmpty()) return false;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM " + TABLE + " WHERE " + COL_URL + " = ? LIMIT 1",
                new String[]{url});
        boolean found = c.moveToFirst();
        c.close();
        return found;
    }

    /** Les téléchargements les plus récents d'abord. */
    public List<DownloadEntry> getRecent(int limit) {
        List<DownloadEntry> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COL_TYPE + ", " + COL_LABEL + ", " + COL_URL + ", " +
                        COL_SIZE + ", " + COL_TIMESTAMP +
                        " FROM " + TABLE +
                        " ORDER BY " + COL_TIMESTAMP + " DESC LIMIT " + limit,
                null);

        if (c != null && c.moveToFirst()) {
            do {
                DownloadEntry e = new DownloadEntry();
                e.setType(c.getString(0));
                e.setTitle(c.getString(1));
                e.setUrl(c.getString(2));
                e.setSize(c.getLong(3));
                e.setSubtitle(formatDate(c.getLong(4)));
                list.add(e);
            } while (c.moveToNext());
            c.close();
        }
        return list;
    }

    public void clear() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    private static String formatDate(long timestamp) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "dd MMM yyyy · HH:mm", java.util.Locale.getDefault());
            return sdf.format(new java.util.Date(timestamp));
        } catch (Exception e) {
            return "";
        }
    }
}
