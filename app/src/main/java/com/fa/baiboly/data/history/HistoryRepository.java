package com.fa.baiboly.data.history;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.fa.baiboly.data.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

public class    HistoryRepository {

    private final DatabaseHelper dbHelper;

    public HistoryRepository(Context context) {
        dbHelper = new DatabaseHelper(context);
    }

    public void insert(String reading) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("reading", reading);
        values.put("timestamp", System.currentTimeMillis());

        db.insertWithOnConflict(
                "history",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    public List<String> getAll() {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT reading FROM history ORDER BY timestamp DESC",
                null
        );

        if (cursor.moveToFirst()) {
            do {
                list.add(cursor.getString(0));
            } while (cursor.moveToNext());
        }

        cursor.close();
        return list;
    }
}