package com.fa.baiboly.data.litorjia;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Base du module « Programme de culte » (Litorjia) — base SQLite séparée
 * (litorjia.db) pour ne pas toucher à la base principale de l'app
 * (chants, Bible, perikopa : tables non modifiées, cf. §5.3).
 */
public class LitorjiaDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "litorjia.db";
    private static final int DB_VERSION = 2;

    private static LitorjiaDbHelper instance;

    public static synchronized LitorjiaDbHelper get(Context context) {
        if (instance == null) instance = new LitorjiaDbHelper(context.getApplicationContext());
        return instance;
    }

    private LitorjiaDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Catalogue des gabarits : extensibles sans mise à jour de l'app
        db.execSQL("CREATE TABLE program_template (" +
                "id      TEXT PRIMARY KEY," +
                "label   TEXT NOT NULL," +
                "kind    TEXT NOT NULL," +     // level | litorjia
                "rank    INTEGER NOT NULL," +  // ordre d'affichage
                "tree    TEXT NOT NULL)");     // JSON : arbre type

        db.execSQL("CREATE TABLE program (" +
                "id           TEXT PRIMARY KEY," +
                "title        TEXT NOT NULL," +
                "service_date TEXT," +
                "template_id  TEXT NOT NULL," +
                "notes        TEXT," +
                "created_at   INTEGER NOT NULL," +
                "updated_at   INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE program_node (" +
                "id        TEXT PRIMARY KEY," +
                "program_id TEXT NOT NULL REFERENCES program(id) ON DELETE CASCADE," +
                "parent_id TEXT REFERENCES program_node(id) ON DELETE CASCADE," +
                "position  INTEGER NOT NULL," +
                "type      TEXT NOT NULL," +
                "title     TEXT," +
                "payload   TEXT," +
                "hidden    INTEGER NOT NULL DEFAULT 0," +
                "origin    TEXT NOT NULL DEFAULT 'manual'," +
                "role      TEXT)");
        db.execSQL("CREATE INDEX idx_node_parent ON program_node(program_id, parent_id, position)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // v2 : RDZ n'était qu'un exemple de forme courte, pas un gabarit
            // du catalogue (§4.1/§4.8). On retire l'entrée codée en dur de la
            // première version et on rattache ses programmes au niveau standard.
            db.execSQL("UPDATE program SET template_id='level-2' WHERE template_id='level-5'");
            db.execSQL("DELETE FROM program_template WHERE id='level-5'");
        }
    }
}
