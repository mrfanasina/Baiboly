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

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.SettingsFragment;
import com.fa.baiboly.data.fanekena.FanekenaService;
import com.fa.baiboly.data.history.HistoryService;
import com.fa.baiboly.databinding.ActivityFanekenaBinding;

import java.util.List;

public class FanekenaActivity extends AppCompatActivity {

    private ActivityFanekenaBinding binding;
    private FanekenaService service;
    private HistoryService historyService;

    /** Quand true, le swipe horizontal entre les credos est désactivé. */
    private boolean swipeLocked = false;

    // Swipe navigation
    private List<String> codes;
    private FanekenaPagerAdapter pagerAdapter;

    public static final String KEY_JUSTIFICATION = "justification_mode";


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

        String code = getIntent().getStringExtra("code");

        historyService = new HistoryService(this);

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