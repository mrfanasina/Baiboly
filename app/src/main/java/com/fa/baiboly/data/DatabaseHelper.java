package com.fa.baiboly.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
public class        DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "baiboly_mg.db";
    private static final int DB_VERSION = 1;

    private final Context context;
    private final String dbPath;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context;
        this.dbPath = context.getDatabasePath(DB_NAME).getPath();

        copyDatabaseIfNeeded();
        createHistoryTableIfNeeded();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // ❌ Rien ici → DB déjà pré-remplie
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 👉 Si tu changes la DB plus tard
        // tu peux gérer migration ici
    }
    private boolean isDatabaseValid() {
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                    dbPath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
            );

            Cursor c = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='reading_days'",
                    null
            );

            boolean ok = c.moveToFirst();

            c.close();
            db.close();

            return ok;

        } catch (Exception e) {
            return false;
        }
    }

    // =========================
    // 📦 Copier DB depuis assets
    // =========================

    private void copyDatabaseIfNeeded() {
        File dbFile = new File(dbPath);

        if (dbFile.exists() && isDatabaseValid()) {
            return; // ✅ on ne touche à rien
        }

        try {
            dbFile.getParentFile().mkdirs();

            InputStream input = context.getAssets().open(DB_NAME);
            OutputStream output = new FileOutputStream(dbPath);

            byte[] buffer = new byte[4096];
            int length;

            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }

            output.flush();
            output.close();
            input.close();

            Log.d("DB", "Database copied from assets");

        } catch (Exception e) {
            Log.e("DB", "Copy failed", e);
        }
    }
    private void createHistoryTableIfNeeded() {
        SQLiteDatabase db = this.getWritableDatabase();

        String createTable = "CREATE TABLE IF NOT EXISTS history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "reading TEXT UNIQUE, " +
                "timestamp INTEGER," +
                "type TEXT" +
                ");";

        db.execSQL(createTable);
    }

    // =========================
    // 🔓 Accès DB sécurisé
    // =========================
    @Override
    public synchronized SQLiteDatabase getReadableDatabase() {
        return super.getReadableDatabase();
    }

    @Override
    public synchronized SQLiteDatabase getWritableDatabase() {
        return super.getWritableDatabase();
    }
}