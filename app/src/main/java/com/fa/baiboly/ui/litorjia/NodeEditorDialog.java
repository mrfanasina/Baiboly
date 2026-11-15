package com.fa.baiboly.ui.litorjia;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.fa.baiboly.R;
import com.fa.baiboly.data.litorjia.NodePayloadParser;
import com.fa.baiboly.data.litorjia.PerikopaFiller;
import com.fa.baiboly.data.litorjia.Program;
import com.fa.baiboly.data.litorjia.ProgramNode;
import com.fa.baiboly.data.litorjia.ReferenceResolver;
import com.fa.baiboly.data.litorjia.TemplateCatalog;
import com.fa.baiboly.ui.fandaharana.BiblePickerSheet;
import com.fa.baiboly.ui.fandaharana.SongPickerSheet;

import java.util.ArrayList;
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

    /** Remontée du clic corbeille : l'appelant confirme et supprime. */
    interface OnDelete {
        void onDelete(ProgramNode node);
    }

    private NodeEditorDialog() {}

    static void show(Context ctx, Program program, ProgramNode node,
                     OnSaved onSaved, OnRestorePerikopa restorer, OnDelete onDelete) {
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

        // Corbeille : ferme l'éditeur puis laisse l'appelant confirmer
        // (même dialogue de confirmation et même annulation que l'arbre).
        root.findViewById(R.id.btnDeleteNode).setOnClickListener(v -> {
            dialog.dismiss();
            if (onDelete != null) onDelete.onDelete(node);
        });

        // « Annuler » n'était câblé à rien : le bouton ne fermait pas.
        root.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        // Les feuilles de choix (chant, lecture) sont des fenêtres qui
        // s'ouvrent par-dessus ce dialogue : on ferme l'éditeur, on choisit,
        // puis on rouvre l'éditeur avec la valeur retenue.
        final Context reopenCtx = ctx;
        final Runnable reopen = () -> show(reopenCtx, program, node, onSaved, restorer, onDelete);

        title.setText(typeName(node));
        kindLabel.setText(typeHint(node.type));
        inputTitle.setText(node.title == null ? "" : node.title);
        inputTitle.setHint(node.defaultTitle());

        switch (node.type) {
            case ProgramNode.TYPE_HYMN:
                buildHymnForm(ctx, root, form, node, dialog, inputTitle, onSaved, reopen);
                break;
            case ProgramNode.TYPE_BIBLE:
                buildBibleForm(ctx, root, form, node, dialog, inputTitle, btnRestore,
                        onSaved, restorer, reopen);
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
                                      EditText inputTitle, OnSaved onSaved,
                                      Runnable reopen) {
        final String[] book = { node.hymnBook() };
        final Integer[] number = { node.hymnNumber() };
        final String[] songTitle = { null };

        // Choix du chant : même feuille que l'écran Fihirana (sokajy +
        // laharan'ny hira), ouverte sur le chant déjà enregistré.
        final TextView tvChoice = new TextView(ctx);
        final TextView tvChoiceSub = new TextView(ctx);
        LinearLayout pickCard = pickCard(ctx, tvChoice, tvChoiceSub);
        tvChoice.setText(songHeading(book[0], number[0]));
        ReferenceResolver.SongInfo currentInfo =
                new ReferenceResolver(ctx).findSong(book[0], number[0]);
        tvChoiceSub.setText(currentInfo != null && currentInfo.title != null
                && !currentInfo.title.isEmpty()
                ? currentInfo.title : "Tsindrio hisafidy hira");

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

        form.addView(pickCard, lpFull());
        form.addView(lblStanzas);
        form.addView(stanzasBox);
        form.addView(row4);

        // Écrit tout le formulaire dans le noeud. Les vues de ce dialogue
        // restent lisibles même après sa fermeture.
        final Runnable apply = () -> {
            List<Integer> stanzas = new ArrayList<>();
            for (int i = 0; i < stanzasBox.getChildCount(); i++) {
                TextView chip = (TextView) stanzasBox.getChildAt(i);
                if (chip.isSelected()) stanzas.add(i + 1);
            }
            node.payload = NodePayloadParser.hymnPayload(book[0], number[0], stanzas,
                    inputLabel.getText().toString().trim());
            node.title = inputTitle.getText().toString().trim();
            if (node.title.isEmpty() && songTitle[0] != null) {
                node.title = songTitle[0]; // titre de la base
            }
        };

        // La feuille de choix est une fenêtre : on ferme l'éditeur avant de
        // l'ouvrir, puis on le rouvre sur le chant retenu (couplets à régler).
        pickCard.setOnClickListener(v -> {
            dialog.dismiss();
            openSongPicker(ctx, book[0], number[0], song -> {
                book[0] = song.getCategory();
                number[0] = song.getNumber();
                songTitle[0] = song.getTitle();
                apply.run();
                if (onSaved != null) onSaved.onSaved(node);
                if (reopen != null) reopen.run();
            });
        });

        root.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            apply.run();
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
                                       OnSaved onSaved, OnRestorePerikopa restorer,
                                       Runnable reopen) {
        final ReferenceResolver resolver = new ReferenceResolver(ctx);
        final List<ProgramNode.BibleRef> refs = new ArrayList<>(node.bibleRefs());
        final LinearLayout refsBox = new LinearLayout(ctx);
        refsBox.setOrientation(LinearLayout.VERTICAL);

        // Liste des lectures, dans l'ordre : affichage + retrait par ligne.
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            refsBox.removeAllViews();
            if (refs.isEmpty()) {
                TextView empty = new TextView(ctx);
                empty.setText("Tsy mbola misy vakiteny — ampio amin'ny safidy eto ambany.");
                empty.setTextSize(13f);
                empty.setAlpha(0.7f);
                empty.setPadding(0, px(ctx, 6), 0, px(ctx, 10));
                refsBox.addView(empty, lpFull());
                return;
            }
            for (int i = 0; i < refs.size(); i++) {
                final int index = i;
                LinearLayout ll = new LinearLayout(ctx);
                ll.setOrientation(LinearLayout.HORIZONTAL);
                ll.setGravity(android.view.Gravity.CENTER_VERTICAL);

                TextView tv = new TextView(ctx);
                tv.setText(refs.get(i).display());
                tv.setTextSize(15f);
                ll.addView(tv, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                ImageButton del = new ImageButton(ctx);
                del.setImageResource(R.drawable.ic_delete);
                del.setColorFilter(androidx.core.content.ContextCompat.getColor(
                        ctx, R.color.lit_swipe_delete));
                del.setContentDescription(ctx.getString(R.string.lit_act_delete));
                android.util.TypedValue ripple = new android.util.TypedValue();
                if (ctx.getTheme().resolveAttribute(
                        android.R.attr.selectableItemBackgroundBorderless, ripple, true)
                        && ripple.resourceId != 0) {
                    del.setBackgroundResource(ripple.resourceId);
                }
                int size = px(ctx, 40);
                del.setOnClickListener(v -> {
                    refs.remove(index);
                    render[0].run();
                });
                ll.addView(del, new LinearLayout.LayoutParams(size, size));
                refsBox.addView(ll, lpFull());
            }
        };
        render[0].run();
        form.addView(refsBox);

        Button btnAddRef = new Button(ctx, null, android.R.attr.borderlessButtonStyle);
        btnAddRef.setText("Hanampy vakiteny");
        // Même principe que pour le chant : la feuille s'ouvre par-dessus le
        // dialogue, donc on ferme l'éditeur, on choisit, on enregistre puis
        // on rouvre l'éditeur avec la lecture ajoutée.
        btnAddRef.setOnClickListener(v -> {
            final String typedTitle = inputTitle.getText().toString().trim();
            dialog.dismiss();
            openBiblePicker(ctx, (reference, title) -> {
                List<ProgramNode.BibleRef> parsed = PerikopaFiller.parse(reference, resolver);
                if (parsed.isEmpty()) {
                    // Référence non reconnue : conservée brute, « à compléter »
                    ProgramNode.BibleRef raw = new ProgramNode.BibleRef();
                    raw.book = reference;
                    raw.label = title;
                    refs.add(raw);
                } else {
                    for (ProgramNode.BibleRef r : parsed) {
                        if (r.label == null || r.label.isEmpty()) r.label = title;
                    }
                    refs.addAll(parsed);
                }
                node.payload = NodePayloadParser.biblePayload(refs);
                if (!typedTitle.isEmpty()) node.title = typedTitle;
                if (onSaved != null) onSaved.onSaved(node);
                if (reopen != null) reopen.run();
            });
        });
        form.addView(btnAddRef, lpFull());

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
            node.payload = NodePayloadParser.biblePayload(refs);
            node.title = inputTitle.getText().toString().trim();
            dialog.dismiss();
            if (onSaved != null) onSaved.onSaved(node);
        });
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

    /** Carte cliquable « choisir » : libellé principal + précision. */
    private static LinearLayout pickCard(Context ctx, TextView main, TextView sub) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(px(ctx, 14), px(ctx, 8), px(ctx, 14), px(ctx, 8));
        card.setBackgroundResource(R.drawable.bg_picker_cell);
        card.setClickable(true);
        card.setFocusable(true);
        main.setTextSize(15f);
        sub.setTextSize(13f);
        sub.setAlpha(0.7f);
        card.addView(main, lpFull());
        card.addView(sub, lpFull());
        return card;
    }

    /** « FFPM 21 » ou « Tsy misy hira » quand rien n'est encore choisi. */
    private static String songHeading(String book, Integer number) {
        if (book == null || book.trim().isEmpty() || number == null) {
            return "Tsy misy hira";
        }
        return book.trim() + " " + number;
    }

    /** Feuille de choix de chant, identique à celle du programme/Fihirana. */
    private static void openSongPicker(Context ctx, String book, Integer number,
                                       SongPickerSheet.OnSongPicked cb) {
        if (!(ctx instanceof androidx.fragment.app.FragmentActivity)) return;
        new SongPickerSheet(cb, book, number).show(
                ((androidx.fragment.app.FragmentActivity) ctx).getSupportFragmentManager(),
                "lit_song_picker");
    }

    /** Feuille de choix de lecture (livre → toko → andininy). */
    private static void openBiblePicker(Context ctx, BiblePickerSheet.OnBiblePicked cb) {
        if (!(ctx instanceof androidx.fragment.app.FragmentActivity)) return;
        new BiblePickerSheet(cb).show(
                ((androidx.fragment.app.FragmentActivity) ctx).getSupportFragmentManager(),
                "lit_bible_picker");
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
                return "Lahatsoratra avy ao amin'ny tahiry (v1–v4)";
            case ProgramNode.TYPE_CHOIR:
                return "Anaran'ny antokom-pihira = lohateny ; hira afaka";
            default:
                return null;
        }
    }
}
