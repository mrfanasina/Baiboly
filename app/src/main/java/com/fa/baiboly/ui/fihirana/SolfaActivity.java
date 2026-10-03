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

        String js =
                "javascript:(function() {" +
                        // ==========================================
                        // NETTOYAGE ET CORRECTION DE L'ESPACEMENT SVG
                        // ==========================================
                        "document.querySelectorAll('svg text, svg tspan').forEach(function(t) {" +
                        "   t.removeAttribute('textLength');" +
                        "   t.removeAttribute('lengthAdjust');" +
                        "   t.removeAttribute('dx');" +
                        "   t.removeAttribute('dy');" +
                        "});" +

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

                        /* PAGE GLOBAL */
                        "html, body {" +
                        "   margin:0 !important;" +
                        "   padding:16px !important;" +
                        "   background:" + bg + " !important;" +
                        "   color:" + fg + " !important;" +
                        "   font-size:" + textSize + "px !important;" +
                        "   font-family: system-ui, sans-serif !important;" +
                        "   line-height:2.0 !important;" +
                        "}" +

                        /* TEXTE STANDARD */
                        "body, p, div, span {" +
                        "   color:" + fg + " !important;" +
                        "}" +

                        /* CONTENEUR SVG */
                        "svg {" +
                        "   background:transparent !important;" +
                        "   overflow:visible !important;" +
                        "   width:100% !important;" +
                        "   height:auto !important;" +
                        "}" +

                        /* TEXTE DANS LE SVG (SOLFÈGE) */
                        "svg text, svg tspan {" +
                        "   fill:" + fg + " !important;" +
                        "   color:" + fg + " !important;" +
                        "   font-family: system-ui, sans-serif !important;" +
                        "   font-size:" + textSize + "px !important;" +
                        "   font-weight:500 !important;" +
                        "   letter-spacing: 0.5px !important;" +
                        "   word-spacing: 4px !important;" +
                        "   white-space: pre-wrap !important;" +
                        "   text-rendering: optimizeLegibility !important;" +
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