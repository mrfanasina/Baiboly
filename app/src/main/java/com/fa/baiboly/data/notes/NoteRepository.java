package com.fa.baiboly.data.notes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;

import com.fa.baiboly.models.Note;

import java.util.ArrayList;
import java.util.List;

/**
 * 💾 CRUD des notes de lecture (épinglées d'abord, puis antichronologiques).
 */
public class NoteRepository {

    private final NoteDbHelper dbHelper;

    public NoteRepository(Context context) {
        this.dbHelper = new NoteDbHelper(context);
    }

    // =========================================================
    // LIRE
    // =========================================================
    public List<Note> getAll(@Nullable String searchQuery,
                             @Nullable String sourceType) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        StringBuilder where = new StringBuilder();
        List<String> args = new ArrayList<>();

        if (searchQuery != null && !searchQuery.isEmpty()) {
            where.append("(plain_text LIKE ? OR ref_label LIKE ?)");
            String like = "%" + searchQuery + "%";
            args.add(like);
            args.add(like);
        }
        if (sourceType != null && !"all".equals(sourceType)) {
            if (where.length() > 0) where.append(" AND ");
            where.append("source_type = ?");
            args.add(sourceType);
        }

        // Épinglées d'abord, puis la plus récemment modifiée
        String sql = "SELECT * FROM " + NoteDbHelper.TABLE_NOTES
                + (where.length() > 0 ? " WHERE " + where : "")
                + " ORDER BY pinned DESC, updated_at DESC";

        Cursor c = db.rawQuery(sql, args.toArray(new String[0]));
        List<Note> out = new ArrayList<>();
        try {
            while (c.moveToNext()) out.add(fromCursor(c));
        } finally {
            c.close();
        }
        return out;
    }

    public int countForSource(String sourceType, String refKey) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM " + NoteDbHelper.TABLE_NOTES
                        + " WHERE source_type = ? AND ref_key = ?",
                new String[]{sourceType, refKey});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    @Nullable
    public Note getById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT * FROM " + NoteDbHelper.TABLE_NOTES + " WHERE id = ?",
                new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() ? fromCursor(c) : null;
        } finally {
            c.close();
        }
    }

    // =========================================================
    // ÉCRIRE
    // =========================================================
    public long insert(Note n) {
        long now = System.currentTimeMillis();
        n.createdAt = now;
        n.updatedAt = now;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.insert(NoteDbHelper.TABLE_NOTES, null, toValues(n, now));
    }

    public void update(Note n) {
        if (n.id < 0) {
            n.id = insert(n);
            return;
        }
        n.updatedAt = System.currentTimeMillis();
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.update(NoteDbHelper.TABLE_NOTES, toValues(n, n.updatedAt),
                "id = ?", new String[]{String.valueOf(n.id)});
    }

    public void setPinned(long id, boolean pinned) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("pinned", pinned ? 1 : 0);
        cv.put("updated_at", System.currentTimeMillis());
        db.update(NoteDbHelper.TABLE_NOTES, cv,
                "id = ?", new String[]{String.valueOf(id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(NoteDbHelper.TABLE_NOTES, "id = ?",
                new String[]{String.valueOf(id)});
    }

    // =========================================================
    // MAPPAGE
    // =========================================================
    private ContentValues toValues(Note n, long updatedAt) {
        ContentValues cv = new ContentValues();
        cv.put("source_type", n.sourceType == null ? Note.TYPE_FREE : n.sourceType);
        cv.put("ref_label", n.refLabel == null ? "" : n.refLabel);
        cv.put("ref_key", n.refKey == null ? "" : n.refKey);
        cv.put("body_json", n.bodyJson == null ? "" : n.bodyJson);
        cv.put("plain_text", n.plainText == null ? "" : n.plainText);
        cv.put("color", n.color);
        cv.put("pinned", n.pinned ? 1 : 0);
        cv.put("created_at", n.createdAt <= 0 ? System.currentTimeMillis() : n.createdAt);
        cv.put("updated_at", updatedAt);
        return cv;
    }

    private Note fromCursor(Cursor c) {
        Note n = new Note();
        n.id = c.getLong(c.getColumnIndexOrThrow("id"));
        n.sourceType = c.getString(c.getColumnIndexOrThrow("source_type"));
        n.refLabel = c.getString(c.getColumnIndexOrThrow("ref_label"));
        n.refKey = c.getString(c.getColumnIndexOrThrow("ref_key"));
        n.bodyJson = c.getString(c.getColumnIndexOrThrow("body_json"));
        n.plainText = c.getString(c.getColumnIndexOrThrow("plain_text"));
        n.color = c.isNull(c.getColumnIndexOrThrow("color"))
                ? null : c.getString(c.getColumnIndexOrThrow("color"));
        n.pinned = c.getInt(c.getColumnIndexOrThrow("pinned")) != 0;
        n.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        n.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        return n;
    }
}
