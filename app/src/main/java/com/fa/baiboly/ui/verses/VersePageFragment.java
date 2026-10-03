package com.fa.baiboly.ui.verses;

import android.graphics.text.LineBreaker;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.fa.baiboly.R;
import com.fa.baiboly.data.bible.BibleService;
import com.fa.baiboly.data.marks.MarkService;
import com.fa.baiboly.data.parser.ReadingParser;
import com.fa.baiboly.models.Reading;
import com.fa.baiboly.models.Verse;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Une page du ViewPager2 de VersesActivity : affiche une lecture avec
 * TOUTES les fonctionnalités (tap sur verset -> sélection, annotations,
 * références cliquables, surlignages persistants, favoris, réglages texte).
 *
 * La page délègue l'état de sélection et les actions de la barre flottante
 * à VersesActivity via l'interface Host, ce qui permet à la sélection de
 * survivre aux swipes et de fonctionner à travers plusieurs chapitres.
 */
public class VersePageFragment extends Fragment implements VerseTextRenderer.Callbacks {

    private static final String ARG_READING = "reading";

    /** Communication page -> activity hôte. */
    public interface Host {

        /** Sélection partagée entre toutes les pages (clés "chapitre:verset"). */
        Set<String> getSelectedVerseKeys();

        /** Recalcule la barre flottante après un changement de sélection. */
        void onSelectionChanged();

        /** Services partagés (une seule instance DB pour toute l'activity). */
        BibleService getBibleService();

        MarkService getMarkService();

        /** Tap sur une référence biblique -> bottom sheet d'aperçu. */
        void openBibleReference(String reference);

        /** Tap sur une annotation -> dialog flottant. */
        void showAnnotationDialog(String word, String annotation);
    }

    private TextView textContent;

    private Reading reading;

    private List<Verse> loadedVerses;

    private VerseTextRenderer renderer;

    public static VersePageFragment newInstance(String reading) {
        VersePageFragment fragment = new VersePageFragment();
        Bundle args = new Bundle();
        args.putString(ARG_READING, reading);
        fragment.setArguments(args);
        return fragment;
    }

    private Host getPageHost() {
        return (getActivity() instanceof Host) ? (Host) getActivity() : null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_verse_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textContent = view.findViewById(R.id.textContent);

        String readingStr = getArguments() != null
                ? getArguments().getString(ARG_READING) : null;

        if (readingStr == null) return;

        Host host = getPageHost();

        BibleService bibleService = (host != null)
                ? host.getBibleService()
                : new BibleService(requireContext());
        MarkService markService = (host != null) ? host.getMarkService() : null;

        ReadingParser parser = new ReadingParser();
        parser.setService(bibleService);
        reading = parser.parse(readingStr);

        if (reading == null) return;

        renderer = new VerseTextRenderer(
                requireContext(),
                bibleService,
                (host != null) ? host.getSelectedVerseKeys() : null,
                this);

        loadAndDisplayVerses(bibleService, markService);

        applyTextSettings();

        setupTalkWithAiButton();
    }

    // =====================================================
    // BOUTON IA (fin de page)
    // =====================================================

    /** Explique toute la lecture de la page avec l'IA. */
    private void setupTalkWithAiButton() {

        View btnTalk = requireView().findViewById(R.id.btnTalkWithAI);

        btnTalk.setOnClickListener(v -> {

            if (reading == null || loadedVerses == null || loadedVerses.isEmpty()) {
                android.widget.Toast.makeText(requireContext(),
                        "Tsy misy andininy voatanisa", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            StringBuilder texteComplet = new StringBuilder();
            for (Verse verse : loadedVerses) {
                texteComplet.append(verse.getText().trim()).append(" ");
            }

            com.fa.baiboly.ui.ai.AiExplanationActivity.start(getActivity(),
                    reading.toString(), texteComplet.toString().trim());
        });
    }

    // =====================================================
    // RENDU
    // =====================================================

    private void loadAndDisplayVerses(BibleService bibleService, MarkService markService) {

        if (reading == null || renderer == null || textContent == null) return;

        // Chargement SYNCHRONE, comme dans les chants : la page voisine est
        // créée HORS ÉCRAN quand on se pose sur la page courante, donc une
        // petite latence de construction à ce moment-là est invisible. En
        // revanche, au moment du swipe, son contenu est déjà prêt -> les
        // aperçus glissent à l'écran comme dans Fihirana.
        List<Verse> verseList = bibleService.getVerseObjectsFromReading(reading);
        if (verseList == null || verseList.isEmpty()) return;

        loadedVerses = verseList;

        textContent.setText(
                renderer.buildReadingText(requireContext(), verseList, reading, markService));

        // Le handleTouch est un OnTouchListener posé sur le TextView :
        // il gère annotations, références et tap sur verset. Le ScrollView
        // reste maître du scroll et du swipe horizontal du ViewPager2.
        textContent.setOnTouchListener(this::handleTouch);
    }

    /**
     * Re-rend le texte (marques et sélection modifiées). Appelé quand la
     * sélection change depuis une autre page ou la barre flottante.
     */
    public void refreshRendering() {

        if (reading == null || !isAdded() || getView() == null) return;

        Host host = getPageHost();
        BibleService bibleService = (host != null)
                ? host.getBibleService()
                : new BibleService(requireContext());
        MarkService markService = (host != null) ? host.getMarkService() : null;

        loadAndDisplayVerses(bibleService, markService);
    }

    // =====================================================
    // TOUCH : annotations > références > tap sur verset
    // =====================================================

    private boolean handleTouch(View view, MotionEvent motionEvent) {

        if (motionEvent == null
                || motionEvent.getAction() != MotionEvent.ACTION_UP) {
            return false;
        }

        TextView textView = (TextView) view;

        CharSequence text = textView.getText();
        if (!(text instanceof android.text.Spanned)) {
            return false;
        }
        android.text.Spanned spanned = (android.text.Spanned) text;

        Layout layout = textView.getLayout();
        if (layout == null) return false;

        int x = (int) motionEvent.getX();
        int y = (int) motionEvent.getY();

        x -= textView.getTotalPaddingLeft();
        y -= textView.getTotalPaddingTop();

        x += textView.getScrollX();
        y += textView.getScrollY();

        int line = layout.getLineForVertical(y);
        int offset = layout.getOffsetForHorizontal(line, x);

        if (x > layout.getLineWidth(line)) {
            return false;
        }

        // =====================================================
        // 1. ANNOTATIONS (priorité)
        // =====================================================
        AnnotationMarker[] markers = spanned.getSpans(
                Math.max(0, offset - 1),
                Math.min(spanned.length(), offset + 1),
                AnnotationMarker.class);

        for (AnnotationMarker marker : markers) {
            int start = spanned.getSpanStart(marker);
            int end = spanned.getSpanEnd(marker);

            if (offset >= (start - 1) && offset <= (end + 1)) {
                CharSequence motClique = spanned.subSequence(start, end);
                Host host = getPageHost();
                if (host != null) {
                    host.showAnnotationDialog(motClique.toString(), marker.getAnnotation());
                }
                return true;
            }
        }

        // =====================================================
        // 2. RÉFÉRENCES BIBLIQUES CLIQUABLES
        // =====================================================
        android.text.style.ClickableSpan[] refSpans = spanned.getSpans(
                Math.max(0, offset - 1),
                Math.min(spanned.length(), offset + 1),
                android.text.style.ClickableSpan.class);

        for (android.text.style.ClickableSpan span : refSpans) {
            int start = spanned.getSpanStart(span);
            int end = spanned.getSpanEnd(span);

            if (offset >= (start - 1) && offset <= (end + 1)) {
                span.onClick(textView);
                return true;
            }
        }

        // =====================================================
        // 3. TAP SUR UN VERSET -> toggle sélection
        // =====================================================
        VerseRangeSpan[] verseSpans = spanned.getSpans(
                Math.max(0, offset - 1),
                Math.min(spanned.length(), offset + 1),
                VerseRangeSpan.class);

        for (VerseRangeSpan vSpan : verseSpans) {
            int start = spanned.getSpanStart(vSpan);
            int end = spanned.getSpanEnd(vSpan);

            if (offset >= start && offset <= end) {
                textView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                toggleVerseSelection(vSpan.getChapter(), vSpan.getVerseNumber());
                return true;
            }
        }

        return false;
    }

    // =====================================================
    // CALLBACKS DU RENDERER
    // =====================================================

    @Override
    public void onAnnotationClicked(String word, String annotation) {
        Host host = getPageHost();
        if (host != null) {
            host.showAnnotationDialog(word, annotation);
        }
    }

    @Override
    public void onBibleReferenceClicked(String reference) {
        Host host = getPageHost();
        if (host != null) {
            host.openBibleReference(reference);
        }
    }

    @Override
    public void onVerseToggled(int chapter, int verseNumber) {
        toggleVerseSelection(chapter, verseNumber);
    }

    // =====================================================
    // SÉLECTION (état délégué à l'activity hôte)
    // =====================================================

    private void toggleVerseSelection(int chapter, int verseNumber) {

        Host host = getPageHost();
        if (host == null) return;

        Set<String> keys = host.getSelectedVerseKeys();
        String key = chapter + ":" + verseNumber;

        if (keys.contains(key)) {
            keys.remove(key);
        } else {
            keys.add(key);
        }

        host.onSelectionChanged();
    }

    // =====================================================
    // RÉGLAGES TEXTE (taille, justification)
    // =====================================================

    /** Applique les réglages du texte sur cette page. */
    public void applyTextSettings() {

        if (!isAdded() || textContent == null) return;

        android.content.SharedPreferences prefs =
                requireContext().getSharedPreferences("app_settings",
                        android.content.Context.MODE_PRIVATE);

        float textSize = prefs.getFloat("text_size", 18f);
        textContent.setTextSize(textSize);
        textContent.setClickable(true);
        textContent.setFocusable(true);
        textContent.setLongClickable(false);
        textContent.setTextIsSelectable(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            textContent.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
            textContent.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean isJustified = prefs.getBoolean("justification_mode", true);
            textContent.setJustificationMode(
                    isJustified
                            ? LineBreaker.JUSTIFICATION_MODE_INTER_WORD
                            : LineBreaker.JUSTIFICATION_MODE_NONE);
        }
    }

}
