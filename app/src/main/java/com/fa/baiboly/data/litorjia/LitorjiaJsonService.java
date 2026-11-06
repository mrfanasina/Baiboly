package com.fa.baiboly.data.litorjia;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Import / export JSON (litorjia/1, §7). L'export et l'import sont
 * symétriques (aller-retour sans perte). L'import traite l'entrée comme
 * une donnée non fiable : JSON borné (1 Mo), profondeur limitée (8),
 * type inconnu converti en custom, livre non reconnu → « à compléter »
 * avec rapport d'erreurs lisible (chemin du noeud fautif, raison).
 */
public final class LitorjiaJsonService {

    public static final String SCHEMA = "litorjia/1";
    private static final int MAX_SIZE = 1024 * 1024;   // 1 Mo
    private static final int MAX_DEPTH = 8;

    private LitorjiaJsonService() {}

    // =========================================================
    // Rapport d'import
    // =========================================================

    public static class Warning {
        public final String path;
        public final String reason;
        public Warning(String path, String reason) { this.path = path; this.reason = reason; }
    }

    public static class Report {
        /** JSON syntaxiquement valide et schéma connu ? */
        public boolean ok;
        /** Message d'erreur principal si !ok. */
        public String error;
        public final List<Warning> warnings = new ArrayList<>();
        public String title;
        public String date;
        public String template;
        public String notes;
        /** Arbre Python-pur (RunnableConvertHelper)… non : racine reconstruite. */
        public ProgramNode root;
        /** Titre fallback au cas où racine sans titre. */
        public String fallbackTitle;
    }

    // =========================================================
    // EXPORT
    // =========================================================

    /** Export symétrique d'un programme (root compris, sans ids). */
    public static String export(Program p) {
        try {
            JSONObject o = new JSONObject();
            o.put("schema", SCHEMA);
            o.put("title", p.title == null ? "" : p.title);
            if (p.serviceDate != null) o.put("date", p.serviceDate);
            if (p.templateId != null) o.put("template", p.templateId);
            if (p.notes != null && !p.notes.isEmpty()) o.put("notes", p.notes);
            JSONArray nodes = new JSONArray();
            if (p.root != null) {
                for (int i = 0; i < p.root.children.size(); i++) {
                    nodes.put(nodeJson(p.root.children.get(i)));
                }
            }
            o.put("nodes", nodes);
            return o.toString(2);
        } catch (Exception e) {
            return "{\"schema\":\"" + SCHEMA + "\",\"nodes\":[]}"; // jamais null
        }
    }

    /** Re-export depuis un rapport d'import (fusion, sans écrire en base). */
    public static String exportOf(ProgramNode root, Report r) {
        try {
            JSONObject o = new JSONObject();
            o.put("schema", SCHEMA);
            o.put("title", r.fallbackTitle == null ? "" : r.fallbackTitle);
            if (r.date != null) o.put("date", r.date);
            if (r.template != null) o.put("template", r.template);
            if (r.notes != null && !r.notes.isEmpty()) o.put("notes", r.notes);
            JSONArray nodes = new JSONArray();
            for (int i = 0; i < root.children.size(); i++) {
                nodes.put(nodeJson(root.children.get(i)));
            }
            o.put("nodes", nodes);
            return o.toString(2);
        } catch (Exception e) {
            return "{\"schema\":\"" + SCHEMA + "\",\"nodes\":[]}";
        }
    }

    private static JSONObject nodeJson(ProgramNode n) {
        JSONObject o = new JSONObject();
        try {
            o.put("type", n.type);
            if (n.title != null && !n.title.isEmpty()) o.put("title", n.title);
            switch (n.type) {
                case ProgramNode.TYPE_HYMN: {
                    JSONObject h = new JSONObject();
                    if (!n.hymnBook().isEmpty()) h.put("book", n.hymnBook());
                    if (n.hymnNumber() != null) h.put("number", n.hymnNumber());
                    List<Integer> st = n.hymnStanzas();
                    if (!st.isEmpty()) {
                        JSONArray a = new JSONArray();
                        for (Integer s : st) a.put(s);
                        h.put("stanzas", a);
                    }
                    if (!n.hymnLabel().isEmpty()) h.put("label", n.hymnLabel());
                    o.put("hymn", h);
                    break;
                }
                case ProgramNode.TYPE_BIBLE: {
                    JSONArray arr = new JSONArray();
                    for (ProgramNode.BibleRef r : n.bibleRefs()) {
                        JSONObject ro = new JSONObject();
                        ro.put("book", r.book == null ? "" : r.book);
                        if (r.chapter != null) ro.put("chapter", intOf(r.chapter));
                        JSONArray ranges = new JSONArray();
                        JSONObject rg = new JSONObject();
                        if (r.from != null) rg.put("from", r.from);
                        if (r.to != null) rg.put("to", r.to);
                        ranges.put(rg);
                        ro.put("ranges", ranges);
                        arr.put(ro);
                    }
                    o.put("bible", arr);
                    break;
                }
                case ProgramNode.TYPE_PRAYER:
                    if (!n.prayerKind().isEmpty()) o.put("prayerKind", n.prayerKind());
                    if (!n.note().isEmpty()) o.put("note", n.note());
                    break;
                case ProgramNode.TYPE_CREED:
                    if (n.creedVersion() != null) o.put("creed", new JSONObject().put("version", n.creedVersion()));
                    break;
                case ProgramNode.TYPE_CHOIR:
                case ProgramNode.TYPE_TEXT:
                case ProgramNode.TYPE_OFFERING:
                case ProgramNode.TYPE_CUSTOM:
                    if (!n.body().isEmpty()) o.put("body", n.body());
                    if (!n.note().isEmpty()) o.put("note", n.note());
                    break;
                default:
                    break;
            }
            if (!n.children.isEmpty()) {
                JSONArray ch = new JSONArray();
                for (int i = 0; i < n.children.size(); i++) ch.put(nodeJson(n.children.get(i)));
                o.put("children", ch);
            }
        } catch (Exception ignored) {}
        return o;
    }

    private static int intOf(String s) {
        if (s == null) return 0;
        String digits = s.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try { return Integer.parseInt(digits); } catch (NumberFormatException e) { return 0; }
    }

    // =========================================================
    // IMPORT (donnée non fiable)
    // =========================================================

    /** Vérifie qu'un identifiant de gabarit existe (base en production). */
    public interface TemplateLookup {
        boolean exists(String templateId);
    }

    /** Pré-contrôles : taille, parse, schéma, puis lecture de l'arbre. */
    public static Report parse(String raw, LitorjiaRepository repo) {
        return parse(raw, repo == null ? null : id -> repo.templateTree(id) != null);
    }

    /** Variante sans base, utilisée par les tests aller-retour. */
    public static Report parse(String raw, TemplateLookup lookup) {
        Report r = new Report();
        if (raw == null || raw.trim().isEmpty()) {
            r.error = "Foana ny JSON — apetaho ny valin'ny AI.";
            return r;
        }
        if (raw.length() > MAX_SIZE) {
            r.error = "Lehibe loatra ny fichier (> 1 Mo).";
            return r;
        }
        JSONObject root;
        try {
            // Tolerance : l'IA renvoie parfois du texte autour du JSON
            String cleaned = extractJsonObject(raw.trim());
            root = new JSONObject(cleaned);
        } catch (Exception e) {
            r.error = "JSON tsy mety — adikao indray ny valin'ny AI "
                    + "(tsy misy soratra aloha na aoriana).";
            return r;
        }
        String schema = root.optString("schema", "");
        if (!SCHEMA.equals(schema)) {
            r.error = "Schéma tsy fantatra : \"" + schema + "\" (tokony \"" + SCHEMA + "\"). "
                    + "Angataho amin'ny AI ny schema " + SCHEMA + ".";
            return r;
        }

        r.title = trimmed(root.optString("title", ""));
        r.date = trimmed(root.optString("date", ""));
        if (r.date != null && !validDate(r.date)) r.date = null;
        r.template = trimmed(root.optString("template", ""));
        if (r.template != null && lookup != null && !lookup.exists(r.template)) r.template = null;
        r.notes = trimmed(root.optString("notes", ""));
        r.fallbackTitle = r.title != null && !r.title.isEmpty() ? r.title : "Fivoriana";

        ProgramNode rootN = new ProgramNode();
        rootN.id = UUID.randomUUID().toString();
        rootN.type = ProgramNode.TYPE_SECTION;
        rootN.title = r.fallbackTitle;
        rootN.origin = ProgramNode.ORIGIN_IMPORT;

        JSONArray nodes = root.optJSONArray("nodes");
        int[] counter = {0};
        if (nodes != null) {
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject on = nodes.optJSONObject(i);
                if (on == null) { r.warnings.add(new Warning("nodes[" + i + "]", "Andalan-tsoratra tsy hita — nafoy")); continue; }
                ProgramNode n = readNode(on, "", r, counter, 1);
                if (n != null) {
                    n.parentId = rootN.id;
                    n.position = rootN.children.size();
                    rootN.children.add(n);
                }
            }
        }
        r.root = rootN;
        r.ok = true;
        return r;
    }

    /** Lit un noeud JSON (avec profondeur limitée et rapport). */
    private static ProgramNode readNode(JSONObject o, String path, Report report,
                                        int[] counter, int depth) {
        if (depth > MAX_DEPTH) {
            report.warnings.add(new Warning(path.isEmpty() ? "?" : path,
                    "Halalina > " + MAX_DEPTH + " — nafoy"));
            return null;
        }
        String self = path.isEmpty() ? "" : path + " / ";
        ProgramNode n = new ProgramNode();
        n.id = UUID.randomUUID().toString();
        n.type = o.optString("type", ProgramNode.TYPE_CUSTOM);
        if (!ProgramNode.ALL_TYPES.contains(n.type)) {
            report.warnings.add(new Warning(self + n.type,
                    "Karazana tsy fantatra → niova ho \"hafa\""));
            n.type = ProgramNode.TYPE_CUSTOM;
        }
        n.title = trimmed(o.optString("title", ""));
        n.origin = ProgramNode.ORIGIN_IMPORT;
        counter[0]++;

        // payload selon le type
        switch (n.type) {
            case ProgramNode.TYPE_HYMN: {
                JSONObject h = o.optJSONObject("hymn");
                if (h != null) {
                    String book = trimmed(h.optString("book", ""));
                    if ("SAFIF".equalsIgnoreCase(book)) book = "FIF";
                    Integer number = h.has("number") && !h.isNull("number")
                            ? h.optInt("number") : null;
                    List<Integer> stanzas = new ArrayList<>();
                    JSONArray sta = h.optJSONArray("stanzas");
                    if (sta != null) {
                        for (int i = 0; i < sta.length(); i++) {
                            try { stanzas.add(sta.getInt(i)); } catch (Exception ignored) {}
                        }
                    }
                    String label = trimmed(h.optString("label", ""));
                    n.payload = NodePayloadParser.hymnPayload(book, number, stanzas, label);
                } else {
                    n.payload = NodePayloadParser.hymnPayload(null, null, null, null);
                    report.warnings.add(new Warning(self + "hymn",
                            "Hira tsy feno (tsy misy laharana) — \"hamenoana\""));
                }
                break;
            }
            case ProgramNode.TYPE_BIBLE: {
                List<ProgramNode.BibleRef> refs = new ArrayList<>();
                JSONArray arr = o.optJSONArray("bible");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject ro = arr.optJSONObject(i);
                        if (ro == null) continue;
                        String book = trimmed(ro.optString("book", ""));
                        ProgramNode.BibleRef br = new ProgramNode.BibleRef();
                        br.book = book;
                        br.bookNumber = BibleRefParserLite.resolve(book);
                        br.chapter = ro.has("chapter") && !ro.isNull("chapter")
                                ? String.valueOf(ro.optInt("chapter")) : null;
                        JSONArray ranges = ro.optJSONArray("ranges");
                        if (ranges != null && ranges.length() > 0) {
                            for (int k = 0; k < ranges.length(); k++) {
                                JSONObject rg = ranges.optJSONObject(k);
                                if (rg == null) continue;
                                ProgramNode.BibleRef copy = cloneRef(br);
                                copy.from = trimmed(rg.optString("from"));
                                copy.to = rg.has("to") && !rg.isNull("to")
                                        ? trimmed(rg.optString("to")) : null;
                                refs.add(copy);
                            }
                        } else {
                            refs.add(br);
                        }
                    }
                }
                if (refs.isEmpty()) {
                    report.warnings.add(new Warning(self + "bible",
                            "Vakiteny tsy misy — \"hamenoana\""));
                } else if (firstRefBad(refs)) {
                    report.warnings.add(new Warning(self + "bible",
                            "Boky tsy fantatra : " + refs.get(0).book + " — \"hamenoana\""));
                }
                n.payload = NodePayloadParser.biblePayload(refs);
                break;
            }
            case ProgramNode.TYPE_PRAYER: {
                String kind = trimmed(o.optString("prayerKind", ""));
                if (kind != null && !kind.isEmpty() && !isKnownKind(kind)) {
                    report.warnings.add(new Warning(self + "prayerKind",
                            "Karazana vavaka tsy fantatra → \"vavaka\""));
                    kind = null;
                }
                if (kind == null || kind.isEmpty()) {
                    String t = n.title;
                    String guessed = guessKind(t);
                    kind = guessed;
                }
                n.payload = NodePayloadParser.prayerPayload(kind, trimmed(o.optString("note", "")));
                break;
            }
            case ProgramNode.TYPE_CREED: {
                JSONObject c = o.optJSONObject("creed");
                Integer version = c != null && c.has("version") && !c.isNull("version")
                        ? c.optInt("version") : null;
                if (version != null && (version < 1 || version > 4)) version = null;
                n.payload = NodePayloadParser.creedPayload(version);
                break;
            }
            case ProgramNode.TYPE_CHOIR:
            case ProgramNode.TYPE_TEXT:
                n.payload = NodePayloadParser.bodyPayload(trimmed(o.optString("body", "")));
                break;
            case ProgramNode.TYPE_OFFERING:
                n.payload = NodePayloadParser.bodyPayload(trimmed(o.optString("body", "")));
                break;
            default:
                n.payload = NodePayloadParser.bodyPayload(trimmed(o.optString("body", "")));
                break;
        }

        JSONArray children = o.optJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                JSONObject co = children.optJSONObject(i);
                String cpath = self + "children[" + i + "]";
                if (co == null) { report.warnings.add(new Warning(cpath, "Andalan-tsoratra tsy hita — nafoy")); continue; }
                ProgramNode c = readNode(co, cpath, report, counter, depth + 1);
                if (c != null) {
                    c.parentId = n.id;
                    c.position = n.children.size();
                    n.children.add(c);
                }
            }
        }
        return n;
    }

    private static boolean firstRefBad(List<ProgramNode.BibleRef> refs) {
        for (ProgramNode.BibleRef r : refs) {
            if (r.bookNumber == null) return true;
        }
        return false;
    }

    private static ProgramNode.BibleRef cloneRef(ProgramNode.BibleRef src) {
        ProgramNode.BibleRef r = new ProgramNode.BibleRef();
        r.book = src.book;
        r.bookNumber = src.bookNumber;
        r.chapter = src.chapter;
        return r;
    }

    private static boolean isKnownKind(String k) {
        String[] known = NodePayloadParser.prayerKinds();
        for (String s : known) if (s.equals(k)) return true;
        return "other".equals(k) || "".equals(k);
    }

    /** Déduit le kind de prière du titre (fisaorana, fangatahana…). */
    private static String guessKind(String title) {
        if (title == null) return null;
        String t = title.toLowerCase();
        if (t.contains("fisaorana")) return "fisaorana";
        if (t.contains("fangatahana")) return "fangatahana";
        if (t.contains("fianken-keloka") || t.contains("fanelan")) return "keloka";
        if (t.contains("fiderana")) return "fiderana";
        if (t.contains("hira")) return "hira";
        return null;
    }

    private static String trimmed(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean validDate(String d) {
        return d != null && d.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    /**
     * Extrait le premier objet JSON "{...}" équilibré d'une chaîne —
     * tolère les blocs de code (```json ... ```) et le blabla autour.
     */
    static String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        if (start < 0) throw new IllegalArgumentException("no object");
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return raw.substring(start, i + 1);
            }
        }
        throw new IllegalArgumentException("unbalanced");
    }

    /** Petit resolver local sans dépendance Android (utilise le parser partagé). */
    private static final class BibleRefParserLite {
        static Integer resolve(String book) {
            return com.fa.baiboly.data.parser.BibleRefParser.resolveBookNumber(book);
        }
    }
}
