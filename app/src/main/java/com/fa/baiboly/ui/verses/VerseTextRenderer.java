package com.fa.baiboly.ui.verses;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.UnderlineSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.fa.baiboly.R;
import com.fa.baiboly.data.bible.BibleService;
import com.fa.baiboly.data.parser.BibleRefParser;
import com.fa.baiboly.data.marks.MarkService;
import com.fa.baiboly.models.Annotation;
import com.fa.baiboly.models.Reading;
import com.fa.baiboly.models.Verse;
import com.fa.baiboly.models.VerseMark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Construit le SpannableStringBuilder d'une lecture : cartes de chapitre,
 * titres, numéros, surlignages persistants, favoris, sélection, annotations
 * et références bibliques cliquables.
 *
 * Un renderer indépendant de toute Activity permet au même rendu d'être
 * utilisé par toutes les pages du ViewPager2 (VersePageFragment), avec
 * exactement les mêmes fonctionnalités quel que soit le mode d'ouverture.
 */
public final class VerseTextRenderer {

    /** Familles de couleur : la teinte réelle est résolue selon le thème. */
    public static final String[] HIGHLIGHT_FAMILIES = {
            "yellow", "green", "blue", "pink"
    };

    /** Callbacks du renderer, fournis par l'écran hôte. */
    public interface Callbacks {

        /** Tap sur une annotation -> afficher la bulle d'explication. */
        void onAnnotationClicked(String word, String annotation);

        /** Tap sur une référence biblique soulignée -> ouvrir l'aperçu. */
        void onBibleReferenceClicked(String reference);

        /** Tap sur un verset -> bascule de la sélection. */
        void onVerseToggled(int chapter, int verseNumber);
    }

    private final BibleService bibleService;

    /** Clés de sélection "chapitre:verset" en cours (peut être null). */
    private final Set<String> selectedVerseKeys;

    private final Callbacks callbacks;

    private final boolean nightMode;

    public VerseTextRenderer(@NonNull Context context,
                             @NonNull BibleService bibleService,
                             @Nullable Set<String> selectedVerseKeys,
                             @Nullable Callbacks callbacks) {
        this.bibleService = bibleService;
        this.selectedVerseKeys = selectedVerseKeys;
        this.callbacks = callbacks;
        this.nightMode = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    // =====================================================
    // API PRINCIPALE
    // =====================================================

    /**
     * Construit le texte formaté d'une lecture.
     *
     * @param context     contexte pour les couleurs du thème
     * @param verseList   versets de la lecture (ordre base de données)
     * @param reading     lecture en cours (livre / chapitre de départ)
     * @param markService service des marques (surlignages + favoris), ou null
     */
    public SpannableStringBuilder buildReadingText(@NonNull Context context,
                                                   @NonNull List<Verse> verseList,
                                                   @NonNull Reading reading,
                                                   @Nullable MarkService markService) {

        SpannableStringBuilder builder = new SpannableStringBuilder();

        int primaryColor = ContextCompat.getColor(context, R.color.teal_200);
        int titleColor = ContextCompat.getColor(context, R.color.title_gray);

        int currentChapter = reading.getStartChapter();

        int bookColor;
        try {
            bookColor = Color.parseColor(reading.getBook().getColor());
        } catch (Exception e) {
            bookColor = primaryColor;
        }

        String bookShortName = reading.getBook().getShortName();
        String bookLongName = (reading.getBook().getLongName() != null)
                ? reading.getBook().getLongName() : bookShortName;

        // Marques du chapitre courant (highlight + favoris), rechargées
        // à chaque changement de chapitre dans la lecture.
        Map<Integer, List<VerseMark>> currentChapterMarks = new HashMap<>();

        int previousVerseNumber = -1;

        for (int i = 0; i < verseList.size(); i++) {

            Verse v = verseList.get(i);
            int verseNumber = v.getNumber();

            // =====================================================
            // CARTES DE CHAPITRE (au changement de chapitre)
            // =====================================================
            if (i == 0 || verseNumber < previousVerseNumber) {

                if (i > 0) {
                    currentChapter++;
                    builder.append("\n\n\n");
                }

                if (markService != null) {
                    currentChapterMarks = markService.getMarksForChapter(
                            bookShortName, currentChapter);
                } else {
                    currentChapterMarks.clear();
                }

                appendChapterCard(builder, bookLongName, currentChapter, bookColor);
            }

            previousVerseNumber = verseNumber;

            // =====================================================
            // TITRE DE PÉRICOPÉ
            // =====================================================
            if (v.getTitle() != null && !v.getTitle().trim().isEmpty()) {

                int startTitle = builder.length();

                builder.append("\n").append(v.getTitle().trim()).append("\n");
                int endTitle = builder.length();

                builder.setSpan(
                        new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                        startTitle, endTitle,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                builder.setSpan(
                        new StyleSpan(Typeface.BOLD_ITALIC),
                        startTitle, endTitle,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                builder.setSpan(
                        new ForegroundColorSpan(titleColor),
                        startTitle, endTitle,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                builder.setSpan(
                        new RelativeSizeSpan(0.85f),
                        startTitle, endTitle,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // =====================================================
            // NUMÉRO DE VERSET
            // =====================================================
            String vNumStr = verseNumber + " ";

            int startNum = builder.length();
            builder.append(vNumStr);
            int endNum = builder.length();

            builder.setSpan(new ForegroundColorSpan(primaryColor),
                    startNum, endNum, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new RelativeSizeSpan(0.7f),
                    startNum, endNum, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new StyleSpan(Typeface.BOLD),
                    startNum, endNum, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new SuperscriptSpan(),
                    startNum, endNum, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            // =====================================================
            // TEXTE DU VERSET
            // =====================================================
            String rawText = v.getText()
                    .replace("\n", " ")
                    .replace("\r", "")
                    .trim() + " ";

            int verseTextStart = builder.length();
            builder.append(rawText);
            int verseFullEnd = builder.length();

            final int chapterForSpan = currentChapter;

            // Span invisible retrouvé au moment du tap (chapitre + numéro)
            builder.setSpan(
                    new VerseRangeSpan(chapterForSpan, verseNumber),
                    startNum, verseFullEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            // =====================================================
            // SURLIGNAGE PERSISTANT + FAVORI (base de données)
            // =====================================================
            boolean isFavoriteVerse = false;

            List<VerseMark> marksHere = currentChapterMarks.get(verseNumber);
            if (marksHere != null) {
                for (VerseMark m : marksHere) {

                    if (MarkService.TYPE_HIGHLIGHT.equals(m.getType())) {

                        builder.setSpan(
                                new BackgroundColorSpan(resolveHighlightColor(m.getColor())),
                                startNum, verseFullEnd,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                        builder.setSpan(
                                new ForegroundColorSpan(getHighlightTextColor()),
                                verseTextStart, verseFullEnd,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                    } else if (MarkService.TYPE_FAVORITE.equals(m.getType())) {
                        isFavoriteVerse = true;
                    }
                }
            }

            // Favori : colore tout le verset (prend le pas sur le surlignage)
            if (isFavoriteVerse) {
                builder.setSpan(
                        new ForegroundColorSpan(getFavoriteColor()),
                        startNum, verseFullEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // =====================================================
            // SÉLECTION EN COURS : soulignement
            // =====================================================
            if (selectedVerseKeys != null
                    && selectedVerseKeys.contains(chapterForSpan + ":" + verseNumber)) {
                builder.setSpan(
                        new UnderlineSpan(),
                        startNum, verseFullEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // =====================================================
            // ANNOTATIONS + RÉFÉRENCES BIBLIQUES CLIQUABLES
            // =====================================================
            List<Annotation> annotations = bibleService.getAnnotationsForVerse(v.getId());

            List<int[]> annotationRanges = new ArrayList<>();

            for (Annotation ann : annotations) {

                int start = verseTextStart + ann.getStartPosition();
                int end = verseTextStart + ann.getEndPosition();

                if (start < 0 || end > builder.length() || start >= end) {
                    continue;
                }

                annotationRanges.add(new int[]{start, end});
            }

            addBibleReferenceSpans(builder, rawText, verseTextStart, annotationRanges);

            for (Annotation ann : annotations) {

                int start = verseTextStart + ann.getStartPosition();
                int end = verseTextStart + ann.getEndPosition();

                if (start < 0 || end > builder.length() || start >= end) {
                    continue;
                }

                builder.setSpan(new ForegroundColorSpan(primaryColor),
                        start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new StyleSpan(Typeface.BOLD),
                        start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new AnnotationMarker(ann.getAnnotation()),
                        start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new UnderlineSpan(),
                        start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        return builder;
    }

    // =====================================================
    // CARTES DE CHAPITRE
    // =====================================================

    private void appendChapterCard(SpannableStringBuilder builder,
                                   String bookLongName,
                                   int currentChapter,
                                   int bookColor) {

        String cardText = bookLongName.toUpperCase() + " | " + currentChapter;

        int startCard = builder.length();
        builder.append(cardText);
        int endCard = builder.length();

        builder.setSpan(new ChapterCardSpan(bookColor, Color.WHITE, cardText),
                startCard, endCard, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new StyleSpan(Typeface.BOLD),
                startCard, endCard, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new RelativeSizeSpan(1.1f),
                startCard, endCard, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                startCard, endCard, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        builder.append("\n\n");
    }

    // =====================================================
    // RÉFÉRENCES BIBLIQUES CLIQUABLES
    // =====================================================

    private void addBibleReferenceSpans(SpannableStringBuilder builder,
                                        String rawText,
                                        int textStart,
                                        List<int[]> excludedRanges) {

        if (callbacks == null) return;

        List<BibleRefParser.DetectedRef> detected =
                BibleRefParser.detectReferences(rawText);

        for (BibleRefParser.DetectedRef dr : detected) {

            int start = textStart + dr.start;
            int end = textStart + dr.end;

            if (overlapsAnyRange(excludedRanges, start, end)) {
                continue;
            }

            final String reference = dr.reference;

            builder.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    callbacks.onBibleReferenceClicked(reference);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setUnderlineText(true);
                }
            }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private boolean overlapsAnyRange(List<int[]> ranges, int start, int end) {
        for (int[] r : ranges) {
            if (start < r[1] && end > r[0]) {
                return true;
            }
        }
        return false;
    }

    // =====================================================
    // COULEURS THÈME CLAIR / SOMBRE
    // =====================================================

    /** Résout la couleur de fond d'un surlignage (teintes douces en sombre). */
    public int resolveHighlightColor(String stored) {

        if (stored == null) stored = "";

        switch (stored) {
            case "yellow":
                return Color.argb(60, 185, 170, 70);
            case "green":
                return Color.argb(60, 90, 170, 100);
            case "blue":
                return Color.argb(60, 80, 90, 200);
            case "pink":
                return Color.argb(60, 180, 120, 160);
            default:
                try {
                    return Color.parseColor(stored);
                } catch (Exception e) {
                    return Color.parseColor(nightMode ? "#8E7A2E" : "#FFF59D");
                }
        }
    }

    /** Couleur du texte sur un verset surligné (lisible quel que soit le thème). */
    public int getHighlightTextColor() {
        return nightMode
                ? Color.parseColor("#F1F1F1")
                : Color.parseColor("#1A1A1A");
    }

    /** Couleur du texte d'un verset favori. */
    public int getFavoriteColor() {
        return nightMode
                ? Color.parseColor("#FFFBE6")
                : Color.parseColor("#8D6E00");
    }
}
