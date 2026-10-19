package com.fa.baiboly.ui.notes;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.notes.NoteRepository;
import com.fa.baiboly.data.notes.NoteService;
import com.fa.baiboly.data.notes.richtext.RichTextCodec;
import com.fa.baiboly.models.Note;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * ✍️ ÉDITEUR DE NOTE enrichi, façon apps de notes modernes :
 * • styles de caractère : gras, italique, souligné, barré ;
 * • couleurs : texte + surlignage (palettes) ;
 * • blocs : titres H1/H2, citation ❝, listes ●/123, cases ☑ tapables ;
 * • couleur de carte, copie, partage, suppression ;
 * • sauvegarde : bouton ✓, retour arrière = auto-sauvegarde si contenu.
 */
public class NoteEditorActivity extends AppCompatActivity {

    // Palettes (mêmes familles que les surlignages de versets)
    private static final int[] TEXT_COLORS = {
            0xFF1A1A1A, 0xFFD32F2F, 0xFF1976D2, 0xFF388E3C, 0xFFF57C00
    };
    private static final int[] HIGHLIGHT_COLORS = {
            0xFFFFF176, 0xFFA5D6A7, 0xFF90CAF9, 0xFFF48FB1
    };

    private com.fa.baiboly.databinding.ActivityNoteEditorBinding binding;

    private NoteService noteService;
    private Note note;
    private boolean loadedExisting;

    private LinearLayout formatRow;
    private LinearLayout paletteRow;
    private LinearLayout cardColorRow;

    /**
     * Lancer l'éditeur : note existante (noteId) ou brouillon neuf
     * attaché à une lecture (type, référence, citation proposée).
     */
    public static void open(Context context, @Nullable Note existing,
                            String sourceType, String refLabel,
                            String refKey, @Nullable String quote) {
        Intent i = new Intent(context, NoteEditorActivity.class);
        if (existing != null) {
            i.putExtra("noteId", existing.id);
        } else {
            i.putExtra("sourceType", sourceType);
            i.putExtra("refLabel", refLabel);
            i.putExtra("refKey", refKey);
            i.putExtra("quote", quote == null ? "" : quote);
        }
        context.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyGlobalTheme();
        super.onCreate(savedInstanceState);
        binding = com.fa.baiboly.databinding.ActivityNoteEditorBinding
                .inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        noteService = new NoteService(this);

        loadedExisting = getIntent().getLongExtra("noteId", -1L) >= 0;
        note = loadedExisting
                ? new NoteRepository(this).getById(
                getIntent().getLongExtra("noteId", -1L))
                : NoteService.draft(
                getIntent().getStringExtra("sourceType"),
                getIntent().getStringExtra("refLabel"),
                getIntent().getStringExtra("refKey"),
                getIntent().getStringExtra("quote"));

        setToolbar();
        formatRow = binding.formatRow;
        paletteRow = binding.paletteRow;
        cardColorRow = binding.cardColorRow;

        buildFormatterRow();
        buildPalettes();
        loadBody();

        // Un tap ☑ dans le texte = édition ; la persistance suit au retour
        binding.richBody.setOnCheckboxToggled(this::saveQuiet);
    }

    // =========================================================
    // CHARGER / ENREGISTRER
    // =========================================================
    private void loadBody() {
        binding.textRefLabel.setText(
                note.refLabel == null || note.refLabel.isEmpty()
                        ? "Fanamarihana" : note.refLabel);
        binding.textRefSub.setText(sourceLabel(note.sourceType));
        binding.richBody.setText(RichTextCodec.decode(note.bodyJson));
        binding.richBody.setSelection(binding.richBody.length());
    }

    /** Sauvegarde silencieuse (micro-éditions comme le tap ☑). */
    private void saveQuiet() {
        if (loadedExisting) save(false);
    }

    private void save(boolean closeAfter) {
        Editable body = binding.richBody.getText();
        String json = RichTextCodec.encode(body);
        String plain = RichTextCodec.toPlainText(json);
        boolean hasContent = !clean(plain).isEmpty();

        if (!hasContent && !loadedExisting) {
            // Note nouvelle entièrement vide : rien à enregistrer
            if (closeAfter) finish();
            return;
        }

        NoteService.fillFromSpannableBuilder(note, body, plain);
        noteService.save(note);

        if (closeAfter) {
            Toast.makeText(this, "Voatahiry ny fanamarihana",
                    Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        }
    }

    /** Retour arrière : save auto si contenu ; sinon on quitte sans trace. */
    @Override
    public void onBackPressed() {
        if (loadedExisting || hasRealContent()) {
            save(true);
            return;
        }
        super.onBackPressed();
    }

    private boolean hasRealContent() {
        Editable body = binding.richBody.getText();
        String plain = RichTextCodec.toPlainText(RichTextCodec.encode(body));
        return !clean(plain).isEmpty();
    }

    private String clean(String s) {
        return s == null ? "" : s
                .replace(RichTextCodec.UL_PREFIX, "")
                .replace(RichTextCodec.CB_CHECKED, "")
                .replace(RichTextCodec.CB_UNCHECKED, "")
                .replaceAll("(?m)^\\d+\\. ", "")
                .trim();
    }

    // =========================================================
    // 🧰 BARRE DE MISE EN FORME
    // =========================================================
    private void buildFormatterRow() {
        formatRow.removeAllViews();
        addFormatBtn("B", v -> toggleStyle(Typeface.BOLD));
        addFormatBtn("I", v -> toggleStyle(Typeface.ITALIC));
        addFormatBtn("U", v -> toggleSpan(() -> new UnderlineSpan(),
                UnderlineSpan.class));
        addFormatBtn("S̶", v -> toggleSpan(() -> new StrikethroughSpan(),
                StrikethroughSpan.class));
        addSeparator();
        addFormatBtn("H1", v -> toggleBlock(RichTextCodec.BLOCK_H1));
        addFormatBtn("H2", v -> toggleBlock(RichTextCodec.BLOCK_H2));
        addFormatBtn("❝", v -> toggleBlock(RichTextCodec.BLOCK_QUOTE));
        addFormatBtn("•", v -> toggleBlock(RichTextCodec.BLOCK_UL));
        addFormatBtn("1.", v -> toggleBlock(RichTextCodec.BLOCK_OL));
        addFormatBtn("☑", v -> toggleBlock(RichTextCodec.BLOCK_CB));
        addSeparator();
        addFormatBtn("✕", v -> clearFormatting());
    }

    private void addFormatBtn(String label, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(resolveOnSurface());
        t.setPadding(dp(14), dp(6), dp(14), dp(6));
        t.setOnClickListener(l);
        formatRow.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private int resolveOnSurface() {
        TypedValue v = new TypedValue();
        getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorOnSurface, v, true);
        return v.data;
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density);
    }

    private void addSeparator() {
        View v = new View(this);
        v.setBackgroundColor(0x22000000);
        formatRow.addView(v, new LinearLayout.LayoutParams(
                1, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // =========================================================
    // 🎨 PALETTES (texte / surlignage)
    // =========================================================
    private void buildPalettes() {
        binding.btnTextColor.setOnClickListener(v -> showPalette(true));
        binding.btnHighlight.setOnClickListener(v -> showPalette(false));
    }

    private void showPalette(boolean textMode) {
        if (binding.scrollPalette.getVisibility() == View.VISIBLE
                && Boolean.valueOf(textMode).equals(binding.scrollPalette.getTag())) {
            closePalette();
            return;
        }
        paletteRow.removeAllViews();
        if (textMode) {
            for (int c : TEXT_COLORS) {
                paletteRow.addView(colorDot(c, v -> applyForegroundColor(c)));
            }
        } else {
            for (int c : HIGHLIGHT_COLORS) {
                paletteRow.addView(colorDot(c, v -> {
                    Editable e = editable();
                    int s = selectionStart();
                    int end = selectionEnd();
                    if (end > s) {
                        for (BackgroundColorSpan span : e.getSpans(s, end,
                                BackgroundColorSpan.class)) {
                            if (e.getSpanStart(span) >= s && e.getSpanEnd(span) <= end) {
                                e.removeSpan(span);
                            }
                        }
                        e.setSpan(new BackgroundColorSpan(argbWithAlpha(c)),
                                s, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    closePalette();
                }));
            }
        }
        // ✕ : texte par défaut / retirer le surlignage
        paletteRow.addView(colorDot(0xFFBDBDBD, v -> {
            Editable e = editable();
            int s = selectionStart();
            int end = selectionEnd();
            if (end > s) {
                Class<? extends Object> type = textMode
                        ? ForegroundColorSpan.class : BackgroundColorSpan.class;
                for (Object span : e.getSpans(s, end, type)) {
                    if (e.getSpanStart(span) >= s && e.getSpanEnd(span) <= end) {
                        e.removeSpan(span);
                    }
                }
            }
            closePalette();
        }));
        binding.scrollPalette.setTag(textMode);
        binding.scrollPalette.setVisibility(View.VISIBLE);
    }

    private int argbWithAlpha(int rgb) {
        // Alpha lisible sous le texte
        return (0xCC << 24) | (0xFFFFFF & rgb);
    }

    private void applyForegroundColor(int argb) {
        Editable e = editable();
        int s = selectionStart();
        int end = selectionEnd();
        if (end > s) {
            for (ForegroundColorSpan span : e.getSpans(s, end,
                    ForegroundColorSpan.class)) {
                if (e.getSpanStart(span) >= s && e.getSpanEnd(span) <= end) {
                    e.removeSpan(span);
                }
            }
            e.setSpan(new ForegroundColorSpan(argb), s, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        closePalette();
    }

    private void closePalette() {
        binding.scrollPalette.setVisibility(View.GONE);
        binding.scrollPalette.setTag(null);
    }

    private View colorDot(int argb, View.OnClickListener l) {
        View d = new View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(argb);
        bg.setStroke(2, 0x33000000);
        d.setBackground(bg);
        d.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(30), dp(30));
        p.setMargins(dp(6), 4, dp(6), 4);
        d.setLayoutParams(p);
        return d;
    }

    // =========================================================
    // 🎨 COULEUR DE CARTE
    // =========================================================
    private void buildCardColorRow() {
        cardColorRow.removeAllViews();
        for (String hex : Note.CARD_COLORS) {
            int argb = hex == null ? 0xFFFFFFFF : Color.parseColor(hex);
            View dot = new View(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(argb);
            bg.setStroke(2, hex == null ? 0x66888888 : 0x22000000);
            dot.setBackground(bg);
            dot.setOnClickListener(v -> {
                note.color = hex;
                binding.scrollCardColors.setVisibility(View.GONE);
                Toast.makeText(this, "Loko voasafidy", Toast.LENGTH_SHORT).show();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(34), dp(34));
            p.setMargins(dp(7), 4, dp(7), 4);
            dot.setLayoutParams(p);
            cardColorRow.addView(dot);
        }
    }

    // =========================================================
    // 🅱️ STYLES DE CARACTÈRE
    // =========================================================
    private void toggleStyle(int style) {
        Editable e = editable();
        int s = selectionStart();
        int end = selectionEnd();
        if (end == s) {
            hintSelection();
            return;
        }
        boolean has = false;
        for (StyleSpan span : e.getSpans(s, end, StyleSpan.class)) {
            if (span.getStyle() == style
                    && e.getSpanStart(span) <= s && e.getSpanEnd(span) >= end) {
                has = true;
                break;
            }
        }
        if (has) {
            for (StyleSpan span : e.getSpans(s, end, StyleSpan.class)) {
                if (span.getStyle() == style
                        && e.getSpanStart(span) >= s && e.getSpanEnd(span) <= end) {
                    e.removeSpan(span);
                }
            }
        } else {
            e.setSpan(new StyleSpan(style), s, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    interface SpanFactory {
        Object create();
    }

    private <T> void toggleSpan(SpanFactory factory, Class<T> type) {
        Editable e = editable();
        int s = selectionStart();
        int end = selectionEnd();
        if (end == s) {
            hintSelection();
            return;
        }
        boolean has = false;
        for (Object span : e.getSpans(s, end, type)) {
            if (e.getSpanStart(span) <= s && e.getSpanEnd(span) >= end) {
                has = true;
                break;
            }
        }
        if (has) {
            for (Object span : e.getSpans(s, end, type)) {
                if (e.getSpanStart(span) >= s && e.getSpanEnd(span) <= end) {
                    e.removeSpan(span);
                }
            }
        } else {
            e.setSpan(factory.create(), s, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void clearFormatting() {
        Editable e = editable();
        int s = selectionStart();
        int end = selectionEnd();
        if (end == s) {
            hintSelection();
            return;
        }
        for (Object span : e.getSpans(s, end, Object.class)) {
            if (span instanceof StyleSpan || span instanceof UnderlineSpan
                    || span instanceof StrikethroughSpan
                    || span instanceof ForegroundColorSpan
                    || span instanceof BackgroundColorSpan) {
                e.removeSpan(span);
            }
        }
    }

    private void hintSelection() {
        Toast.makeText(this, "Safidio ny soratra aloha", Toast.LENGTH_SHORT).show();
    }

    // =========================================================
    // 🧱 BLOCS : h1 / h2 / citation / listes / checkboxes
    // =========================================================

    /** Ranges de lignes scanées DANS le texte (pas de Layout périmé). */
    private static int[][] lineRanges(CharSequence text) {
        List<int[]> ranges = new ArrayList<>();
        int start = 0;
        int len = text.length();
        while (true) {
            int nl = -1;
            for (int i = start; i < len; i++) {
                if (text.charAt(i) == '\n') {
                    nl = i;
                    break;
                }
            }
            // [lineStartIncl, lineEndIncl] — la ligne SEUL, incluant son '\n' s'il y en a un
            ranges.add(new int[]{start, nl < 0 ? len : nl + 1});
            if (nl < 0) break;
            start = nl + 1;
            if (start > len) break;
        }
        return ranges.toArray(new int[0][]);
    }

    /**
     * Change le bloc des lignes touchées par la sélection (ou la ligne du
     * curseur). Si TOUTES ces lignes sont déjà dans ce bloc → retour plain.
     */
    private void toggleBlock(String block) {
        Editable e = editable();
        int cursor = Math.min(Math.max(0, selectionStart()), e.length());
        int selEnd = Math.min(Math.max(cursor, selectionEnd()), e.length());

        int[][] ranges = lineRanges(e);
        boolean allSame = true;
        boolean touchedAny = false;
        for (int[] r : ranges) {
            boolean intersects = r[1] > cursor && r[0] < selEnd;
            if (!intersects) continue;
            touchedAny = true;
            if (!block.equals(blockInRange(e, r))) {
                allSame = false;
            }
        }
        if (!touchedAny) return;

        String target = allSame ? RichTextCodec.BLOCK_PLAIN : block;

        // Du bas vers le haut : les éditions de préfixe ne décalent pas
        // les lignes pas encore traitées.
        for (int i = ranges.length - 1; i >= 0; i--) {
            int[] r = ranges[i];
            boolean intersects = r[1] > cursor && r[0] < selEnd;
            if (intersects) applyBlockToLine(e, r[0], r[1], target);
        }
        int safeCursor = Math.min(cursor, e.length());
        binding.richBody.setSelection(safeCursor);
    }

    private String blockInRange(Editable e, int[] range) {
        for (RichTextCodec.BlockSpan bs : e.getSpans(range[0],
                Math.min(e.length(), range[1] + 1),
                RichTextCodec.BlockSpan.class)) {
            if (e.getSpanStart(bs) <= range[0] && e.getSpanEnd(bs) > range[0]) {
                return bs.block;
            }
        }
        return RichTextCodec.BLOCK_PLAIN;
    }

    /**
     * Bloc d'UNE ligne : préfixe échangé (☐/☑, •, N.), spans de déco
     * purgés puis re-posés (taille, retré, italique grisé, marge).
     * {@code lineEndIncl} inclut le '\n' de la ligne (exception : dernière
     * ligne du texte qui ne finit pas par '\n').
     */
    private void applyBlockToLine(Editable e, int lineStartIncl,
                                  int lineEndIncl, String target) {
        int len = e.length();
        int lineEnd = Math.min(lineEndIncl, len);
        if (lineEnd <= lineStartIncl) {
            // Ligne vide : on pose quand même le bloc
            e.setSpan(new RichTextCodec.BlockSpan(target), lineStartIncl,
                    lineStartIncl, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return;
        }

        // 1️⃣ Retirer le préfixe ACTUEL s'il y en a un
        String raw = e.toString().substring(lineStartIncl, lineEnd);
        int oldPrefix = prefixLenOf(raw);
        if (oldPrefix > 0) {
            e.delete(lineStartIncl, lineStartIncl + oldPrefix);
            lineEnd -= oldPrefix;
        }

        // 2️⃣ Purge des spans de DÉCOR couvrant TOUTE la ligne
        for (Object span : e.getSpans(lineStartIncl, Math.min(len, lineEnd + 1),
                Object.class)) {
            if (e.getSpanStart(span) <= lineStartIncl
                    && e.getSpanEnd(span) >= lineEnd) {
                if (span instanceof RichTextCodec.BlockSpan
                        || span instanceof RelativeSizeSpan
                        || span instanceof LeadingMarginSpan
                        || span instanceof RichTextCodec.QuoteLookSpan
                        || span instanceof StyleSpan) {
                    e.removeSpan(span);
                }
            }
        }

        // 3️⃣ Préfixe cible
        String prefix = "";
        if (RichTextCodec.BLOCK_UL.equals(target)) {
            prefix = RichTextCodec.UL_PREFIX;
        } else if (RichTextCodec.BLOCK_OL.equals(target)) {
            prefix = "1. "; // renuméroté automatiquement au décodage
        } else if (RichTextCodec.BLOCK_CB.equals(target)) {
            prefix = RichTextCodec.CB_UNCHECKED;
        }
        if (!prefix.isEmpty()) {
            e.insert(lineStartIncl, prefix);
            lineEnd += prefix.length();
        }

        // 4️⃣ Bloc + décor
        lineEnd = Math.min(lineEnd, e.length());
        e.setSpan(new RichTextCodec.BlockSpan(target), lineStartIncl,
                lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int contentStart = Math.min(len, lineStartIncl + prefix.length());
        switch (target) {
            case RichTextCodec.BLOCK_H1:
                e.setSpan(new RelativeSizeSpan(RichTextCodec.H1_SCALE),
                        lineStartIncl, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                e.setSpan(new StyleSpan(Typeface.BOLD), contentStart, lineEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case RichTextCodec.BLOCK_H2:
                e.setSpan(new RelativeSizeSpan(RichTextCodec.H2_SCALE),
                        lineStartIncl, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                e.setSpan(new StyleSpan(Typeface.BOLD), contentStart, lineEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case RichTextCodec.BLOCK_QUOTE:
                e.setSpan(new LeadingMarginSpan.Standard(
                                RichTextCodec.QUOTE_INDENT_PX),
                        lineStartIncl, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                e.setSpan(new StyleSpan(Typeface.ITALIC), contentStart, lineEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                e.setSpan(new RichTextCodec.QuoteLookSpan(), contentStart,
                        lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case RichTextCodec.BLOCK_UL:
            case RichTextCodec.BLOCK_OL:
                e.setSpan(new LeadingMarginSpan.Standard(
                                RichTextCodec.LIST_INDENT_PX),
                        lineStartIncl, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            default:
                break;
        }
    }

    /** Longueur du préfixe visuel d'une ligne ("• ", "12. ", "☐ ", "☑ "). */
    private static int prefixLenOf(String line) {
        if (line.startsWith(RichTextCodec.UL_PREFIX)) {
            return RichTextCodec.UL_PREFIX.length();
        }
        if (line.startsWith(RichTextCodec.CB_CHECKED)) {
            return RichTextCodec.CB_CHECKED.length();
        }
        if (line.startsWith(RichTextCodec.CB_UNCHECKED)) {
            return RichTextCodec.CB_UNCHECKED.length();
        }
        // "12. " / "12) "
        int dot = line.indexOf(". ");
        int par = line.indexOf(") ");
        int cut = (dot >= 1 && (par < 0 || dot < par)) ? dot : par;
        if (cut > 0) {
            String digits = line.substring(0, cut);
            boolean ok = !digits.isEmpty();
            for (char c : digits.toCharArray()) {
                if (!Character.isDigit(c)) {
                    ok = false;
                    break;
                }
            }
            if (ok) return cut + 2;
        }
        return 0;
    }

    // =========================================================
    // 🧰 OUTILS
    // =========================================================
    private Editable editable() {
        Editable e = binding.richBody.getText();
        return e == null ? Editable.Factory.getInstance().newEditable("") : e;
    }

    private int selectionStart() {
        Editable e = editable();
        int s = binding.richBody.getSelectionStart();
        return Math.max(0, Math.min(s < 0 ? e.length() : s, e.length()));
    }

    private int selectionEnd() {
        Editable e = editable();
        int en = binding.richBody.getSelectionEnd();
        return Math.max(0, Math.min(en < 0 ? e.length() : en, e.length()));
    }

    private String sourceLabel(String sourceType) {
        if (Note.TYPE_VERSE.equals(sourceType)) return "Andininy";
        if (Note.TYPE_SONG.equals(sourceType)) return "Fihira";
        if (Note.TYPE_PROGRAM.equals(sourceType)) return "Fandaharana";
        return "Faingo";
    }

    // =========================================================
    // 🖼️ TOOLBAR : retour, ✓, couleur de carte, plus (copie/partage…)
    // =========================================================
    private void setToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = binding.toolbar;
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        binding.btnSave.setOnClickListener(v -> save(true));
        binding.btnCardColor.setOnClickListener(v -> {
            if (binding.scrollCardColors.getVisibility() == View.VISIBLE) {
                binding.scrollCardColors.setVisibility(View.GONE);
            } else {
                buildCardColorRow();
                binding.scrollCardColors.setVisibility(View.VISIBLE);
            }
        });
        binding.btnMoreBtn.setOnClickListener(v -> showMoreMenu());
    }

    private void showMoreMenu() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(note.refLabel == null || note.refLabel.isEmpty()
                        ? "Fanamarihana" : note.refLabel)
                .setItems(new CharSequence[]{
                        "Loko ny karatra",
                        "Adikao (copier)",
                        "Zaraina",
                        "Fafao"
                }, (d, which) -> {
                    if (which == 0) {
                        buildCardColorRow();
                        binding.scrollCardColors.setVisibility(View.VISIBLE);
                    } else if (which == 1) {
                        copyNote();
                    } else if (which == 2) {
                        shareNote();
                    } else {
                        confirmDelete();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String shareText() {
        String json = RichTextCodec.encode(editable());
        String text = RichTextCodec.toPlainText(json);
        if (note.refLabel != null && !note.refLabel.isEmpty()) {
            text = note.refLabel + " \u2014 " + text;
        }
        return text;
    }

    private void copyNote() {
        ClipboardManager cm = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("note", shareText()));
        }
        Toast.makeText(this, "Voakopia", Toast.LENGTH_SHORT).show();
    }

    private void shareNote() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, shareText());
        startActivity(Intent.createChooser(share, "Zaraina"));
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Ffafana ilay izy ?")
                .setMessage("Ny fanamarihana ho lasy tanteraka.")
                .setPositiveButton("Fafao", (d, w) -> {
                    if (loadedExisting) noteService.delete(note.id);
                    setResult(RESULT_OK);
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void applyGlobalTheme() {
        android.content.SharedPreferences prefs =
                getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        int savedMode = prefs.getInt("theme_mode",
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);
        ColorManager.applyTheme(this);
    }
}
