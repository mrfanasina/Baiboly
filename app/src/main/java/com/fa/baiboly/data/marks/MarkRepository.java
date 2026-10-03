package com.fa.baiboly.data.marks;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class MarkRepository {

    private final MarkDbHelper dbHelper;

    public MarkRepository(Context context) {
        dbHelper = new MarkDbHelper(context);
    }

    public void addOrUpdateMark(String bookShort, int chapter, int verse, String type, String color) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("book_short", bookShort);
        cv.put("chapter", chapter);
        cv.put("verse", verse);
        cv.put("type", type);
        cv.put("color", color);
        cv.put("created_at", System.currentTimeMillis());
        db.insertWithOnConflict(MarkDbHelper.TABLE_MARKS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void removeMark(String bookShort, int chapter, int verse, String type) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(
                MarkDbHelper.TABLE_MARKS,
                "book_short=? AND chapter=? AND verse=? AND type=?",
                new String[]{bookShort, String.valueOf(chapter), String.valueOf(verse), type}
        );
    }

    public boolean exists(String bookShort, int chapter, int verse, String type) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(
                MarkDbHelper.TABLE_MARKS, new String[]{"id"},
                "book_short=? AND chapter=? AND verse=? AND type=?",
                new String[]{bookShort, String.valueOf(chapter), String.valueOf(verse), type},
                null, null, null
        );
        boolean exists = c != null && c.moveToFirst();
        if (c != null) c.close();
        return exists;
    }

    public Cursor getMarksForChapter(String bookShort, int chapter) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.query(
                MarkDbHelper.TABLE_MARKS, null,
                "book_short=? AND chapter=?",
                new String[]{bookShort, String.valueOf(chapter)},
                null, null, null
        );
    }

    public Cursor getFavorites() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.query(
                MarkDbHelper.TABLE_MARKS, null,
                "type=?", new String[]{"favorite"},
                null, null, "created_at DESC"
        );
    }
}