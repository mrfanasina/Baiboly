package com.fa.baiboly.ui.litorjia;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.fa.baiboly.R;
import com.fa.baiboly.data.litorjia.NodePayloadParser;
import com.fa.baiboly.data.litorjia.Program;
import com.fa.baiboly.data.litorjia.ProgramNode;
import com.fa.baiboly.data.litorjia.ReferenceResolver;
import com.fa.baiboly.data.litorjia.TemplateCatalog;
import com.fa.baiboly.data.parser.BibleRefParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Éditeur d'un noeud (§4.3) : champ titre commun + formulaire selon le
 * type.
 *
 *  - hymn   : recueil (sokajy de la base) + numéro + couplets cochables,
 *             validation du chant (introuvable → conservé "à compléter")
 *  - bible  : livre (alias normalisés) + chapitre + de/à ; refs multiples
 *             et bouton « Rétablir le perikopa » (origin calendar→manual)
 *  - prayer : kind (+ note) ; creed : version 1-4 ;
 *             text/choir : corps ; offering/custom : note
 */
final class NodeEditorDialog {

    interface OnSaved {
        void onSaved(ProgramNode node);
    }

    /** Remontée du clic « Rétablir le perikopa du jour ». */
    interface OnRestorePerikopa {
        void restorePerikopa(ProgramNode node);
    }

    private NodeEditorDialog() {}

    static void show(Context ctx, Program program, ProgramNode node,
                     OnSaved onSaved, OnRestorePerikopa restorer) {
        View root = LayoutInflater.from(ctx).inflate(R.layout.dialog_litorjia_node, null);
        AlertDialog dialog = new AlertDialog.Builder(ctx).create();
        dialog.setView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView title = root.findViewById(R.id.dialogTitle);
        TextView kindLabel = root.findViewById(R.id.textKind);
        EditText inputTitle = root.findViewById(R.id.inputTitle);
        LinearLayout form = root.findViewById(R.id.formContainer);
        Button btnRestore = root.findViewById(R.id.btnRestore);

        title.setText(typeName(node));
        kindLabel.setText(typeHint(node.type));
        inputTitle.setText(node.title == null ? "" : node.title);
        inputTitle.setHint(node.defaultTitle());

        switch (node.type) {
            case ProgramNode.TYPE_HYMN:
                buildHymnForm(ctx, root, form, node, dialog, inputTitle, onSaved);
                break;
            case ProgramNode.TYPE_BIBLE:
                buildBibleForm(ctx, root, form, node, dialog, inputTitle, btnRestore, onSaved, restorer);
                break;
            case ProgramNode.TYPE_PRAYER:
                buildPrayerForm(ctx, root, form, node, dialog, inputTitle, onSaved);
                break;
            case ProgramNode.TYPE_CREED:
                buildCreedForm(ctx, root, form, node, dialog, inputTitle, onSaved);
                break;
            case ProgramNode.TYPE_TEXT:
            case ProgramNode.TYPE_CHOIR:
                buildBodyForm(ctx, root, form, node, dialog, inputTitle,
                        ProgramNode.TYPE_CHOIR.equals(node.type)
                                ? "Hira safidin'ny antokom-pihira" : "Lahatsoratra", onSaved);
                break;
            default:
                buildNoteForm(ctx, root, form, node, dialog, inputTitle, "Fanamarihana", onSaved);
                break;
        }

        dialog.show();
    }

    // =========================================================
    // hymn
    // =========================================================

    private static void buildHymnForm(Context ctx, View root, LinearLayout form,
                                      ProgramNode node, AlertDialog dialog,
                                      EditText inputTitle, OnSaved onSaved) {
        ReferenceResolver resolver = new ReferenceResolver(ctx);
        List<String> books = resolver.songBooks();
        if (books.isEmpty()) {
            books = new ArrayList<>(Arrays.asList("FFPM", "FIF", "FF"));
        }

        LinearLayout row = rowStrip(ctx);
        TextView lblBook = labelOf(ctx, "Sokajy :");
        final Spinner spBook = new Spinner(ctx);
        ArrayAdapter<String> ad = new ArrayAdapter<>(
                ctx, android.R.layout.simple_spinner_dropdown_item, books);
        spBook.setAdapter(ad);
        int sel = 0;
        String cur = node.hymnBook();
        if (cur != null && !cur.isEmpty()) {
            for (int i = 0; i < books.size(); i++) {
                if (books.get(i).equalsIgnoreCase(cur)) { sel = i; break; }
            }
        }
        spBook.setSelection(Math.min(sel, Math.max(0, books.size() - 1)));
        row.addView(lblBook, lpWrap());
        row.addView(spBook, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout row2 = rowStrip(ctx);
        TextView lblNum = labelOf(ctx, "Laharana :");
        final EditText inputNum = numberEdit(ctx);
        Integer n = node.hymnNumber();
        if (n != null) inputNum.setText(String.valueOf(n));
        row2.addView(lblNum, lpWrap());
        row2.addView(inputNum, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView lblStanzas = labelOf(ctx,
                "Andininy (foana = manontolo) :");
        final LinearLayout stanzasBox = new LinearLayout(ctx);
        stanzasBox.setOrientation(LinearLayout.HORIZONTAL);
        List<Integer> curStanzas = node.hymnStanzas();
        int dp40 = px(ctx, 40);
        int dp34 = px(ctx, 34);
        for (int i = 1; i <= 12; i++) {
            TextView chip = new TextView(ctx);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setText(String.valueOf(i));
            chip.setTextSize(13f);
            chip.setBackgroundResource(R.drawable.bg_verse_chip);
            chip.setOnClickListener(v -> {
                chip.setSelected(!chip.isSelected());
                chip.setAlpha(chip.isSelected() ? 1f : 0.55f);
            });
            chip.setSelected(curStanzas.contains(i));
            chip.setAlpha(chip.isSelected() ? 1f : 0.55f);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp40, dp34);
            clp.setMargins(0, 0, 6, 0);
            stanzasBox.addView(chip, clp);
        }

        LinearLayout row4 = rowStrip(ctx);
        TextView lblLabel = labelOf(ctx, "Fanazavana :");
        final EditText inputLabel = textEdit(ctx);
        inputLabel.setHint("Fanazavana hafa");
        inputLabel.setText(node.hymnLabel());
        row4.addView(lblLabel, lpWrap());
        row4.addView(inputLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        form.addView(row);
        form.addView(row2);
        form.addView(lblStanzas);
        form.addView(stanzasBox);
        form.addView(row4);

        root.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            String nStr = inputNum.getText().toString().trim();
            Integer number = null;
            ReferenceResolver.SongInfo info = null;
            if (!nStr.isEmpty()) {
                try { number = Integer.parseInt(nStr); } catch (NumberFormatException ignored) {}
                if (number != null) {
                    String b = (String) spBook.getSelectedItem();
                    info = resolver.findSong(b, number);
                    if (info == null) {
                        // Chant introuvable : conservé avec son libellé, marqué
                        // « à compléter » (§7.4) — jamais bloquant
                        Toast.makeText(ctx,
                                "Tsy hita ilay hira " + b + " " + number, Toast.LENGTH_SHORT).show();
                    }
                }
            }
            List<Integer> stanzas = new ArrayList<>();
            for (int i = 0; i < stanzasBox.getChildCount(); i++) {
                TextView chip = (TextView) stanzasBox.getChildAt(i);
                if (chip.isSelected()) stanzas.add(i + 1);
            }
            node.payload = NodePayloadParser.hymnPayload(
                    (String) spBook.getSelectedItem(), number, stanzas,
                    inputLabel.getText().toString().trim());
            node.title = inputTitle.getText().toString().trim();
            if (node.title.isEmpty() && info != null && info.title != null) {
                node.title = info.title; // titre de la base
            }
            dialog.dismiss();
            if (onSaved != null) onSaved.onSaved(node);
        });
    }

    // =========================================================
    // bible
    // =========================================================

    private static void buildBibleForm(Context ctx, View root, LinearLayout form,
                                       ProgramNode node, AlertDialog dialog,
                                       EditText inputTitle, Button btnRestore,
                                       OnSaved onSaved, OnRestorePerikopa restorer) {
        final List<ProgramNode.BibleRef> refs = new ArrayList<>(node.bibleRefs());
        LinearLayout refsBox = new LinearLayout(ctx);
        refsBox.setOrientation(LinearLayout.VERTICAL);
        final List<List<EditText>> rowsFields = new ArrayList<>();

        Runnable render = () -> {
            refsBox.removeAllViews();
            rowsFields.clear();
            for (int i = 0; i < refs.size(); i++) {
                ProgramNode.BibleRef r = refs.get(i);
                LinearLayout ll = new LinearLayout(ctx);
                ll.setOrientation(LinearLayout.HORIZONTAL);
                ll.setGravity(android.view.Gravity.CENTER_VERTICAL);

                AutoCompleteTextView inputBook = new AutoCompleteTextView(ctx);
                inputBook.setHint("Boky");
                inputBook.setTextSize(14f);
                if (r.book != null) inputBook.setText(r.book);
                ArrayAdapter<String> sugAd = new ArrayAdapter<>(ctx,
                        android.R.layout.simple_dropdown_item_1line, commonBooks());
                inputBook.setAdapter(sugAd);

                EditText inputChapter = new EditText(ctx);
                inputChapter.setHint("Tok.");
                inputChapter.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                inputChapter.setTextSize(14f);
                if (r.chapter != null) inputChapter.setText(r.chapter);

                EditText inputFrom = new EditText(ctx);
                inputFrom.setHint("Manomboka");
                inputFrom.setTextSize(14f);
                if (r.from != null) inputFrom.setText(r.from);

                EditText inputTo = new EditText(ctx);
                inputTo.setHint("Mifarana");
                inputTo.setTextSize(14f);
                if (r.to != null) inputTo.setText(r.to);

                TextView num = new TextView(ctx);
                num.setText((i + 1) + ".");
                num.setTextSize(13f);
                num.setTextColor(ctx.getResources().getColor(android.R.color.darker_gray));
                num.setPadding(0, 0, 8, 0);
                ll.addView(num);
                ll.addView(inputBook, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
                ll.addView(inputChapter, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.6f));
                ll.addView(inputFrom, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f));
                ll.addView(inputTo, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f));
                refsBox.addView(ll);

                List<EditText> f = new ArrayList<>();
                f.add(inputBook);
                f.add(inputChapter);
                f.add(inputFrom);
                f.add(inputTo);
                rowsFields.add(f);
            }
        };
        refs.add(new ProgramNode.BibleRef());
        render.run();

        Button btnAddRef = new Button(ctx, null, android.R.attr.borderlessButtonStyle);
        btnAddRef.setText("Hanampy andinin-teny");
        btnAddRef.setOnClickListener(v -> {
            refs.add(new ProgramNode.BibleRef());
            render.run();
        });
        refsBox.addView(btnAddRef, lpFull());
        form.addView(refsBox);

        // Bouton « Rétablir le perikopa du jour » (§4.3 : calendar ↔ manual)
        if (node.origin != null && ProgramNode.ORIGIN_CALENDAR.equals(node.origin)) {
            btnRestore.setVisibility(View.VISIBLE);
            btnRestore.setOnClickListener(v -> {
                dialog.dismiss();
                if (restorer != null) restorer.restorePerikopa(node);
            });
        } else {
            btnRestore.setVisibility(View.GONE);
        }

        root.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            List<ProgramNode.BibleRef> newRefs = new ArrayList<>();
            boolean anyFilled = false;
            for (List<EditText> f : rowsFields) {
                String book = f.get(0).getText().toString().trim();
                if (book.isEmpty()) continue;
                ProgramNode.BibleRef r = new ProgramNode.BibleRef();
                r.book = normalizeBook(book);
                Integer bn = BibleRefParser.resolveBookNumber(book);
                r.bookNumber = bn;
                r.chapter = f.get(1).getText().toString().trim();
                r.from = trimOrNull(f.get(2).getText().toString());
                r.to = trimOrNull(f.get(3).getText().toString());
                newRefs.add(r);
                anyFilled = true;
            }
            if (!anyFilled) {
                // Lecture vide conservée « à compléter » : ne casse jamais
                newRefs.clear();
            }
            node.payload = NodePayloadParser.biblePayload(newRefs);
            node.title = inputTitle.getText().toString().trim();
            dialog.dismiss();
            if (onSaved != null) onSaved.onSaved(node);
        });
    }

    private static List<String> commonBooks() {
        return Arrays.asList(
                "Genesisy", "Exodely", "Salamo", "Manao fahalalana", "Isaia", "Jeremia",
                "Danely", "Mpanjaka", "Esay", "Zakaria", "Mattheo", "Marka", "Lioka",
                "Jaona", "Asan'ny Apostoly", "Romanà", "Korinto", "Galatiana",
                "Efesiana", "Filipiana", "Kolosiana", "Tesaloniana", "Timoteo",
                "Hebreo", "Jakoba", "Petero", "Joda", "Apokalypsy");
    }

    private static String normalizeBook(String raw) {
        Integer bn = BibleRefParser.resolveBookNumber(raw);
        if (bn == null) return raw.trim();
        String s = BibleRefParser.shortNameOf(bn);
        return s == null || s.isEmpty() ? raw.trim() : s;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // =========================================================
    // prayer / creed / text / note
    // =========================================================

    private static void buildPrayerForm(Context ctx, View root, LinearLayout form,
                                        ProgramNode node, AlertDialog dialog,
                                        EditText inputTitle, OnSaved onSaved) {
        final String[] kinds = NodePayloadParser.prayerKinds();
        final Spinner sp = new Spinner(ctx);
        List<String> labels = new ArrayList<>();
        for (String k : kinds) labels.add(NodePayloadParser.prayerKindLabel(k));
        labels.add("Hafa / tsy voafaritra");
        ArrayAdapter<String> ad = new ArrayAdapter<>(ctx,
                android.R.layout.simple_spinner_dropdown_item, labels);
        sp.setAdapter(ad);
        int sel = labels.size() - 1;
        String cur = node.prayerKind();
        if (cur != null && !cur.isEmpty()) {
            for (int i = 0; i < kinds.length; i++) {
                if (kinds[i].equals(cur)) { sel = i; break; }
            }
        }
        sp.setSelection(sel);

        LinearLayout row = rowStrip(ctx);
        row.addView(labelOf(ctx, "Karazana :"), lpWrap());
        row.addView(sp, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        form.addView(row);

        TextView lblNote = labelOf(ctx, "Fanamarihana :");
        final EditText inputNote = new EditText(ctx);
        inputNote.setTextSize(15f);
        inputNote.setMinLines(2);
        inputNote.setText(node.note());
        form.addView(lblNote);
        form.addView(inputNote, lpFull());

        root.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            int pos = sp.getSelectedItemPosition();
            String kind = pos >= 0 && pos < kinds.length ? kinds[pos] : null;
            node.payload = NodePayloadParser.prayerPayload(kind,
                    trimOrNull(inputNote.getText().toString()));
            node.title = inputTitle.getText().toString().trim();
            dialog.dismiss();
            if (onSaved != null) onSaved.onSaved(node);
        });
    }

    private static void buildCreedForm(Context ctx, View root, LinearLayout form,
                                       ProgramNode node, AlertDialog dialog,
                                       EditText inputTitle, OnSaved onSaved) {
        final Spinner sp = new Spinner(ctx);
        List<String> labels = new ArrayList<>();
        for (int i = 1; i <= 4; i++) labels.add("Fanekem-pinoana v" + i);
        ArrayAdapter<String> ad = new ArrayAdapter<>(ctx,
                android.R.layout.simple_spinner_dropdown_item, labels);
        sp.setAdapter(ad);
        Integer cur = node.creedVersion();
        if (cur == null) cur = TemplateCatalog.defaultCreedVersion(null);
        sp.setSelection(Math.max(0, Math.min(3, cur - 1)));

        LinearLayout row = rowStrip(ctx);
        row.addView(labelOf(ctx, "Kinova :"), lpWrap());
        row.addView(sp, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        form.addView(row);

        root.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            node.payload = NodePayloadParser.creedPayload(sp.getSelectedItemPosition() + 1);
            node.title = inputTitle.getText().toString().trim();
            dialog.dismiss();
            if (onSaved != null) onSaved.onSaved(node);
        });
    }

    private static void buildBodyForm(Context ctx, View root, LinearLayout form,
                                      ProgramNode node, AlertDialog dialog,
                                      EditText inputTitle, String bodyHint, OnSaved onSaved) {
        form.addView(labelOf(ctx, bodyHint + " :"));
        final EditText input = new EditText(ctx);
        input.setTextSize(15f);
        input.setMinLines(2);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(node.body());
        form.addView(input, lpFull());

        root.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            node.payload = NodePayloadParser.bodyPayload(
                    trimOrNull(input.getText().toString()));
            node.title = inputTitle.getText().toString().trim();
            dialog.dismiss();
            if (onSaved != null) onSaved.onSaved(node);
        });
    }

    private static void buildNoteForm(Context ctx, View root, LinearLayout form,
                                      ProgramNode node, AlertDialog dialog,
                                      EditText inputTitle, String hint, OnSaved onSaved) {
        form.addView(labelOf(ctx, hint + " :"));
        final EditText input = new EditText(ctx);
        input.setTextSize(15f);
        input.setMinLines(1);
        input.setText(node.note());
        form.addView(input, lpFull());

        root.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            node.payload = NodePayloadParser.bodyPayload(
                    trimOrNull(input.getText().toString()));
            node.title = inputTitle.getText().toString().trim();
            dialog.dismiss();
            if (onSaved != null) onSaved.onSaved(node);
        });
    }

    // =========================================================
    // Widgets helpers
    // =========================================================

    private static int px(Context ctx, int dp) {
        return (int) (dp * ctx.getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams lpWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams lpFull() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout rowStrip(Context ctx) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(android.view.Gravity.CENTER_VERTICAL);
        l.setPadding(0, px(ctx, 10), 0, px(ctx, 4));
        return l;
    }

    private static TextView labelOf(Context ctx, String s) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(13f);
        t.setTextColor(ctx.getResources().getColor(android.R.color.darker_gray));
        t.setPadding(0, 0, px(ctx, 12), 0);
        return t;
    }

    private static EditText numberEdit(Context ctx) {
        EditText e = new EditText(ctx);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.setTextSize(15f);
        return e;
    }

    private static EditText textEdit(Context ctx) {
        EditText e = new EditText(ctx);
        e.setTextSize(15f);
        return e;
    }

    private static String typeName(ProgramNode n) {
        switch (n.type) {
            case ProgramNode.TYPE_HYMN: return "Hira";
            case ProgramNode.TYPE_BIBLE: return "Vakiteny";
            case ProgramNode.TYPE_PRAYER: return "Vavaka";
            case ProgramNode.TYPE_CREED: return "Fanekem-pinoana";
            case ProgramNode.TYPE_CHOIR: return "Antokom-pihira";
            case ProgramNode.TYPE_TEXT: return "Lahatsoratra";
            case ProgramNode.TYPE_OFFERING: return "Rakitra";
            case ProgramNode.TYPE_SECTION: return "Sampana";
            case ProgramNode.TYPE_TITLE: return "Lohateny";
            default: return "Hafa";
        }
    }

    private static String typeHint(String type) {
        switch (type) {
            case ProgramNode.TYPE_HYMN:
                return "Sokajy + laharana + andininy — voamarina ao amin'ny tahiry";
            case ProgramNode.TYPE_BIBLE:
                return "Boky, toko, andininy (13b ok ; : na .)";
            case ProgramNode.TYPE_PRAYER:
                return "Karazana sy fanamarihana";
            case ProgramNode.TYPE_CREED:
                return "Lahatsoratra avy ao amin'ny base (v1–v4)";
            case ProgramNode.TYPE_CHOIR:
                return "Anaran'ny antokom-pihira = lohateny ; hira afaka";
            default:
                return null;
        }
    }
}
