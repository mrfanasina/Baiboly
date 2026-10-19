package com.fa.baiboly.data.notes;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 🗄️ Base des notes de lecture (séparée, comme baiboly_marks.db).
 */
public class NoteDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "baiboly_notes.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE_NOTES = "notes";

    /** Sources prises en charge : 'free', 'verse', 'song', 'program'. */
    public static final String[] SUPPORTED_SOURCE_TYPES = {
            "free", "verse", "song", "program"
    };

    public NoteDbHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE_NOTES + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "source_type TEXT NOT NULL, " +          // free|verse|song|program
                        "ref_label TEXT NOT NULL DEFAULT '', " + // "MAT 3:16", "FFPM 49"…
                        "ref_key TEXT NOT NULL DEFAULT '', " +   // clé stable de la source
                        "body_json TEXT NOT NULL DEFAULT '', " + // corps enrichi (JSON)
                        "plain_text TEXT NOT NULL DEFAULT '', " +// recherche + aperçu
                        "color TEXT, " +                          // couleur de carte
                        "pinned INTEGER NOT NULL DEFAULT 0, " +
                        "created_at INTEGER NOT NULL, " +
                        "updated_at INTEGER NOT NULL" +
                        ")"
        );
        db.execSQL("CREATE INDEX idx_notes_ref ON " + TABLE_NOTES
                + " (source_type, ref_key)");
        db.execSQL("CREATE INDEX idx_notes_updated ON " + TABLE_NOTES
                + " (updated_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOTES);
        onCreate(db);
    }
}
