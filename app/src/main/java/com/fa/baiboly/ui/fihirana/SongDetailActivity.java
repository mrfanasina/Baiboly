package com.fa.baiboly.ui.fihirana;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import android.view.animation.DecelerateInterpolator;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.SettingsFragment;
import com.fa.baiboly.data.fihirana.FihiranaService;
import com.fa.baiboly.data.history.HistoryService;
import com.fa.baiboly.data.playback.PlaybackManager;
import com.fa.baiboly.data.playback.PlaybackService;
import com.fa.baiboly.models.Playback;
import com.fa.baiboly.databinding.ActivitySongDetailBinding;
import com.fa.baiboly.models.Song;
import com.fa.baiboly.models.SongVerse;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.fa.baiboly.ui.fandaharana.SongPickerSheet;
import com.fa.baiboly.ui.reader.ProgramSession;
import com.fa.baiboly.ui.reader.ReaderNeighborPageFragment;
import com.fa.baiboly.ui.reader.ReaderSessionPagerAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SongDetailActivity extends AppCompatActivity
        implements ReaderNeighborPageFragment.Host {

    private ActivitySongDetailBinding binding;
    private FihiranaService service;
    private HistoryService historyService;

    /** Quand true, le swipe horizontal entre les chants est désactivé. */
    private boolean swipeLocked = false;

    private float textSize = 20f;
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_TEXT_SIZE = "text_size";

    // Swipe navigation
    private List<Song> songsInCategory;
    private int currentSongIndex = -1;
    private SongPagerAdapter pagerAdapter;

    // =========================
    // PLAYBACK (feo) — service arrière-plan + notification média
    // =========================
    private PlaybackManager playbackManager;
    /** Pont vers PlaybackService : le son continue quand on quitte
     *  l'activité et la notification média reste affichée. */
    private PlaybackServiceBridge playbackBridge;
    /** SongId dont le playback est en cours de lecture. */
    private String playingSongId;
    /** Playback en cours (version choisie). */    private Playback playingPlayback;
    /** Vrai pendant que l'utilisateur déplace la barre de progression. */
    private boolean userSeeking = false;
    /** Vrai si le panneau détaillé du mini-lecteur est visible. */
    private boolean playbackExpanded = false;
    /** Vrai juste après l'ouverture d'AddPlayBackActivity : au retour
     *  onResume recharge la liste et dévoile/zappe le lecteur automatiquement. */
    private boolean playbackMaybeAdded;

    /** Boîte "Maka avy amin'ny serveur..." supprimée : la progression est
     *  désormais visible en direct dans la feuille "Feo" (aucun popup). */

    // --- Session de lecture d'un programme ---
    private boolean sessionMode = false;
    private String sessionJson;
    private List<ProgramSession.Item> sessionItems;
    private int sessionStartIndex = 0;
    private int sessionRunSize = 0;
    private ProgramSession.Item sessionPrevItem;
    private ProgramSession.Item sessionNextItem;
    private ProgramSession.Item currentSessionItem;
    /** Évite de déclencher deux fois un passage (avant ou arrière). */
    private boolean sessionNavigating = false;

    /** Titre courant du chant ("FFPM 49"), affiché au scroll dans la toolbar. */
    private String currentSongTitle = "";

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

    /** État du titre de scroll : chant affiché (true) ou "Fihirana" (false). */
    private boolean titleShowing = false;

    /**
     * Static helper method to open this Activity
     */
    public static void open(Context context, String songId, String title) {
        Intent intent = new Intent(context, SongDetailActivity.class);
        intent.putExtra("songId", songId);
        intent.putExtra("title", title);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Mode nuit + couleurs de base choisies (avant inflation)
        ColorManager.applyGlobalTheme(this);
        super.onCreate(savedInstanceState);
        binding = ActivitySongDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // High refresh rate
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = 120.0f;
        getWindow().setAttributes(params);

        // Keep screen ON
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Load text size
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        textSize = prefs.getFloat(KEY_TEXT_SIZE, 18f);
        service = new FihiranaService(this);
        historyService = new HistoryService(this);

        // Open solfa
        binding.solfaButton.setOnClickListener(v -> openSolfa());

        // Playback (feo) : service arrière-plan + panneau flottant
        initPlayback();
        playbackBridge.connect();
        adjustPlaybackBarForTablet();

        // Mode session de programme : hira consécutifs + voisins au swipe
        String sessionExtra = getIntent().getStringExtra(ProgramSession.EXTRA_SESSION);
        if (sessionExtra != null) {
            openInSessionMode(sessionExtra,
                    getIntent().getIntExtra(ProgramSession.EXTRA_SESSION_INDEX, 0),
                    getIntent().getIntExtra(ProgramSession.EXTRA_SESSION_PAGE, 0));
            return;
        }

        String songId = getIntent().getStringExtra("songId");

        // Save reading history (post-premier-frame, hors transition)
        deferEntryHistory(songId, "fihirana");

        // Setup toolbar
        setupToolbar(songId != null ? songId.toUpperCase().replace("_", " ") : "HIra");

        // Load songs and setup ViewPager2
        setupViewPager(songId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Retour d'AddPlayBackActivity : rafraîchit l'état, dévoile la barre
        // mini si le chant a (désormais) un playback écouté ou déjà tapé.
        if (playbackMaybeAdded && playingSongId == null) {
            playbackMaybeAdded = false;
            loadPlaybackState();
            refreshPlaybackUi();
        }
    }

    @Override
    protected void onDestroy() {
        if (playbackBridge != null) {
            // On se détache seulement : le SERVICE continue en fond
            // (le son et la notification restent).
            playbackBridge.disconnect();
        }
        super.onDestroy();
    }

    // =========================
    // PRÉTÉLÉCHARGEMENT AUTOMATIQUE À L'ENTRÉE
    // =========================

    /** Chant pour lequel le pré-téléchargement a été lancé
     *  (évite de re-lancer à chaque onPageSelected du même chant). */
    private String prefetchedSongId;

    /** Appelé quand le chant affiché change : lance le téléchargement de
     *  fond de la version serveur si aucune version locale n'existe. */
    private void prefetchPlaybackFor(String songId) {
        if (songId == null || playbackManager == null) return;
        if (songId.equals(prefetchedSongId)) return;
        prefetchedSongId = songId;

        String[] parts = songId.split("_");
        if (parts.length < 2) return;
        String category = parts[0].toLowerCase();
        int number = parseIntSafe(parts[1]);
        if (number < 0) return;

        playbackManager.prefetch(category, number);
    }

    // =========================
    // MENU
    // =========================
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_song_detail, menu);
        com.fa.baiboly.ui.MenuHelper.enableOverflowIcons(this, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            openSettings();
            return true;
        }

        // Ouvre l'écran d'ajout de playback (feo)
        if (item.getItemId() == R.id.action_add_playback) {
            openAddPlayback();
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

        // Cadenas : verrouille/déverrouille le swipe entre les chants.
        // Pendant un culte, éviter de changer de chant en feuilletant.
        if (item.getItemId() == R.id.action_toggle_swipe) {

            swipeLocked = !swipeLocked;

            // Cadenas fermé = swipe verrouillé, cadenas ouvert = swipe libre
            item.setIcon(swipeLocked
                    ? R.drawable.ic_lock
                    : R.drawable.ic_lock_open);
            item.setTitle(swipeLocked
                    ? R.string.action_swipe_locked
                    : R.string.action_swipe_unlocked);

            binding.viewPager.setUserInputEnabled(!swipeLocked);

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // =========================
    // SETTINGS
    // =========================
    private void openSettings() {
        binding.settingsContainer.setVisibility(View.VISIBLE);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .addToBackStack(null)
                .commit();
    }

    private void closeSettings() {
        getSupportFragmentManager().popBackStack();
        binding.settingsContainer.setVisibility(View.GONE);
    }

    private boolean isSettingsOpen() {
        return binding.settingsContainer.getVisibility() == View.VISIBLE;
    }

    private void setupToolbar(String title) {
        setSupportActionBar(binding.toolbar);

        currentSongTitle = title;

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            // Pas de titre ActionBar : le titre affiché est le TextView dédié
            // (toolbarTitle), qui seul se prête au glissement vertical.
            getSupportActionBar().setTitle(null);
        }

        // Titre initial de la toolbar dédiée
        binding.toolbarTitle.setText("Fihirana");

        binding.toolbar.setNavigationOnClickListener(v -> {
            if (isSettingsOpen()) {
                closeSettings();
            } else {
                finish();
            }
        });
    }

    /**
     * En-tête du chant ("Fihirana FFPM | 49") cliquable -> pad numérique
     * de sélection d'un autre chant.
     */
    public void openSongPicker() {
        new SongPickerSheet(song -> {
            // Ouvre le chant choisi (crossfade) puis ferme l'écran courant
            SongDetailActivity.open(this, song.getId(), song.getTitle());
            overridePendingTransition(
                    R.anim.slide_in_right, R.anim.slide_out_left);
            finish();
        }).show(getSupportFragmentManager(), "song_picker");
    }

    // =========================
    // VIEWPAGER2 SETUP
    // =========================
    private void setupViewPager(String songId) {
        if (songId == null) return;

        // Parse category from songId (e.g., "ffpm_171" → "ffpm")
        String category = "ff";
        if (songId.contains("_")) {
            category = songId.split("_")[0];
        }

        // Load all songs in this category
        songsInCategory = service.getSongsByCategory(category);

        // Find current song index
        for (int i = 0; i < songsInCategory.size(); i++) {
            if (songsInCategory.get(i).getId().equals(songId)) {
                currentSongIndex = i;
                break;
            }
        }

        // Setup ViewPager2
        pagerAdapter = new SongPagerAdapter(this, songsInCategory, textSize);
        binding.viewPager.setAdapter(pagerAdapter);

        // Smooth page transform (crossfade between pages)
        binding.viewPager.setPageTransformer(new CrossfadeTransformer());

        // Set initial page to the current song
        binding.viewPager.setCurrentItem(currentSongIndex, false);

        // Pré-téléchargement de fond du chant affiché à l'ouverture
        if (currentSongIndex >= 0
                && currentSongIndex < songsInCategory.size()) {
            prefetchPlaybackFor(
                    songsInCategory.get(currentSongIndex).getId());
        }

        // Update toolbar title and history when page changes
        binding.viewPager.registerOnPageChangeCallback(
                new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        // Changement de chant : stopper la lecture en cours
                        stopPlayback();
                        currentSongIndex = position;
                        Song song = songsInCategory.get(position);

                        // Pré-téléchargement de fond du chant affiché
                        prefetchPlaybackFor(song.getId());

                        // Chant courant (affiché au scroll, cf. onHeaderScrolled)
                        currentSongTitle = songFormatTitle(song.getId());

                        // Nouvelle page : on repart de "Fihirana"
                        // (auto-corrigé au premier scroll vertical)
                        resetToolbarTitle();

                        // Save to history
                        historyService.addHistory(song.getId(), "fihirana");
                    }
                });
    }

    // =========================
    // SESSION DE PROGRAMME
    // =========================
    /**
     * Affiche les hira consécutifs du programme à partir de l'index donné,
     * puis une page "Manaraka" qui enchaîne sur l'item suivant (changement
     * d'activity animé si le type change).
     */
    private void openInSessionMode(String sessionJson, int index, int startPage) {
        sessionMode = true;
        this.sessionJson = sessionJson;
        sessionItems = ProgramSession.decode(sessionJson);
        sessionStartIndex = index;

        final List<ProgramSession.Item> run =
                ProgramSession.sameTypeRun(sessionItems, index);

        if (run.isEmpty()) {
            finish();
            return;
        }

        sessionRunSize = run.size();
        sessionPrevItem = index > 0 ? sessionItems.get(index - 1) : null;
        sessionNextItem = (index + run.size() < sessionItems.size())
                ? sessionItems.get(index + run.size()) : null;

        int start = Math.max(0, Math.min(startPage, run.size() - 1));
        currentSessionItem = run.get(start);

        setupToolbar(sessionTitle(currentSessionItem));
        deferEntryHistory(currentSessionItem.reference, "fihirana");

        int offset = sessionPrevItem != null ? 1 : 0;

        binding.viewPager.setOffscreenPageLimit(1);
        binding.viewPager.setAdapter(new ReaderSessionPagerAdapter(
                this,
                sessionPrevItem,
                run.size(),
                position -> SongPageFragment.newInstance(
                        run.get(position).reference,
                        safeVerses(run.get(position).reference), textSize),
                sessionNextItem));
        binding.viewPager.setPageTransformer(new CrossfadeTransformer());

        // Arrivée directe sur la page demandée (swipe arrière, ancrage)
        binding.viewPager.setCurrentItem(offset + start, false);

        binding.viewPager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {

                        // Changement de chant : stopper la lecture en cours
                        stopPlayback();

                        int off = sessionPrevItem != null ? 1 : 0;

                        if (sessionPrevItem != null && position == 0) {
                            // Page "Teo aloha" feuilletée : item précédent
                            scheduleSessionBack();
                            return;
                        }

                        if (position < off + run.size()) {
                            currentSessionItem = run.get(position - off);

                            // Pré-téléchargement de fond du chant affiché
                            prefetchPlaybackFor(currentSessionItem.reference);

                            currentSongTitle = sessionTitle(currentSessionItem);
                            resetToolbarTitle();
                            historyService.addHistory(
                                    currentSessionItem.reference, "fihirana");
                        } else {
                            // Page "Manaraka" feuilletée : item suivant
                            scheduleSessionAdvance();
                        }
                    }

                    @Override
                    public void onPageScrollStateChanged(int state) {
                        // L'utilisateur regagne une page de contenu (retour
                        // manuel depuis "Manaraka") : annule le passage.
                        if (state == ViewPager2.SCROLL_STATE_IDLE) {
                            int off = sessionPrevItem != null ? 1 : 0;
                            int pos = binding.viewPager.getCurrentItem();
                            boolean onContent = pos >= off
                                    && pos < off + sessionRunSize;
                            if (onContent) cancelPendingSessionMove();
                        }
                    }
                });

        // Après un retour arrière (ou à l'ouverture) : réaffiche le lecteur
        // si ce chant a déjà un playback (sans relancer la lecture)
        // + pré-téléchargement de fond du chant affiché.
        loadPlaybackState();
        if (currentSessionItem != null) {
            prefetchPlaybackFor(currentSessionItem.reference);
        }
    }

    /**
     * Appelé par les pages pendant le scroll vertical : replie aussi le
     * panneau de lecture (état mini : bouton + X uniquement).
     */
    public void onHeaderScrolled(float headerBottom) {
        if (isSettingsOpen()) return;

        // En-tête disparu derrière la toolbar -> on affiche le chant ;
        // en-tête encore visible -> on affiche "Fihirana".
        boolean showSong = headerBottom <= 0f;
        if (showSong == titleShowing) return;

        titleShowing = showSong;

        // Glissement vertical fluide : le texte courant sort vers le haut,
        // le nouveau entre par le bas (pas de clignotement).
        animateTitleChange(showSong ? currentSongTitle : "Fihirana");
    }

    /** Replie le panneau de lecture (état mini : temps/seek masqués),
     *  sans toucher au son. Le X reste TOUJOURS visible. */
    private void collapsePlayback() {
        if (!playbackExpanded || playingSongId == null) return;
        playbackExpanded = false;
        binding.playbackControls.setVisibility(View.GONE);
        binding.playbackClose.setVisibility(View.VISIBLE);
    }

    /** Ouvre le panneau de lecture (état détaillé : temps + progression). */
    private void expandPlayback() {
        if (playbackExpanded || playingSongId == null) return;
        playbackExpanded = true;
        binding.playbackControls.setVisibility(View.VISIBLE);
        binding.playbackClose.setVisibility(View.VISIBLE);
    }

    /**
     * Remplace le titre de la toolbar avec un glissement vertical : le texte
     * courant glisse vers le haut en s'estompant, le nouveau entre par le
     * bas. Un seul TextView, sortie puis entrée -> pas de clignotement.
     */
    private void animateTitleChange(String newTitle) {
        TextView title = binding.toolbarTitle;
        if (newTitle.contentEquals(title.getText())) return;

        // Annule toute animation en cours et repart d'une position propre
        title.animate().cancel();
        title.setTranslationY(0f);
        title.setAlpha(1f);

        // Garde-fou : hauteur à 0 si l'animation part avant le layout
        float slide = Math.max(title.getHeight() * 0.6f, 20f);

        // Sortie du texte courant (vers le haut)
        title.animate()
                .translationY(-slide)
                .alpha(0f)
                .setDuration(140)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    // Le texte est remplacé pendant qu'il est invisible
                    title.setText(newTitle);
                    // Entrée du nouveau texte par le bas
                    title.setTranslationY(slide);
                    title.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(180)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                })
                .start();
    }

    /** Après un swipe horizontal : repart de "Fihirana" ; le rapport d'état
     *  initial de la nouvelle page (premier frame) réaffiche le chant si
     *  elle est déjà enroulée. */
    private void resetToolbarTitle() {
        titleShowing = false;
        // Swap sec volontaire : il coïncide avec le crossfade horizontal
        // entre les pages, la permutation de titre n'est pas perçue.
        binding.toolbarTitle.setText("Fihirana");
    }

    /** Format "SOKAJY NUMERO" affiché dans la toolbar (ex. FFPM 24). */
    private String songFormatTitle(String songId) {
        if (songId != null && !songId.isEmpty()) {
            return songId.toUpperCase().replace("_", " ");
        }
        return "Hira";
    }

    /** Titre affiché pour un item de session (sokajy + numéro). */
    private String sessionTitle(ProgramSession.Item item) {
        if (item.reference != null && !item.reference.isEmpty()) {
            return songFormatTitle(item.reference);
        }
        if (item.title != null && !item.title.isEmpty()) return item.title;
        return "Hira";
    }

    /** Paroles d'un chant (liste vide si introuvable). */
    private List<SongVerse> safeVerses(String songId) {
        List<SongVerse> verses = service.getSongVerses(songId);
        return verses != null ? verses : new ArrayList<>();
    }

    /** Repos sur l'écran de transition ("Manaraka" / "Teo aloha") avant
     *  de lancer le lecteur suivant : le swipe se termine visuellement,
     *  l'activity se construit hors animation -> plus de saccade. */
    private static final long TRANSITION_REST_MS = 50;

    /** Changement de lecteur programmé (annulable, cf. onPageScrollStateChanged). */
    private final Runnable pendingSessionMove = new Runnable() {
        @Override
        public void run() {
            pendingBack = false;
            performSessionAdvance();
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

    /** Annule le passage programmé (l'utilisateur a regagné une page de
     *  contenu ou quitté l'écran). */
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

    /** Tap sur la page "Manaraka" -> même passage que le swipe. */
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

    @Override
    public void onReaderNextTapped() {
        advanceSession();
    }

    /** Tap sur la page "Teo aloha" -> même retour que le swipe. */
    @Override
    public void onReaderPrevTapped() {
        goBackSession();
    }

    // =========================
    // PLAYBACK (feo)
    // =========================

    /** Crée le lecteur (service en arrière-plan) et branche les boutons
     *  du panneau flottant. */
    private void initPlayback() {
        playbackManager = new PlaybackManager(this);

        // Le pont vers le service : à la connexion, on synchronise l'UI
        // avec l'état réel (utile au retour sur l'activité pendant que
        // le son continue en fond).
        playbackBridge = new PlaybackServiceBridge(this,
                new PlaybackServiceBridge.Listener() {
                    @Override
                    public void onPlayingChanged(boolean playing) {
                        updatePlaybackUi(playing);
                    }

                    @Override
                    public void onProgress(int currentMs, int durationMs) {
                        updatePlaybackProgress(currentMs, durationMs);
                    }

                    @Override
                    public void onCompleted() {
                        updatePlaybackUi(false);
                        // Son fini : on repart d'un écran propre (barre
                        // mini "Réécouter" conservée via loadPlaybackState).
                        if (lastStoppedPlayback != null
                                && currentSongId() != null) {
                            showMiniPlayer(lastStoppedPlayback, currentSongId());
                        } else {
                            stopPlayback();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(SongDetailActivity.this, message,
                                Toast.LENGTH_SHORT).show();
                        stopPlayback();
                    }

                    @Override
                    public void onStopped() {
                        stopPlayback();
                    }
                },
                this::restorePlaybackUiFromService);

        // Tap sur le panneau replié (hors boutons) : affiche la lecture
        binding.playbackBar.setOnClickListener(v -> expandPlayback());
        // Panneau : toggle lecture/pause
        binding.playbackToggle.setOnClickListener(
                v -> playbackBridge.toggle());

        binding.playButton.setOnClickListener(v -> onPlaybackClicked());

        // X : stoppe la lecture ; un 2e X sur la barre mini referme tout.
        binding.playbackClose.setOnClickListener(v -> onPlaybackCloseClicked());

        binding.playbackSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    binding.playbackTime.setText(
                            formatMs(progress) + " / " + formatMs(seekBar.getMax()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                playbackBridge.seek(seekBar.getProgress());
            }
        });
    }

    // =========================
    // ÉTAT DU PLAYBACK
    // =========================

    /** Chant + version écoutés en dernier (restaurés au retour sur un
     *  chant, évite de repartir de la version 1 à chaque fois). */
    private String lastStoppedSongId = null;
    private Playback lastStoppedPlayback = null;

    /** Bas de page atteint ? (signale par les pages au scroll) */
    private boolean pageAtBottom = false;

    /** Appelé par les pages : le bas de la page est atteint -> le bouton
     *  flottant "Feo" apparaît en bas ; remonté -> il disparaît. */
    public void onPageAtBottom(boolean atBottom) {
        if (isSettingsOpen()) {
            pageAtBottom = atBottom;
            return;
        }
        if (pageAtBottom == atBottom) return;
        pageAtBottom = atBottom;
        refreshPlaybackUi();
    }

    /** FAB "Feo" : visible SEULEMENT quand le bas de la page est atteint.
     *  Le bouton play du toolbar suit l'état de la barre lecteur.
     *  La visibilité de playbackBar est posée explicitement par
     *  startPlayback / showMiniPlayer / stopPlayback. */
    private void refreshPlaybackUi() {
        binding.playButton.setVisibility(View.VISIBLE);
    }

    /** Ouvre l'écran d'ajout, prérempli avec le chant courant si possible. */
    private void openAddPlayback() {
        Intent intent = new Intent(this, AddPlayBackActivity.class);
        String songId = currentSongId();
        if (songId != null && songId.contains("_")) {
            String[] parts = songId.split("_");
            intent.putExtra("category", parts[0]);
            try {
                intent.putExtra("number", Integer.parseInt(parts[1]));
            } catch (NumberFormatException ignored) {
            }
        }
        playbackMaybeAdded = true;
        startActivity(intent);
    }

    /** Id du chant affiché ("ffpm_49"), ou null (page inconnue). */
    private String currentSongId() {
        if (sessionMode) {
            return currentSessionItem != null ? currentSessionItem.reference : null;
        }
        if (songsInCategory != null && currentSongIndex >= 0
                && currentSongIndex < songsInCategory.size()) {
            return songsInCategory.get(currentSongIndex).getId();
        }
        return null;
    }

    /**
     * Mini-barre "Réécouter" au retour sur un écran (ajout de playback) :
     * montrée seulement si le chant affiché a un playback et que rien ne
     * joue. Sinon, aucun changement.
     */
    private void loadPlaybackState() {
        if (playingSongId != null) return; // lecture en cours : ne pas toucher

        String songId = currentSongId();
        if (songId == null || playbackManager == null) return;

        String[] parts = songId.split("_");
        String category = parts[0].toLowerCase();
        int number = parts.length >= 2 ? parseIntSafe(parts[1]) : -1;
        if (number < 0) return;

        List<Playback> versions = filterExisting(playbackManager.getAll(category, number));
        if (versions.isEmpty()) return;

        Playback target =
                lastStoppedSongId != null && lastStoppedSongId.equals(songId)
                        && lastStoppedPlayback != null
                        && fileExists(lastStoppedPlayback)
                ? lastStoppedPlayback : versions.get(0);
        showMiniPlayer(target, songId);
    }

    /** Barre mini repliée : une seule icône de chant (pas de pause, pas
     *  de X). Tap = lecture directe de cette version + panneau détaillé. */
    private void showMiniPlayer(Playback playback, String songId) {
        playingSongId = null;
        playingPlayback = playback;
        lastStoppedSongId = songId;
        lastStoppedPlayback = playback;

        binding.playbackSeek.setMax(0);
        binding.playbackSeek.setProgress(0);
        binding.playbackTime.setText("0:00");
        binding.playbackToggle.setImageResource(R.drawable.ic_play);
        binding.playbackControls.setVisibility(View.GONE);
        // Le X reste visible en état mini : on peut refermer la barre.
        binding.playbackClose.setVisibility(View.VISIBLE);
        binding.playbackBar.setVisibility(View.VISIBLE);
        playbackExpanded = false;

        binding.playbackBar.setOnClickListener(v -> startPlayback(playback, songId));

        refreshPlaybackUi();
    }

    /** Index d'un playback dans la liste de son chant (-1 si absent) : sert
     *  au libellé de repli "Version n" quand le label est vide. */
    private int indexOfPlayback(Playback playback) {
        List<Playback> versions = filterExisting(playbackManager.getAll(
                playback.getCategory(), playback.getNumber()));
        for (int i = 0; i < versions.size(); i++) {
            if (versions.get(i).getId() == playback.getId()) return i;
        }
        return -1;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Tap sur le bouton flottant ou le bouton du toolbar. */
    private void onPlaybackClicked() {
        String songId = currentSongId();
        if (songId == null) {
            Toast.makeText(this, "Hira tsy hita", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] parts = songId.split("_");
        String category = parts[0].toLowerCase();
        int number = -1;
        if (parts.length >= 2) {
            try {
                number = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        if (number < 0) {
            Toast.makeText(this, "Tsy azo atao ny playback ho an'io hira io",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Même chant déjà chargé : pause / reprise
        if (playingSongId != null && playingSongId.equals(songId)) {
            playbackBridge.toggle();
            return;
        }

        // Revenir sur un chant déjà écouté : restaurer la version écoutée
        // au moment où on avait quitté (le sélecteur de version du lecteur
        // permet toujours de changer).
        if (playingSongId == null
                && lastStoppedSongId != null && lastStoppedSongId.equals(songId)
                && lastStoppedPlayback != null
                && lastStoppedPlayback.getCategory() != null
                && lastStoppedPlayback.getCategory().equalsIgnoreCase(category)
                && lastStoppedPlayback.getNumber() == number
                && fileExists(lastStoppedPlayback)) {
            startPlayback(lastStoppedPlayback, songId);
            return;
        }

        // Plusieurs versions possibles : laisser l'utilisateur choisir
        List<Playback> versions = filterExisting(playbackManager.getAll(category, number));

        if (versions.isEmpty()) {
            showPlaybackMissingDialog(category, number);
        } else if (versions.size() == 1) {
            startPlayback(versions.get(0), songId);
        } else {
            showVersionPicker(category, number, versions, songId);
        }
    }

    /** Ne garde que les playbacks dont le fichier existe encore. */
    private List<Playback> filterExisting(List<Playback> versions) {
        List<Playback> ok = new ArrayList<>();
        for (Playback p : versions) {
            if (fileExists(p)) ok.add(p);
        }
        return ok;
    }

    /** Le fichier du playback est-il encore accessible (interne ou SAF) ? */
    private static boolean fileExists(Playback p) {
        String path = p.getFilePath();
        if (path == null || path.isEmpty()) return false;
        return path.startsWith("content:") || new File(path).exists();
    }

    /** Sélecteur de version (plusieurs playbacks pour ce chant). */
    private void showVersionPicker(String category, int number,
                                   List<Playback> versions, String songId) {
        String[] labels = new String[versions.size()];
        for (int i = 0; i < versions.size(); i++) {
            labels[i] = versions.get(i).displayLabel(i);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(PlaybackManager.displayTitle(category, number))
                .setItems(labels, (d, w) -> startPlayback(versions.get(w), songId))
                .setNegativeButton("Hanafoana", null)
                .show();
    }

    /** Pas encore de playback : ouvrir la feuille "Feo" (versions +
     *  téléchargements), sans popup modal ; la progression s'affiche en
     *  direct et la lecture démarre dès que la version est prête. */
    private void showPlaybackMissingDialog(String category, int number) {
        openPlaybackVersionsSheet(category, number);
    }

    /** Feuille des versions/téléchargements du chant courant. */
    private void openPlaybackVersionsSheet(String category, int number) {
        PlaybackVersionsSheet sheet =
                PlaybackVersionsSheet.newInstance(category, number);
        sheet.setOnVersionsChanged(this::onPlaybackVersionsChanged);
        sheet.setOnPlayRequested(playback -> {
            if (isFinishing() || isDestroyed()) return;
            startPlayback(playback, currentSongId());
        });
        sheet.show(getSupportFragmentManager(), "playback_versions");
    }

    /** Une version a été supprimée/ajoutée via la feuille : mettre l'UI
     *  du lecteur à jour (chip version, barre mini). */
    private void onPlaybackVersionsChanged() {
        if (playingPlayback != null) {
            List<Playback> remaining = filterExisting(playbackManager.getAll(
                    playingPlayback.getCategory(), playingPlayback.getNumber()));
            boolean mineGone = true;
            for (Playback p : remaining) {
                if (p.getId() == playingPlayback.getId()) {
                    mineGone = false;
                    break;
                }
            }
            if (mineGone) {
                // La version écoutée vient d'être supprimée : stopper.
                stopPlayback();
                return;
            }
            updateVersionChip();
        }
        loadPlaybackState();
    }

    private void startPlayback(Playback playback, String songId) {
        playingSongId = songId;
        playingPlayback = playback;
        lastStoppedSongId = songId;
        lastStoppedPlayback = playback;
        updateVersionChip();
        binding.playbackSeek.setMax(0);
        binding.playbackSeek.setProgress(0);
        binding.playbackTime.setText("0:00");
        binding.playbackToggle.setImageResource(R.drawable.ic_pause);
        binding.playbackBar.setVisibility(View.VISIBLE);
        // Listeners standard du panneau détaillé (le mini-lecteur les
        // remplace temporairement : barre = lecture, toggle = relance)
        binding.playbackBar.setOnClickListener(v -> expandPlayback());
        binding.playbackToggle.setOnClickListener(
                v -> playbackBridge.toggle());
        // Panneau détaillé ouvert au lancement (se replie au scroll)
        expandPlayback();
        playbackBridge.play(playback.getFilePath(),
                PlaybackManager.displayTitle(playback.getCategory(),
                        playback.getNumber()),
                songId);
        refreshPlaybackUi();
    }

    /** Chip "Version n" retiré de l'UI (la version se choisit désormais
     *  via {@link PlaybackVersionsSheet}) : plus rien à mettre à jour. */
    private void updateVersionChip() {
    }

    /**
     * Service reconnecté (onCreate ou retour d'un autre écran) :\n     * synchronise l'UI avec l'état réel de la lecture. Si le son joue
     * déjà (ex : notification, retour d'arrière-plan), on ressuscite le
     * panneau local sans relancer le média.
     */
    private void restorePlaybackUiFromService() {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            PlaybackService.ServiceState s = playbackBridge.peekState();
            if (s == null || !s.hasCurrent) return;

            // Le chant courant du service n'est plus celui affiché :\n            // on masque le panneau local, le son continue via la notif.
            if (s.songId != null && !s.songId.equals(currentSongId())
                    && playingSongId == null) {
                return;
            }

            // Chant identique : rebrancher les contrôles
            playingSongId = s.songId;
            playingPlayback = findPlaybackFor(s.songId);
            playbackExpanded = false;
            binding.playbackControls.setVisibility(View.GONE);
            // Le X reste visible : on peut refermer la barre.
            binding.playbackClose.setVisibility(View.VISIBLE);
            binding.playbackBar.setVisibility(View.VISIBLE);
            binding.playbackToggle.setImageResource(
                    s.playing ? R.drawable.ic_pause : R.drawable.ic_play);
            if (s.durationMs > 0) {
                binding.playbackSeek.setMax(s.durationMs);
                binding.playbackSeek.setProgress(s.currentMs);
                binding.playbackTime.setText(
                        formatMs(s.currentMs) + " / " + formatMs(s.durationMs));
            }
        });
    }

    /** Retrouve un playback existant pour un chant ("ffpm_49").
     *  Sert à la reconnexion au service (retour d'arrière-plan). */
    private Playback findPlaybackFor(String songId) {
        if (songId == null || playbackManager == null) return null;
        String[] parts = songId.split("_");
        if (parts.length < 2) return null;
        String category = parts[0].toLowerCase();
        int number = parseIntSafe(parts[1]);
        if (number < 0) return null;
        List<Playback> versions =
                filterExisting(playbackManager.getAll(category, number));
        return versions.isEmpty() ? null : versions.get(0);
    }

    /** Stoppe la lecture et masque le panneau flottant.
     *  Fonctionne aussi sur la barre mini (rien ne joue : simple masquage). */
    private void stopPlayback() {
        if (playingSongId != null) {
            lastStoppedSongId = playingSongId;
            lastStoppedPlayback = playingPlayback;
            playbackBridge.stop();
        }
        playingSongId = null;
        playingPlayback = null;
        playbackExpanded = false;
        refreshPlaybackUi();
    }

    /** X du lecteur : si une version joue -> stop (retour icône mini) ;
     *  si barre mini seule -> referme tout (retour FAB). */
    private void onPlaybackCloseClicked() {
        if (playingSongId != null) {
            stopPlayback();
            if (lastStoppedPlayback != null && currentSongId() != null) {
                showMiniPlayer(lastStoppedPlayback, currentSongId());
            }
        } else {
            // Barre mini seule : fermeture complète
            stopPlayback();
        }
    }

    private void updatePlaybackUi(boolean playing) {
        binding.playbackToggle.setImageResource(
                playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void updatePlaybackProgress(int currentMs, int durationMs) {
        binding.playbackSeek.setMax(durationMs);
        if (!userSeeking) {
            binding.playbackSeek.setProgress(currentMs);
        }
        binding.playbackTime.setText(formatMs(currentMs) + " / " + formatMs(durationMs));
    }

    /** Format "1:23". */
    private static String formatMs(int ms) {
        int totalSec = ms / 1000;
        return (totalSec / 60) + ":"
                + String.format(java.util.Locale.US, "%02d", totalSec % 60);
    }

    /** Sur tablette la barre reste compacte, calée à droite ; sur
     *  téléphone elle occupe toute la largeur utile. */
    private void adjustPlaybackBarForTablet() {
        boolean tablet = getResources().getConfiguration().screenWidthDp >= 600;
        android.view.ViewGroup.LayoutParams lp = binding.playbackBar.getLayoutParams();
        if (tablet) {
            lp.width = getResources().getDisplayMetrics().widthPixels * 2 / 5;
        } else {
            lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        }
        binding.playbackBar.setLayoutParams(lp);
    }

    // =========================
    // PAGE TRANSFORMER
    // =========================
    /**
     * Simple crossfade + slight slide transformer for a smooth page transition.
     */
    private static class CrossfadeTransformer implements androidx.viewpager2.widget.ViewPager2.PageTransformer {

        @Override
        public void transformPage(@NonNull View page, float position) {
            float absPos = Math.abs(position);

            // Fade: pages fade out as they move away
            page.setAlpha(1f - absPos * 0.3f);

            // Slight scale for depth
            float scale = 1f - absPos * 0.05f;
            page.setScaleX(scale);
            page.setScaleY(scale);
        }
    }

    // =========================
    // SOLFA
    // =========================
    private void openSolfa() {
        // Chant affiché : item de session, sinon chant de la catégorie
        String rawId;
        String title;

        if (sessionMode) {
            if (currentSessionItem == null) return;
            rawId = currentSessionItem.reference;
            title = currentSessionItem.title;
        } else {
            if (songsInCategory == null || currentSongIndex < 0) return;
            Song currentSong = songsInCategory.get(currentSongIndex);
            rawId = currentSong.getId();
            title = currentSong.getTitle();
        }

        String category = "ff";
        String id = rawId;
        if (id.contains("_")) {
            String[] parts = id.split("_");
            if (parts.length >= 2) {
                category = parts[0];
                id = parts[1];
            }
        }

        Song song = new Song(id, 0, category, title);
        Intent intent = new Intent(this, SolfaActivity.class);
        intent.putExtra("song", song);
        startActivity(intent);
    }
}