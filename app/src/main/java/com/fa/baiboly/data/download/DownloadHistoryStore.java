package com.fa.baiboly.data.download;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.fa.baiboly.models.DownloadEntry;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Journal (historique) des téléchargements effectués + état de la file.
 *
 * Règles de conservation :
 * - un téléchargement terminé reste enregistré, même sans réseau ;
 * - un contenu IMPORTÉ dans la base (perikopa, aina…) n'est JAMAIS supprimé,
 *   même s'il disparaît du serveur ;
 * - un téléchargement simple dont l'URL n'existe plus côté serveur est
 *   supprimé quand le réseau revient (voir {@link #reconcile}).
 */
public class DownloadHistoryStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "download_center.db";
    private static final int DB_VERSION = 2;

    public static final String TABLE = "downloads";
    public static final String TABLE_QUEUE = "download_queue";
    public static final String TABLE_DATASETS = "data_sets";

    public static final String COL_ID = "id";
    public static final String COL_TYPE = "type";
    public static final String COL_LABEL = "label";
    public static final String COL_URL = "url";
    public static final String COL_SIZE = "size_bytes";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_PATH = "path";
    public static final String COL_STATUS = "status";
    public static final String COL_IMPORTED = "imported";
    public static final String COL_DATASET = "dataset";
    public static final String COL_VERSION = "version";
    public static final String COL_SUBTITLE = "subtitle";

    public DownloadHistoryStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createDownloadsTable(db);
        createAuxTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Migration douce : on garde l'historique existant.
        createDownloadsTable(db);
        addColumnIfMissing(db, TABLE, COL_PATH, "TEXT");
        addColumnIfMissing(db, TABLE, COL_STATUS, "INTEGER DEFAULT 0");
        addColumnIfMissing(db, TABLE, COL_IMPORTED, "INTEGER DEFAULT 0");
        addColumnIfMissing(db, TABLE, COL_DATASET, "TEXT");
        addColumnIfMissing(db, TABLE, COL_VERSION, "TEXT");
        addColumnIfMissing(db, TABLE, COL_SUBTITLE, "TEXT");
        createAuxTables(db);
    }

    private void createDownloadsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TYPE + " TEXT, " +
                COL_LABEL + " TEXT, " +
                COL_URL + " TEXT, " +
                COL_SIZE + " INTEGER, " +
                COL_TIMESTAMP + " INTEGER, " +
                COL_PATH + " TEXT, " +
                COL_STATUS + " INTEGER DEFAULT 0, " +
                COL_IMPORTED + " INTEGER DEFAULT 0, " +
                COL_DATASET + " TEXT, " +
                COL_VERSION + " TEXT, " +
                COL_SUBTITLE + " TEXT);");
        // Index simple : la version 1 pouvait contenir des doublons d'URL,
        // un index UNIQUE ferait échouer la migration.
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_downloads_url " +
                "ON " + TABLE + "(" + COL_URL + ")");
    }

    private void createAuxTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_QUEUE + " (" +
                "url TEXT PRIMARY KEY, " +
                "state INTEGER, " +
                "bytes INTEGER, " +
                "total INTEGER, " +
                "position INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_DATASETS + " (" +
                "dataset_key TEXT PRIMARY KEY, " +
                "imported_at INTEGER, " +
                "source_url TEXT)");
    }

    private void addColumnIfMissing(SQLiteDatabase db, String table,
                                    String column, String definition) {
        try {
            Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
            boolean exists = false;
            if (c != null) {
                while (c.moveToNext()) {
                    if (column.equalsIgnoreCase(
                            c.getString(c.getColumnIndexOrThrow("name")))) {
                        exists = true;
                        break;
                    }
                }
                c.close();
            }
            if (!exists) {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN "
                        + column + " " + definition);
            }
        } catch (Exception ignored) {
        }
    }

    // =========================================================
    // ÉCRITURE
    // =========================================================

    /** Enregistre (ou met à jour) un téléchargement terminé. */
    public void record(DownloadEntry entry, File file) {
        if (entry == null) return;
        ContentValues values = new ContentValues();
        values.put(COL_TYPE, entry.getType());
        values.put(COL_LABEL, entry.getTitle());
        values.put(COL_SUBTITLE, entry.getSubtitle());
        values.put(COL_URL, entry.getUrl());
        values.put(COL_SIZE, file != null ? file.length() : entry.getSize());
        values.put(COL_TIMESTAMP, System.currentTimeMillis());
        values.put(COL_PATH, file != null ? file.getAbsolutePath() : entry.getLocalPath());
        values.put(COL_STATUS, DownloadQueue.STATE_DONE);
        values.put(COL_DATASET, entry.inferDataset());
        values.put(COL_VERSION, entry.getVersionName());

        String url = entry.getUrl();
        if (url == null || url.isEmpty()) {
            getWritableDatabase().insert(TABLE, null, values);
            return;
        }
        // Upsert : une même URL ne crée pas de doublon.
        int updated = getWritableDatabase().update(TABLE, values,
                COL_URL + " = ?", new String[]{url});
        if (updated == 0) {
            values.put(COL_IMPORTED, entry.isImported() ? 1 : 0);
            getWritableDatabase().insert(TABLE, null, values);
        }
    }

    /** Compatibilité : enregistrement sans fichier (mise à jour de l'app). */
    public void add(DownloadEntry entry, long size, long timestamp) {
        if (entry == null) return;
        ContentValues values = new ContentValues();
        values.put(COL_TYPE, entry.getType());
        values.put(COL_LABEL, entry.getTitle());
        values.put(COL_SUBTITLE, entry.getSubtitle());
        values.put(COL_URL, entry.getUrl());
        values.put(COL_SIZE, size);
        values.put(COL_TIMESTAMP, timestamp);
        values.put(COL_STATUS, DownloadQueue.STATE_DONE);
        values.put(COL_DATASET, entry.inferDataset());
        values.put(COL_VERSION, entry.getVersionName());

        String url = entry.getUrl();
        if (url == null || url.isEmpty()) {
            getWritableDatabase().insert(TABLE, null, values);
            return;
        }
        int updated = getWritableDatabase().update(TABLE, values,
                COL_URL + " = ?", new String[]{url});
        if (updated == 0) getWritableDatabase().insert(TABLE, null, values);
    }

    /** Le contenu a été importé dans la base : il devient ineffaçable. */
    public void markImported(String url, String dataset) {
        ContentValues values = new ContentValues();
        values.put(COL_IMPORTED, 1);
        if (dataset != null) values.put(COL_DATASET, dataset);
        String where;
        String[] args;
        if (url != null && !url.isEmpty()) {
            where = COL_URL + " = ?";
            args = new String[]{url};
        } else if (dataset != null) {
            where = COL_DATASET + " = ?";
            args = new String[]{dataset};
        } else {
            return;
        }
        getWritableDatabase().update(TABLE, values, where, args);
    }

    public boolean isImported(String url) {
        if (url == null || url.isEmpty()) return false;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COL_IMPORTED + " FROM " + TABLE +
                        " WHERE " + COL_URL + " = ? LIMIT 1",
                new String[]{url});
        boolean imported = c.moveToFirst() && c.getInt(0) == 1;
        c.close();
        return imported;
    }

    // =========================================================
    // LECTURE
    // =========================================================

    public boolean hasUrl(String url) {
        return findByUrl(url) != null;
    }

    /** Ligne correspondant à l'URL (null si inconnue). */
    public DownloadEntry findByUrl(String url) {
        if (url == null || url.isEmpty()) return null;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE + " WHERE " + COL_URL + " = ? LIMIT 1",
                new String[]{url});
        DownloadEntry e = c.moveToFirst() ? read(c) : null;
        c.close();
        return e;
    }

    /** Téléchargement terminé ET fichier encore présent (ou importé). */
    public boolean isDownloaded(DownloadEntry entry) {
        if (entry == null) return false;
        DownloadEntry row = findByUrl(entry.getUrl());
        if (row == null) return false;
        if (row.isImported()) return true;
        String path = row.getLocalPath();
        return path != null && new File(path).exists();
    }

    /** Les téléchargements les plus récents d'abord. */
    public List<DownloadEntry> getRecent(int limit) {
        return query("SELECT * FROM " + TABLE +
                " ORDER BY " + COL_TIMESTAMP + " DESC LIMIT " + limit);
    }

    public List<DownloadEntry> getAll() {
        return query("SELECT * FROM " + TABLE +
                " ORDER BY " + COL_TIMESTAMP + " DESC");
    }

    public List<DownloadEntry> getByType(String type) {
        return query("SELECT * FROM " + TABLE + " WHERE " + COL_TYPE + " = ?" +
                        " ORDER BY " + COL_TIMESTAMP + " DESC",
                new String[]{type});
    }

    private List<DownloadEntry> query(String sql) {
        return query(sql, null);
    }

    private List<DownloadEntry> query(String sql, String[] args) {
        List<DownloadEntry> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        if (c != null && c.moveToFirst()) {
            do {
                list.add(read(c));
            } while (c.moveToNext());
            c.close();
        }
        return list;
    }

    private DownloadEntry read(Cursor c) {
        DownloadEntry e = new DownloadEntry();
        e.setType(c.getString(c.getColumnIndexOrThrow(COL_TYPE)));
        e.setTitle(c.getString(c.getColumnIndexOrThrow(COL_LABEL)));
        e.setSubtitle(c.getString(c.getColumnIndexOrThrow(COL_SUBTITLE)));
        e.setUrl(c.getString(c.getColumnIndexOrThrow(COL_URL)));
        e.setSize(c.getLong(c.getColumnIndexOrThrow(COL_SIZE)));
        e.setLocalPath(c.getString(c.getColumnIndexOrThrow(COL_PATH)));
        e.setState(c.getInt(c.getColumnIndexOrThrow(COL_STATUS)));
        e.setImported(c.getInt(c.getColumnIndexOrThrow(COL_IMPORTED)) == 1);
        e.setDataset(c.getString(c.getColumnIndexOrThrow(COL_DATASET)));
        e.setVersionName(c.getString(c.getColumnIndexOrThrow(COL_VERSION)));
        long ts = c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP));
        if (e.getSubtitle() == null || e.getSubtitle().isEmpty()) {
            e.setSubtitle(formatDate(ts));
        }
        return e;
    }

    // =========================================================
    // SUPPRESSION
    // =========================================================

    /**
     * Supprime la ligne et le fichier téléchargé (jamais un contenu importé
     * sauf si {@code force} est vrai).
     */
    public boolean deleteByUrl(String url, boolean force) {
        DownloadEntry row = findByUrl(url);
        if (row == null) return false;
        if (row.isImported() && !force) return false;
        getWritableDatabase().delete(TABLE, COL_URL + " = ?", new String[]{url});
        deleteFile(row.getLocalPath());
        return true;
    }

    public boolean deleteByUrl(String url) {
        return deleteByUrl(url, false);
    }

    /** Supprime la ligne correspondant à un fichier local (transfert). */
    public boolean deleteByPath(String path) {
        if (path == null || path.isEmpty()) return false;
        return getWritableDatabase().delete(TABLE, COL_PATH + " = ?",
                new String[]{path}) > 0;
    }

    public void clear() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    private void deleteFile(String path) {
        if (path == null || path.isEmpty()) return;
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(path).delete();
        } catch (Exception ignored) {
        }
    }

    // =========================================================
    // RÉCONCILIATION AVEC LE SERVEUR
    // =========================================================

    /**
     * Le serveur a répondu : tout téléchargement simple dont l'URL n'est plus
     * proposée est supprimé. Les contenus importés dans la base restent.
     *
     * @return nombre de lignes supprimées.
     */
    public int reconcile(Collection<String> serverUrls) {
        if (serverUrls == null) return 0;
        List<DownloadEntry> all = getAll();
        int removed = 0;
        for (DownloadEntry e : all) {
            String url = e.getUrl();
            if (url == null || url.isEmpty()) continue;
            if (e.isImported()) continue;
            // Lecture déjà importée dans la base (même si le marqueur de la
            // ligne manque) : jamais supprimée par un simple retour réseau.
            if (e.getDataset() != null && isDataSetImported(e.getDataset())) continue;
            if (serverUrls.contains(url)) continue;
            // Ne jamais supprimer un APK déjà enregistré par l'utilisateur.
            if (DownloadEntry.TYPE_APP.equals(e.getType())) continue;
            getWritableDatabase().delete(TABLE, COL_URL + " = ?", new String[]{url});
            deleteFile(e.getLocalPath());
            removed++;
        }
        return removed;
    }

    // =========================================================
    // FILE D'ATTENTE (persistance)
    // =========================================================

    /** Une ligne de file relue au démarrage. */
    public static class QueueRow {
        public String url;
        public int state;
        public long bytes;
        public long total;
        public int position;
    }

    public void saveQueue(List<QueueRow> rows) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_QUEUE, null, null);
            for (int i = 0; i < rows.size(); i++) {
                QueueRow r = rows.get(i);
                ContentValues v = new ContentValues();
                v.put("url", r.url);
                v.put("state", r.state);
                v.put("bytes", r.bytes);
                v.put("total", r.total);
                v.put("position", i);
                db.insertWithOnConflict(TABLE_QUEUE, null, v,
                        SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<QueueRow> loadQueue() {
        List<QueueRow> rows = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT url, state, bytes, total, position FROM " + TABLE_QUEUE +
                        " ORDER BY position ASC", null);
        if (c.moveToFirst()) {
            do {
                QueueRow r = new QueueRow();
                r.url = c.getString(0);
                r.state = c.getInt(1);
                r.bytes = c.getLong(2);
                r.total = c.getLong(3);
                r.position = c.getInt(4);
                rows.add(r);
            } while (c.moveToNext());
        }
        c.close();
        return rows;
    }

    // =========================================================
    // JEUX DE DONNÉES IMPORTÉS
    // =========================================================

    public boolean isDataSetImported(String key) {
        if (key == null) return false;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM " + TABLE_DATASETS + " WHERE dataset_key = ? LIMIT 1",
                new String[]{key});
        boolean found = c.moveToFirst();
        c.close();
        return found;
    }

    public void markDataSetImported(String key, String sourceUrl) {
        if (key == null) return;
        ContentValues v = new ContentValues();
        v.put("dataset_key", key);
        v.put("imported_at", System.currentTimeMillis());
        v.put("source_url", sourceUrl);
        getWritableDatabase().insertWithOnConflict(TABLE_DATASETS, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Marqueur d'import retiré (quand le contenu est supprimé de la base). */
    public void clearDataSetImported(String key) {
        if (key == null) return;
        getWritableDatabase().delete(TABLE_DATASETS,
                "dataset_key = ?", new String[]{key});
    }

    public List<String> getImportedDataSetKeys() {
        List<String> keys = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT dataset_key FROM " + TABLE_DATASETS, null);
        if (c.moveToFirst()) {
            do {
                keys.add(c.getString(0));
            } while (c.moveToNext());
        }
        c.close();
        return keys;
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
