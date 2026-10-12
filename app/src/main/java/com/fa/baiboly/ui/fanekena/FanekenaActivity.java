package com.fa.baiboly.ui.fanekena;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.SettingsFragment;
import com.fa.baiboly.data.fanekena.FanekenaService;
import com.fa.baiboly.data.history.HistoryService;
import com.fa.baiboly.databinding.ActivityFanekenaBinding;
import com.fa.baiboly.ui.reader.ProgramSession;
import com.fa.baiboly.ui.reader.ReaderNeighborPageFragment;
import com.fa.baiboly.ui.reader.ReaderSessionPagerAdapter;

import java.util.List;

public class FanekenaActivity extends AppCompatActivity
        implements ReaderNeighborPageFragment.Host {

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

    /** Code/type à enregistrer à l'entrée (post-premier-frame). */
    private android.util.Pair<String, String> entryHistoryRef;

    private ActivityFanekenaBinding binding;
    private FanekenaService service;
    private HistoryService historyService;

    /** Quand true, le swipe horizontal entre les credos est désactivé. */
    private boolean swipeLocked = false;

    // Swipe navigation
    private List<String> codes;
    private FanekenaPagerAdapter pagerAdapter;

    // --- Session de lecture d'un programme ---
    private String sessionJson;
    private List<ProgramSession.Item> sessionItems;
    private int sessionStartIndex = 0;
    private int sessionRunSize = 0;
    private ProgramSession.Item sessionPrevItem;
    private ProgramSession.Item sessionNextItem;
    /** Évite de déclencher deux fois un passage (avant ou arrière). */
    private boolean sessionNavigating = false;

    public static final String KEY_JUSTIFICATION = "justification_mode";

    /** État du titre de scroll : fanekena affiché (true) ou rien (false). */
    private boolean titleShowing = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Mode nuit + couleurs de base choisies (avant inflation)
        ColorManager.applyGlobalTheme(this);
        super.onCreate(savedInstanceState);
        binding = ActivityFanekenaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Force high refresh rate for smoother scrolling experience
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = 120.0f;
        getWindow().setAttributes(params);

        // Keep screen ON while reading songs (prevents sleep interruption)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        service = new FanekenaService(this);
        historyService = new HistoryService(this);

        // Mode session de programme : fanekena consécutifs + voisins au swipe
        String sessionExtra = getIntent().getStringExtra(ProgramSession.EXTRA_SESSION);
        if (sessionExtra != null) {
            openInSessionMode(sessionExtra,
                    getIntent().getIntExtra(ProgramSession.EXTRA_SESSION_INDEX, 0),
                    getIntent().getIntExtra(ProgramSession.EXTRA_SESSION_PAGE, 0));
            return;
        }

        String code = getIntent().getStringExtra("code");

        // Setup toolbar
        setupToolbar(code);

        // Charge tous les credos et active le swipe entre eux
        setupViewPager(code);
    }
    // =========================
    // MENU
    // =========================
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_fanekena, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.action_settings) {
            openSettings();
            return true;
        }

        // Hazavao AI : ouvre le chat d'explication IA sur le fanekena
        // affiché (texte + référence passés en contexte)
        if (item.getItemId() == R.id.action_ai_chat) {
            openAiChat();
            return true;
        }

        if (item.getItemId() == R.id.about) {
            // Simple placeholder
            android.widget.Toast.makeText(this, "Mombamomba", android.widget.Toast.LENGTH_SHORT).show();
            return true;
        }

        // Cadenas : verrouille/déverrouille le swipe entre les credos
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
    // HAZAVAO AI
    // =========================
    /** Ouvre le chat IA prérempli avec le fanekena affiché (référence +
     *  texte), ou sans contexte si la page est inconnue. */
    private void openAiChat() {
        String reference = null;
        String text = null;

        if (sessionMode()) {
            int off = sessionPrevItem != null ? 1 : 0;
            int pos = binding.viewPager.getCurrentItem();
            if (pos >= off && pos < off + sessionRunSize) {
                ProgramSession.Item item =
                        sessionItems.get(sessionStartIndex + (pos - off));
                reference = CodeAdapter.getDisplayName(item.reference);
                text = new com.fa.baiboly.data.fanekena.FanekenaService(this)
                        .getByCode(item.reference) != null
                        ? new com.fa.baiboly.data.fanekena.FanekenaService(this)
                                .getByCode(item.reference).getText() : null;
            }
        } else if (pagerAdapter != null) {
            String code = pagerAdapter.getCode(binding.viewPager.getCurrentItem());
            if (code != null) {
                reference = CodeAdapter.getDisplayName(code);
                com.fa.baiboly.models.Fanekena f =
                        service.getByCode(code);
                text = f != null ? f.getText() : null;
            }
        }

        if (reference == null) {
            // Aucune page connue : chat vierge
            com.fa.baiboly.ui.ai.AiExplanationActivity.startWithQuestion(this, null);
            return;
        }

        com.fa.baiboly.ui.ai.AiExplanationActivity.start(this, reference, text);
    }

    /** Vrai si l'activity est en mode session de programme. */
    private boolean sessionMode() {
        return sessionItems != null;
    }

    // =========================
    // SETTINGS FRAGMENT CONTROL
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

    /**
     * Configure toolbar (back navigation)
     */
    private void setupToolbar(String code) {
        setSupportActionBar(binding.toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        binding.toolbar.setNavigationOnClickListener(v -> {
            if (isSettingsOpen()) {
                closeSettings();
            } else {
                finish();
            }
        });
    }

    // =========================
    // TITRE DE SCROLL (topbar)
    // =========================

    /** Appelé par les pages quand on scroll : le titre de la page a
     *  disparu derrière la toolbar -> on l'affiche dans la topbar
     *  (glissement vertical, même principe que Fihirana). */
    public void onTitleScrolled(boolean showTitle) {
        if (isSettingsOpen()) return;
        if (showTitle == titleShowing) return;
        titleShowing = showTitle;

        String current = "";
        if (getSupportActionBar() != null) {
            CharSequence t = getSupportActionBar().getTitle();
            current = t != null ? t.toString() : "";
        }

        animateTitleChange(showTitle ? current : null);
    }

    /** Remplace le titre de la toolbar avec un glissement vertical
     *  (sortie vers le haut, entrée par le bas). newTitle null ou vide :
     *  la topbar redevient vide. */
    private void animateTitleChange(String newTitle) {
        TextView title = binding.toolbarTitle;
        if (title == null) return;

        String target = newTitle != null ? newTitle : "";
        if (target.contentEquals(title.getText())) return;

        title.animate().cancel();
        title.setTranslationY(0f);
        title.setAlpha(1f);

        float slide = Math.max(title.getHeight() * 0.6f, 20f);

        title.animate()
                .translationY(-slide)
                .alpha(0f)
                .setDuration(140)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    title.setText(target);
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

    // =========================
    // VIEWPAGER2 SETUP (swipe)
    // =========================
    private void setupViewPager(String code) {
        // Tous les codes de credos, dans l'ordre de la base
        codes = service.getCodes();

        pagerAdapter = new FanekenaPagerAdapter(this, codes);
        binding.viewPager.setAdapter(pagerAdapter);

        // Transition douce entre les pages
        binding.viewPager.setPageTransformer(new CrossfadeTransformer());

        // Page initiale = credo demandé
        int startIndex = Math.max(0, pagerAdapter.indexOf(code));
        binding.viewPager.setCurrentItem(startIndex, false);

        // Historique à chaque credo feuilleté
        binding.viewPager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        String current = pagerAdapter.getCode(position);
                        if (current != null) {
                            historyService.addHistory(current, "fanekena");
                        }
                    }
                });
    }

    // =========================
    // SESSION DE PROGRAMME
    // =========================
    /**
     * Affiche les fanekena consécutifs du programme à partir de l'index
     * donné, puis une page "Manaraka" qui enchaîne sur l'item suivant
     * (changement d'activity animé si le type change).
     */
    private void openInSessionMode(String sessionJson, int index, int startPage) {
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

        setupToolbar(CodeAdapter.getDisplayName(run.get(start).reference));
        deferEntryHistory(run.get(start).reference, "fanekena");

        int offset = sessionPrevItem != null ? 1 : 0;

        binding.viewPager.setOffscreenPageLimit(1);
        binding.viewPager.setAdapter(new ReaderSessionPagerAdapter(
                this,
                sessionPrevItem,
                run.size(),
                position -> FanekenaPageFragment.newInstance(
                        run.get(position).reference),
                sessionNextItem));

        // Transition douce entre les pages
        binding.viewPager.setPageTransformer(new CrossfadeTransformer());

        // Arrivée directe sur la page demandée (swipe arrière, ancrage)
        binding.viewPager.setCurrentItem(offset + start, false);

        binding.viewPager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {

                        int off = sessionPrevItem != null ? 1 : 0;

                        if (sessionPrevItem != null && position == 0) {
                            // Page "Teo aloha" feuilletée : item précédent
                            scheduleSessionBack();
                            return;
                        }

                        if (position < off + run.size()) {
                            ProgramSession.Item item = run.get(position - off);
                            if (getSupportActionBar() != null) {
                                getSupportActionBar().setTitle(
                                        CodeAdapter.getDisplayName(item.reference));
                            }
                            historyService.addHistory(item.reference, "fanekena");
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
                                    && pos < off + run.size();
                            if (onContent) cancelPendingSessionMove();
                        }
                    }
                });
    }

    /** Repos sur l'écran de transition ("Manaraka" / "Teo aloha") avant
     *  de lancer le lecteur suivant : le swipe se termine visuellement,
     *  l'activity se construit hors animation -> plus de saccade. */
    private static final long TRANSITION_REST_MS = 1500;

    /** Changement de lecteur programmé (annulable). */
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

    // =========================
    // PAGE TRANSFORMER
    // =========================
    /**
     * Crossfade + léger scale pour une transition fluide entre les pages.
     */
    private static class CrossfadeTransformer implements ViewPager2.PageTransformer {

        @Override
        public void transformPage(@NonNull View page, float position) {
            float absPos = Math.abs(position);

            // Fade : les pages s'estompent en s'éloignant
            page.setAlpha(1f - absPos * 0.3f);

            // Léger scale pour la profondeur
            float scale = 1f - absPos * 0.05f;
            page.setScaleX(scale);
            page.setScaleY(scale);
        }
    }

}