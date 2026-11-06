package com.fa.baiboly.data.litorjia;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Accès à program / program_node / program_template pour le module
 * Litorjia. Toute écriture d'arbre est transactionnelle (autosave sûr,
 * aucune perte en cas de fermeture brutale, §10).
 */
public class LitorjiaRepository {

    private static final String TAG = "LitorjiaRepo";
    private final SQLiteDatabase db;

    public LitorjiaRepository(Context context) {
        db = LitorjiaDbHelper.get(context).getWritableDatabase();
        TemplateCatalog.seed(db);
    }

    // =========================================================
    // Gabarits
    // =========================================================

    public List<TemplateCatalog.TemplateInfo> templates() {
        List<TemplateCatalog.TemplateInfo> out = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT id, label, kind, rank FROM program_template ORDER BY rank", null);
        if (c != null) {
            while (c.moveToNext()) {
                out.add(new TemplateCatalog.TemplateInfo(
                        c.getString(0), c.getString(1), c.getString(2), c.getInt(3)));
            }
            c.close();
        }
        return out;
    }

    public String templateLabel(String id) {
        if (id == null) return null;
        Cursor c = db.rawQuery("SELECT label FROM program_template WHERE id=?", new String[]{id});
        String label = null;
        if (c != null && c.moveToFirst()) { label = c.getString(0); c.close(); }
        return label;
    }

    public String templateTree(String id) {
        if (id == null) return null;
        Cursor c = db.rawQuery("SELECT tree FROM program_template WHERE id=?", new String[]{id});
        String tree = null;
        if (c != null && c.moveToFirst()) { tree = c.getString(0); c.close(); }
        return tree;
    }

    // =========================================================
    // Programmes
    // =========================================================

    /** Liste des programmes, du plus récent au plus ancien (sans arbres). */
    public List<Program> getAllPrograms() {
        List<Program> out = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT id, title, service_date, template_id, notes, created_at, updated_at " +
                        "FROM program ORDER BY updated_at DESC", null);
        if (c != null) {
            while (c.moveToNext()) {
                out.add(programFromRow(c, c.getString(0)));
            }
            c.close();
        }
        return out;
    }

    public Program getProgram(String id) {
        Cursor c = db.rawQuery(
                "SELECT id, title, service_date, template_id, notes, created_at, updated_at " +
                        "FROM program WHERE id=?", new String[]{id});
        Program p = null;
        if (c != null && c.moveToFirst()) {
            p = programFromRow(c, id);
            c.close();
        }
        if (p != null) p.root = loadRoot(id, p.title);
        return p;
    }

    private Program programFromRow(Cursor c, String id) {
        Program p = new Program();
        p.id = c.getString(0) != null ? c.getString(0) : id;
        p.title = c.getString(1);
        p.serviceDate = c.getString(2);
        p.templateId = c.getString(3);
        p.notes = c.getString(4);
        p.createdAt = c.getLong(5);
        p.updatedAt = c.getLong(6);
        return p;
    }

    public Program createProgram(String title, String date, String templateId, String notes) {
        Program p = new Program();
        p.id = UUID.randomUUID().toString();
        p.title = title == null || title.trim().isEmpty() ? "Fivoriana" : title.trim();
        p.serviceDate = date;
        p.templateId = templateId;
        p.notes = notes;
        p.createdAt = System.currentTimeMillis();
        p.updatedAt = p.createdAt;
        cv(p.id, title, date, templateId, notes, p.createdAt, p.updatedAt).also_insert(db, p.id);
        return p;
    }

    public void insertProgram(Program p) {
        db.insert("program", null, programCv(p));
    }

    public void renameProgram(String id, String newTitle, String newDate) {
        ContentValues cv = new ContentValues();
        cv.put("title", newTitle);
        if (newDate != null) cv.put("service_date", newDate);
        cv.put("updated_at", System.currentTimeMillis());
        db.update("program", cv, "id=?", new String[]{id});
    }

    public void touchProgram(String id) {
        ContentValues cv = new ContentValues();
        cv.put("updated_at", System.currentTimeMillis());
        db.update("program", cv, "id=?", new String[]{id});
    }

    public void deleteProgram(String id) {
        db.execSQL("BEGIN IMMEDIATE");
        try {
            db.execSQL("DELETE FROM program_node WHERE program_id=?", new Object[]{id});
            db.execSQL("DELETE FROM program WHERE id=?", new Object[]{id});
            db.execSQL("COMMIT");
        } catch (Exception e) {
            try { db.execSQL("ROLLBACK"); } catch (Exception ignored) {}
            Log.e(TAG, "deleteProgram", e);
        }
    }

    /** Duplique un programme (arbre complet), nouveau titre daté si fourni. */
    public Program duplicateProgram(String sourceId, String newTitle, String newDate) {
        Program src = getProgram(sourceId);
        if (src == null) return null;
        SQLiteDatabase d = this.db;
        Program copy = new Program();
        copy.id = UUID.randomUUID().toString();
        copy.title = newTitle != null ? newTitle : src.title;
        copy.serviceDate = newDate != null ? newDate : src.serviceDate;
        copy.templateId = src.templateId;
        copy.notes = src.notes;
        copy.createdAt = System.currentTimeMillis();
        copy.updatedAt = copy.createdAt;
        d.insert("program", null, programCv(copy));
        if (src.root != null) {
            d.execSQL("BEGIN IMMEDIATE");
            try {
                // Copie profonde avec de NOUVEAUX identifiants : réutiliser
                // ceux de la source écraserait les noeuds du programme copié.
                ProgramNode clone = copyTree(src.root);
                insertTree(copy.id, clone);
                copy.root = clone;
                d.execSQL("COMMIT");
            } catch (Exception e) {
                try { d.execSQL("ROLLBACK"); } catch (Exception ignored) {}
            }
        }
        return copy;
    }

    private ProgramNode copyTree(ProgramNode src) {
        ProgramNode n = new ProgramNode();
        n.id = UUID.randomUUID().toString();
        n.type = src.type;
        n.title = src.title;
        n.payload = src.payload;
        n.hidden = src.hidden;
        n.origin = src.origin;
        n.role = src.role;
        for (ProgramNode c : src.children) n.children.add(copyTree(c));
        return n;
    }

    // =========================================================
    // Arbre : chargement / enregistrement
    // =========================================================

    /** Charge tout l'arbre d'un programme (descendants de la racine). */
    private ProgramNode loadRoot(String programId, String programTitle) {
        List<ProgramNode> all = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT id, parent_id, position, type, title, payload, hidden, origin, role " +
                        "FROM program_node WHERE program_id=? ORDER BY position ASC",
                new String[]{programId});
        if (c != null) {
            while (c.moveToNext()) {
                ProgramNode n = new ProgramNode();
                n.id = c.getString(0);
                n.parentId = c.getString(1);
                n.position = c.getInt(2);
                n.type = c.getString(3);
                n.title = c.getString(4);
                n.payload = c.getString(5);
                n.hidden = c.getInt(6) == 1;
                n.origin = c.getString(7);
                n.role = c.getString(8);
                all.add(n);
            }
            c.close();
        }
        // Reconstruction de l'arbre
        ProgramNode root = null;
        java.util.Map<String, ProgramNode> byId = new java.util.HashMap<>();
        for (ProgramNode n : all) byId.put(n.id, n);
        java.util.List<ProgramNode> orphans = new ArrayList<>();
        for (ProgramNode n : all) {
            if (n.parentId == null) { orphans.add(n); continue; }
            ProgramNode parent = byId.get(n.parentId);
            if (parent != null) parent.children.add(n);
            else {
                Log.w(TAG, "orphan node " + n.id);
                orphans.add(n);
            }
        }
        if (orphans.size() == 1) {
            root = orphans.get(0);
        } else if (orphans.size() > 1) {
            // Réparation des programmes écrits par l'ancienne version :
            // tous les noeuds de premier niveau avaient parent_id = NULL.
            // Si l'ancienne racine (section portant le titre du programme)
            // est présente, on lui rattache les autres au lieu d'ajouter une
            // racine parasite ; sinon on crée une racine synthétique.
            for (ProgramNode n : orphans) {
                if (ProgramNode.TYPE_SECTION.equals(n.type)
                        && programTitle != null && programTitle.equals(n.title)) {
                    root = n;
                    break;
                }
            }
            if (root == null) {
                root = new ProgramNode();
                root.id = "root-" + programId;
                root.type = ProgramNode.TYPE_SECTION;
                root.origin = ProgramNode.ORIGIN_TEMPLATE;
            }
            for (ProgramNode n : orphans) {
                if (n == root) continue;
                n.parentId = root.id;
                root.children.add(n);
            }
            for (int i = 0; i < root.children.size(); i++) {
                root.children.get(i).position = i;
            }
            root.expanded = true;
            Log.w(TAG, "racines multiples réparées pour " + programId);
        }
        return root;
    }

    /**
     * Enregistre tout l'arbre d'un programme dans une transaction :
     * suppression + réinsertion complète — simplicité et fiabilité
     * (nœuds nouveaux/moves/deletions gérés d'un coup).
     */
    public void saveTree(Program p, ProgramNode root) {
        db.execSQL("BEGIN IMMEDIATE");
        try {
            db.execSQL("DELETE FROM program_node WHERE program_id=?", new Object[]{p.id});
            if (root != null) insertTree(p.id, root);
            ContentValues cv = new ContentValues();
            cv.put("updated_at", System.currentTimeMillis());
            db.update("program", cv, "id=?", new String[]{p.id});
            db.execSQL("COMMIT");
        } catch (Exception e) {
            try { db.execSQL("ROLLBACK"); } catch (Exception ignored) {}
            Log.e(TAG, "saveTree", e);
        }
    }

    private void insertTree(String programId, ProgramNode node) {
        insertTree(programId, node, null);
    }

    /**
     * Insère un noeud puis ses enfants avec un parent_id explicite : la
     * position dans l'arbre est la seule source de vérité (les champs
     * parentId en mémoire peuvent être obsolètes après un déplacement).
     */
    private void insertTree(String programId, ProgramNode node, String parentId) {
        if (node.id == null || node.id.isEmpty()) node.id = UUID.randomUUID().toString();
        node.parentId = parentId;
        insertNode(programId, node, parentId);
        for (int i = 0; i < node.children.size(); i++) {
            ProgramNode ch = node.children.get(i);
            ch.position = i;
            insertTree(programId, ch, node.id);
        }
    }

    private void insertNode(String programId, ProgramNode n, String parentId) {
        if (n.id == null || n.id.isEmpty()) n.id = UUID.randomUUID().toString();
        ContentValues cv = new ContentValues();
        cv.put("id", n.id);
        cv.put("program_id", programId);
        cv.put("parent_id", parentId);
        cv.put("position", n.position);
        cv.put("type", n.type);
        cv.put("title", n.title);
        cv.put("payload", n.payload == null ? "" : n.payload);
        cv.put("hidden", n.hidden ? 1 : 0);
        cv.put("origin", n.origin == null ? ProgramNode.ORIGIN_MANUAL : n.origin);
        cv.put("role", n.role);
        db.insertWithOnConflict("program_node", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // =========================================================
    // Helper interne
    // =========================================================

    private ContentValues programCv(Program p) {
        ContentValues cv = new ContentValues();
        cv.put("id", p.id);
        cv.put("title", p.title);
        cv.put("service_date", p.serviceDate);
        cv.put("template_id", p.templateId);
        cv.put("notes", p.notes);
        cv.put("created_at", p.createdAt);
        cv.put("updated_at", p.updatedAt);
        return cv;
    }

    private static CVBuilder cv(String id, String title, String date, String templateId,
                                String notes, long created, long updated) {
        return new CVBuilder(id, title, date, templateId, notes, created, updated);
    }

    /** Petit helper interne pour createProgram (évite un copy-paste). */
    private static final class CVBuilder {
        private final String id, title, date, templateId, notes;
        private final long created, updated;
        CVBuilder(String id, String title, String date, String templateId,
                  String notes, long created, long updated) {
            this.id = id; this.title = title; this.date = date;
            this.templateId = templateId; this.notes = notes;
            this.created = created; this.updated = updated;
        }
        void also_insert(SQLiteDatabase db2, String pid) {
            ContentValues cvv = new ContentValues();
            cvv.put("id", pid);
            cvv.put("title", title == null || title.trim().isEmpty() ? "Fivoriana" : title.trim());
            cvv.put("service_date", date);
            cvv.put("template_id", templateId);
            cvv.put("notes", notes);
            cvv.put("created_at", created);
            cvv.put("updated_at", updated);
            db2.insertWithOnConflict("program", null, cvv,
                    SQLiteDatabase.CONFLICT_IGNORE);
        }
    }
}
