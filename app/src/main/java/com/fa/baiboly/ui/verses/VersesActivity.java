package com.fa.baiboly.ui.verses;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.SettingsFragment;
import com.fa.baiboly.data.bible.BibleService;
import com.fa.baiboly.data.history.HistoryService;
import com.fa.baiboly.data.marks.MarkService;
import com.fa.baiboly.data.parser.ReadingParser;
import com.fa.baiboly.databinding.ActivityVersesBinding;
import com.fa.baiboly.models.Reading;
import com.fa.baiboly.models.Verse;
import com.fa.baiboly.ui.ai.AiExplanationActivity;
import com.fa.baiboly.ui.reader.ProgramSession;
import com.fa.baiboly.ui.reader.ReaderNeighborPageFragment;
import com.fa.baiboly.ui.reader.ReaderSessionPagerAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lecteur de versets : un ViewPager2 unique dont chaque page est une
 * VersePageFragment interactive (sélection de versets, annotations,
 * références cliquables, surlignages, favoris, réglages texte).
 *
 * Le swipe horizontal natif du ViewPager2 remplace l'ancien GestureDetector
 * collé au NestedScrollView, qui entrait en conflit avec le TextView
 * cliquable et ne se déclenchait jamais.
 *
 * Mode programme (extra "readings") : chaque vakiteny est une page.
 * Mode session de programme (extra "programSession") : les vakiteny
 * consécutifs du programme sont les pages, puis une page "Manaraka"
 * enchaîne sur l'item suivant (hira, fanekena...) au swipe.
 * Mode simple (extra "reading") : la lecture demandée est la page centrale,
 * entourée des chapitres précédent et suivant -> le swipe fonctionne aussi.
 */
public class VersesActivity extends AppCompatActivity
        implements VersePageFragment.Host, ReaderNeighborPageFragment.Host,
        SettingsFragment.TextSettingsListener {

    /** L'historique écrit en DB après le premier frame (évite un jank
     *  d'entrée pendant la transition d'ouverture). */
    private final Runnable deferredHistory = new Runnable() {
        @Override
        public void run() {
            if (entryHistoryRef != null) {
                historyService.addHistory(entryHistoryRef.first, entryHistoryRef.second);
            }
        }
    };

    /** Lecture/type à enregistrer à l'entrée (post-premier-frame). */
    private android.util.Pair<String, String> entryHistoryRef;

    private ActivityVersesBinding binding;

    private BibleService bibleService;

    private HistoryService historyService;

    private MarkService markService;

    /** Pages du pager (références textuelles, ex "Jao 3"). */
    private final List<String> pages = new ArrayList<>();

    /** Sélection partagée entre toutes les pages (clés "chapitre:verset"). */
    private final Set<String> selectedVerseKeys = new HashSet<>();

    /** Quand true, le swipe horizontal du pager est désactivé. */
    private boolean swipeLocked = false;

    // --- Session de lecture d'un programme ---
    /** Session complète (JSON) reçue de l'écran appelant. */
    private String sessionJson;
    /** Items de la session (version décodée). */
    private List<ProgramSession.Item> sessionItems;
    /** Premier item de la portion affichée par ce lecteur. */
    private int sessionStartIndex = 0;
    /** Nombre d'items consécutifs de type vakiteny affichés ici. */
    private int sessionRunSize = 0;
    /** Item précédent (autre type -> autre lecteur), null si début. */
    private ProgramSession.Item sessionPrevItem;
    /** Item suivant (autre type -> autre lecteur), null si fin de session. */
    private ProgramSession.Item sessionNextItem;
    /** Évite de déclencher deux fois un passage (avant ou arrière). */
    private boolean sessionNavigating = false;

    private static final String PREFS_NAME = "app_settings";

    public static final String KEY_THEME = "theme_mode";

    public static final String KEY_TEXT_SIZE = "text_size";

    public static final String KEY_JUSTIFICATION = "justification_mode";

    // Familles de couleur (et non hex brut) : la teinte réelle est résolue
    // au rendu selon le thème clair/sombre (voir VerseTextRenderer).
    private static final String[] HIGHLIGHT_FAMILIES =
            VerseTextRenderer.HIGHLIGHT_FAMILIES;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        applyGlobalTheme();

        super.onCreate(savedInstanceState);

        binding = ActivityVersesBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

            WindowManager.LayoutParams params =
                    getWindow().getAttributes();

            params.preferredRefreshRate = 120.0f;

            getWindow().setAttributes(params);
        }

        bibleService = new BibleService(this);

        historyService = new HistoryService(this);

        markService = new MarkService(this);

        setupFloatingActionBar();

        // =====================================================
        // MODE SESSION : vakiteny du programme, puis item suivant
        // =====================================================
        String sessionExtra = getIntent().getStringExtra(ProgramSession.EXTRA_SESSION);

        if (sessionExtra != null) {
            openInSessionMode(sessionExtra,
                    getIntent().getIntExtra(ProgramSession.EXTRA_SESSION_INDEX, 0),
                    getIntent().getIntExtra(ProgramSession.EXTRA_SESSION_PAGE, 0));
        } else {

            // =================================================
            // MODE PROGRAMME : plusieurs vakiteny, un par page
            // =================================================
            String readingsExtra = getIntent().getStringExtra("readings");

            if (readingsExtra != null && !readingsExtra.isEmpty()) {
                openInProgramMode(readingsExtra);
            } else {
                openInSingleMode();
            }
        }

        setupViewPagerTransform();
    }

    // =====================================================
    // CONSTRUCTION DES PAGES
    // =====================================================

    /**
     * Mode simple : le pager contient TOUS les chapitres de la Bible dans
     * l'ordre canonique (Genèse 1 -> Apocalypse 22), positionné sur la
     * lecture demandée. Le swipe enchaîne donc sans limite jusqu'au
     * premier et au dernier chapitre de la Bible.
     */
    private void openInSingleMode() {

        String rawInput =
                getIntent().getStringExtra("reading");
        if (rawInput == null) {
            rawInput = "Jao 1:1";
        }

        // -------------------------------------------------
        // Chemin DIRECT : bookNumber + nombres fournis par
        // l'écran appelant (choix livre/chapitre/versets).
        // Aucun parsing du nom de livre -> jamais d'erreur.
        // -------------------------------------------------
        int directBookNumber = getIntent().getIntExtra("bookNumber", -1);

        Reading currentReading = null;

        if (directBookNumber > 0) {

            android.os.Bundle sel = getIntent().getExtras();

            currentReading = buildReadingFromNumbers(
                    directBookNumber,
                    sel.getInt("startChapter", 1),
                    sel.getInt("startVerse", 1),
                    sel.getInt("endChapter", sel.getInt("startChapter", 1)),
                    sel.getInt("endVerse", 999)
            );
        }

        // -------------------------------------------------
        // Chemin standard : parsing de la chaîne via le parser
        // centralisé (BibleRefParser via ReadingParser).
        // -------------------------------------------------
        if (currentReading == null) {
            ReadingParser parser = new ReadingParser();
            parser.setService(bibleService);
            currentReading = parser.parse(rawInput);
        }

        if (currentReading == null) {
            // Lecture non reconnue : on affiche quand même la page demandée
            setupToolbar(rawInput);
            pages.add(rawInput);
            setupPagerAdapter();
            return;
        }

        setupToolbar(currentReading.toString());
        deferEntryHistory(currentReading.toString(), "baiboly");

        // Tous les chapitres de la Bible dans l'ordre canonique (1 requête)
        pages.addAll(bibleService.getAllChapterPages());

        // Position de départ : la page demandée, au format "Livre N"
        String target = currentReading.getBook().getShortName() + " "
                + currentReading.getStartChapter();
        int index = pages.indexOf(target);

        // Une plage de versets (ex "Lio 8:1-4") doit afficher la plage exacte,
        // pas le chapitre entier : on remplace la page "Livre N" par la
        // référence précise. Le swipe vers les chapitres voisins fonctionne
        // toujours. Chapitre entier ("Lio 8") : aucun changement.
        boolean isFullChapter =
                currentReading.getStartChapter() == currentReading.getEndChapter()
                        && currentReading.getStartVerse() <= 1
                        && currentReading.getEndVerse() >= 999;

        if (!isFullChapter && index >= 0) {
            pages.set(index, currentReading.toString());
        }

        setupPagerAdapter();

        // Si la référence exacte est introuvable, on reste sur la 1re page
        binding.viewPager.setCurrentItem(index >= 0 ? index : 0, false);
    }

    /** Mode programme : chaque lecture est une page. */
    private void openInProgramMode(String readingsExtra) {

        String[] readings = readingsExtra.split(",");

        for (String r : readings) {
            String trimmed = r.trim();
            if (!trimmed.isEmpty()) {
                pages.add(trimmed);
            }
        }

        if (pages.isEmpty()) {
            finish();
            return;
        }

        setupToolbar("Vakiteny (" + pages.size() + ")");

        deferEntryHistory(pages.get(0), "baiboly");

        setupPagerAdapter();
    }

    // =====================================================
    // MODE SESSION DE PROGRAMME
    // =====================================================

    /**
     * Affiche les vakiteny consécutifs de la portion commençant à
     * {@code index}, précédée d'une page "Teo aloha" (item précédent) et
     * suivie d'une page "Manaraka" (item suivant). Le swipe dans les deux
     * sens enchaîne les items du programme (changement d'activity animé
     * quand le type change).
     */
    private void openInSessionMode(String sessionJson, int index, int startPage) {

        this.sessionJson = sessionJson;
        sessionItems = ProgramSession.decode(sessionJson);
        sessionStartIndex = index;

        List<ProgramSession.Item> run = ProgramSession.sameTypeRun(sessionItems, index);

        if (run.isEmpty()) {
            // Sécurité : rien à afficher ici, on passe à l'item suivant
            if (index + 1 < sessionItems.size()) {
                ProgramSession.finishAndAdvance(this, sessionJson, index + 1);
            } else {
                finish();
            }
            return;
        }

        sessionRunSize = run.size();
        sessionPrevItem = index > 0 ? sessionItems.get(index - 1) : null;
        sessionNextItem = (index + run.size() < sessionItems.size())
                ? sessionItems.get(index + run.size()) : null;

        for (ProgramSession.Item item : run) {
            if (item.reference != null && !item.reference.trim().isEmpty()) {
                pages.add(item.reference.trim());
            }
        }

        if (pages.isEmpty()) {
            finish();
            return;
        }

        int start = Math.max(0, Math.min(startPage, pages.size() - 1));

        setupToolbar(pages.get(start));
        deferEntryHistory(pages.get(start), "baiboly");

        int offset = sessionPrevItem != null ? 1 : 0;

        binding.viewPager.setOffscreenPageLimit(1);
        binding.viewPager.setAdapter(new ReaderSessionPagerAdapter(
                this,
                sessionPrevItem,
                pages.size(),
                position -> VersePageFragment.newInstance(pages.get(position)),
                sessionNextItem));

        // Arrivée directe sur la page demandée (swipe arrière, ancrage)
        binding.viewPager.setCurrentItem(offset + start, false);

        binding.viewPager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {

                        int off = sessionPrevItem != null ? 1 : 0;

                        // Pages de transition ("Teo aloha" / "Manaraka") :
                        // le passage est déclenché à la FIN du swipe (voir
                        // onPageScrollStateChanged), pas au milieu du geste.
                        if (sessionPrevItem != null && position == 0) return;

                        if (position < off + pages.size()) {
                            String ref = pages.get(position - off);
                            if (getSupportActionBar() != null) {
                                getSupportActionBar().setTitle(ref);
                            }
                            historyService.addHistory(ref, "baiboly");
                        }
                    }

                    @Override
                    public void onPageScrollStateChanged(int state) {
                        // L'utilisateur re-touche le pager pendant le repos :
                        // il reprend la main, le passage est annulé.
                        if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                            cancelPendingSessionMove();
                            return;
                        }
                        if (state != ViewPager2.SCROLL_STATE_IDLE) return;

                        int off = sessionPrevItem != null ? 1 : 0;
                        int pos = binding.viewPager.getCurrentItem();

                        if (sessionPrevItem != null && pos == 0) {
                            // Swipe posé sur "Teo aloha" : item précédent,
                            // après un court repos sur l'écran de transition.
                            scheduleSessionBack();
                        } else if (pos == off + pages.size()) {
                            // Swipe posé sur "Manaraka" : item suivant.
                            scheduleSessionAdvance();
                        } else {
                            // Retour sur une page de contenu (retour
                            // manuel depuis "Manaraka") : annule le passage.
                            cancelPendingSessionMove();
                        }
                    }
                });
    }

    /** Repos sur l'écran de transition ("Manaraka" / "Teo aloha") après le
     *  repos COMPLET du swipe (déclenché sur SCROLL_STATE_IDLE, plus jamais
     *  en plein geste) : le swipe se termine visuellement, on laisse
     *  l'écran de transition respirer, puis l'activité suivante démarre
     *  hors animation -> glissement fluide sans saccade. */
    private static final long TRANSITION_REST_MS = 150;

    /** Changement de lecteur programmé (annulable). */
    private final Runnable pendingSessionMove = new Runnable() {
        @Override
        public void run() {
            // lire AVANT de consommer pendingBack : l'ordre inverse
            // forçait toujours un passage "vers l'avant" (retour impossible).
            performSessionAdvance();
            pendingBack = false;
        }
    };

    /** Sens du changement programmé : true = item précédent. */
    private boolean pendingBack = false;

    /** Programme le passage (avant ou arrière) après le repos de transition. */
    private void scheduleSessionMove(boolean back) {
        pendingBack = back;
        binding.viewPager.removeCallbacks(pendingSessionMove);
        binding.viewPager.postDelayed(pendingSessionMove, TRANSITION_REST_MS);
    }

    /** Programme le passage vers l'item suivant. */
    private void scheduleSessionAdvance() {
        scheduleSessionMove(false);
    }

    /** Programme le retour vers l'item précédent. */
    private void scheduleSessionBack() {
        scheduleSessionMove(true);
    }

    /** Annule le passage programmé. */
    private void cancelPendingSessionMove() {
        binding.viewPager.removeCallbacks(pendingSessionMove);
    }

    /** Effectue le passage programmé (callback du délai). */
    private void performSessionAdvance() {
        if (isFinishing() || isDestroyed()) return;
        if (pendingBack) {
            goBackSession();
        } else {
            advanceSession();
        }
    }

    /** Passe à l'item suivant du programme (lecteur suivant, animé). */
    private void advanceSession() {
        if (sessionNavigating || sessionNextItem == null) return;
        sessionNavigating = true;
        ProgramSession.finishAndAdvance(
                this, sessionJson, sessionStartIndex + sessionRunSize);
    }

    /** Revient à l'item précédent du programme (lecteur précédent, animé). */
    private void goBackSession() {
        if (sessionNavigating || sessionPrevItem == null) return;
        sessionNavigating = true;

        int prevIndex = sessionStartIndex - 1;
        int prevRunStart = ProgramSession.runStart(sessionItems, prevIndex);

        ProgramSession.finishAndGoBack(
                this, sessionJson, prevRunStart, prevIndex - prevRunStart);
    }

    /** Service d'historique partagé avec les pages voisines. */
    @Override
    public com.fa.baiboly.data.history.HistoryService getHistoryService() {
        return historyService;
    }

    /** Écrit l'historique d'entrée après le premier frame (hors transition). */
    private void deferEntryHistory(String reading, String type) {
        entryHistoryRef = new android.util.Pair<>(reading, type);
        getWindow().getDecorView().removeCallbacks(deferredHistory);
        getWindow().getDecorView().post(deferredHistory);
    }

    /** Tap sur la page "Manaraka" -> même passage que le swipe. */
    @Override
    public void onReaderNextTapped() {
        advanceSession();
    }

    /** Tap sur la page "Teo aloha" -> même retour que le swipe. */
    @Override
    public void onReaderPrevTapped() {
        goBackSession();
    }

    private void setupPagerAdapter() {

        binding.viewPager.setAdapter(
                new VersePagerAdapter(this, pages));

        // Pré-charge la page voisine dès que la page courante est posée :
        // le swipe suivant ne construit plus rien en plein geste (fluidité
        // identique à l'écran des chants).
        binding.viewPager.setOffscreenPageLimit(1);

        binding.viewPager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {

                        if (position < 0 || position >= pages.size()) return;

                        String ref = pages.get(position);

                        if (getSupportActionBar() != null) {
                            getSupportActionBar().setTitle(ref);
                        }

                        // L'historique enregistre la lecture affichée
                        historyService.addHistory(ref, "baiboly");
                    }
                });
    }

    /** Transformation visuelle des pages (alpha + léger zoom). */
    private void setupViewPagerTransform() {

        binding.viewPager.setPageTransformer((page, position) -> {
            float absPos = Math.abs(position);
            page.setAlpha(1f - absPos * 0.3f);
            float scale = 1f - absPos * 0.05f;
            page.setScaleX(scale);
            page.setScaleY(scale);
        });
    }

    // =====================================================
    // HOST (implémenté par les VersePageFragment)
    // =====================================================

    @Override
    public Set<String> getSelectedVerseKeys() {
        return selectedVerseKeys;
    }

    @Override
    public BibleService getBibleService() {
        return bibleService;
    }

    @Override
    public MarkService getMarkService() {
        return markService;
    }

    @Override
    public void onSelectionChanged() {

        // Masque la palette de couleurs pendant la réévaluation
        binding.floatingColorStrip.setVisibility(View.GONE);

        binding.floatingActionBar.setVisibility(
                selectedVerseKeys.isEmpty() ? View.GONE : View.VISIBLE);

        // Re-rend toutes les pages vivantes : la sélection peut chevaucher
        // plusieurs chapitres, l'autre page doit refléter l'état courant.
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof VersePageFragment) {
                ((VersePageFragment) fragment).refreshRendering();
            }
        }
    }

    @Override
    public void openBibleReference(String reference) {
        VerseBottomSheetFragment.display(
                getSupportFragmentManager(),
                reference);
    }

    @Override
    public void showAnnotationDialog(String word, String annotation) {

        BottomSheetDialog dialog =
                new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);

        View sheetView =
                getLayoutInflater().inflate(R.layout.layout_annotation_sheet, null);

        TextView title = sheetView.findViewById(R.id.tvSheetTitle);
        TextView content = sheetView.findViewById(R.id.tvSheetContent);
        View btnClose = sheetView.findViewById(R.id.btnSheetClose);

        title.setText(word);
        content.setText(annotation);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(sheetView);
        dialog.show();
    }

    // =====================================================
    // SÉLECTION : HELPERS
    // =====================================================

    private List<int[]> getSortedSelection() {

        List<int[]> pairs = new ArrayList<>();

        for (String key : selectedVerseKeys) {
            String[] parts = key.split(":");
            pairs.add(new int[]{
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1])
            });
        }

        Collections.sort(pairs, (a, b) -> {
            if (a[0] != b[0]) return Integer.compare(a[0], b[0]);
            return Integer.compare(a[1], b[1]);
        });

        return pairs;
    }

    /**
     * Construit une lecture directement depuis le numéro canonique du livre
     * et les nombres sélectionnés (chemin sans parsing du nom).
     */
    private Reading buildReadingFromNumbers(int bookNumber,
                                            int startChapter,
                                            int startVerse,
                                            int endChapter,
                                            int endVerse) {

        com.fa.baiboly.models.Book book = bibleService.getBookById(bookNumber);

        if (book == null) return null;

        return new Reading(book, startChapter, startVerse, endChapter, endVerse);
    }

    /** Livre de la page actuellement affichée (pour copier / marquer). */
    private String getCurrentBookShortName() {

        int position = binding.viewPager.getCurrentItem();

        if (position < 0 || position >= pages.size()) return "";

        ReadingParser parser = new ReadingParser();
        parser.setService(bibleService);

        Reading reading = parser.parse(pages.get(position));

        return (reading != null)
                ? reading.getBook().getShortName()
                : "";
    }

    /**
     * Retrouve le texte d'un verset sélectionné en rechargeant sa lecture
     * (identifiée par le livre courant + le chapitre du verset).
     */
    private String findVerseText(String bookShort, int chapter, int verseNumber) {

        if (bookShort == null || bookShort.isEmpty()) return null;

        // Résolution directe par numéro (BibleRefParser) : plus de LIKE SQL
        Integer bookNumber = com.fa.baiboly.data.parser.BibleRefParser
                .resolveBookNumber(bookShort);

        if (bookNumber == null) return null;

        Reading reading = buildReadingFromNumbers(
                bookNumber, chapter, 1, chapter, 999);

        if (reading == null) return null;

        List<Verse> verses = bibleService.getVerseObjectsFromReading(reading);

        for (Verse v : verses) {
            if (v.getNumber() == verseNumber) {
                return v.getText();
            }
        }

        return null;
    }

    private void clearSelection() {
        selectedVerseKeys.clear();
        onSelectionChanged();
    }

    // =====================================================
    // BARRE FLOTTANTE
    // =====================================================

    private void setupFloatingActionBar() {

        binding.btnFloatClose.setOnClickListener(v -> clearSelection());

        binding.btnFloatCopy.setOnClickListener(v -> {

            String book = getCurrentBookShortName();

            StringBuilder sb = new StringBuilder();

            for (int[] pair : getSortedSelection()) {

                String verseText = findVerseText(book, pair[0], pair[1]);
                if (verseText == null) continue;

                sb.append(book).append(" ")
                        .append(pair[0]).append(":").append(pair[1])
                        .append(" — ")
                        .append(verseText.trim())
                        .append("\n");
            }

            copyToClipboard(sb.toString().trim());
            clearSelection();
        });

        binding.btnFloatHighlight.setOnClickListener(v -> {
            boolean showing =
                    binding.floatingColorStrip.getVisibility() == View.VISIBLE;
            binding.floatingColorStrip.setVisibility(
                    showing ? View.GONE : View.VISIBLE);
        });

        binding.btnAIExplain.setOnClickListener(v -> {

            List<int[]> selection = getSortedSelection();

            if (selection.isEmpty()) {
                Toast.makeText(this, "Misafidiana andininy aloha",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            String book = getCurrentBookShortName();

            StringBuilder texteComplet = new StringBuilder();

            String premiereRef = null;
            String derniereRef = null;

            for (int[] pair : selection) {

                String verseText = findVerseText(book, pair[0], pair[1]);
                if (verseText == null) continue;

                if (premiereRef == null) {
                    premiereRef = pair[0] + ":" + pair[1];
                }
                derniereRef = pair[0] + ":" + pair[1];

                texteComplet.append(verseText.trim()).append(" ");
            }

            String reference = book + " " + premiereRef
                    + (derniereRef != null && !derniereRef.equals(premiereRef)
                    ? "-" + derniereRef : "");

            AiExplanationActivity.start(this, reference,
                    texteComplet.toString().trim());

            clearSelection();
        });

        binding.btnFloatFavorite.setOnClickListener(v -> {

            String book = getCurrentBookShortName();
            List<int[]> selection = getSortedSelection();

            boolean allAlreadyFav = true;

            for (int[] pair : selection) {
                if (!markService.isFavorite(book, pair[0], pair[1])) {
                    allAlreadyFav = false;
                    break;
                }
            }

            boolean newFavState = !allAlreadyFav;

            for (int[] pair : selection) {
                markService.toggleFavorite(book, pair[0], pair[1], newFavState);
            }

            Toast.makeText(
                    this,
                    newFavState ? "Ajoutés aux favoris" : "Retirés des favoris",
                    Toast.LENGTH_SHORT
            ).show();

            clearSelection();
        });

        // 📝 NOTE sur la sélection courante (lecture proposée en citation)
        binding.btnFloatNote.setOnClickListener(v -> {
            String book = getCurrentBookShortName();
            List<int[]> selection = getSortedSelection();
            if (selection.isEmpty()) {
                Toast.makeText(this, "Misafidiana andininy aloha",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            int firstChapter = selection.get(0)[0];
            int firstVerse = selection.get(0)[1];
            int lastChapter = selection.get(selection.size() - 1)[0];
            int lastVerse = selection.get(selection.size() - 1)[1];

            String refLabel = book + " " + firstChapter + ":" + firstVerse
                    + ((lastChapter != firstChapter || lastVerse != firstVerse)
                    ? "-" + lastVerse : "");
            String refKey = "verse:" + book + ":" + firstChapter + ":"
                    + firstVerse + ":" + lastVerse;

            StringBuilder quoted = new StringBuilder();
            for (int[] pair : selection) {
                String verseText = findVerseText(book, pair[0], pair[1]);
                if (verseText != null && !verseText.trim().isEmpty()) {
                    quoted.append(verseText.trim()).append(" ");
                }
            }

            com.fa.baiboly.ui.notes.NoteEditorActivity.open(
                    VersesActivity.this, null, com.fa.baiboly.models.Note.TYPE_VERSE,
                    refLabel, refKey, quoted.toString().trim());
            clearSelection();
        });

        int[] colorButtonIds = {
                R.id.colorYellow, R.id.colorGreen,
                R.id.colorBlue, R.id.colorPink
        };

        for (int i = 0; i < colorButtonIds.length; i++) {

            final String family = HIGHLIGHT_FAMILIES[i];

            findViewById(colorButtonIds[i]).setOnClickListener(v -> {

                String book = getCurrentBookShortName();

                for (int[] pair : getSortedSelection()) {
                    markService.setHighlight(book, pair[0], pair[1], family);
                }

                clearSelection();
            });
        }

        binding.colorRemove.setOnClickListener(v -> {

            String book = getCurrentBookShortName();

            for (int[] pair : getSortedSelection()) {
                markService.removeHighlight(book, pair[0], pair[1]);
            }

            clearSelection();
        });
    }

    private void copyToClipboard(String content) {

        ClipboardManager cm =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        ClipData clip = ClipData.newPlainText("verset", content);

        if (cm != null) cm.setPrimaryClip(clip);

        Toast.makeText(this, "Copié", Toast.LENGTH_SHORT).show();
    }

    // =====================================================
    // RÉGLAGES TEXTE : appliqués à toutes les pages vivantes
    // =====================================================

    private void applyTextSettings() {

        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof VersePageFragment) {
                ((VersePageFragment) fragment).applyTextSettings();
            }
        }
    }

    /** Appelé par SettingsFragment (panneau intégré) quand la taille du
     *  texte ou la justification change : les pages vivantes lisent les
     *  nouvelles préférences et se re-rendent immédiatement. */
    @Override
    public void onTextSettingsChanged() {
        applyTextSettings();
    }

    // =====================================================
    // TOOLBAR + SETTINGS
    // =====================================================

    private void setupToolbar(String title) {

        setSupportActionBar(binding.toolbar);

        if (getSupportActionBar() != null) {

            getSupportActionBar()
                    .setDisplayHomeAsUpEnabled(true);

            getSupportActionBar()
                    .setTitle(title);
        }

        binding.toolbar.setNavigationOnClickListener(v -> {

            if (!selectedVerseKeys.isEmpty()) {
                clearSelection();
            } else if (isSettingsOpen()) {
                closeSettings();
            } else {
                finish();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(
                R.menu.menu_verses,
                menu
        );

        com.fa.baiboly.ui.MenuHelper.enableOverflowIcons(this, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.action_settings) {

            openSettings();

            return true;
        }

        // Chat IA générique (menu 3 points)
        if (item.getItemId() == R.id.chat) {

            com.fa.baiboly.ui.ai.AiExplanationActivity.startWithQuestion(this, "");

            return true;
        }

        // Mombamomba
        if (item.getItemId() == R.id.about) {

            com.fa.baiboly.ui.MenuHelper.showAbout(this);

            return true;
        }

        if (item.getItemId() == R.id.action_toggle_swipe) {

            swipeLocked = !swipeLocked;

            // Cadenas fermé = swipe verrouillé, cadenas ouvert = swipe libre
            item.setIcon(swipeLocked
                    ? R.drawable.ic_lock
                    : R.drawable.ic_lock_open);
            item.setTitle(swipeLocked
                    ? R.string.action_swipe_locked
                    : R.string.action_swipe_unlocked);

            // Bloque ou réautorise le swipe du pager (API ViewPager2)
            binding.viewPager.setUserInputEnabled(!swipeLocked);

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void openSettings() {

        binding.settingsContainer.setVisibility(View.VISIBLE);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.settings_container,
                        new SettingsFragment()
                )
                .addToBackStack(null)
                .commit();
    }

    private void closeSettings() {

        getSupportFragmentManager().popBackStack();

        binding.settingsContainer.setVisibility(View.GONE);

        applyTextSettings();
    }

    private boolean isSettingsOpen() {

        return binding.settingsContainer.getVisibility()
                == View.VISIBLE;
    }

    @Override
    public void onBackPressed() {

        if (!selectedVerseKeys.isEmpty()) {
            clearSelection();
        } else if (isSettingsOpen()) {
            closeSettings();
        } else {
            super.onBackPressed();
        }
    }

    private void applyGlobalTheme() {

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS_NAME,
                        android.content.Context.MODE_PRIVATE
                );

        int savedMode =
                prefs.getInt(
                        KEY_THEME,
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                );

        AppCompatDelegate.setDefaultNightMode(savedMode);

        // Couleurs de base choisies par l'utilisateur
        ColorManager.applyTheme(this);
    }
}
