package com.fa.baiboly;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.fa.baiboly.data.playback.PlaybackService;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.MofonainaRepository;
import com.fa.baiboly.databinding.ActivityMainBinding;
import com.fa.baiboly.ui.fihirana.PlaybackServiceBridge;
import com.fa.baiboly.ui.fihirana.SongDetailActivity;
import com.fa.baiboly.ui.search.SearchActivity;
import com.fa.baiboly.ui.verses.VersesActivity;
import com.fa.baiboly.worker.MofonainaSyncWorker;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    // Preference constants (must match SettingsFragment)
    private static final String PREFS_NAME = "app_settings";
    public static final String KEY_THEME = "theme_mode";
    private static final String KEY_TUTORIAL_SHOWN = "tutorial_shown";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // APPLY THEME BEFORE SUPER.ONCREATE AND BEFORE INFLATING VIEW
        applyAppThemeAtStartup();

        super.onCreate(savedInstanceState);

        // Inflate layout using ViewBinding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set high refresh rate if supported (for smoother UI)
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = 120.0f;
        getWindow().setAttributes(params);

        BottomNavigationView navView = binding.navView;

        // Get NavHostFragment and NavController
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host_fragment_activity_main);

        NavController navController = navHostFragment.getNavController();

        // Hide loader when navigation destination changes
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (binding.mainLoader != null) {
                binding.mainLoader.setVisibility(View.GONE);
            }
            // Update right-aligned toolbar title
            binding.toolbarTitle.setText(destination.getLabel());
        });

        // Set custom toolbar (do NOT use default ActionBar title system)
        setSupportActionBar(binding.toolbar);

        // Hide default ActionBar title (we use custom TextView instead)
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Connect BottomNavigationView with Navigation Component :
        // mêmes règles que NavigationUI (singleTop + restoreState +
        // popUpTo racine) mais avec une transition crossfade douce
        // entre les fragments (nav_fade_in / nav_fade_out)
        navView.setOnItemSelectedListener(item -> {
            NavOptions.Builder builder = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true);
            if ((item.getOrder() & Menu.CATEGORY_SECONDARY) == 0) {
                builder.setPopUpTo(
                        navController.getGraph().getStartDestinationId(), false);
            }
            builder.setEnterAnim(R.anim.nav_fade_in);
            builder.setExitAnim(R.anim.nav_fade_out);
            builder.setPopEnterAnim(R.anim.nav_fade_in);
            builder.setPopExitAnim(R.anim.nav_fade_out);
            try {
                navController.navigate(item.getItemId(), null, builder.build());
            } catch (IllegalArgumentException e) {
                // Item sans destination dans le graphe
                return false;
            }
            return true;
        });

        // Coche l'onglet correspondant à la destination courante
        // (setChecked ne redéclenche PAS la navigation)
        navController.addOnDestinationChangedListener(
                (controller, destination, arguments) -> {
                    MenuItem menuItem = navView.getMenu().findItem(
                            getBottomNavMenuId(destination.getId()));
                    if (menuItem != null) menuItem.setChecked(true);
                });

        // Sync bottom nav when navigating to sub-destinations like ChapterFragment
        // (which are not in the bottom menu but belong to the Baiboly tab)
        // IMPORTANT: use setChecked() NOT setSelectedItemId() — the latter re-triggers navigation
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId = destination.getId();
            if (destId == R.id.navigation_chapter) {
                navView.getMenu().findItem(R.id.navigation_dashboard).setChecked(true);
            }
        });

        //search
        binding.mitady.setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            startActivity(intent);
        });

        // Fandaharana (programs)
        binding.fandaharana.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.fa.baiboly.ui.fandaharana.FandaharanaActivity.class);
            startActivity(intent);
        });

        // Mini-lecteur : affiché au-dessus de la bottom nav dès qu'un feo
        // joue (il continue depuis SongDetailActivity via le service).
        setupPlaybackBar();

        // Optional: preload data in background
        // preloadMofonaina();
        scheduleMofonainaSync();

        // Show tutorial on first launch
        showTutorialIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Restore bottom nav selection when returning from another Activity
        // (e.g., VersesActivity opened from ChapterFragment)
        BottomNavigationView navView = binding.navView;
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host_fragment_activity_main);

        if (navHostFragment != null && navHostFragment.getNavController() != null) {
            int currentDest = navHostFragment.getNavController().getCurrentDestination().getId();
            int menuId = getBottomNavMenuId(currentDest);
            if (menuId != -1) {
                navView.getMenu().findItem(menuId).setChecked(true);
            }
        }
    }

    /**
     * Map navigation destination ID to bottom nav menu item ID.
     * Returns -1 if the destination doesn't belong to any bottom nav tab.
     */
    private int getBottomNavMenuId(int destId) {
        if (destId == R.id.navigation_home || destId == R.id.navigation_chapter) {
            // ChapterFragment is a sub-destination of the Baiboly tab
            // but since navigation_dashboard maps to Baiboly, we check both
            if (destId == R.id.navigation_chapter) {
                return R.id.navigation_dashboard;
            }
            return R.id.navigation_home;
        }
        if (destId == R.id.navigation_dashboard) return R.id.navigation_dashboard;
        if (destId == R.id.navigation_notifications) return R.id.navigation_notifications;
        if (destId == R.id.fanekena) return R.id.fanekena;
        return -1;
    }

    /**
     * Reads saved theme from SharedPreferences and applies it globally
     */
    private void applyAppThemeAtStartup() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Default to System (Follow System) if no preference is saved yet
        int savedMode = preferences.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        // Apply the mode immediately
        AppCompatDelegate.setDefaultNightMode(savedMode);

        // Couleurs de base choisies par l'utilisateur (avant inflation)
        ColorManager.applyTheme(this);
    }

    /**
     * Preload data to improve app performance
     */
    private void preloadMofonaina() {
        new Thread(() -> {
            try {
                MofonainaRepository repo = new MofonainaRepository(this);
                // Warm up cache (non-blocking network request)
                repo.get("https://www.fjkm.mg");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Inflate toolbar menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        com.fa.baiboly.ui.MenuHelper.enableOverflowIcons(this, menu);
        return true;
    }

    /**
     * Handle toolbar menu clicks
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Downloads (écran voasintona / data serveur)
        if (item.getItemId() == R.id.downloads) {
            com.fa.baiboly.ui.download.DownloadCenterActivity.open(this);
            return true;
        }

        // Transfert WiFi (send/receive, QR)
        if (item.getItemId() == R.id.transfer) {
            startActivity(new Intent(this, com.fa.baiboly.ui.transfer.TransferActivity.class));
            return true;
        }

        if (item.getItemId() == R.id.action_settings) {
            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment_activity_main);

            if (navHostFragment != null) {
                navHostFragment.getNavController().navigate(R.id.settingsFragment);
            }
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

        return super.onOptionsItemSelected(item);
    }

    /**
     * Mini-lecteur global : se connecte au PlaybackService. Si un feo
     * joue (même lancé depuis SongDetailActivity), la barre s'affiche
     * AU-DESSUS de la bottom navigation — jamais par-dessus.
     */
    private PlaybackServiceBridge playbackBridge;

    private void setupPlaybackBar() {
        playbackBridge = new PlaybackServiceBridge(this,
                new PlaybackServiceBridge.Listener() {
                    @Override
                    public void onPlayingChanged(boolean playing) {
                        syncPlaybackBar();
                    }

                    @Override
                    public void onProgress(int currentMs, int durationMs) {
                        binding.mainPlaybackBar.playbackTime.setText(
                                formatMs(currentMs) + " / " + formatMs(durationMs));
                        if (durationMs > 0) {
                            binding.mainPlaybackBar.playbackSeek.setMax(durationMs);
                        }
                        binding.mainPlaybackBar.playbackSeek.setProgress(currentMs);
                    }

                    @Override
                    public void onCompleted() {
                        syncPlaybackBar();
                    }

                    @Override
                    public void onError(String message) {
                        syncPlaybackBar();
                    }

                    @Override
                    public void onStopped() {
                        binding.mainPlaybackBar.getRoot().setVisibility(View.GONE);
                    }
                },
                this::syncPlaybackBar);
        playbackBridge.connect();

        // Play / pause
        binding.mainPlaybackBar.playbackToggle.setOnClickListener(
                v -> playbackBridge.toggle());
        // Stop
        binding.mainPlaybackBar.playbackClose.setOnClickListener(v -> {
            playbackBridge.stop();
            binding.mainPlaybackBar.getRoot().setVisibility(View.GONE);
        });
        // Tap sur la barre : rouvre le chant en cours
        binding.mainPlaybackBar.getRoot().setOnClickListener(v -> {
            PlaybackService.ServiceState s = playbackBridge.peekState();
            if (s != null && s.songId != null) {
                SongDetailActivity.open(this, s.songId, s.title);
            }
        });
    }

    /** Affiche/masque la barre selon l'état réel du service. */
    private void syncPlaybackBar() {
        runOnUiThread(() -> {
            if (binding == null || playbackBridge == null) return;
            PlaybackService.ServiceState s = playbackBridge.peekState();
            boolean visible = s != null && s.hasCurrent;
            binding.mainPlaybackBar.getRoot().setVisibility(
                    visible ? View.VISIBLE : View.GONE);
            if (!visible) return;

            binding.mainPlaybackBar.playbackToggle.setImageResource(
                    s.playing ? R.drawable.ic_pause : R.drawable.ic_play);
            adjustPlaybackBarForTablet();
        });
    }

    /** Sur tablette la barre reste compacte, calée à droite ; sur
     *  téléphone elle garde sa largeur wrap_content (déjà à droite). */
    private void adjustPlaybackBarForTablet() {
        boolean tablet = getResources().getConfiguration().screenWidthDp >= 600;
        ViewGroup.LayoutParams lp = binding.mainPlaybackBar.getRoot().getLayoutParams();
        lp.width = tablet
                ? getResources().getDisplayMetrics().widthPixels * 2 / 5
                : ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.mainPlaybackBar.getRoot().setLayoutParams(lp);
    }

    /** Format "1:23". */
    private static String formatMs(int ms) {
        int totalSec = ms / 1000;
        return (totalSec / 60) + ":"
                + String.format(java.util.Locale.US, "%02d", totalSec % 60);
    }

    @Override
    protected void onDestroy() {
        if (playbackBridge != null) {
            playbackBridge.disconnect(); // le service continue en fond
        }
        super.onDestroy();
    }

    /**
     * Show or hide Bottom Navigation bar (Called from Fragments)
     */
    public void setBottomNavVisibility(boolean visible) {
        if (binding == null || binding.navView == null) return;
        binding.navView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // =========================================================
    // TUTORIEL PREMIER LANCEMENT
    // =========================================================

    private void showTutorialIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean shown = prefs.getBoolean(KEY_TUTORIAL_SHOWN, false);
        if (!shown) {
            showTutorial();
            prefs.edit().putBoolean(KEY_TUTORIAL_SHOWN, true).apply();
        }
    }

    private void showTutorial() {
        View view = getLayoutInflater().inflate(R.layout.dialog_tutorial, null);

        ImageView image = view.findViewById(R.id.tutorialImage);
        TextView title = view.findViewById(R.id.tutorialTitle);
        TextView desc = view.findViewById(R.id.tutorialDescription);
        LinearLayout dots = view.findViewById(R.id.dotContainer);
        TextView btnSkip = view.findViewById(R.id.btnSkip);
        com.google.android.material.button.MaterialButton btnNext = view.findViewById(R.id.btnNext);

        // Tutorial pages in Malagasy — 8 pages
        final int[] images = {
                R.drawable.book_open,
                R.drawable.ic_bible,
                R.drawable.ic_music_note,
                R.drawable.ic_search,
                R.drawable.ic_ai_chat,
                R.drawable.ic_highlight,
                R.drawable.history,
                R.drawable.ic_home_black_24dp
        };
        final String[] titles = {
                "Tongasoa eto!",
                "Baiboly Malagasy",
                "Vakiteny isam-bolana",
                "Fihirana",
                "Chat IA — Fanazavana",
                "Highlight sy Fintinina",
                "Tadidiana (Historique)",
                "Widget amin'ny ofisinao"
        };
        final String[] descs = {
                "Fikarohana ny Baiboly amin'ny teny Malagasy! Manana vakiteny isam-andro, fihirana, fanazavana, ary rafitra Chat IA isika ho anao.",
                "Vakio ny Baiboly isam-bolana araka ny vanim-potoana ara-pihetseham-po. Misy Perikopa isam-andro ho anao.",
                "Jereo ny vakiteny isam-bolana amin'ny alalan'ny volana. Misy Perikopa isam-andro araka ny Advent, Krismasy, Karemy, Paska, ary Pentekosta.",
                "Tadiavo ny hirainao amin'ny alalan'ny laharana na sokajy. Manana hira fahagagana isika: Tsy maintsy hifaly, Iraisam-pirenena, sns.",
                "Manana Chat IA isika mba hanazavana ny Baiboly. Andramo manontany momba ny Baiboly — hafatra, tantara, ary fanazavana!",
                "Manao highlight amin'ny teny sady tadiavina avy hatrany amin'ny Baiboly. Azonao tadiavina ny fetin-dreny.",
                "Jereo ny tantara sady tadiavina tany amin'ny historique. Tsy mila manao highlight indray mba hahitana ny fetin-dreny.",
                "Ampidiro ny widget amin'ny sehatra ofisinao. Hahitana ny mofonaina isam-andro tsy manokatra ny app."
        };

        // Build dots
        dots.removeAllViews();
        View[] dotViews = new View[titles.length];
        for (int i = 0; i < titles.length; i++) {
            View dot = new View(this);
            int size = dpToPx(8);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(i == 0 ? R.drawable.circle_green : R.drawable.circle_dot);
            dots.addView(dot);
            dotViews[i] = dot;
        }

        final int[] current = {0};

        Runnable updatePage = () -> {
            image.setImageResource(images[current[0]]);
            title.setText(titles[current[0]]);
            desc.setText(descs[current[0]]);

            for (int i = 0; i < dotViews.length; i++) {
                dotViews[i].setBackgroundResource(i == current[0] ? R.drawable.circle_green : R.drawable.circle_dot);
            }

            if (current[0] == titles.length - 1) {
                btnNext.setText("Manomboka");
                btnSkip.setVisibility(View.GONE);
            } else {
                btnNext.setText("Manaraka");
                btnSkip.setVisibility(View.VISIBLE);
            }
        };

        updatePage.run();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_tutorial);
        }

        btnNext.setOnClickListener(v -> {
            if (current[0] < titles.length - 1) {
                current[0]++;
                updatePage.run();
            } else {
                dialog.dismiss();
            }
        });

        btnSkip.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    // =========================================================
    // WIDGET PINNING
    // =========================================================

    public void pinWidget(String providerLabel) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        if (!mgr.isRequestPinAppWidgetSupported()) {
            return;
        }

        // Find the widget provider by label
        for (AppWidgetProviderInfo info : mgr.getInstalledProviders()) {
            if (info.provider.getPackageName().equals(getPackageName())
                    && info.label.equalsIgnoreCase(providerLabel)) {
                mgr.requestPinAppWidget(info.provider, null, null);
                return;
            }
        }
    }

    public void pinWidgetByClass(Class<?> widgetClass) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        if (!mgr.isRequestPinAppWidgetSupported()) return;

        android.content.ComponentName cn = new android.content.ComponentName(this, widgetClass);
        mgr.requestPinAppWidget(cn, null, null);
    }

    /**
     * Make mofonaina in Background
     */
    private void scheduleMofonainaSync() {

        Constraints constraints =
                new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build();

        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                        MofonainaSyncWorker.class,
                        6,
                        TimeUnit.HOURS
                )
                        .setConstraints(constraints)
                        .build();

        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                        "mofonaina_sync",
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                );
    }

}