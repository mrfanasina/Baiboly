package com.fa.baiboly.ui.common;

import android.content.Context;
import android.graphics.Color;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fa.baiboly.R;
import com.fa.baiboly.data.parser.BibleRefParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liens cliquables des textes de lecture (définitions, méditations) :
 * <ul>
 *   <li>références bibliques détectées par {@link BibleRefParser} ;</li>
 *   <li>renvois du dictionnaire du type « Jereo Andriamanitra » : seul le
 *       mot cible est souligné et ouvre sa fiche ;</li>
 *   <li>tous les mots de la définition qui ont leur propre fiche : soulignés
 *       et cliquables eux aussi.</li>
 * </ul>
 *
 * <p>On n'utilise volontairement pas {@code LinkMovementMethod} : il désactive
 * la justification sur Android 10+. Le tap est résolu à la main sur
 * ACTION_UP, comme dans VersesActivity et MofonainaActivity.</p>
 */
public final class ReadingLinks {

    public interface BibleRefHandler {
        void open(String reference);
    }

    public interface DictRefHandler {
        void open(int dictId);
    }

    /** Un renvoi « Jereo … » : position du mot cible dans le texte. */
    public static final class CrossRef {
        public final int start;
        public final int end;
        public final String target;
        public final String key;

        CrossRef(int start, int end, String target, String key) {
            this.start = start;
            this.end = end;
            this.target = target;
            this.key = key;
        }
    }

    /**
     * « Jereo Andriamanitra », « jereo Fiainan-tsi-hita », « Jereo koa Kaina ».
     * Le groupe capturé est le mot cible (pas le verbe « jereo »).
     */
    private static final Pattern JEREO = Pattern.compile(
            "(?i)\\bjereo\\s+(?:koa\\s+)?([A-Za-zÀ-ÿ][A-Za-zÀ-ÿ'’\\- ]*?)"
                    + "(?=\\s*[).;,:!?\\n»«”\"]|$)");

    /** Un mot simple (apostrophes et traits d'union internes compris). */
    private static final Pattern WORD = Pattern.compile(
            "[\\p{L}]+(?:['’\\-][\\p{L}]+)*");

    /** Longueur maximale d'une vedette en nombre de mots (« Anjelin'ny Tompo »). */
    private static final int MAX_HEADWORD_WORDS = 5;

    private ReadingLinks() {
    }

    /**
     * Liste les renvois « Jereo … » d'un texte, sans accès à la base. La
     * résolution vers un identifiant de fiche se fait ensuite hors du thread
     * principal (voir DictionaryDetailActivity).
     */
    public static List<CrossRef> findCrossReferences(String text) {
        List<CrossRef> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }

        Matcher matcher = JEREO.matcher(text);
        while (matcher.find()) {
            int start = matcher.start(1);
            int end = matcher.end(1);

            // Le groupe peut inclure les espaces qui précèdent la ponctuation.
            while (start < end && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
            while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
                end--;
            }
            if (end <= start) {
                continue;
            }

            String target = text.substring(start, end);
            out.add(new CrossRef(start, end, target, normalizeTarget(target)));
        }
        return out;
    }

    /** Clé de rapprochement avec {@code bible_dictionary.headword_norm}. */
    public static String normalizeTarget(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT)
                .replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replaceAll("\\s+", " ");

        // Retire une éventuelle ponctuation finale.
        while (!value.isEmpty()
                && ").,;:!?".indexOf(value.charAt(value.length() - 1)) >= 0) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return value;
    }

    /** Applique uniquement les liens bibliques. */
    public static void applyBible(TextView textView, String text,
                                  BibleRefHandler handler) {
        apply(textView, text, null, -1, handler, null);
    }

    /**
     * Applique les liens bibliques et, si {@code dictWords} est fourni, tous
     * les mots de dictionnaire du texte. {@code dictWords} est l'index
     * {@link com.fa.baiboly.data.dictionary.DictionaryService#getWordIndex()}
     * (mot normalisé -> identifiant de fiche). {@code excludeDictId} évite de
     * souligner le mot dont on lit justement la définition.
     */
    public static void apply(TextView textView, String text,
                             Map<String, Integer> dictWords,
                             int excludeDictId,
                             BibleRefHandler bibleHandler,
                             DictRefHandler dictHandler) {
        if (textView == null) {
            return;
        }

        String source = text == null ? "" : text;
        SpannableString spannable = new SpannableString(source);
        List<int[]> usedRanges = new ArrayList<>();
        int color = linkColor(textView);
        boolean hasLinks = false;

        // Références bibliques d'abord : elles sont prioritaires sur un mot du
        // dictionnaire (« Mpanjaka », « Solomona ») présent dans la référence.
        if (bibleHandler != null) {
            for (BibleRefParser.DetectedRef detected
                    : BibleRefParser.detectReferences(source)) {
                spannable.setSpan(
                        new LinkSpan(color,
                                () -> bibleHandler.open(detected.reference)),
                        detected.start, detected.end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                usedRanges.add(new int[]{detected.start, detected.end});
                hasLinks = true;
            }
        }

        if (dictWords != null && !dictWords.isEmpty() && dictHandler != null) {
            // Renvois explicites « Jereo … » dont la cible n'apparaîtrait pas
            // comme un simple mot (ponctuation, formulation particulière).
            for (CrossRef ref : findCrossReferences(source)) {
                if (overlaps(usedRanges, ref.start, ref.end)) {
                    continue;
                }
                Integer id = dictWords.get(ref.key);
                if (id == null || id == excludeDictId) {
                    continue;
                }
                final int dictId = id;
                spannable.setSpan(
                        new LinkSpan(color, () -> dictHandler.open(dictId)),
                        ref.start, ref.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                usedRanges.add(new int[]{ref.start, ref.end});
                hasLinks = true;
            }

            if (addDictionaryWordSpans(spannable, source, dictWords,
                    excludeDictId, dictHandler, color, usedRanges)) {
                hasLinks = true;
            }
        }

        textView.setText(spannable);

        if (hasLinks) {
            installTouch(textView);
        }
    }

    /**
     * Souligne chaque mot (ou vedette de plusieurs mots) du texte qui possède
     * sa propre fiche. La correspondance la plus longue l'emporte, et les
     * plages déjà liées (références bibliques) sont laissées intactes.
     */
    private static boolean addDictionaryWordSpans(SpannableString spannable,
                                                  String source,
                                                  Map<String, Integer> dictWords,
                                                  int excludeDictId,
                                                  DictRefHandler dictHandler,
                                                  int color,
                                                  List<int[]> usedRanges) {
        List<int[]> tokens = new ArrayList<>();
        Matcher matcher = WORD.matcher(source);
        while (matcher.find()) {
            tokens.add(new int[]{matcher.start(), matcher.end()});
        }

        boolean added = false;
        int index = 0;
        while (index < tokens.size()) {
            int start = tokens.get(index)[0];
            int bestEnd = -1;
            int bestId = -1;

            int maxWords = Math.min(MAX_HEADWORD_WORDS, tokens.size() - index);
            for (int words = maxWords; words >= 1; words--) {
                int end = tokens.get(index + words - 1)[1];
                Integer id = dictWords.get(normalizeTarget(
                        source.substring(start, end)));
                if (id != null && id != excludeDictId) {
                    bestEnd = end;
                    bestId = id;
                    break;
                }
            }

            if (bestEnd <= start || overlaps(usedRanges, start, bestEnd)) {
                index++;
                continue;
            }

            final int dictId = bestId;
            spannable.setSpan(
                    new LinkSpan(color, () -> dictHandler.open(dictId)),
                    start, bestEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            usedRanges.add(new int[]{start, bestEnd});
            added = true;
            index++;
        }
        return added;
    }

    private static int linkColor(TextView textView) {
        Context context = textView.getContext();
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(
                androidx.appcompat.R.attr.colorPrimary, value, true)) {
            return value.data;
        }
        return textView.getCurrentTextColor();
    }

    private static void installTouch(TextView textView) {
        textView.setOnTouchListener(ReadingLinks::handleSpanTouch);
        textView.setHighlightColor(Color.TRANSPARENT);
        textView.setClickable(true);
        textView.setFocusable(true);
    }

    /**
     * Détection manuelle des ClickableSpan, sans MovementMethod, pour ne pas
     * casser la justification (même principe que VersesActivity).
     */
    private static boolean handleSpanTouch(View view, MotionEvent event) {
        if (!(view instanceof TextView)) {
            return false;
        }
        if (event == null || event.getAction() != MotionEvent.ACTION_UP) {
            return false;
        }

        TextView textView = (TextView) view;
        CharSequence text = textView.getText();
        if (!(text instanceof Spanned)) {
            return false;
        }
        Spanned spanned = (Spanned) text;

        Layout layout = textView.getLayout();
        if (layout == null) {
            return false;
        }

        int x = (int) event.getX() - textView.getTotalPaddingLeft()
                + textView.getScrollX();
        int y = (int) event.getY() - textView.getTotalPaddingTop()
                + textView.getScrollY();

        int line = layout.getLineForVertical(y);
        int offset = layout.getOffsetForHorizontal(line, x);

        // Ne pas détecter un tap dans le vide à droite de la ligne.
        if (x > layout.getLineWidth(line)) {
            return false;
        }

        ClickableSpan[] spans = spanned.getSpans(
                Math.max(0, offset - 1),
                Math.min(spanned.length(), offset + 1),
                ClickableSpan.class);

        for (ClickableSpan span : spans) {
            int start = spanned.getSpanStart(span);
            int end = spanned.getSpanEnd(span);
            if (offset >= start - 1 && offset <= end + 1) {
                span.onClick(textView);
                return true;
            }
        }
        return false;
    }

    private static boolean overlaps(List<int[]> ranges, int start, int end) {
        for (int[] range : ranges) {
            if (start < range[1] && end > range[0]) {
                return true;
            }
        }
        return false;
    }

    private static final class LinkSpan extends ClickableSpan {
        private final int color;
        private final Runnable action;

        LinkSpan(int color, Runnable action) {
            this.color = color;
            this.action = action;
        }

        @Override
        public void onClick(@NonNull View widget) {
            action.run();
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            super.updateDrawState(ds);
            ds.setColor(color);
            ds.setUnderlineText(true);
        }
    }
}
