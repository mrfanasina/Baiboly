package com.fa.baiboly.ui.fihirana;

import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.SettingsFragment;
import com.fa.baiboly.databinding.ActivitySolfaBinding;
import com.fa.baiboly.models.Song;

import java.io.IOException;

public class SolfaActivity extends AppCompatActivity {

    private WebView webView;
    private Toolbar toolbar;
    private ImageView btnFullScreen;
    private ActivitySolfaBinding binding;
    private boolean isImmersiveMode = false;

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_TEXT_SIZE = "text_size";

    private float textSize = 18f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable injectionRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Mode nuit + couleurs de base choisies (avant inflation)
        ColorManager.applyGlobalTheme(this);
        super.onCreate(savedInstanceState);

        // Utilisation correcte du ViewBinding si initialisé
        binding = ActivitySolfaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        btnFullScreen = findViewById(R.id.fullScreen);
        btnFullScreen.setOnClickListener(v -> toggleImmersiveMode());

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        textSize = prefs.getFloat(KEY_TEXT_SIZE, 18f);

        webView = findViewById(R.id.webView);
        configureWebView();

        Song song = (Song) getIntent().getSerializableExtra("song");
        String filePath = "solfa/none.html";

        if (song != null) {
            String htmlPath = "solfa/" + song.getCategory() + "/" + song.getId() + ".html";
            if (assetExists(htmlPath)) {
                filePath = htmlPath;
            }
            setupToolbar(song.getId() != null ? song.getCategory().toUpperCase() + " " + song.getId() : "Hira");
        } else {
            setupToolbar("Hira");
        }

        webView.loadUrl("file:///android_asset/" + filePath);
        startInjectionLoop();
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);

        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Relance proprement la boucle à chaque nouvelle page chargée
                startInjectionLoop();
            }
        });

        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF);
        }
        settings.setTextZoom(100);
        webView.setInitialScale(1);
    }

    /**
     * Boucle d'injection continue nettoyée pour éviter la superposition de runnables
     */
    private void startInjectionLoop() {
        if (injectionRunnable != null) {
            handler.removeCallbacks(injectionRunnable);
        }

        injectionRunnable = new Runnable() {
            @Override
            public void run() {
                injectStyle();
                // Réinjection toutes les 800ms pour contrer les scripts dynamiques du DOM
                handler.postDelayed(this, 800);
            }
        };

        handler.postDelayed(injectionRunnable, 300);
    }

    private void injectStyle() {
        boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        String bg = isNight ? "#000000" : "#E9E9E9";
        String fg = isNight ? "#FFFFFF" : "#1C1B1F";
        String accent = isNight ? "#F0F0F0" : "#2C2B2F";

        // Échelle uniforme : les caractères du solfa sont positionnés au
        // pixel dans le SVG (x par glyphe, ex. x="24 35 38 49"). Forcer
        // une font-size, une graisse, une police ou des espacements
        // décale chaque glyphe de sa position : textes mélangés. On
        // zoome donc la feuille entière, métriques préservées.
        // textSize 18 (défaut) = 100 %, borné 70–200 %.
        int scalePct = Math.round(textSize / 18f * 100f);
        scalePct = Math.max(70, Math.min(200, scalePct));

        String js =
                "javascript:(function() {" +
                        // =========================
                        // CRÉATION / MAJ DU STYLE
                        // =========================
                        "var style = document.getElementById('solfa-style');" +
                        "if(!style) {" +
                        "   style = document.createElement('style');" +
                        "   style.id = 'solfa-style';" +
                        "   document.head.appendChild(style);" +
                        "}" +

                        // =========================
                        // CSS INJECTÉ
                        // =========================
                        "style.innerHTML = `" +

                        /* PAGE GLOBALE */
                        "html, body {" +
                        "   margin:0 !important;" +
                        "   padding:16px !important;" +
                        "   background:" + bg + " !important;" +
                        "   color:" + fg + " !important;" +
                        "   overflow-x:auto !important;" +
                        "}" +

                        /* Conteneur en block : une feuille plus large que
                           l'écran déborde vers la droite (scrollable), au
                           lieu d'être recadrée des deux côtés par le
                           centrage flex de .p. */
                        ".p { display:block !important; }" +

                        /* FEUILLE : zoom uniforme, métriques préservées
                           (le pinçage reste disponible en plus). */
                        "svg {" +
                        "   background:transparent !important;" +
                        "   overflow:visible !important;" +
                        "   width:" + scalePct + "% !important;" +
                        "   max-width:none !important;" +
                        "   height:auto !important;" +
                        "   display:block !important;" +
                        "   margin:0 auto !important;" +
                        "}" +

                        /* COULEURS UNIQUEMENT : ni famille, ni taille, ni
                           espacement — chaque caractère est positionné au
                           pixel dans la feuille. */
                        "svg text, svg tspan {" +
                        "   fill:" + fg + " !important;" +
                        "   color:" + fg + " !important;" +
                        "}" +

                        /* LIGNES DE PORTÉE ET TRAITS */
                        "svg path, svg line, svg polyline {" +
                        "   stroke:" + accent + " !important;" +
                        "   fill:none !important;" +
                        "}" +

                        /* FORMES PLEINES SVG */
                        "svg rect, svg circle, svg ellipse {" +
                        "   fill:" + accent + " !important;" +
                        "}" +

                        /* INTERDICTION DE SÉLECTION */
                        "body {" +
                        "   user-select:none !important;" +
                        "   -webkit-user-select:none !important;" +
                        "}" +

                        "`;" +
                        "})();";

        webView.evaluateJavascript(js, null);
    }

    private void toggleImmersiveMode() {
        isImmersiveMode = !isImmersiveMode;

        if (isImmersiveMode) {
            if (getSupportActionBar() != null) getSupportActionBar().hide();
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            );
        } else {
            if (getSupportActionBar() != null) getSupportActionBar().show();
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private boolean assetExists(String path) {
        AssetManager am = getAssets();
        try {
            am.open(path).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ==========================================
    // CONTRÔLE DU FRAGMENT DE CONFIGURATION
    // ==========================================
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
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(title);
        }

        toolbar.setNavigationOnClickListener(v -> {
            if (isSettingsOpen()) {
                closeSettings();
            } else {
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Libère le handler pour éviter les fuites de mémoire
        if (injectionRunnable != null) {
            handler.removeCallbacks(injectionRunnable);
        }
    }

    @Override
    public void onBackPressed() {
        if (isImmersiveMode) {
            toggleImmersiveMode();
        } else if (isSettingsOpen()) {
            closeSettings();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}