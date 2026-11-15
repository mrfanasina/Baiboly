package com.fa.baiboly.ui.litorjia;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.fa.baiboly.R;
import com.fa.baiboly.data.bible.BibleRepository;
import com.fa.baiboly.data.fanekena.FanekenaService;
import com.fa.baiboly.data.litorjia.NodePayloadParser;
import com.fa.baiboly.data.litorjia.ProgramNode;
import com.fa.baiboly.data.litorjia.ReferenceResolver;

import java.util.List;

/**
 * Détail d'un noeud en mode consultation (§4.4) : paroles du chant avec
 * couplets sélectionnés, texte des versets, texte du credo. Les textes
 * viennent TOUJOURS de la base locale — le programme ne transporte que
 * des références.
 */
final class NodeDetailDialog {

    private NodeDetailDialog() {}

    static void show(Context ctx, ProgramNode node) {
        show(ctx, node, null, null);
    }

    static void show(Context ctx, ProgramNode node, Runnable onEdit) {
        show(ctx, node, onEdit, null);
    }

    /**
     * @param onEdit   action « Ovay » proposée au bas du détail (null = pas
     *                 d'édition depuis la consultation)
     * @param onDelete action « Hofafana » : suppression directe, confirmée
     *                 par l'appelant (null = pas de suppression)
     */
    static void show(Context ctx, ProgramNode node, Runnable onEdit, Runnable onDelete) {
        AlertDialog dialog = new AlertDialog.Builder(ctx).create();

        View root = LayoutInflater.from(ctx).inflate(R.layout.dialog_litorjia_detail, null);
        LinearLayout box = root.findViewById(R.id.detailBox);
        TextView tvTitle = root.findViewById(R.id.detailTitle);
        TextView tvSummary = root.findViewById(R.id.detailSummary);

        tvTitle.setText(node.displayTitle());
        String s = node.summary();
        tvSummary.setText(s == null || s.isEmpty() ? "" : s);
        tvSummary.setVisibility(s == null || s.isEmpty() ? View.GONE : View.VISIBLE);

        try {
            switch (node.type) {
                case ProgramNode.TYPE_HYMN:
                    fillHymn(ctx, box, node);
                    break;
                case ProgramNode.TYPE_BIBLE:
                    fillBible(ctx, box, node);
                    break;
                case ProgramNode.TYPE_CREED:
                    fillCreed(ctx, box, node);
                    break;
                case ProgramNode.TYPE_PRAYER:
                    addHead(box, NodePayloadParser.prayerKindLabel(node.prayerKind()));
                    addNoteIfAny(box, node.note());
                    break;
                case ProgramNode.TYPE_CHOIR:
                case ProgramNode.TYPE_TEXT:
                    addBody(box, node.body(), true);
                    addNoteIfAny(box, "");
                    break;
                default:
                    addNoteIfAny(box, node.note());
                    break;
            }
        } catch (Exception e) {
            Toast.makeText(ctx, "Tsy azo vakiana ny votoaty", Toast.LENGTH_SHORT).show();
        }

        dialog.setView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        root.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());

        View btnEdit = root.findViewById(R.id.btnEdit);
        btnEdit.setVisibility(onEdit == null ? View.GONE : View.VISIBLE);
        btnEdit.setOnClickListener(v -> {
            dialog.dismiss();
            if (onEdit != null) onEdit.run();
        });

        View btnDelete = root.findViewById(R.id.btnDelete);
        btnDelete.setVisibility(onDelete == null ? View.GONE : View.VISIBLE);
        btnDelete.setOnClickListener(v -> {
            dialog.dismiss();
            if (onDelete != null) onDelete.run();
        });
        dialog.show();
    }

    // =========================================================
    // Hira : couplets sélectionnés + paroles de la base
    // =========================================================

    private static void fillHymn(Context ctx, LinearLayout box, ProgramNode node) {
        Integer number = node.hymnNumber();
        if (number == null) {
            addBody(box, "Hira tsy voamarina — safidio ny laharana ao amin'ny fanatsarana.", false);
            return;
        }
        ReferenceResolver resolver = new ReferenceResolver(ctx);
        ReferenceResolver.SongInfo info = resolver.findSong(node.hymnBook(), number);
        if (info == null) {
            addBody(box, node.hymnBook() + " " + number
                    + " : tsy hita ao anaty base — jereo ny sokajy na ny laharana.", false);
            return;
        }
        com.fa.baiboly.data.fihirana.FihiranaRepository repo =
                new com.fa.baiboly.data.fihirana.FihiranaRepository(ctx);
        List<Integer> selected = node.hymnStanzas();
        boolean all = selected.isEmpty();
        android.database.Cursor c = repo.getSongVerses(info.id);
        if (c == null || !c.moveToFirst()) {
            addBody(box, "Tsy hita ny tononkira ao anaty base.", false);
            if (c != null) c.close();
            return;
        }
        int colFiverenany = c.getColumnIndexOrThrow("fiverenany");
        do {
            try {
                int andininy = c.getInt(0);
                if (!all && !selected.contains(andininy)) continue;
                addHead(box, "Andininy " + andininy);
                addBody(box, c.getString(1), true);
                String fv = c.getString(colFiverenany);
                if (fv != null && !fv.trim().isEmpty()) {
                    TextView t = new TextView(ctx);
                    t.setText("Fiverenany : " + fv.trim());
                    t.setTextSize(14f);
                    t.setTypeface(null, Typeface.ITALIC);
                    box.addView(t, boxParams());
                }
            } catch (Exception ignored) {}
        } while (c.moveToNext());
        c.close();
        String self = node.hymnLabel();
        if (self != null && !self.isEmpty()) {
            TextView t = new TextView(ctx);
            t.setText(self);
            t.setTextSize(14f);
            t.setPadding(0, 12, 0, 0);
            box.addView(t, boxParams());
        }
    }

    // =========================================================
    // Vakiteny : texte des versets depuis la Bible locale
    // =========================================================

    private static void fillBible(Context ctx, LinearLayout box, ProgramNode node) {
        List<ProgramNode.BibleRef> refs = node.bibleRefs();
        if (refs.isEmpty()) {
            addBody(box, "Vakiteny tsy voamarina — safidio ny toko sy ny andininy.", false);
            return;
        }
        BibleRepository bible = new BibleRepository(ctx);
        boolean any = false;
        for (ProgramNode.BibleRef r : refs) {
            Integer bn = r.bookNumber != null ? r.bookNumber
                    : bible.resolveBookNumber(r.book);
            if (bn == null) {
                addBody(box, (r.book == null ? "?" : r.book) + " : tsy fantatra ilay boky.", false);
                continue;
            }
            int chap = 0;
            try {
                chap = Integer.parseInt(r.chapter == null ? ""
                        : r.chapter.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {}
            if (chap <= 0) {
                addBody(box, r.display(), false);
                continue;
            }

            android.database.Cursor c = bible.getVerses(bn, chap);
            if (c == null || !c.moveToFirst()) {
                addBody(box, r.display() + " : tsy hita ny toko.", false);
                if (c != null) c.close();
                continue;
            }
            addHead(box, r.display());
            int from = parseVerse(r.from);
            int to = r.to != null ? parseVerse(r.to) : from;
            do {
                int v = c.getInt(0);
                if (from > 0 && (v < Math.min(from, to) || v > Math.max(from, to))) continue;
                TextView t = new TextView(ctx);
                t.setText(v + "  " + c.getString(1));
                t.setTextSize(15f);
                box.addView(t, boxParams());
                any = true;
            } while (c.moveToNext());
            c.close();
        }
        if (!any) addBody(box, "Safidio andininy hahitana ny lahatsoratra.", false);
    }

    private static int parseVerse(String s) {
        if (s == null) return 0;
        String clean = s.trim().replaceAll("[bB]$", "");
        try {
            if (clean.contains(".")) {
                // Forme "8.1" (plage multi-chapitres) : on retient le verset final
                String last = clean.substring(clean.lastIndexOf('.') + 1);
                return Integer.parseInt(last);
            }
            return Integer.parseInt(clean);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // =========================================================
    // Fanekem-pinoana : texte de la base (codes V1..V4)
    // =========================================================

    private static void fillCreed(Context ctx, LinearLayout box, ProgramNode node) {
        Integer v = node.creedVersion();
        if (v == null) v = 1;
        FanekenaService svc = new FanekenaService(ctx);
        com.fa.baiboly.models.Fanekena f = svc.getByCode("V" + v);
        if (f == null || f.getText() == null || f.getText().isEmpty()) {
            addBody(box, "Fanekem-pinoana v" + v + " : tsy hita ao anaty base.", false);
            return;
        }
        addHead(box, "Fanekem-pinoana v" + v);
        addBody(box, f.getText().trim(), true);
    }

    // =========================================================
    // Helpers d'affichage
    // =========================================================

    private static void addHead(LinearLayout box, String text) {
        TextView t = new TextView(box.getContext());
        t.setText(text);
        t.setTextSize(15f);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(box.getResources().getColor(R.color.lit_heading));
        t.setPadding(0, 12, 0, 6);
        box.addView(t, boxParams());
    }

    private static void addBody(LinearLayout box, String text, boolean wrapTop) {
        if (text == null || text.isEmpty()) return;
        TextView t = new TextView(box.getContext());
        t.setText(text);
        t.setTextSize(14.5f);
        t.setPadding(0, wrapTop ? 4 : 8, 0, 0);
        box.addView(t, boxParams());
    }

    private static void addNoteIfAny(LinearLayout box, String note) {
        if (note == null || note.isEmpty()) return;
        TextView t = new TextView(box.getContext());
        t.setText(note);
        t.setTextSize(14f);
        t.setTypeface(null, Typeface.ITALIC);
        t.setPadding(0, 8, 0, 0);
        box.addView(t, boxParams());
    }

    private static LinearLayout.LayoutParams boxParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
