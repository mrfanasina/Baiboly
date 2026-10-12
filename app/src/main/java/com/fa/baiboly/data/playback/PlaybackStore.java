package com.fa.baiboly.data.playback;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.fa.baiboly.models.Playback;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Base SQLite des playbacks (feo) et emplacement des fichiers audio.
 *
 * Depuis la version 2, UN chant (catégorie + numéro) peut avoir
 * PLUSIEURS versions de playback (une ligne par version, distinguées
 * par {@code label}).
 *
 * Depuis la version 3, les fichiers sont enregistrés dans un dossier
 * choisi par l'utilisateur (SAF), mémorisé PAR CATÉGORIE dans les
 * SharedPreferences : {@code treeUri}, {@code sous-dossier} et
 * {@code label de version} d'après le nom du fichier choisi.
 *
 * Depuis la version 4 :
 * - un dossier « qui contient TOUT » (sous-dossiers = catégories) est
 *   mémorisé une seule fois (pas par catégorie) : {@link #getRootFolderUri()} ;
 * - les fichiers détectés mais NON reconnus (pas de laharana / pas de
 *   sokajy devinable) sont gardés dans la table {@code detected_files}
 *   pour être réassignés plus tard.
 *   // TODO : fenêtre dédiée « Hanovaana ny sokajy/laharana » pour
 *   // TODO : réassigner ces fichiers à un chant (table detected_files).
 *
 * Sans dossier choisi, fallback dans le stockage interne de l'app
 * ({@code filesDir/playbacks}) : aucune permission nécessaire.
 */
public class PlaybackStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "playbacks.db";
    private static final int DB_VERSION = 4;
    private static final String TABLE = "playbacks";
    /** Fichiers détectés dans un dossier mais non attribués à un chant. */
    private static final String TABLE_DETECTED = "detected_files";

    private static final String PREFS = "playback_prefs";
    /** Clé du dossier « qui contient tout » (unique, pas par catégorie). */
    private static final String KEY_ROOT_FOLDER = "folder__all";

    private final Context context;

    public PlaybackStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "category TEXT NOT NULL, " +
                "number INTEGER NOT NULL, " +
                "title TEXT, " +
                "file_path TEXT NOT NULL, " +
                "source TEXT, " +
                "created_at INTEGER, " +
                "label TEXT, " +
                "doc_uri TEXT)");
        createDetectedTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Versions multiples : nouvelle colonne label
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN label TEXT");
        }
        if (oldVersion < 3) {
            // Fichier dans un dossier utilisateur (SAF)
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN doc_uri TEXT");
        }
        if (oldVersion < 4) {
            createDetectedTable(db);
        }
    }

    private void createDetectedTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_DETECTED + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "doc_uri TEXT NOT NULL, " +
                "name TEXT, " +
                "parent TEXT, " +
                "category TEXT, " +
                "number INTEGER, " +
                "title TEXT, " +
                "label TEXT, " +
                "created_at INTEGER)");
    }

    // =========================
    // LECTURE
    // =========================

    /**
     * Playbacks d'un chant (sokajy + numéro), toutes versions,
     * la plus récente en premier.
     */
    public List<Playback> getAll(String category, int number) {
        List<Playback> list = new ArrayList<>();
        String cat = normalize(category);
        if (cat.isEmpty()) return list;

        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id, category, number, title, file_path, source, created_at, "
                        + "label, doc_uri FROM " + TABLE
                        + " WHERE category = ? AND number = ? ORDER BY created_at DESC, id DESC",
                new String[]{cat, String.valueOf(number)});

        if (c.moveToFirst()) {
            do {
                list.add(read(c));
            } while (c.moveToNext());
        }
        c.close();
        return list;
    }

    /** Version par défaut (la plus récente), null si aucun playback. */
    @Nullable
    public Playback get(String category, int number) {
        List<Playback> all = getAll(category, number);
        return all.isEmpty() ? null : all.get(0);
    }

    // =========================
    // ÉCRITURE
    // =========================

    /**
     * Enregistre une version de playback (un chant peut en avoir plusieurs).
     * Si une version avec le même label ET la même origine (serveur / fichier)
     * existe déjà pour ce chant, elle est remplacée (l'ancien fichier interne
     * est supprimé) ; sinon une nouvelle ligne est ajoutée : on peut donc
     * avoir côte à côte la version du téléphone, celle du dossier et celle
     * du serveur.
     */
    public synchronized Playback save(String category, int number, String title,
                                      String filePath, String source,
                                      @Nullable String label, @Nullable String docUri) {
        String cat = normalize(category);
        String cleanLabel = cleanLabel(label);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // Version existante (même label + même origine) -> remplacée.
            // Le serveur ne remplace PAS un fichier du téléphone/dossier :
            // les deux versions coexistent dans la liste.
            long existingId = -1;
            String oldPath = null;
            String oldUri = null;
            Cursor c = db.rawQuery(
                    "SELECT id, file_path, doc_uri FROM " + TABLE
                            + " WHERE category = ? AND number = ? "
                            + "AND IFNULL(label, '') = ? "
                            + "AND IFNULL(source, '') = ?",
                    new String[]{cat, String.valueOf(number),
                            cleanLabel == null ? "" : cleanLabel,
                            sourceGroup(source)});
            if (c.moveToFirst()) {
                existingId = c.getLong(0);
                oldPath = c.getString(1);
                oldUri = c.getString(2);
            }
            c.close();

            if (existingId >= 0) {
                // Supprime l'ancien fichier si c'est un fichier interne
                // (pas un fichier du dossier utilisateur, qui peut servir
                // à d'autres versions)
                deleteFileQuietly(oldPath, oldUri, filePath);
            }

            ContentValues v = new ContentValues();
            v.put("category", cat);
            v.put("number", number);
            v.put("title", title);
            v.put("file_path", filePath);
            v.put("source", source);
            v.put("created_at", System.currentTimeMillis());
            v.put("label", cleanLabel);
            v.put("doc_uri", docUri);

            if (existingId >= 0) {
                db.update(TABLE, v, "id = ?", new String[]{String.valueOf(existingId)});
            } else {
                db.insert(TABLE, null, v);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return get(cat, number, cleanLabel);
    }

    /**
     * Playback d'une version précise (label), null si introuvable.
     * {@code label} null/"" : première version trouvée.
     */
    @Nullable
    public Playback get(String category, int number, @Nullable String label) {
        String cat = normalize(category);
        if (cat.isEmpty()) return null;
        String clean = cleanLabel(label);

        SQLiteDatabase db = getReadableDatabase();
        Cursor c;
        if (clean == null) {
            c = db.rawQuery(
                    "SELECT id, category, number, title, file_path, source, created_at, "
                            + "label, doc_uri FROM " + TABLE
                            + " WHERE category = ? AND number = ? "
                            + "ORDER BY created_at DESC, id DESC LIMIT 1",
                    new String[]{cat, String.valueOf(number)});
        } else {
            c = db.rawQuery(
                    "SELECT id, category, number, title, file_path, source, created_at, "
                            + "label, doc_uri FROM " + TABLE
                            + " WHERE category = ? AND number = ? AND IFNULL(label, '') = ?",
                    new String[]{cat, String.valueOf(number), clean});
        }

        Playback playback = null;
        if (c.moveToFirst()) {
            playback = read(c);
        }
        c.close();
        return playback;
    }

    /** Supprime un playback (ligne DB + fichier audio). */
    public boolean delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery(
                "SELECT file_path, doc_uri FROM " + TABLE + " WHERE id = ?",
                new String[]{String.valueOf(id)});
        String path = null;
        String uri = null;
        if (c.moveToFirst()) {
            path = c.getString(0);
            uri = c.getString(1);
        }
        c.close();

        int rows = db.delete(TABLE, "id = ?", new String[]{String.valueOf(id)});
        deleteFileQuietly(path, uri, null);
        return rows > 0;
    }

    /**
     * Supprime le fichier d'une version remplacée/supprimée, sauf si le
     * nouveau chemin est identique, ou si le fichier vit dans le dossier
     * utilisateur (l'utilisateur le gère lui-même).
     */
    private void deleteFileQuietly(String oldPath, String oldUri, String keepPath) {
        if (oldPath == null || oldPath.equals(keepPath)) return;
        if (oldUri != null && !oldUri.isEmpty()) return; // fichier SAF : pas touche
        //noinspection ResultOfMethodCallIgnored
        new File(oldPath).delete();
    }

    // =========================
    // DOSSIER PAR CATÉGORIE (SAF)
    // =========================

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Uri du dossier choisi par l'utilisateur pour cette catégorie (null si aucun). */
    @Nullable
    public Uri getFolderUri(String category) {
        String s = prefs().getString(keyFolder(normalize(category)), null);
        if (s == null || s.isEmpty()) return null;
        try {
            return Uri.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** Enregistre le dossier (tree Uri) choisi pour cette catégorie. */
    public void setFolderUri(String category, @Nullable Uri treeUri) {
        prefs().edit()
                .putString(keyFolder(normalize(category)),
                        treeUri != null ? treeUri.toString() : null)
                .apply();
    }

    /**
     * Uri du dossier « qui contient tout » (sous-dossiers = catégories),
     * unique pour toute l'app (null si aucun).
     */
    @Nullable
    public Uri getRootFolderUri() {
        String s = prefs().getString(KEY_ROOT_FOLDER, null);
        if (s == null || s.isEmpty()) return null;
        try {
            return Uri.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** Enregistre le dossier « qui contient tout ». */
    public void setRootFolderUri(@Nullable Uri treeUri) {
        prefs().edit()
                .putString(KEY_ROOT_FOLDER,
                        treeUri != null ? treeUri.toString() : null)
                .apply();
    }

    /** Nom du fichier de destination dans le dossier catégorie (ex. "ffpm_49.mp3"). */
    public String fileNameFor(String category, int number, String extension) {
        String ext = (extension == null || extension.isEmpty())
                ? "mp3" : extension.replace(".", "").toLowerCase();
        return normalize(category) + "_" + number + "." + ext;
    }

    /** Fichier interne de fallback (ex. filesDir/playbacks/ffpm_49.mp3). */
    public File internalFileFor(String category, int number, String extension) {
        File dir = new File(context.getFilesDir(), "playbacks");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, fileNameFor(category, number, extension));
    }

    // =========================
    // FICIERS DETECTES MAIS NON RECONNUS
    // =========================

    /** Un fichier détecté dans un dossier mais pas encore attribué. */
    public static class DetectedFile {
        public final long id;
        public final String docUri;
        public final String name;
        public final String parent;
        public final String category;
        public final int number;
        public final String title;
        public final String label;

        DetectedFile(long id, String docUri, String name, String parent,
                     String category, int number, String title, String label) {
            this.id = id;
            this.docUri = docUri;
            this.name = name;
            this.parent = parent;
            this.category = category;
            this.number = number;
            this.title = title;
            this.label = label;
        }
    }

    /**
     * Met de côté un fichier détecté mais non reconnu (sokajy ou laharana
     * introuvable dans le nom). Un ré-scan écrase la ligne existante
     * (dédupliqué sur {@code doc_uri}).
     *
     * // TODO : fenêtre dédiée pour réassigner ces fichiers à un chant.
     */
    public synchronized void saveDetected(String docUri, @Nullable String name,
                                          @Nullable String parent,
                                          @Nullable String category, int number,
                                          @Nullable String title,
                                          @Nullable String label) {
        if (docUri == null || docUri.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        if (!detectedTableExists(db)) return;

        ContentValues v = new ContentValues();
        v.put("doc_uri", docUri);
        v.put("name", name);
        v.put("parent", parent);
        v.put("category", category);
        v.put("number", number);
        v.put("title", title);
        v.put("label", label);
        v.put("created_at", System.currentTimeMillis());

        long existingId = -1;
        Cursor c = db.rawQuery(
                "SELECT id FROM " + TABLE_DETECTED + " WHERE doc_uri = ?",
                new String[]{docUri});
        if (c.moveToFirst()) existingId = c.getLong(0);
        c.close();

        if (existingId >= 0) {
            db.update(TABLE_DETECTED, v, "id = ?",
                    new String[]{String.valueOf(existingId)});
        } else {
            db.insert(TABLE_DETECTED, null, v);
        }
    }

    /** Fichiers détectés en attente d'attribution (TODO : fenêtre de réassignation). */
    public List<DetectedFile> getDetected() {
        List<DetectedFile> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        if (!detectedTableExists(db)) return list;

        Cursor c = db.rawQuery(
                "SELECT id, doc_uri, name, parent, category, number, title, label "
                        + "FROM " + TABLE_DETECTED + " ORDER BY created_at DESC",
                null);
        if (c.moveToFirst()) {
            do {
                list.add(new DetectedFile(c.getLong(0), c.getString(1),
                        c.getString(2), c.getString(3), c.getString(4),
                        c.getInt(5), c.getString(6), c.getString(7)));
            } while (c.moveToNext());
        }
        c.close();
        return list;
    }

    /** Nombre de fichiers détectés en attente. */
    public int countDetected() {
        SQLiteDatabase db = getReadableDatabase();
        if (!detectedTableExists(db)) return 0;
        Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_DETECTED, null);
        int n = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return n;
    }

    /** Ré-assigne un fichier détecté à un chant (sokajy + laharana). */
    public synchronized boolean assignDetected(long detectedId, String category,
                                               int number) {
        SQLiteDatabase db = getWritableDatabase();
        if (!detectedTableExists(db)) return false;
        ContentValues v = new ContentValues();
        v.put("category", normalize(category));
        v.put("number", number);
        return db.update(TABLE_DETECTED, v, "id = ?",
                new String[]{String.valueOf(detectedId)}) > 0;
    }

    /** Supprime un fichier de la table des fichiers détectés. */
    public synchronized boolean deleteDetected(long detectedId) {
        SQLiteDatabase db = getWritableDatabase();
        if (!detectedTableExists(db)) return false;
        return db.delete(TABLE_DETECTED, "id = ?",
                new String[]{String.valueOf(detectedId)}) > 0;
    }

    /** La table {@code detected_files} existe-t-elle ? (DB fraîchement créée) */
    private boolean detectedTableExists(SQLiteDatabase db) {
        Cursor c = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
                new String[]{TABLE_DETECTED});
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }

    /**
     * Groupe d'origine pour l'unicité d'une version : un fichier téléchargé
     * depuis le serveur ne remplace jamais un fichier du téléphone ou du
     * dossier choisi portant le même label (et inversement) — sinon
     * télécharger effaçait la version déjà enregistrée.
     */
    private static String sourceGroup(@Nullable String source) {
        return Playback.SOURCE_SERVER.equals(source)
                ? Playback.SOURCE_SERVER : "file";
    }

    /** Normalise une catégorie (minuscules, sans espaces superflus). */
    public static String normalize(String category) {
        return category == null ? "" : category.trim().toLowerCase();
    }

    /** Nettoie un label de version (null si vide). */
    public static String cleanLabel(@Nullable String label) {
        if (label == null) return null;
        String t = label.trim();
        return t.isEmpty() ? null : t;
    }

    private static String keyFolder(String category) {
        return "folder_" + (category.isEmpty() ? "_default" : category);
    }

    private Playback read(Cursor c) {
        return new Playback(
                c.getLong(0),
                c.getString(1),
                c.getInt(2),
                c.getString(3),
                c.getString(4),
                c.getString(5),
                c.getLong(6),
                c.getString(7),
                c.getString(8));
    }
}
