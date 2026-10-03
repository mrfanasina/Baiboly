package com.fa.baiboly.data.marks;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class MarkDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "baiboly_marks.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE_MARKS = "verse_marks";

    public MarkDbHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE_MARKS + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "book_short TEXT NOT NULL, " +
                        "chapter INTEGER NOT NULL, " +
                        "verse INTEGER NOT NULL, " +
                        "type TEXT NOT NULL, " +          // 'highlight' | 'favorite'
                        "color TEXT, " +
                        "created_at INTEGER NOT NULL, " +
                        "UNIQUE(book_short, chapter, verse, type) ON CONFLICT REPLACE" +
                        ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MARKS);
        onCreate(db);
    }
}