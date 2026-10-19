package com.fa.baiboly.data.notes.richtext;

import android.graphics.Typeface;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.ParagraphStyle;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 🧬 Moteur de texte enrichi des notes : Spannable ↔ JSON stable.
 *
 * Format :
 * <pre>
 * { "version": 1,
 *   "lines": [
 *     { "block": "plain|h1|h2|quote|ul|ol|cb",
 *       "checked": true,                          // uniquement "cb"
 *       "segs": [ { "t": "texte",
 *                   "st": 1, "u": 1, "s": 1,     // gras/italique…, souligné, barré
 *                   "c": "#RRGGBB", "bg": "#AARRGGBB" } ] } ] }
 * </pre>
 * Règles :
 * • Les styles de BLOC (h1/h2/quote/ul/ol/cb) ne sont pas mis en "segs" —
 *   leur rendu est régénéré au décodage (taille, retrait, prefixe "• "/"N. ",
 *   ☐/☑, italique grisé) ;
 * • Les seuls styles de SEGMENT stockés : gras/italique/souligné/barré,
 *   couleur du texte, couleur de surlignage ;
 * • Le Spannable d'entrée retombe toujours sur '\n' (la DERNIÈRE ligne est
 *   suivie d'un '\n') : c'est le format canonique, l'éditeur affiche
 *   naturellement le curseur sur une nouvelle ligne.
 */
public final class RichTextCodec {

    public static final String BLOCK_PLAIN = "plain";
    public static final String BLOCK_H1 = "h1";
    public static final String BLOCK_H2 = "h2";
    public static final String BLOCK_QUOTE = "quote";
    public static final String BLOCK_UL = "ul";
    public static final String BLOCK_OL = "ol";
    public static final String BLOCK_CB = "cb";

    public static final String UL_PREFIX = "• ";
    public static final String CB_UNCHECKED = "☐ ";
    public static final String CB_CHECKED = "☑ ";

    public static final float H1_SCALE = 1.35f;
    public static final float H2_SCALE = 1.18f;
    public static final int LIST_INDENT_PX = 26;
    public static final int QUOTE_INDENT_PX = 40;

    private static final int SPAN_FLAGS = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;

    private RichTextCodec() {
    }

    // =========================================================
    // 🏷️ SPANS PROPRES AU MOTEUR
    // =========================================================

    /** Type de bloc d'une ligne (style de paragraphe, éditable). */
    public static class BlockSpan implements ParagraphStyle {
        public final String block;

        public BlockSpan(String block) {
            this.block = block;
        }
    }

    /** Fin de contexte : rendu italique grisé d'une ligne "quote". */
    public static class QuoteLookSpan extends CharacterStyle {
        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setColor(0xFF8A8A8A);
        }
    }

    // =========================================================
    // 🔖 NOTE NEUVE : lecture proposée en citation + lignevide
    // =========================================================

    /** Corps initial d'une note : la lecture citée + une ligne de suite. */
    public static String encodeQuotedStart(String quotedText) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(quotedText).append('\n');
        sb.setSpan(new BlockSpan(BLOCK_QUOTE), 0, sb.length(), SPAN_FLAGS);
        sb.setSpan(new StyleSpan(Typeface.ITALIC), 0, quotedText.length(),
                SPAN_FLAGS);
        sb.setSpan(new QuoteLookSpan(), 0, quotedText.length(), SPAN_FLAGS);
        sb.append('\n');
        return encode(sb);
    }

    // =========================================================
    // ⬇️ ENCODAGE : Spannable (édité) → JSON
    // =========================================================
    public static String encode(CharSequence cs) {
        Spanned spanned = cs instanceof Spanned
                ? (Spanned) cs
                : SpannableStringBuilder.valueOf(cs == null ? "" : cs);
        try {
            String full = spanned.toString();
            JSONArray lines = new JSONArray();

            int lineStart = 0;
            while (true) {
                int lineEnd = full.indexOf('\n', lineStart);
                if (lineEnd < 0) lineEnd = full.length();
                lines.put(encodeLine(spanned, lineStart, lineEnd));
                if (lineEnd >= full.length() || lineEnd + 1 > full.length()) break;
                lineStart = lineEnd + 1;
            }

            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("lines", lines);
            return root.toString();
        } catch (Exception e) {
            // 🛟 le texte brut n'est JAMAIS perdu
            return plainFallback(spanned.toString());
        }
    }

    private static String plainFallback(String text) {
        try {
            JSONObject root = new JSONObject();
            JSONArray lines = new JSONArray();
            JSONObject line = new JSONObject();
            line.put("block", BLOCK_PLAIN);
            JSONArray segs = new JSONArray();
            segs.put(new JSONObject().put("t",
                    text == null ? "" : text));
            line.put("segs", segs);
            lines.put(line);
            root.put("version", 1);
            root.put("lines", lines);
            return root.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static JSONObject encodeLine(Spanned spanned, int lineStart,
                                         int lineEnd) throws Exception {
        String full = spanned.toString();
        String raw = full.substring(lineStart, lineEnd);

        // 1️⃣ Bloc de la ligne (span posé jusqu'au '\n' inclus)
        String block = BLOCK_PLAIN;
        int searchEnd = Math.min(spanned.length(), lineEnd + 1);
        for (BlockSpan bs : spanned.getSpans(lineStart, searchEnd, BlockSpan.class)) {
            if (spanned.getSpanStart(bs) <= lineStart
                    && spanned.getSpanEnd(bs) > lineStart) {
                block = bs.block;
                break;
            }
        }

        // 2️⃣ Texte sans le préfixe visuel (régénéré au décodage)
        String content = raw;
        int prefixLen = 0;
        if (BLOCK_UL.equals(block)) {
            if (content.startsWith(UL_PREFIX)) prefixLen = UL_PREFIX.length();
        } else if (BLOCK_OL.equals(block)) {
            int dot = content.contains(". ")
                    ? content.indexOf(". ") : content.indexOf(") ");
            if (dot > 0 && isDigits(content.substring(0, dot))) {
                prefixLen = dot + 2;
            }
        } else if (BLOCK_CB.equals(block)) {
            if (content.startsWith(CB_CHECKED)) prefixLen = CB_CHECKED.length();
            else if (content.startsWith(CB_UNCHECKED))
                prefixLen = CB_UNCHECKED.length();
        }
        content = content.substring(Math.min(prefixLen, content.length()));

        JSONObject line = new JSONObject();
        line.put("block", block);
        if (BLOCK_CB.equals(block)) {
            line.put("checked", raw.startsWith(CB_CHECKED));
        }

        // 3️⃣ Segments enrichis (format identique fusionné)
        JSONArray segs = new JSONArray();
        List<int[]> boundaries = segmentBoundaries(spanned, lineStart + prefixLen,
                lineEnd);
        String lastKey = null;
        int mergeStart = -1;
        int mergeEnd = -1;
        for (int[] interval : boundaries) {
            int a = Math.max(interval[0], lineStart + prefixLen);
            int b = interval[1];
            if (b <= a) continue;
            String key = formatKey(spanned, a, b);
            if (key.equals(lastKey)) {
                mergeEnd = b;
            } else {
                if (mergeStart >= 0) {
                    segs.put(segJson(spanned, lineStart + prefixLen,
                            mergeStart, mergeEnd));
                }
                mergeStart = a;
                mergeEnd = b;
                lastKey = key;
            }
        }
        if (mergeStart >= 0) {
            segs.put(segJson(spanned, lineStart + prefixLen,
                    mergeStart, mergeEnd));
        }
        if (segs.length() == 0) {
            segs.put(new JSONObject().put("t", ""));
        }
        line.put("segs", segs);
        return line;
    }

    private static boolean isDigits(String s) {
        if (s == null || s.isEmpty()) return false;
        for (char c : s.toCharArray()) {
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }

    private static JSONObject segJson(Spanned spanned, int contentStart,
                                      int start, int end) throws Exception {
        JSONObject o = new JSONObject();
        String full = spanned.toString();
        o.put("t", full.substring(start, end));

        int style = 0;
        boolean underline = false;
        boolean strike = false;
        String fg = "";
        String bg = "";

        for (Object span : spanned.getSpans(start, end, CharacterStyle.class)) {
            if (span instanceof StyleSpan) {
                style |= ((StyleSpan) span).getStyle();
            } else if (span instanceof UnderlineSpan) {
                underline = true;
            } else if (span instanceof StrikethroughSpan) {
                strike = true;
            } else if (span instanceof ForegroundColorSpan) {
                fg = hex6(((ForegroundColorSpan) span).getForegroundColor());
            } else if (span instanceof BackgroundColorSpan) {
                bg = hex8(((BackgroundColorSpan) span).getBackgroundColor());
            }
        }
        if (style != 0) o.put("st", style);
        if (underline) o.put("u", 1);
        if (strike) o.put("s", 1);
        if (!fg.isEmpty()) o.put("c", fg);
        if (!bg.isEmpty()) o.put("bg", bg);
        return o;
    }

    /** Frontières clippées : [contentStart, lineEnd] découpé par les styles. */
    private static List<int[]> segmentBoundaries(Spanned spanned,
                                                 int contentStart, int lineEnd) {
        List<Integer> cuts = new ArrayList<>();
        cuts.add(contentStart);
        cuts.add(lineEnd);
        for (Object span : spanned.getSpans(contentStart, lineEnd,
                CharacterStyle.class)) {
            if (span instanceof QuoteLookSpan || span instanceof RelativeSizeSpan) {
                continue; // décor du bloc, jamais une frontière de segment
            }
            int a = spanned.getSpanStart(span);
            int b = spanned.getSpanEnd(span);
            if (a > contentStart && a < lineEnd) cuts.add(a);
            if (b > contentStart && b < lineEnd) cuts.add(b);
        }
        java.util.Collections.sort(cuts);

        List<int[]> out = new ArrayList<>();
        int prev = -1;
        for (int c : cuts) {
            if (c == prev) continue;
            if (prev >= 0 && c > prev) {
                out.add(new int[]{prev, c});
            }
            prev = c;
        }
        return out;
    }

    /** Clé compacte du format de caractère d'une tranche. */
    private static String formatKey(Spanned spanned, int a, int b) {
        int style = 0;
        boolean underline = false;
        boolean strike = false;
        String fg = "";
        String bg = "";
        for (Object span : spanned.getSpans(a, b, CharacterStyle.class)) {
            if (span instanceof StyleSpan) {
                style |= ((StyleSpan) span).getStyle();
            } else if (span instanceof UnderlineSpan) {
                underline = true;
            } else if (span instanceof StrikethroughSpan) {
                strike = true;
            } else if (span instanceof ForegroundColorSpan) {
                fg = hex6(((ForegroundColorSpan) span).getForegroundColor());
            } else if (span instanceof BackgroundColorSpan) {
                bg = hex8(((BackgroundColorSpan) span).getBackgroundColor());
            }
        }
        return style + "|" + underline + "|" + strike + "|" + fg + "|" + bg;
    }

    private static String hex6(int argb) {
        return String.format(Locale.US, "#%06X", 0xFFFFFF & argb);
    }

    private static String hex8(int argb) {
        return String.format(Locale.US, "#%08X", argb);
    }

    // =========================================================
    // ⬆️ DÉCODAGE : JSON → Spannable
    // =========================================================
    public static SpannableStringBuilder decode(@Nullable String json) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (json == null || json.isEmpty()) return sb;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray lines = root.optJSONArray("lines");
            if (lines == null) return plainFallbackText(json);

            int olCounter = 0;
            for (int i = 0; i < lines.length(); i++) {
                JSONObject line = lines.getJSONObject(i);
                String block = line.optString("block", BLOCK_PLAIN);
                boolean isOl = BLOCK_OL.equals(block);
                olCounter = isOl ? olCounter + 1 : 0;

                int lineStart = sb.length();
                String prefix = "";
                if (BLOCK_UL.equals(block)) prefix = UL_PREFIX;
                else if (isOl) prefix = olCounter + ". ";
                else if (BLOCK_CB.equals(block)) {
                    prefix = line.optBoolean("checked") ? CB_CHECKED : CB_UNCHECKED;
                }
                sb.append(prefix);
                int contentStart = sb.length();

                JSONArray segs = line.optJSONArray("segs");
                if (segs != null) {
                    for (int k = 0; k < segs.length(); k++) {
                        JSONObject seg = segs.getJSONObject(k);
                        int segStart = sb.length();
                        sb.append(seg.optString("t", ""));
                        int segEnd = sb.length();
                        if (segEnd > segStart) {
                            applySeg(sb, seg, segStart, segEnd);
                        }
                    }
                }
                int lineEnd = sb.length();

                sb.append('\n');
                // Le bloc couvre le '\n' : c'est LE span de paragraphe de la
                // ligne, il invalidera le bon positionnement au rechargement
                // et confondra jamais avec la ligne suivante.
                applyBlock(sb, block, lineStart, contentStart, sb.length());
            }
            return sb;
        } catch (Exception e) {
            return plainFallbackText(json);
        }
    }

    private static void applySeg(SpannableStringBuilder sb, JSONObject seg,
                                 int start, int end) {
        int style = seg.optInt("st", 0);
        if (style != 0) {
            sb.setSpan(new StyleSpan(style), start, end, SPAN_FLAGS);
        }
        if (seg.optInt("u", 0) != 0) {
            sb.setSpan(new UnderlineSpan(), start, end, SPAN_FLAGS);
        }
        if (seg.optInt("s", 0) != 0) {
            sb.setSpan(new StrikethroughSpan(), start, end, SPAN_FLAGS);
        }
        String fg = seg.optString("c", "");
        if (!fg.isEmpty()) {
            try {
                sb.setSpan(new ForegroundColorSpan((int) Long.parseLong(
                        fg.replace("#", ""), 16)), start, end, SPAN_FLAGS);
            } catch (Exception ignored) {
            }
        }
        String bg = seg.optString("bg", "");
        if (!bg.isEmpty()) {
            try {
                sb.setSpan(new BackgroundColorSpan((int) Long.parseLong(
                        bg.replace("#", ""), 16)), start, end, SPAN_FLAGS);
            } catch (Exception ignored) {
            }
        }
    }

    private static void applyBlock(SpannableStringBuilder sb, String block,
                                   int lineStart, int contentStart, int lineEnd) {
        sb.setSpan(new BlockSpan(block), lineStart, lineEnd, SPAN_FLAGS);
        switch (block) {
            case BLOCK_H1:
                sb.setSpan(new RelativeSizeSpan(H1_SCALE), lineStart, lineEnd,
                        SPAN_FLAGS);
                sb.setSpan(new StyleSpan(Typeface.BOLD), contentStart, lineEnd,
                        SPAN_FLAGS);
                break;
            case BLOCK_H2:
                sb.setSpan(new RelativeSizeSpan(H2_SCALE), lineStart, lineEnd,
                        SPAN_FLAGS);
                sb.setSpan(new StyleSpan(Typeface.BOLD), contentStart, lineEnd,
                        SPAN_FLAGS);
                break;
            case BLOCK_QUOTE:
                sb.setSpan(new LeadingMarginSpan.Standard(QUOTE_INDENT_PX),
                        lineStart, lineEnd, SPAN_FLAGS);
                sb.setSpan(new StyleSpan(Typeface.ITALIC), contentStart,
                        lineEnd, SPAN_FLAGS);
                sb.setSpan(new QuoteLookSpan(), contentStart, lineEnd, SPAN_FLAGS);
                break;
            case BLOCK_UL:
            case BLOCK_OL:
                sb.setSpan(new LeadingMarginSpan.Standard(LIST_INDENT_PX),
                        lineStart, lineEnd, SPAN_FLAGS);
                break;
            default:
                break;
        }
    }

    private static SpannableStringBuilder plainFallbackText(String text) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(text == null ? "" : text);
        if (sb.length() == 0 || sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
        return sb;
    }

    // =========================================================
    // 📋 APERÇUS (liste des notes, partage)
    // =========================================================

    /**
     * Titre + snippet : titre = première ligne (et SEULEMENT si h1/h2 ou
     * note libre) ; la citation d'introduction ne compte jamais comme titre.
     */
    public static String[] titleAndSnippet(@Nullable String json,
                                           boolean hasRef) {
        String[] out = {"", ""};
        if (json == null || json.isEmpty()) return out;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray lines = root.optJSONArray("lines");
            if (lines == null) return out;
            int found = 0;
            for (int i = 0; i < lines.length() && found < 2; i++) {
                JSONObject line = lines.getJSONObject(i);
                String block = line.optString("block", BLOCK_PLAIN);
                boolean isTitle = BLOCK_H1.equals(block) || BLOCK_H2.equals(block);
                // styled titles idx 0 enn note libre sans ref
                if (i == 0 && hasRef && !isTitle
                        && BLOCK_QUOTE.equals(block)) {
                    continue; // citation de la lecture → pas un titre
                }
                StringBuilder text = new StringBuilder();
                JSONArray segs = line.optJSONArray("segs");
                if (segs != null) {
                    for (int k = 0; k < segs.length(); k++) {
                        text.append(segs.getJSONObject(k).optString("t", ""));
                    }
                }
                String t = text.toString().trim();
                if (t.isEmpty()) continue;
                out[found++] = t;
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Texte brut complet (sans préfixes visuels) — recherche, copie… */
    public static String toPlainText(@Nullable String json) {
        try {
            JSONArray lines = new JSONObject(json == null ? "" : json)
                    .optJSONArray("lines");
            if (lines == null) return "";
            StringBuilder sb = new StringBuilder();
            int olCounter = 0;
            for (int i = 0; i < lines.length(); i++) {
                JSONObject line = lines.getJSONObject(i);
                String block = line.optString("block", BLOCK_PLAIN);
                boolean isOl = BLOCK_OL.equals(block);
                olCounter = isOl ? olCounter + 1 : 0;
                if (BLOCK_UL.equals(block)) sb.append(UL_PREFIX);
                else if (isOl) sb.append(olCounter).append(". ");
                else if (BLOCK_CB.equals(block)) {
                    sb.append(line.optBoolean("checked") ? CB_CHECKED : CB_UNCHECKED);
                }
                JSONArray segs = line.optJSONArray("segs");
                if (segs != null) {
                    for (int k = 0; k < segs.length(); k++) {
                        sb.append(segs.getJSONObject(k).optString("t", ""));
                    }
                }
                sb.append('\n');
            }
            return sb.toString().trim().replaceAll("\n{2,}", "\n");
        } catch (Exception ignored) {
            return "";
        }
    }
}
