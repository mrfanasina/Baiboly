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

    private static final String DB_NAME = "Baiboly_mg.db";
    private static final int DB_VERSION = 2;

    private final Context context;
    private final String dbPath;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context;
        this.dbPath = context.getDatabasePath(DB_NAME).getPath();

        copyDatabaseIfNeeded();
        createHistoryTableIfNeeded();
        ensureDictionaryTables();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // ❌ Rien ici → DB déjà pré-remplie
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Migration v1 -> v2 : dictionnaire biblique.
        //
        // On crée seulement les tables ici : SQLiteOpenHelper exécute
        // onUpgrade dans une transaction, or ATTACH DATABASE (utilisé pour
        // importer les données depuis l'asset) est interdit dans une
        // transaction. L'import lui-même se fait dans ensureDictionaryTables(),
        // appelé juste après l'ouverture de la base.
        createDictionaryTables(db);
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
    // 📖 DICTIONNAIRE BIBLIQUE
    // =========================

    /**
     * Crée les tables du dictionnaire si besoin, puis importe les données
     * depuis la base livrée dans assets (une seule fois). Les utilisateurs
     * qui avaient déjà l'ancienne base gardent leur historique : on ajoute
     * les tables au lieu de remplacer le fichier.
     */
    private void ensureDictionaryTables() {
        SQLiteDatabase db = getWritableDatabase();

        createDictionaryTables(db);

        if (tableHasRows(db, "bible_dictionary")) {
            return;
        }

        importDictionaryFromAsset(db);
    }

    private void createDictionaryTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS bible_dictionary (" +
                "id INTEGER PRIMARY KEY, " +
                "headword TEXT NOT NULL, " +
                "headword_norm TEXT NOT NULL, " +
                "qualifier TEXT, " +
                "definition TEXT NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS dictionary_aliases (" +
                "dict_id INTEGER NOT NULL, " +
                "alias TEXT NOT NULL, " +
                "alias_norm TEXT NOT NULL, " +
                "PRIMARY KEY(dict_id, alias_norm), " +
                "FOREIGN KEY(dict_id) REFERENCES bible_dictionary(id) ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE IF NOT EXISTS dictionary_links (" +
                "dict_id INTEGER NOT NULL, " +
                "verse_id INTEGER NOT NULL, " +
                "book_number INTEGER NOT NULL, " +
                "chapter INTEGER NOT NULL, " +
                "verse INTEGER NOT NULL, " +
                "start_position INTEGER NOT NULL, " +
                "end_position INTEGER NOT NULL, " +
                "word TEXT NOT NULL, " +
                "PRIMARY KEY(dict_id, verse_id, start_position), " +
                "FOREIGN KEY(dict_id) REFERENCES bible_dictionary(id) ON DELETE CASCADE, " +
                "FOREIGN KEY(verse_id) REFERENCES bible_verses(id) ON DELETE CASCADE) WITHOUT ROWID");

        db.execSQL("CREATE TABLE IF NOT EXISTS annotation_dictionary (" +
                "annotation_id INTEGER NOT NULL, " +
                "dict_id INTEGER NOT NULL, " +
                "PRIMARY KEY(annotation_id, dict_id))");

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dict_headword_norm " +
                "ON bible_dictionary(headword_norm)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dict_links_verse " +
                "ON dictionary_links(verse_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dict_links_dict " +
                "ON dictionary_links(dict_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dict_aliases_norm " +
                "ON dictionary_aliases(alias_norm)");
    }

    private boolean tableHasRows(SQLiteDatabase db, String table) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
            return c.moveToFirst() && c.getInt(0) > 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.close();
        }
    }

    /**
     * Copie la base livrée dans assets vers un fichier temporaire, l'attache
     * puis recopie uniquement les tables du dictionnaire. Le fichier de
     * données de l'utilisateur (historique, notes…) n'est jamais remplacé.
     */
    private void importDictionaryFromAsset(SQLiteDatabase db) {
        File temp = new File(context.getCacheDir(), "baiboly_dict_source.db");

        try {
            temp.getParentFile().mkdirs();

            InputStream input = context.getAssets().open(DB_NAME);
            OutputStream output = new FileOutputStream(temp);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
            output.flush();
            output.close();
            input.close();

            // Chemin échappé explicitement : ATTACH accepte une chaîne SQL,
            // et on évite toute ambiguïté de liaison de paramètre.
            String escapedPath = temp.getAbsolutePath().replace("'", "''");
            db.execSQL("ATTACH DATABASE '" + escapedPath + "' AS dictsrc");

            boolean inTransaction = false;
            try {
                // Import atomique : une interruption laisse les tables vides
                // au lieu d'un dictionnaire à moitié rempli.
                db.beginTransaction();
                inTransaction = true;

                if (attachedHasTable(db, "bible_dictionary")) {
                    db.execSQL("INSERT OR IGNORE INTO bible_dictionary " +
                            "SELECT * FROM dictsrc.bible_dictionary");
                }
                if (attachedHasTable(db, "dictionary_aliases")) {
                    db.execSQL("INSERT OR IGNORE INTO dictionary_aliases " +
                            "SELECT * FROM dictsrc.dictionary_aliases");
                }
                if (attachedHasTable(db, "dictionary_links")) {
                    db.execSQL("INSERT OR IGNORE INTO dictionary_links " +
                            "SELECT * FROM dictsrc.dictionary_links");
                }
                if (attachedHasTable(db, "annotation_dictionary")) {
                    db.execSQL("INSERT OR IGNORE INTO annotation_dictionary " +
                            "SELECT * FROM dictsrc.annotation_dictionary");
                }

                db.setTransactionSuccessful();
                Log.d("DB", "Dictionnaire importé depuis assets");
            } finally {
                if (inTransaction) {
                    db.endTransaction();
                }
                try {
                    db.execSQL("DETACH DATABASE dictsrc");
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            Log.e("DB", "Import du dictionnaire impossible", e);
        } finally {
            if (temp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        }
    }

    private boolean attachedHasTable(SQLiteDatabase db, String table) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT 1 FROM dictsrc.sqlite_master " +
                    "WHERE type='table' AND name=?", new String[]{table});
            return c.moveToFirst();
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.close();
        }
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