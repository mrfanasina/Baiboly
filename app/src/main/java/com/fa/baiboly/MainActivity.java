package com.fa.baiboly;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
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
import com.fa.baiboly.data.download.AppUpdateNotifier;
import com.fa.baiboly.models.DownloadEntry;
import com.fa.baiboly.databinding.ActivityMainBinding;
import com.fa.baiboly.ui.common.AppBackgrounds;
import com.fa.baiboly.ui.fihirana.PlaybackServiceBridge;
import com.fa.baiboly.ui.fihirana.SongDetailActivity;
import com.fa.baiboly.ui.search.SearchActivity;
import com.fa.baiboly.ui.verses.VersesActivity;
import com.fa.baiboly.worker.MofonainaSyncWorker;
import android.content.res.ColorStateList;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import androidx.core.content.ContextCompat;
import com.google.android.material.color.MaterialColors;

import java.util.WeakHashMap;
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

        // Barre d'état transparente : le fond de l'écran passe dessous.
        setupEdgeToEdge();

        BottomNavigationView navView = binding.navView;
        styleBottomNavColors(navView);

        // Get NavHostFragment and NavController
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host_fragment_activity_main);

        NavController navController = navHostFragment.getNavController();

        // Barre du haut et bottom nav posées sur les barres système, contenu
        // des écrans décalé pour défiler SOUS la barre du haut.
        setupSystemBarInsets(navHostFragment);

        // Hide loader when navigation destination changes
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (binding.mainLoader != null) {
                binding.mainLoader.setVisibility(View.GONE);
            }
            // Update right-aligned toolbar title
            binding.toolbarTitle.setText(destination.getLabel());
            // Sur l'écran de configuration : flèche retour, et plus d'entrée
            // « Fikirakirana » dans le menu (sinon un second accès aux
            // paramètres apparaîtrait pendant qu'on y est déjà).
            styleSettingsDestination(destination.getId(), controller);
            // Toolbar en verre flouté : la photo dépend de l'écran affiché.
            applyToolbarGlass(destination.getId());
        });

        // Toolbar en verre flouté (photo de l'écran derrière la barre).
        // Appliqué après la mesure pour connaître la taille exacte de la barre.
        binding.toolbar.post(() -> {
            int destId = navController.getCurrentDestination() != null
                    ? navController.getCurrentDestination().getId()
                    : R.id.navigation_home;
            applyToolbarGlass(destId);
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

        // Fandaharana (programmes de lecture)
        binding.fandaharana.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.fa.baiboly.ui.fandaharana.FandaharanaActivity.class);
            startActivity(intent);
        });

        // Litorjia (programme de culte — module séparé)
        binding.litorjia.setOnClickListener(v -> {
            com.fa.baiboly.ui.litorjia.LitorjiaListActivity.open(this);
        });

        // Mini-lecteur : affiché au-dessus de la bottom nav dès qu'un feo
        // joue (il continue depuis SongDetailActivity via le service).
        setupPlaybackBar();

        // Optional: preload data in background
        // preloadMofonaina();
        scheduleMofonainaSync();

        // Show tutorial on first launch (le pop-up de mise à jour attend le
        // prochain lancement pour ne pas s'empiler par-dessus).
        boolean tutorialShown = showTutorialIfNeeded();
        checkForAppUpdate(savedInstanceState == null && !tutorialShown);
    }

    // =========================================================
    // ⬆️ MISE À JOUR : point orange dans le menu + pop-up
    // =========================================================

    /** Le pop-up n'est proposé qu'une fois par lancement de l'activité. */
    private boolean updateDialogShown;
    /** Vrai quand une version plus récente (non ignorée) est publiée. */
    private boolean updateAvailable;

    /**
     * Vérifie update.json en arrière-plan. Le point orange apparaît dès
     * qu'une version plus récente est publiée ; le pop-up n'est proposé
     * que si l'utilisateur n'a pas coché « ne plus afficher » pour elle.
     */
    private void checkForAppUpdate(boolean allowPopup) {
        AppUpdateNotifier.check(this, update -> {
            boolean show = update != null
                    && !AppUpdateNotifier.isDismissed(this, update);
            setUpdateIndicator(show);
            if (show && allowPopup && !updateDialogShown && !isFinishing()) {
                updateDialogShown = true;
                showUpdateDialog(update);
            }
        });
    }

    /**
     * Affiche / masque la pastille orange sur l'entrée de menu
     * « Fanavaozana ny application ».
     */
    private void setUpdateIndicator(boolean visible) {
        if (updateAvailable == visible) return;
        updateAvailable = visible;
        invalidateOptionsMenu();
    }

    /**
     * Icône de l'entrée « Fanavaozana ny application » : l'icône habituelle
     * (teintée comme les autres) + une pastille orange en haut à droite
     * quand une mise à jour (non ignorée) est disponible.
     */
    private Drawable downloadsMenuIcon() {

        Drawable base = ContextCompat.getDrawable(this, R.drawable.ic_auto_mode);
        if (base == null) return null;
        base = base.mutate();
        base.setTint(MaterialColors.getColor(
                binding.toolbar, android.R.attr.textColorPrimary));

        if (!updateAvailable) return base;

        Drawable dot = ContextCompat.getDrawable(this, R.drawable.bg_update_dot);
        if (dot == null) return base;

        int baseSize = base.getIntrinsicWidth();
        if (baseSize <= 0) {
            baseSize = Math.round(24 * getResources().getDisplayMetrics().density);
        }
        int dotSize = Math.round(9 * getResources().getDisplayMetrics().density);

        LayerDrawable icon = new LayerDrawable(new Drawable[]{base, dot});
        // Pastille en haut à droite de l'icône.
        icon.setLayerInset(1, baseSize - dotSize, 0, 0, baseSize - dotSize);
        return icon;
    }

    /** Pop-up « mise à jour disponible » avec case « ne plus afficher ». */
    private void showUpdateDialog(DownloadEntry update) {
        View view = getLayoutInflater().inflate(
                R.layout.dialog_update_available, null);
        TextView message = view.findViewById(R.id.textUpdateMessage);
        TextView changelogTitle = view.findViewById(R.id.textUpdateChangelogTitle);
        TextView changelogText = view.findViewById(R.id.textUpdateChangelog);
        android.widget.CheckBox neverShow =
                view.findViewById(R.id.checkNeverShow);

        String version = AppUpdateNotifier.versionLabel(update);
        StringBuilder text = new StringBuilder(
                "Misy version vaovao");
        if (!version.isEmpty()) text.append(" (").append(version).append(')');
        text.append(" vonona hosintomina. Tsindrio « Hijery » hamakafaka ")
                .append("ny fanavaozana.");
        message.setText(text.toString());

        // Changelog complet (toutes les lignes de update.json).
        java.util.List<String> notes = update.getChangelog();
        if (notes != null && !notes.isEmpty()) {
            StringBuilder log = new StringBuilder();
            for (String note : notes) {
                if (log.length() > 0) log.append('\n');
                log.append("• ").append(note);
            }
            changelogText.setText(log.toString());
            changelogText.setVisibility(View.VISIBLE);
            changelogTitle.setVisibility(View.VISIBLE);
        } else {
            changelogText.setVisibility(View.GONE);
            changelogTitle.setVisibility(View.GONE);
        }

        new AlertDialog.Builder(this)
                .setTitle("Fanavaozana vaovao")
                .setIcon(R.drawable.ic_auto_mode)
                .setView(view)
                .setPositiveButton("Hijery", (d, w) -> {
                    if (neverShow.isChecked()) {
                        AppUpdateNotifier.dismissForVersion(this, update);
                        setUpdateIndicator(false);
                    }
                    com.fa.baiboly.ui.download.DownloadCenterActivity.open(this);
                })
                .setNegativeButton("Aoriana", (d, w) -> {
                    if (neverShow.isChecked()) {
                        AppUpdateNotifier.dismissForVersion(this, update);
                        setUpdateIndicator(false);
                    }
                })
                .show();
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
        if (destId == R.id.navigation_fihirana) return R.id.navigation_fihirana;
        if (destId == R.id.fanekena) return R.id.fanekena;
        return -1;
    }

    // =========================================================
    // EDGE TO EDGE : barre d'état transparente + contenu sous la barre
    // =========================================================

    /** Hauteur de la barre du haut, hors barre d'état. */
    private int actionBarSizePx;

    /** Décalage vertical à appliquer au contenu des écrans. */
    private int contentTopInsetPx;

    /** Padding haut d'origine, pour pouvoir réappliquer sans cumuler. */
    private final WeakHashMap<View, Integer> originalPaddingTop = new WeakHashMap<>();

    private void setupEdgeToEdge() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        boolean light = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
    }

    private void setupSystemBarInsets(NavHostFragment navHostFragment) {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, value, true);
        actionBarSizePx = TypedValue.complexToDimensionPixelSize(
                value.data, getResources().getDisplayMetrics());

        // Chaque écran reçoit son décalage dès que sa vue est créée.
        navHostFragment.getChildFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewCreated(@NonNull FragmentManager fm,
                                                      @NonNull Fragment fragment,
                                                      @NonNull View view,
                                                      @Nullable Bundle savedState) {
                        applyContentTopInset(view);
                    }
                }, false);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // La barre du haut s'étend derrière la barre d'état : la photo
            // floutée passe sous l'heure, le contenu reste lisible dessous.
            int barHeight = actionBarSizePx + bars.top;
            ViewGroup.LayoutParams lp = binding.toolbar.getLayoutParams();
            if (lp.height != barHeight) {
                lp.height = barHeight;
                binding.toolbar.setLayoutParams(lp);
            }
            if (binding.toolbar.getPaddingTop() != bars.top) {
                binding.toolbar.setPadding(0, bars.top, 0, 0);
            }

            // Bottom nav : son fond passe sous la barre de navigation système.
            if (binding.navView.getPaddingBottom() != bars.bottom) {
                binding.navView.setPadding(0, 0, 0, bars.bottom);
            }

            if (contentTopInsetPx != barHeight) {
                contentTopInsetPx = barHeight;
                applyContentTopInsetToFragments(navHostFragment);
                // Double post : la photo du verre est calculée après la
                // mesure de la barre (elle vient de changer de hauteur).
                binding.toolbar.post(() -> binding.toolbar.post(
                        () -> applyToolbarGlass(currentDestinationId(navHostFragment))));
            }
            return insets;
        });
    }

    private void applyContentTopInsetToFragments(NavHostFragment host) {
        if (host == null) return;
        for (Fragment fragment : host.getChildFragmentManager().getFragments()) {
            View view = fragment.getView();
            if (view != null) applyContentTopInset(view);
        }
    }

    /**
     * Décale le contenu de l'écran sous la barre du haut. Quand l'écran est
     * un conteneur défilant plein cadre, c'est lui qui est décalé et son
     * clipToPadding est désactivé : le texte défile donc derrière la barre.
     */
    private void applyContentTopInset(View root) {
        if (root == null || contentTopInsetPx <= 0) return;
        View target = paddingTarget(root);
        Integer original = originalPaddingTop.get(target);
        if (original == null) {
            original = target.getPaddingTop();
            originalPaddingTop.put(target, original);
        }
        target.setPaddingRelative(target.getPaddingStart(), original + contentTopInsetPx,
                target.getPaddingEnd(), target.getPaddingBottom());
        if (isScrollContainer(target)) {
            ((ViewGroup) target).setClipToPadding(false);
        }
    }

    private static boolean isScrollContainer(View view) {
        return view instanceof NestedScrollView
                || view instanceof android.widget.ScrollView
                || view instanceof androidx.recyclerview.widget.RecyclerView;
    }

    /** Le défilant plein cadre s'il existe, sinon la racine de l'écran. */
    private View paddingTarget(View root) {
        if (isScrollContainer(root)) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                ViewGroup.LayoutParams lp = child.getLayoutParams();
                if (lp != null
                        && lp.height == ViewGroup.LayoutParams.MATCH_PARENT
                        && isScrollContainer(child)) {
                    return child;
                }
            }
        }
        return root;
    }

    private int currentDestinationId(NavHostFragment host) {
        if (host == null || host.getNavController() == null
                || host.getNavController().getCurrentDestination() == null) {
            return R.id.navigation_home;
        }
        return host.getNavController().getCurrentDestination().getId();
    }

    /**
     * Toolbar « transparent blur » : verre flouté de la photo de l'écran
     * courant. Baiboly / Toko utilisent fond_02, comme le fond du fragment.
     */
    private void applyToolbarGlass(int destId) {
        if (binding == null) return;
        int photoRes;
        if (destId == R.id.navigation_dashboard || destId == R.id.navigation_chapter) {
            photoRes = R.drawable.fond_02;
        } else if (destId == R.id.navigation_fihirana) {
            photoRes = R.drawable.fond_12;
        } else {
            photoRes = AppBackgrounds.getHomeBackground(this);
        }
        // Photo derrière les barres : les coins arrondis du verre laissent voir
        // le décor de l'écran, pas le fond de fenêtre (noir en mode sombre).
        AppBackgrounds.applyBarBackdrop(binding.getRoot(), photoRes);

        AppBackgrounds.applyGlassBar(binding.toolbar, photoRes);
        // Bottom navigation : même verre, coins arrondis en haut.
        if (binding.navView.getHeight() > 0) {
            AppBackgrounds.applyGlassBar(binding.navView, photoRes, 22f, 0f);
        } else {
            binding.navView.post(() -> AppBackgrounds.applyGlassBar(
                    binding.navView, photoRes, 22f, 0f));
        }
    }

    /**
     * Barre du haut en mode « configuration » : flèche retour + titre
     * « Fikirakirana », et suppression de l'entrée de menu qui ouvrirait à
     * nouveau les paramètres.
     */
    private void styleSettingsDestination(int destId, NavController navController) {
        if (binding == null) return;
        boolean onSettings = destId == R.id.settingsFragment;

        if (onSettings) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            binding.toolbar.setNavigationOnClickListener(
                    v -> navController.popBackStack());
        } else {
            binding.toolbar.setNavigationIcon(null);
            binding.toolbar.setNavigationOnClickListener(null);
        }

        invalidateOptionsMenu();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem settings = menu.findItem(R.id.action_settings);
        if (settings != null) {
            NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment_activity_main);
            boolean onSettings = host != null && host.getNavController() != null
                    && host.getNavController().getCurrentDestination() != null
                    && host.getNavController().getCurrentDestination().getId()
                    == R.id.settingsFragment;
            settings.setVisible(!onSettings);
        }

        // Pastille de mise à jour sur « Fanavaozana ny application ».
        MenuItem downloads = menu.findItem(R.id.downloads);
        if (downloads != null) {
            Drawable icon = downloadsMenuIcon();
            if (icon != null) downloads.setIcon(icon);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * Couleurs des onglets de la bottom nav. La couleur active est adoucie vers
     * le ton de la barre : le rose/vert saturé du thème jurait avec le verre
     * flouté.
     */
    private void styleBottomNavColors(BottomNavigationView navView) {
        int primary = MaterialColors.getColor(navView,
                androidx.appcompat.R.attr.colorPrimary);
        int onSurface = MaterialColors.getColor(navView,
                com.google.android.material.R.attr.colorOnSurface);
        int inactive = MaterialColors.getColor(navView,
                com.google.android.material.R.attr.colorOnSurfaceVariant);

        int active = ColorUtils.blendARGB(primary, onSurface, 0.35f);

        ColorStateList tint = new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{active, inactive});
        navView.setItemIconTintList(tint);
        navView.setItemTextColor(tint);
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

        // Diksionera Baiboly (liste des mots + filtre)
        if (item.getItemId() == R.id.dictionary) {
            startActivity(new Intent(this,
                    com.fa.baiboly.ui.dictionary.DictionaryListActivity.class));
            return true;
        }

        // Downloads (écran voasintona / data serveur)
        if (item.getItemId() == R.id.downloads) {
            com.fa.baiboly.ui.download.DownloadCenterActivity.open(this);
            return true;
        }

        // 📝 Notes de lecture (écran central : recherche + épinglage)
        if (item.getItemId() == R.id.notes) {
            com.fa.baiboly.ui.notes.NotesActivity.open(this);
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

    /** @return true si le tutoriel vient d'être affiché. */
    private boolean showTutorialIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean shown = prefs.getBoolean(KEY_TUTORIAL_SHOWN, false);
        if (!shown) {
            showTutorial();
            prefs.edit().putBoolean(KEY_TUTORIAL_SHOWN, true).apply();
            return true;
        }
        return false;
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