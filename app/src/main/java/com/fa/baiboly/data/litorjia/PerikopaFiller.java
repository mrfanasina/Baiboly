package com.fa.baiboly.data.litorjia;

import android.content.Context;
import android.util.Log;

import com.fa.baiboly.data.perikopa.PerikopaService;
import com.fa.baiboly.models.PerikopaDay;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Remplissage du noeud « Vakiteny » (role = readings) avec les lectures
 * du perikopa d'une date. Utilisé par la génération et par le bouton
 * « Rétablir le perikopa du jour » de l'édition (§4.3).
 */
public final class PerikopaFiller {

    private static final String TAG = "PerikopaFiller";

    private PerikopaFiller() {}

    /** Expose l'analyse d'une chaîne de perikopa en références. */
    public static List<ProgramNode.BibleRef> parse(String raw, ReferenceResolver resolver) {
        return ProgramGenerator.parseReading(raw, resolver);
    }

    /** Renvoie les refs de la date, liste vide si pas de perikopa. */
    public static List<ProgramNode.BibleRef> perikopaRefs(Context ctx, String date) {
        List<ProgramNode.BibleRef> refs = new ArrayList<>();
        PerikopaService svc = new PerikopaService(ctx);
        PerikopaDay day = svc.getPerikopaByDate(date);
        if (day == null || day.getVerses() == null) return refs;
        ReferenceResolver resolver = new ReferenceResolver(ctx);
        for (String v : day.getVerses()) {
            if (v == null || v.trim().isEmpty()) continue;
            refs.addAll(parse(v, resolver));
        }
        return refs;
    }

    /** Nom du jour de perikopa ("Alahadin'ny …"), null si aucun. */
    public static String perikopaDayName(Context ctx, String date) {
        PerikopaService svc = new PerikopaService(ctx);
        PerikopaDay day = svc.getPerikopaByDate(date);
        if (day == null) return null;
        String name = day.getName();
        return name == null || name.isEmpty() || name.startsWith("Tsy misy") ? null : name;
    }

    private static List<ProgramNode.BibleRef> normalize(
            List<ProgramNode.BibleRef> parsed, ReferenceResolver resolver) {
        List<ProgramNode.BibleRef> out = new ArrayList<>();
        if (parsed == null) return out;
        for (ProgramNode.BibleRef r : parsed) {
            if (r.bookNumber == null) {
                Integer bn = com.fa.baiboly.data.parser.BibleRefParser
                        .resolveBookNumber(r.book);
                if (bn != null) r.bookNumber = bn;
            }
            out.add(r);
        }
        return out;
    }

    /** Interface de callback du remplissage asynchrone léger. */
    public interface Done {
        void done();
    }

    /** Replaces the children of the node with role == readings. */
    public static void fillReadings(ProgramNode root, Context ctx, Program program,
                                    Done done) {
        ProgramNode readings = findRole(root, ProgramNode.ROLE_READINGS);
        if (readings == null) {
            readings = findRoleInChildren(root, ProgramNode.ROLE_READINGS);
        }
        if (readings == null) {
            Log.w(TAG, "Aucun noeud readings dans le programme");
            if (done != null) done.done();
            return;
        }
        List<ProgramNode.BibleRef> refs =
                perikopaRefs(ctx, program.serviceDate);
        readings.children.clear();
        int pos = 0;
        for (ProgramNode.BibleRef r : refs) {
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
        if (done != null) done.done();
    }

    private static List<ProgramNode.BibleRef> single(ProgramNode.BibleRef r) {
        List<ProgramNode.BibleRef> out = new ArrayList<>();
        out.add(r);
        return out;
    }

    private static ProgramNode findRole(ProgramNode root, String role) {
        if (role.equals(root.role)) return root;
        return findRoleInChildren(root, role);
    }

    private static ProgramNode findRoleInChildren(ProgramNode n, String role) {
        for (ProgramNode c : n.children) {
            if (role.equals(c.role)) return c;
            ProgramNode deep = findRoleInChildren(c, role);
            if (deep != null) return deep;
        }
        return null;
    }
}
