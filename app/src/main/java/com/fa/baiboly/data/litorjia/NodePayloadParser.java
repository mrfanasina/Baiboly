package com.fa.baiboly.data.litorjia;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sérialisation / lecture du payload JSON de chaque type de noeud, et
 * helpers de libellés (kinds de prières…). Sans Gson : org.json suffit
 * (payloads petits et plats).
 */
public final class NodePayloadParser {

    private NodePayloadParser() {}

    static final Pattern STRING_FIELD = Pattern.compile(
            "\"([\\w]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"|\"([\\w]+)\"\\s*:\\s*(null|true|false)");
    static final Pattern ARRAY_FIELD = Pattern.compile("\"([\\w]+)\"\\s*:\\s*\\[([^\\[\\]]*)\\]");

    // =========================
    // Construction des payloads
    // =========================

    /** hymn : {book, number, stanzas[], label} */
    public static String hymnPayload(String book, Integer number, List<Integer> stanzas, String label) {
        JSONObject o = new JSONObject();
        try {
            if (book != null) o.put("book", book);
            if (number != null) o.put("number", number);
            JSONArray st = new JSONArray();
            if (stanzas != null) for (Integer s : stanzas) st.put(s);
            o.put("stanzas", st);
            if (label != null && !label.isEmpty()) o.put("label", label);
        } catch (Exception ignored) {}
        return o.toString();
    }

    /** bible : {refs:[{book, bookNumber, chapter, from, to, label}], origin?} */
    public static String biblePayload(List<ProgramNode.BibleRef> refs) {
        JSONObject o = new JSONObject();
        try {
            JSONArray arr = new JSONArray();
            if (refs != null) {
                for (ProgramNode.BibleRef r : refs) {
                    JSONObject ro = new JSONObject();
                    if (r.book != null) ro.put("book", r.book);
                    if (r.bookNumber != null) ro.put("bookNumber", r.bookNumber);
                    if (r.chapter != null && !r.chapter.isEmpty()) ro.put("chapter", r.chapter);
                    if (r.from != null && !r.from.isEmpty()) ro.put("from", r.from);
                    if (r.to != null && !r.to.isEmpty()) ro.put("to", r.to);
                    if (r.label != null && !r.label.isEmpty()) ro.put("label", r.label);
                    arr.put(ro);
                }
            }
            o.put("refs", arr);
        } catch (Exception ignored) {}
        return o.toString();
    }

    /** prayer : {kind, note} */
    public static String prayerPayload(String kind, String note) {
        JSONObject o = new JSONObject();
        try {
            if (kind != null && !kind.isEmpty()) o.put("kind", kind);
            if (note != null && !note.isEmpty()) o.put("note", note);
        } catch (Exception ignored) {}
        return o.toString();
    }

    /** creed : {version} */
    public static String creedPayload(Integer version) {
        JSONObject o = new JSONObject();
        try {
            if (version != null) o.put("version", version);
        } catch (Exception ignored) {}
        return o.toString();
    }

    /** text / choir : {body} ; offering : {note} */
    public static String bodyPayload(String body) {
        JSONObject o = new JSONObject();
        try {
            if (body != null && !body.isEmpty()) o.put("body", body);
        } catch (Exception ignored) {}
        return o.toString();
    }

    /** notePayload pour offering/custom/prayer note only. */
    public static String notePayload(String note) { return prayerPayload(null, note); }

    /** payload de gabarit : {role} */
    public static String rolePayload(String role) {
        JSONObject o = new JSONObject();
        try {
            if (role != null) o.put("role", role);
        } catch (Exception ignored) {}
        return o.toString();
    }

    // =========================
    // Lecture : bible refs
    // =========================

    /** Lit le tableau "refs" du payload bible (tolérant aux payloads importés). */
    public static List<ProgramNode.BibleRef> bibleRefs(String payload) {
        List<ProgramNode.BibleRef> out = new ArrayList<>();
        if (payload == null || payload.isEmpty()) return out;
        try {
            JSONObject o = new JSONObject(payload);
            if (!o.has("refs") || o.isNull("refs")) return out;
            JSONArray arr = o.getJSONArray("refs");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject ro = arr.optJSONObject(i);
                if (ro == null) continue;
                ProgramNode.BibleRef r = new ProgramNode.BibleRef();
                r.book = ro.has("book") ? ro.optString("book") : ro.optString("bookName");
                r.bookNumber = ro.has("bookNumber") && !ro.isNull("bookNumber")
                        ? (int) ro.optDouble("bookNumber") : null;
                r.chapter = ro.has("chapter") && !ro.isNull("chapter") ? ro.optString("chapter") : null;
                r.from = ro.has("from") && !ro.isNull("from") ? ro.optString("from") : null;
                r.to = ro.has("to") && !ro.isNull("to") ? ro.optString("to") : null;
                r.label = ro.has("label") && !ro.isNull("label") ? ro.optString("label") : null;
                out.add(r);
            }
        } catch (Exception ignored) {}
        return out;
    }

    // =========================
    // Libellés des kinds de prières
    // =========================

    public static String prayerKindLabel(String kind) {
        if (kind == null) return "Vavaka";
        switch (kind) {
            case "fiderana":    return "Vavaka fiderana";
            case "fisaorana":   return "Vavaka fisaorana";
            case "fangatahana": return "Vavaka fangatahana";
            case "keloka":      return "Vavaka fianken-keloka";
            case "hira":        return "Vavaka hira";
            default:            return "Vavaka";
        }
    }

    /** Kinds extensibles proposés à l'édition. */
    public static String[] prayerKinds() {
        return new String[]{"fiderana", "fisaorana", "fangatahana", "keloka", "hira"};
    }
}
