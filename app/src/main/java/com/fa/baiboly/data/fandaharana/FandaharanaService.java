package com.fa.baiboly.data.fandaharana;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.fa.baiboly.data.DatabaseHelper;
import com.fa.baiboly.models.Program;
import com.fa.baiboly.models.ProgramItem;

import java.util.ArrayList;
import java.util.List;

public class FandaharanaService {

    private final DatabaseHelper dbHelper;

    public FandaharanaService(Context context) {
        this.dbHelper = new DatabaseHelper(context);
        createTablesIfNeeded();
    }

    private void createTablesIfNeeded() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        db.execSQL("CREATE TABLE IF NOT EXISTS programs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS program_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "program_id INTEGER NOT NULL, " +
                "type TEXT NOT NULL, " +
                "reference TEXT NOT NULL, " +
                "title TEXT, " +
                "position INTEGER NOT NULL, " +
                "FOREIGN KEY(program_id) REFERENCES programs(id) ON DELETE CASCADE)");

        // Migration légère (colonnes ajoutées après coup) : l'ALTER échoue
        // silencieusement si la colonne existe déjà.
        addColumnIfMissing(db, "program_items", "description", "TEXT");
        addColumnIfMissing(db, "program_items", "favorite", "INTEGER NOT NULL DEFAULT 0");
    }

    /** Ajoute une colonne si absente (migration douce, sans crash). */
    private void addColumnIfMissing(SQLiteDatabase db, String table,
                                    String column, String definition) {
        try {
            Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
            boolean exists = false;
            if (c != null) {
                while (c.moveToNext()) {
                    if (column.equalsIgnoreCase(c.getString(c.getColumnIndexOrThrow("name")))) {
                        exists = true;
                        break;
                    }
                }
                c.close();
            }
            if (!exists) {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        } catch (Exception ignored) {
            // Table déjà à jour ou PRAGMA indisponible : on ne casse rien.
        }
    }

    // =========================
    // PROGRAMS CRUD
    // =========================

    public long createProgram(String name) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("created_at", System.currentTimeMillis());
        return db.insert("programs", null, cv);
    }

    public List<Program> getAllPrograms() {
        List<Program> programs = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM programs ORDER BY created_at DESC", null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                Program p = new Program();
                p.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                p.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
                p.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
                p.setItems(getItemsForProgram(p.getId()));
                programs.add(p);
            }
            cursor.close();
        }
        return programs;
    }

    public Program getProgram(long programId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM programs WHERE id = ?",
                new String[]{String.valueOf(programId)});

        Program p = null;
        if (cursor != null && cursor.moveToFirst()) {
            p = new Program();
            p.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
            p.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            p.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
            p.setItems(getItemsForProgram(p.getId()));
            cursor.close();
        }
        return p;
    }

    public void updateProgram(long programId, String newName) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", newName);
        db.update("programs", cv, "id = ?", new String[]{String.valueOf(programId)});
    }

    public void deleteProgram(long programId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("program_items", "program_id = ?", new String[]{String.valueOf(programId)});
        db.delete("programs", "id = ?", new String[]{String.valueOf(programId)});
    }

    // =========================
    // ITEMS CRUD
    // =========================

    public long addItem(long programId, String type, String reference, String title) {
        return addItem(programId, type, reference, title, null);
    }

    public long addItem(long programId, String type, String reference,
                        String title, String description) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Get next position
        int nextPos = 0;
        Cursor cursor = db.rawQuery(
                "SELECT MAX(position) FROM program_items WHERE program_id = ?",
                new String[]{String.valueOf(programId)});
        if (cursor != null && cursor.moveToFirst()) {
            nextPos = cursor.getInt(0) + 1;
            cursor.close();
        }

        ContentValues cv = new ContentValues();
        cv.put("program_id", programId);
        cv.put("type", type);
        cv.put("reference", reference);
        cv.put("title", title);
        cv.put("description", description);
        cv.put("position", nextPos);
        return db.insert("program_items", null, cv);
    }

    public void removeItem(long itemId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("program_items", "id = ?", new String[]{String.valueOf(itemId)});
    }

    public void updateItemPosition(long itemId, int newPosition) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("position", newPosition);
        db.update("program_items", cv, "id = ?", new String[]{String.valueOf(itemId)});
    }

    public void reorderItems(long programId, List<Long> orderedIds) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        for (int i = 0; i < orderedIds.size(); i++) {
            ContentValues cv = new ContentValues();
            cv.put("position", i);
            db.update("program_items", cv,
                    "id = ? AND program_id = ?",
                    new String[]{String.valueOf(orderedIds.get(i)), String.valueOf(programId)});
        }
    }

    public List<ProgramItem> getItemsForProgram(long programId) {
        List<ProgramItem> items = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM program_items WHERE program_id = ? " +
                        "ORDER BY favorite DESC, position ASC",
                new String[]{String.valueOf(programId)});

        if (cursor != null) {
            while (cursor.moveToNext()) {
                ProgramItem item = new ProgramItem();
                item.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                item.setProgramId(cursor.getLong(cursor.getColumnIndexOrThrow("program_id")));
                item.setType(cursor.getString(cursor.getColumnIndexOrThrow("type")));
                item.setReference(cursor.getString(cursor.getColumnIndexOrThrow("reference")));
                item.setTitle(cursor.getString(cursor.getColumnIndexOrThrow("title")));
                int descIdx = cursor.getColumnIndex("description");
                item.setDescription(descIdx >= 0 ? cursor.getString(descIdx) : null);
                int favIdx = cursor.getColumnIndex("favorite");
                item.setFavorite(favIdx >= 0 && cursor.getInt(favIdx) == 1);
                item.setPosition(cursor.getInt(cursor.getColumnIndexOrThrow("position")));
                items.add(item);
            }
            cursor.close();
        }
        return items;
    }

    /** Met à jour titre, description et favori d'un item. */
    public void updateItemDetails(long itemId, String title, String description, boolean favorite) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("description", description);
        cv.put("favorite", favorite ? 1 : 0);
        db.update("program_items", cv, "id = ?", new String[]{String.valueOf(itemId)});
    }

    /** Bascule le favori d'un item et renvoie le nouvel état. */
    public boolean toggleFavorite(long itemId, boolean newFavorite) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("favorite", newFavorite ? 1 : 0);
        db.update("program_items", cv, "id = ?", new String[]{String.valueOf(itemId)});
        return newFavorite;
    }

    /**
     * Duplique un item : il est inséré juste après l'original, la position
     * des suivants est décalée. Renvoie la nouvelle position d'insertion.
     */
    public int duplicateItem(ProgramItem source) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        int newPos = source.getPosition() + 1;

        // Décale les positions >= newPos pour faire de la place
        db.execSQL("UPDATE program_items SET position = position + 1 " +
                        "WHERE program_id = ? AND position >= ?",
                new Object[]{source.getProgramId(), newPos});

        ContentValues cv = new ContentValues();
        cv.put("program_id", source.getProgramId());
        cv.put("type", source.getType());
        cv.put("reference", source.getReference());
        cv.put("title", source.getTitle());
        cv.put("description", source.getDescription());
        cv.put("favorite", source.isFavorite() ? 1 : 0);
        cv.put("position", newPos);
        db.insert("program_items", null, cv);

        return newPos;
    }

    /**
     * Returns all verse references for a program as a list of reading strings.
     * Only includes items of type "verse".
     */
    public List<String> getVerseReferences(long programId) {
        List<String> refs = new ArrayList<>();
        List<ProgramItem> items = getItemsForProgram(programId);
        for (ProgramItem item : items) {
            if ("verse".equals(item.getType())) {
                refs.add(item.getReference());
            }
        }
        return refs;
    }
}
