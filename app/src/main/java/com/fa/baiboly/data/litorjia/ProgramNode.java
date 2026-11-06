package com.fa.baiboly.data.litorjia;

import com.fa.baiboly.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un noeud unique de l'arbre du programme de culte — le seul concept du
 * modèle de données : le type, un titre optionnel, des enfants et une
 * charge utile (payload) JSON dépendant du type (§5.1–5.2 du doc technique).
 *
 * <p>Types : section, title, hymn, bible, prayer, creed, choir, text,
 * offering, custom (type inconnu à l'import -> custom).</p>
 */
public class ProgramNode {

    // =========================
    // Types de noeuds
    // =========================
    public static final String TYPE_SECTION  = "section";
    public static final String TYPE_TITLE    = "title";
    public static final String TYPE_HYMN     = "hymn";
    public static final String TYPE_BIBLE    = "bible";
    public static final String TYPE_PRAYER   = "prayer";
    public static final String TYPE_CREED    = "creed";
    public static final String TYPE_CHOIR    = "choir";
    public static final String TYPE_TEXT     = "text";
    public static final String TYPE_OFFERING = "offering";
    public static final String TYPE_CUSTOM   = "custom";

    public static final List<String> ALL_TYPES = Collections.unmodifiableList(Arrays.asList(
            TYPE_SECTION, TYPE_TITLE, TYPE_HYMN, TYPE_BIBLE, TYPE_PRAYER,
            TYPE_CREED, TYPE_CHOIR, TYPE_TEXT, TYPE_OFFERING, TYPE_CUSTOM));

    // =========================
    // Origine (où vient l'élément)
    // =========================
    public static final String ORIGIN_TEMPLATE = "template";
    public static final String ORIGIN_CALENDAR = "calendar";
    public static final String ORIGIN_MANUAL   = "manual";
    public static final String ORIGIN_IMPORT   = "import";

    // =========================
    // Rôles pour le générateur (retrouver un noeud sans dépendre du titre)
    // =========================
    public static final String ROLE_READINGS = "readings";
    public static final String ROLE_MAIN     = "main";

    public String id;
    public String parentId;
    public int position;
    public String type = TYPE_CUSTOM;

    /** Titre modifiable : n'importe quel noeud peut porter un titre. */
    public String title;

    /** Payload JSON selon le type (voir NodePayloadBuilder / getters ci-dessous). */
    public String payload;

    public boolean hidden;
    /** template | calendar | manual | import */
    public String origin = ORIGIN_MANUAL;

    /** Rôle du noeud de gabarit ("readings"…), non persistant ? si : stocké payload→role. */
    public String role;

    /** Enfants en mémoire (ordre = position). Non persistant dans la ligne elle-même. */
    public List<ProgramNode> children = new ArrayList<>();

    /** Expand/collapse (état mémoire de la session, replié par défaut pour sections vides non…). */
    public boolean expanded = true;

    // =========================
    // Payload : hymn
    // =========================

    public String hymnBook()   { return NodeJson.str(payload, "book", ""); }
    public Integer hymnNumber() {
        String v = NodeJson.str(payload, "number", "");
        if (v == null || v.trim().isEmpty()) return null;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return null; }
    }
    public List<Integer> hymnStanzas() {
        List<Integer> out = new ArrayList<>();
        String raw = NodeJson.rawArray2(payload, "stanzas");
        if (raw == null) return out;
        for (String part : raw.split(",")) {
            try { out.add(Integer.parseInt(part.trim())); } catch (NumberFormatException ignored) {}
        }
        return out;
    }
    public String hymnLabel()  { return NodeJson.str(payload, "label", ""); }

    // =========================
    // Payload : bible
    // =========================

    public static class BibleRef {
        /** Nom de livre normalisé (malgache long si connu). */
        public String book;
        /** Numéro canonique 1..66, ou null si non résolu ("à compléter"). */
        public Integer bookNumber;
        public String chapter;         // chaîne tolérante ("33")
        public String from;            // "1", "13b"
        public String to;              // nullable = verset unique
        public String label;           // titre optionnel de la lecture

        public BibleRef() {}
        public BibleRef(String book, Integer bookNumber, String chapter, String from, String to) {
            this.book = book; this.bookNumber = bookNumber;
            this.chapter = chapter; this.from = from; this.to = to;
        }

        /** "Salamo 138:1-6" ; "Apo 21:5" ; "Lio 39b,42". */
        public String display() {
            StringBuilder sb = new StringBuilder();
            sb.append(book == null ? "" : book);
            if (chapter != null && !chapter.isEmpty()) {
                sb.append(' ').append(chapter);
                if (from != null && !from.isEmpty()) {
                    sb.append(':').append(from);
                    if (to != null && !to.isEmpty() && !to.equals(from)) sb.append('-').append(to);
                }
            }
            return sb.toString();
        }
    }

    public List<BibleRef> bibleRefs() { return NodePayloadParser.bibleRefs(payload); }

    // =========================
    // Payload : prayer / creed / text / choir / offering
    // =========================
    public String prayerKind()  { return NodeJson.str(payload, "kind", ""); }
    public String note()        { return NodeJson.str(payload, "note", ""); }
    public Integer creedVersion() {
        String v = NodeJson.str(payload, "version", "");
        if (v == null || v.trim().isEmpty()) return null;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return null; }
    }
    public String body()        { return NodeJson.str(payload, "body", ""); }

    // =========================
    // Divers
    // =========================

    /** Récupère le rôle stocké dans le payload du gabarit. */
    public String templateRole() { return NodeJson.str(payload, "role", null); }

    /** Conteneur : accepte des enfants. */
    public boolean isContainer() {
        return TYPE_SECTION.equals(type);
    }

    /** Résumé affichable (deuxième ligne d'une ligne d'arbre). */
    public String summary() {
        switch (type) {
            case TYPE_HYMN: {
                Integer n = hymnNumber();
                String line = "";
                if (n == null) return "hamenoana"; // à compléter
                line = hymnBook() + " " + n;
                List<Integer> st = hymnStanzas();
                if (!st.isEmpty()) {
                    StringBuilder sb = new StringBuilder(" · ");
                    for (int i = 0; i < st.size(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append(st.get(i));
                    }
                    line += sb;
                }
                return line;
            }
            case TYPE_BIBLE: {
                List<BibleRef> refs = bibleRefs();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < refs.size(); i++) {
                    if (i > 0) sb.append(" · ");
                    sb.append(refs.get(i).display());
                }
                return sb.toString();
            }
            case TYPE_CREED:
                return "Fanekem-pinoana" + (creedVersion() != null ? " v" + creedVersion() : "");
            case TYPE_PRAYER: return NodePayloadParser.prayerKindLabel(prayerKind());
            case TYPE_CHOIR: return body().isEmpty() ? "Hira safidy" : body();
            case TYPE_TEXT: {
                String b = body();
                return b.isEmpty() ? "" : (b.length() > 60 ? b.substring(0, 60) + "…" : b);
            }
            default: return "";
        }
    }

    /** Ligne "incomplète" ? */
    public boolean isIncomplete() {
        switch (type) {
            case TYPE_HYMN: return hymnNumber() == null;
            case TYPE_BIBLE: {
                List<BibleRef> refs = bibleRefs();
                if (refs.isEmpty()) return true;
                for (BibleRef r : refs) {
                    if (r.book == null || r.book.trim().isEmpty()) return true;
                    Integer bn = r.bookNumber != null ? r.bookNumber
                            : com.fa.baiboly.data.parser.BibleRefParser.resolveBookNumber(r.book);
                    if (bn == null) return true;
                    if (r.chapter == null || r.chapter.trim().isEmpty()) return true;
                }
                return false;
            }
            case TYPE_TITLE: return title == null || title.trim().isEmpty();
            case TYPE_CHOIR: return body().isEmpty();
            default: return false;
        }
    }

    /** Libellé par défaut du type quand le titre est vide. */
    public String defaultTitle() {
        switch (type) {
            case TYPE_SECTION:  return "Andinin-t sampana";
            case TYPE_TITLE:    return "Lohateny";
            case TYPE_HYMN:     return "Hira";
            case TYPE_BIBLE:    return "Vakiteny";
            case TYPE_PRAYER:   return "Vavaka";
            case TYPE_CREED:    return "Fanekem-pinoana";
            case TYPE_CHOIR:    return "Antsan'ny Salema";
            case TYPE_TEXT:     return "Lahatsoratra";
            case TYPE_OFFERING: return "Rakitra";
            default:            return "Hafa";
        }
    }

    /** Titre affichable : titre propre, sinon défaut. */
    public String displayTitle() {
        if (title != null && !title.trim().isEmpty()) return title.trim();
        return defaultTitle();
    }

    /** Icône d'après le type (drawable). */
    public int iconRes() {
        switch (type) {
            case TYPE_SECTION:  return R.drawable.lit_ic_folder;
            case TYPE_TITLE:    return R.drawable.lit_ic_title;
            case TYPE_HYMN:     return R.drawable.lit_ic_hymn;
            case TYPE_BIBLE:    return R.drawable.lit_ic_bible;
            case TYPE_PRAYER:   return R.drawable.lit_ic_prayer;
            case TYPE_CREED:    return R.drawable.lit_ic_creed;
            case TYPE_CHOIR:    return R.drawable.lit_ic_hymn;
            case TYPE_TEXT:     return R.drawable.lit_ic_text;
            case TYPE_OFFERING: return R.drawable.lit_ic_offering;
            default:            return R.drawable.lit_ic_custom;
        }
    }

    /**
     * Charge utilitaire par défaut d'un type nouveau — construite via
     * {@link NodePayloadParser#buildPayload} à l'édition.
     */
    public void ensurePayload() {
        if (payload == null) payload = "";
    }

    /** Petite classe utilitaire JSON (sans dépendance). */
    static final class NodeJson {
        static String str(String json, String key, String def) {
            if (json == null) return def;
            // 1) nombre ou mot-clé : "number":12, "bookNumber":3, version:null
            Matcher num = Pattern.compile("\"" + key + "\"\\s*:\\s*([\\d\\-]+|null|true|false)")
                    .matcher(json);
            if (num.find()) {
                String raw = num.group(1);
                return "null".equals(raw) || "false".equals(raw) ? (raw.equalsIgnoreCase("null") ? null : raw) : raw;
            }
            Matcher m = NodePayloadParser.STRING_FIELD.matcher(json);
            while (m.find()) {
                if (key.equals(m.group(1))) {
                    String raw = m.group(2) == null ? m.group(3) : m.group(2);
                    if (raw == null) return null;
                    return raw.replace("\\\"", "\"").replace("\\\\", "\\");
                }
            }
            return def;
        }
        static String rawArray2(String json, String key) {
            if (json == null) return null;
            Matcher m = NodePayloadParser.ARRAY_FIELD.matcher(json);
            while (m.find()) {
                if (key.equals(m.group(1))) return m.group(2);
            }
            return null;
        }
    }
}
