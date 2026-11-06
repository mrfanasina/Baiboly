package com.fa.baiboly.data.litorjia;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalogue des gabarits (niveaux 1 à 4 + litorjia I à III) — §4.1 du
 * document technique. Chaque gabarit est un arbre JSON de briques
 * réutilisables stocké dans la table program_template : modifiable en
 * base sans mise à jour de l'application.
 *
 * <p>Note §14.3 : l'ordre exact des litorjia II et III reste à confirmer
 * (les diaporamas fournis sont identiques à Litorjia I) — les arbres
 * ci-dessous assembleront les mêmes briques dans un ordre ajustable.</p>
 */
public final class TemplateCatalog {

    private TemplateCatalog() {}

    public static class TemplateInfo {
        public String id, label, kind;
        public int rank;
        public TemplateInfo(String id, String label, String kind, int rank) {
            this.id = id; this.label = label; this.kind = kind; this.rank = rank;
        }
    }

    /** Catalogue dans l'ordre d'affichage : niveaux puis litorjia. */
    public static List<TemplateInfo> catalog() {
        List<TemplateInfo> list = new ArrayList<>();
        list.add(new TemplateInfo("level-1", "Tsotra indrindra (1)", "level", 1));
        list.add(new TemplateInfo("level-2", "Mahazatra (2)", "level", 2));
        list.add(new TemplateInfo("level-3", "Feno kokoa (3)", "level", 3));
        list.add(new TemplateInfo("level-4", "Antsam-panahy (arab-panahy)", "level", 4));
        list.add(new TemplateInfo("litorjia-1", "Litorjia I", "litorjia", 10));
        list.add(new TemplateInfo("litorjia-2", "Litorjia II", "litorjia", 11));
        list.add(new TemplateInfo("litorjia-3", "Litorjia III", "litorjia", 12));
        return list;
    }

    /** Version par défaut du Fanekem-pinoana par gabarit (§14.6). */
    public static int defaultCreedVersion(String templateId) {
        if (templateId != null && templateId.startsWith("litorjia-")) return 2; // I & II vus dans les diaporamas
        return 1;
    }

    /**
     * Aperçu des sections de premier niveau d'un gabarit, pour l'écran de
     * création : "Fitarihana · Vavaka · Vakiteny · Vavaka hira". Les
     * libellés viennent du catalogue en base, jamais d'une liste codée ici.
     */
    public static String preview(String treeJson) {
        if (treeJson == null) return "";
        StringBuilder sb = new StringBuilder();
        try {
            JSONArray arr = new JSONArray(treeJson);
            int shown = 0;
            for (int i = 0; i < arr.length() && shown < 6; i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String title = o.optString("title", "").trim();
                if (title.isEmpty()) continue;
                if (sb.length() > 0) sb.append(" · ");
                sb.append(title);
                shown++;
            }
            if (arr.length() > shown) sb.append(" · …");
        } catch (Exception ignored) {}
        return sb.toString();
    }

    // =========================================================
    // Briques
    // =========================================================

    private static JSONObject node(String type, String title, String payload) {
        JSONObject o = new JSONObject();
        try { o.put("type", type); if (title != null) o.put("title", title);
              if (payload != null && !payload.isEmpty()) o.put("payload", payload); }
        catch (Exception ignored) {}
        return o;
    }

    private static JSONObject node(String type, String title, String payload, JSONArray children) {
        JSONObject o = node(type, title, payload);
        try { if (children != null) o.put("children", children); } catch (Exception ignored) {}
        return o;
    }

    private static JSONArray arr(JSONObject... nodes) {
        JSONArray a = new JSONArray();
        for (JSONObject n : nodes) if (n != null) a.put(n);
        return a;
    }

    private static JSONArray arr(JSONArray... groups) {
        JSONArray a = new JSONArray();
        for (JSONArray g : groups) for (int i = 0; i < g.length(); i++) a.put(g.opt(i));
        return a;
    }

    private static JSONArray empty() { return new JSONArray(); }

    public static final String T_MIVAVAHA = "Mivavaha mandrakariva";

    // hymn payload sans numéro = chant à compléter
    static String toggleHymn(String book, Integer n, int[] stanzas) {
        List<Integer> st = new ArrayList<>();
        if (stanzas != null) for (int s : stanzas) st.add(s);
        return NodePayloadParser.hymnPayload(book, n, st, null);
    }

    static String bible(String book, Integer bookNumber, int chapter, String from, String to) {
        List<ProgramNode.BibleRef> refs = new ArrayList<>();
        refs.add(new ProgramNode.BibleRef(book, bookNumber, String.valueOf(chapter), from, to));
        return NodePayloadParser.biblePayload(refs);
    }

    // Brique 1 : Fiderana sy fisaorana
    static JSONArray brickFiderana() {
        return arr(
                node(ProgramNode.TYPE_HYMN, "Hira", toggleHymn("FFPM", null, null)),
                node(ProgramNode.TYPE_BIBLE, "Fiarahabana apostolika",
                        bible("Romana", 45, 1, "7", null)),
                node(ProgramNode.TYPE_BIBLE, null, bible("Salamo", 19, 138, "1", "6")),
                node(ProgramNode.TYPE_HYMN, null, toggleHymn("FFPM", 12, new int[]{1, 2, 3})),
                node(ProgramNode.TYPE_PRAYER, "Vavaka fisaorana",
                        NodePayloadParser.prayerPayload("fisaorana", null)));
    }

    // Brique 2 : Fanambarana ny sitrapon'Andriamanitra
    static JSONArray brickFanambarana() {
        return arr(
                node(ProgramNode.TYPE_BIBLE, null, bible("Jeremia", 24, 33, "3", null)),
                node(ProgramNode.TYPE_BIBLE, null, bible("Jaona", 43, 4, "23", "24")),
                node(ProgramNode.TYPE_HYMN, null, toggleHymn("FIF", 105, new int[]{1, 2})),
                node(ProgramNode.TYPE_PRAYER, "Vavaka fianken-keloka",
                        NodePayloadParser.prayerPayload("keloka", null)));
    }

    // Brique 3 : Famelan-keloka
    static JSONArray brickFamelanKeloka() {
        return arr(
                node(ProgramNode.TYPE_BIBLE, null, bible("Salamo", 19, 91, "14", "16")),
                node(ProgramNode.TYPE_HYMN, null, toggleHymn("FIF", 324, new int[]{1, 2})));
    }

    // Brique 4 : Raharaham-piangonana
    static JSONArray brickRaharaham() {
        return arr(
                node(ProgramNode.TYPE_TITLE, "Famakiana ny hafatra avy amin'ny foibe", null),
                node(ProgramNode.TYPE_OFFERING, "Rakitra", null),
                node(ProgramNode.TYPE_CUSTOM, "Tenin'Andriamanitra · Sekoly alahady 5 mn", null),
                node(ProgramNode.TYPE_HYMN, null, toggleHymn("FIF", 326, new int[]{1, 2})));
    }

    // Brique 5 : Fangatahana sy asa (ligne 16 du culte du 25/10)
    static JSONArray brickFangatahana() {
        return arr(
                node(ProgramNode.TYPE_HYMN, null, toggleHymn("FFPM", null, null)),
                node(ProgramNode.TYPE_PRAYER, "Vavaka fangatahana",
                        NodePayloadParser.prayerPayload("fangatahana", null)),
                node(ProgramNode.TYPE_CUSTOM, "Asa vavolombelona", null),
                node(ProgramNode.TYPE_CUSTOM, "Harona mamangy · Lavanti-kira SAFIF", null));
    }

    // Brique 6 : Vakiteny (rôle readings — rempli par le perikopa)
    static JSONArray brickVakiteny() { return empty(); }

    static String rolePayload(String role) { return NodePayloadParser.rolePayload(role); }

    // =========================================================
    // Arbres des gabarits
    // =========================================================

    /**
     * Arbre type d'un gabarit. Renvoie null si l'identifiant est inconnu.
     */
    public static String tree(String templateId) {
        JSONObject credo = node(ProgramNode.TYPE_CREED, "Fanekem-pinoana apostolika",
                NodePayloadParser.creedPayload(defaultCreedVersion(templateId)));
        JSONObject credo1 = node(ProgramNode.TYPE_CREED, "Fanekem-pinoana apostolika",
                NodePayloadParser.creedPayload(1));

        switch (safe(templateId)) {
            case "level-1": {
                // Essentiel : Fitarihana, Vavaka, Vakiteny, Vavaka hira
                return arr(
                        node(ProgramNode.TYPE_TITLE, T_MIVAVAHA, null),
                        node(ProgramNode.TYPE_SECTION, "Fitarihana", null,
                                arr(node(ProgramNode.TYPE_HYMN, null, toggleHymn("FFPM", null, null)))),
                        node(ProgramNode.TYPE_PRAYER, "Vavaka",
                                NodePayloadParser.prayerPayload("fiderana", null)),
                        node(ProgramNode.TYPE_SECTION, "Vakiteny", rolePayload(ProgramNode.ROLE_READINGS),
                                brickVakiteny()),
                        node(ProgramNode.TYPE_PRAYER, "Vavaka hira",
                                NodePayloadParser.prayerPayload("hira", null))
                ).toString();
            }
            case "level-2": {
                // Standard : + Hira, Fanazavana
                return arr(
                        node(ProgramNode.TYPE_TITLE, T_MIVAVAHA, null),
                        node(ProgramNode.TYPE_SECTION, "Fitarihana", null,
                                arr(node(ProgramNode.TYPE_HYMN, null, toggleHymn("FFPM", null, null)))),
                        node(ProgramNode.TYPE_PRAYER, "Vavaka",
                                NodePayloadParser.prayerPayload("fiderana", null)),
                        node(ProgramNode.TYPE_SECTION, "Vakiteny", rolePayload(ProgramNode.ROLE_READINGS),
                                brickVakiteny()),
                        node(ProgramNode.TYPE_SECTION, "Fanazavana", null,
                                arr(node(ProgramNode.TYPE_CUSTOM, "Fanambarana", null))),
                        node(ProgramNode.TYPE_PRAYER, "Vavaka hira",
                                NodePayloadParser.prayerPayload("hira", null))
                ).toString();
            }
            case "level-3": {
                // Étendu : niveau 2 + Fanekem-pinoana, Rakitra, Toriteny, Tsodrano
                return arr(
                        node(ProgramNode.TYPE_TITLE, T_MIVAVAHA, null),
                        node(ProgramNode.TYPE_SECTION, "Fitarihana", null,
                                arr(node(ProgramNode.TYPE_HYMN, null, toggleHymn("FFPM", null, null)))),
                        node(ProgramNode.TYPE_PRAYER, "Vavaka",
                                NodePayloadParser.prayerPayload("fiderana", null)),
                        node(ProgramNode.TYPE_SECTION, "Vakiteny", rolePayload(ProgramNode.ROLE_READINGS),
                                brickVakiteny()),
                        credo1,
                        node(ProgramNode.TYPE_OFFERING, "Rakitra", null),
                        node(ProgramNode.TYPE_TITLE, "Toriteny", null),
                        node(ProgramNode.TYPE_TITLE, "Tsodrano", null),
                        node(ProgramNode.TYPE_SECTION, "Fanazavana", null,
                                arr(node(ProgramNode.TYPE_CUSTOM, "Fanambarana", null))),
                        node(ProgramNode.TYPE_PRAYER, "Vavaka hira",
                                NodePayloadParser.prayerPayload("hira", null))
                ).toString();
            }
            case "level-4": {
                // Détaillé : niveau 3 + Raharaham-piangonana, Vavaka fangatahana,
                // Asa vavolombelona, Famaranana
                return arr(
                        node(ProgramNode.TYPE_TITLE, T_MIVAVAHA, null),
                        node(ProgramNode.TYPE_SECTION, "Fitarihana", null,
                                arr(node(ProgramNode.TYPE_HYMN, null, toggleHymn("FFPM", null, null)))),
                        node(ProgramNode.TYPE_PRAYER, "Vavaka",
                                NodePayloadParser.prayerPayload("fiderana", null)),
                        node(ProgramNode.TYPE_SECTION, "Fameleza", null),
                        node(ProgramNode.TYPE_SECTION, "Vakiteny", rolePayload(ProgramNode.ROLE_READINGS),
                                brickVakiteny()),
                        credo1,
                        node(ProgramNode.TYPE_SECTION, "Raharaham-piangonana", null, brickRaharaham()),
                        node(ProgramNode.TYPE_OFFERING, "Rakitra", null),
                        node(ProgramNode.TYPE_TITLE, "Toriteny", null),
                        node(ProgramNode.TYPE_SECTION, "Fangatahana sy asa", null, brickFangatahana()),
                        node(ProgramNode.TYPE_TITLE, "Tsodrano", null),
                        node(ProgramNode.TYPE_TEXT, "Fanamarihana", null),
                        node(ProgramNode.TYPE_TEXT, "Famaranana", NodePayloadParser.bodyPayload(""))
                ).toString();
            }
            case "litorjia-1": {
                // Séquence complète du culte du 25/10/2026 (§2.2)
                return arr(
                        node(ProgramNode.TYPE_TITLE, T_MIVAVAHA, null),
                        node(ProgramNode.TYPE_SECTION, "Fiderana sy fisaorana an'Andriamanitra",
                                null, brickFiderana()),
                        node(ProgramNode.TYPE_SECTION, "Fanambarana ny sitrapon'Andriamanitra",
                                null, brickFanambarana()),
                        node(ProgramNode.TYPE_SECTION, "Famelan-keloka", null, brickFamelanKeloka()),
                        node(ProgramNode.TYPE_CREED, "Fanekem-pinoana apostolika",
                                NodePayloadParser.creedPayload(2)),
                        node(ProgramNode.TYPE_SECTION, "Raharaham-piangonana", null, brickRaharaham()),
                        node(ProgramNode.TYPE_SECTION, "Vakiteny", rolePayload(ProgramNode.ROLE_READINGS),
                                brickVakiteny()),
                        node(ProgramNode.TYPE_TITLE, "Toriteny", null),
                        node(ProgramNode.TYPE_HYMN, null, toggleHymn("FIF", 328, new int[]{1, 2})),
                        node(ProgramNode.TYPE_SECTION, "Fangatahana sy asa", null, brickFangatahana()),
                        node(ProgramNode.TYPE_SECTION, "Asa fampaherezena", null,
                                arr(node(ProgramNode.TYPE_HYMN, null,
                                        toggleHymn("FFPM", 356, new int[]{1, 3})))),
                        node(ProgramNode.TYPE_TITLE, "Tsodrano", null),
                        node(ProgramNode.TYPE_TEXT, "Fanamarihana", null),
                        node(ProgramNode.TYPE_TEXT, "Famaranana",
                                NodePayloadParser.bodyPayload("Doxa"))
                ).toString();
            }
            case "litorjia-2": {
                // Mêmes briques que Litorjia I, ordre différent : le credo
                // avant la Famelan-keloka, Raharaham avant la Fanambarana.
                // (variantes à valider d'après les diaporamas réels, §14.3)
                return arr(
                        node(ProgramNode.TYPE_TITLE, T_MIVAVAHA, null),
                        node(ProgramNode.TYPE_SECTION, "Fiderana sy fisaorana an'Andriamanitra",
                                null, brickFiderana()),
                        node(ProgramNode.TYPE_SECTION, "Raharaham-piangonana", null, brickRaharaham()),
                        node(ProgramNode.TYPE_CREED, "Fanekem-pinoana apostolika",
                                NodePayloadParser.creedPayload(2)),
                        node(ProgramNode.TYPE_SECTION, "Fanambarana ny sitrapon'Andriamanitra",
                                null, brickFanambarana()),
                        node(ProgramNode.TYPE_SECTION, "Famelan-keloka", null, brickFamelanKeloka()),
                        node(ProgramNode.TYPE_SECTION, "Vakiteny", rolePayload(ProgramNode.ROLE_READINGS),
                                brickVakiteny()),
                        node(ProgramNode.TYPE_TITLE, "Toriteny", null),
                        node(ProgramNode.TYPE_SECTION, "Fangatahana sy asa", null, brickFangatahana()),
                        node(ProgramNode.TYPE_SECTION, "Asa fampaherezena", null,
                                arr(node(ProgramNode.TYPE_HYMN, null,
                                        toggleHymn("FFPM", 356, new int[]{1, 3})))),
                        node(ProgramNode.TYPE_TITLE, "Tsodrano", null),
                        node(ProgramNode.TYPE_TEXT, "Famaranana",
                                NodePayloadParser.bodyPayload("Doxa"))
                ).toString();
            }
            case "litorjia-3": {
                // Mêmes briques : credo après les Vakiteny.
                // (variante à valider d'après les diaporamas réels, §14.3)
                return arr(
                        node(ProgramNode.TYPE_TITLE, T_MIVAVAHA, null),
                        node(ProgramNode.TYPE_SECTION, "Fiderana sy fisaorana an'Andriamanitra",
                                null, brickFiderana()),
                        node(ProgramNode.TYPE_SECTION, "Fanambarana ny sitrapon'Andriamanitra",
                                null, brickFanambarana()),
                        node(ProgramNode.TYPE_SECTION, "Famelan-keloka", null, brickFamelanKeloka()),
                        node(ProgramNode.TYPE_SECTION, "Vakiteny", rolePayload(ProgramNode.ROLE_READINGS),
                                brickVakiteny()),
                        node(ProgramNode.TYPE_CREED, "Fanekem-pinoana apostolika",
                                NodePayloadParser.creedPayload(2)),
                        node(ProgramNode.TYPE_TITLE, "Toriteny", null),
                        node(ProgramNode.TYPE_SECTION, "Raharaham-piangonana", null, brickRaharaham()),
                        node(ProgramNode.TYPE_HYMN, null, toggleHymn("FIF", 328, new int[]{1, 2})),
                        node(ProgramNode.TYPE_SECTION, "Fangatahana sy asa", null, brickFangatahana()),
                        node(ProgramNode.TYPE_TITLE, "Tsodrano", null),
                        node(ProgramNode.TYPE_TEXT, "Famaranana",
                                NodePayloadParser.bodyPayload("Doxa"))
                ).toString();
            }
            default:
                return null;
        }
    }

    /** Seed du catalogue en base (idempotent). */
    public static void seed(android.database.sqlite.SQLiteDatabase db) {
        try {
            db.execSQL("BEGIN IMMEDIATE");
            for (TemplateInfo t : catalog()) {
                android.database.Cursor c = db.rawQuery(
                        "SELECT id FROM program_template WHERE id=?", new String[]{t.id});
                boolean exists = c.moveToFirst();
                if (c != null) c.close();
                if (exists) continue;
                String tree = tree(t.id);
                if (tree == null) continue;
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put("id", t.id);
                cv.put("label", t.label);
                cv.put("kind", t.kind);
                cv.put("rank", t.rank);
                cv.put("tree", tree);
                db.insertWithOnConflict("program_template", null, cv,
                        android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.execSQL("COMMIT");
        } catch (Exception e) {
            try { db.execSQL("ROLLBACK"); } catch (Exception ignored) {}
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
