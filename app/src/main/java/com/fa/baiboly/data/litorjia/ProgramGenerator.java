package com.fa.baiboly.data.litorjia;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fa.baiboly.data.parser.BibleRefParser;
import com.fa.baiboly.data.perikopa.PerikopaService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Génération automatique (§4.2, §6.3) : instancie l'arbre du gabarit,
 * remplit le noeud « Vakiteny » (role = readings) avec les lectures du
 * perikopa de la date (origin = calendar), applique la version du credo
 * du gabarit. Ne doit jamais échouer faute de données : sans perikopa,
 * le noeud Vakiteny reste vide.
 */
public class ProgramGenerator {

    private static final String TAG = "LitorjiaGen";
    private final LitorjiaRepository repo;
    private final PerikopaService perikopa;

    public ProgramGenerator(Context context) {
        repo = new LitorjiaRepository(context);
        perikopa = new PerikopaService(context);
    }

    /**
     * Génère un programme complet. Renvoie {@code null} si le gabarit
     * n'existe pas ou si son arbre est vide : l'appelant affiche alors
     * un message (jamais un programme silencieusement vide).
     */
    public Program generate(Context context, String date, String templateId, String title) {
        String treeJson = repo.templateTree(templateId);
        if (treeJson == null || treeJson.trim().isEmpty()) {
            Log.w(TAG, "Gabarit inconnu ou vide : " + templateId);
            return null;
        }

        Program p = new Program();
        p.id = UUID.randomUUID().toString();
        p.title = title == null || title.trim().isEmpty() ? "Fivoriana" : title.trim();
        p.serviceDate = date;
        p.templateId = templateId;
        p.createdAt = System.currentTimeMillis();
        p.updatedAt = p.createdAt;

        // 1) Instanciation de l'arbre du gabarit (origin = template)
        ProgramNode root = instantiate(templateId, treeJson, p.title);
        if (root == null || root.children.isEmpty()) {
            Log.w(TAG, "Arbre de gabarit vide : " + templateId);
            return null;
        }

        // 2) Perikopa : les lectures du jour dans le noeud role = readings
        fillPerikopa(context, date, root);

        // 3) Textes fixes selon le gabarit (credo déjà versionné à l'expansion)
        applyCreedVersions(root, TemplateCatalog.defaultCreedVersion(templateId));

        p.root = root;
        repo.insertProgram(p);
        repo.saveTree(p, root);
        return p;
    }

    /**
     * Construit l'arbre en mémoire à partir du JSON d'un gabarit, sans
     * accès à la base. Les parentId sont recalculés après coup : c'est ce
     * qui garantit une arborescence persistable en un seul arbre (le
     * bug « 0 elements » venait de noeuds de premier niveau écrits avec
     * parent_id = NULL).
     */
    public static ProgramNode instantiate(String templateId, String treeJson, String title) {
        if (treeJson == null) return null;
        ProgramNode root = new ProgramNode();
        root.id = UUID.randomUUID().toString();
        root.type = ProgramNode.TYPE_SECTION;
        root.title = title; // la racine n'est pas affichée comme ligne
        root.origin = ProgramNode.ORIGIN_TEMPLATE;
        try {
            JSONArray arr = new JSONArray(treeJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                ProgramNode child = expand(o, templateId);
                if (child != null) {
                    child.position = root.children.size();
                    root.children.add(child);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Arbre de gabarit illisible " + templateId, e);
            return null;
        }
        assignParents(root, null);
        return root;
    }

    /** Recalcule id/parentId/position de tout le sous-arbre. */
    private static void assignParents(ProgramNode node, String parentId) {
        if (node.id == null || node.id.isEmpty()) node.id = UUID.randomUUID().toString();
        node.parentId = parentId;
        for (int i = 0; i < node.children.size(); i++) {
            ProgramNode child = node.children.get(i);
            child.position = i;
            assignParents(child, node.id);
        }
    }

    // =========================================================
    // Expansion de l'arbre du gabarit
    // =========================================================

    private static ProgramNode expand(JSONObject o, String templateId) {
        if (o == null) return null;
        ProgramNode n = new ProgramNode();
        n.id = UUID.randomUUID().toString();
        n.type = o.optString("type", ProgramNode.TYPE_CUSTOM);
        if (!ProgramNode.ALL_TYPES.contains(n.type)) n.type = ProgramNode.TYPE_CUSTOM;
        n.title = o.optString("title", null);
        String payload = o.optString("payload", "");
        n.role = extractRole(payload);
        n.payload = payload;
        n.origin = ProgramNode.ORIGIN_TEMPLATE;
        // Le rôle est stocké dans la colonne dédiée, retiré du payload
        if (n.role != null && payload != null) n.payload = stripRole(payload);

        if (ProgramNode.TYPE_BIBLE.equals(n.type) && payload != null) {
            for (ProgramNode.BibleRef r : NodePayloadParser.bibleRefs(payload)) {
                if (r.bookNumber == null) {
                    Integer bn = com.fa.baiboly.data.parser.BibleRefParser.resolveBookNumber(r.book);
                    if (bn != null) r.bookNumber = bn;
                }
            }
            n.payload = NodePayloadParser.biblePayload(NodePayloadParser.bibleRefs(payload));
        }

        JSONArray children = o.optJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                JSONObject co = children.optJSONObject(i);
                if (co == null) continue;
                ProgramNode c = expand(co, templateId);
                if (c == null) continue;
                c.parentId = n.id;
                c.position = n.children.size();
                n.children.add(c);
            }
        }
        return n;
    }

    private static String extractRole(String payload) {
        if (payload == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"role\"\\s*:\\s*\"([^\"]+)\"").matcher(payload);
        return m.find() ? m.group(1) : null;
    }

    private static String stripRole(String payload) {
        try {
            JSONObject o = new JSONObject(payload);
            o.remove("role");
            return o.toString();
        } catch (Exception e) {
            return payload;
        }
    }

    // =========================================================
    // Perikopa : remplissage des Vakiteny (origin = calendar)
    // =========================================================

    private void fillPerikopa(Context context, String date, ProgramNode root) {
        ProgramNode readings = findRole(root, ProgramNode.ROLE_READINGS);
        if (readings == null) return;

        com.fa.baiboly.models.PerikopaDay day = perikopa.getPerikopaByDate(date);
        if (day == null || day.getVerses() == null || day.getVerses().isEmpty()) return;

        ReferenceResolver resolver = new ReferenceResolver(context);

        readings.children.clear();
        int pos = 0;
        for (String verseStr : day.getVerses()) {
            if (verseStr == null || verseStr.trim().isEmpty()) continue;
            for (ProgramNode.BibleRef r : parseReading(verseStr, resolver)) {
                ProgramNode b = new ProgramNode();
                b.id = UUID.randomUUID().toString();
                b.parentId = readings.id;
                b.position = pos++;
                b.type = ProgramNode.TYPE_BIBLE;
                b.title = r.label;
                b.payload = NodePayloadParser.biblePayload(single(r));
                b.origin = ProgramNode.ORIGIN_CALENDAR;
                readings.children.add(b);
            }
        }
    }

    /** Découpe une string de perikopa ("Salamo 138:1-6", "Apo 21.5") en refs. */
    static List<ProgramNode.BibleRef> parseReading(String raw, ReferenceResolver resolver) {
        List<ProgramNode.BibleRef> out = new ArrayList<>();
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return out;

        // Cas "Lio 2:39b,42" (liste de versets discrets) et formes générales
        BibleRefParser.ParsedRef ref = BibleRefParser.parse(s);
        if (ref == null) {
            // Non reconnu : ref conservée brute, "à compléter"
            ProgramNode.BibleRef r = new ProgramNode.BibleRef();
            r.book = s;
            out.add(r);
            return out;
        }

        String label = raw.trim();

        if (ref.discreteVerses != null && !ref.discreteVerses.isEmpty()) {
            for (int i = 0; i < ref.discreteVerses.size(); i++) {
                ProgramNode.BibleRef r = new ProgramNode.BibleRef();
                r.book = BibleRefParser.shortNameOf(ref.bookNumber);
                r.bookNumber = ref.bookNumber;
                r.chapter = String.valueOf(ref.startChapter);
                r.from = String.valueOf(ref.discreteVerses.get(i)[0]);
                String letter = ref.discreteLetters != null && i < ref.discreteLetters.size()
                        ? ref.discreteLetters.get(i) : null;
                if (letter != null) r.from += letter;
                r.label = label;
                out.add(r);
            }
            return out;
        }

        ProgramNode.BibleRef r = new ProgramNode.BibleRef();
        r.book = BibleRefParser.shortNameOf(ref.bookNumber);
        r.bookNumber = ref.bookNumber;
        r.chapter = String.valueOf(ref.startChapter);
        if (ref.endChapter != ref.startChapter) {
            r.from = ref.startChapter + "." + ref.startVerse;
            r.to = ref.endChapter + "." + ref.endVerse;
        } else {
            r.from = ref.endVerse >= 999 ? null : String.valueOf(ref.startVerse);
            r.to = ref.endVerse >= 999 || ref.endVerse == ref.startVerse
                    ? null : String.valueOf(ref.endVerse);
        }
        r.label = label;
        out.add(r);
        return out;
    }

    private static List<ProgramNode.BibleRef> single(ProgramNode.BibleRef r) {
        List<ProgramNode.BibleRef> out = new ArrayList<>();
        out.add(r);
        return out;
    }

    /** Version du credo du gabarit (1 à 4, §14.6). */
    private void applyCreedVersions(ProgramNode root, int version) {
        for (ProgramNode n : collect(root, new ArrayList<>())) {
            if (ProgramNode.TYPE_CREED.equals(n.type)) {
                Integer v = n.creedVersion();
                if (v == null || v < 1 || v > 4) {
                    n.payload = NodePayloadParser.creedPayload(version);
                }
            }
        }
    }

    private List<ProgramNode> collect(ProgramNode n, List<ProgramNode> out) {
        for (ProgramNode c : n.children) {
            out.add(c);
            collect(c, out);
        }
        return out;
    }

    private ProgramNode findRole(ProgramNode root, String role) {
        for (ProgramNode n : root.children) {
            if (role.equals(n.role)) return n;
            ProgramNode deep = findRole(n, role);
            if (deep != null) return deep;
        }
        return null;
    }

}
