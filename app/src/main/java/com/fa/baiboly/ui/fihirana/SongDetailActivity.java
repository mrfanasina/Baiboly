package com.fa.baiboly.ui.fihirana;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.SettingsFragment;
import com.fa.baiboly.data.fihirana.FihiranaService;
import com.fa.baiboly.data.history.HistoryService;
import com.fa.baiboly.databinding.ActivitySongDetailBinding;
import com.fa.baiboly.models.Song;

import java.util.List;

public class SongDetailActivity extends AppCompatActivity {

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

        String songId = getIntent().getStringExtra("songId");

        // Save reading history
        historyService = new HistoryService(this);
        historyService.addHistory(songId, "fihirana");

        // Open solfa
        binding.solfaButton.setOnClickListener(v -> openSolfa());

        // Setup toolbar
        setupToolbar(songId != null ? songId.toUpperCase().replace("_", " ") : "HIra");

        // Load songs and setup ViewPager2
        setupViewPager(songId);
    }

    // =========================
    // MENU
    // =========================
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_song_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            openSettings();
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
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(title);
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

        // Update toolbar title and history when page changes
        binding.viewPager.registerOnPageChangeCallback(
                new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        currentSongIndex = position;
                        Song song = songsInCategory.get(position);

                        // Update toolbar title
                        if (getSupportActionBar() != null) {
                            getSupportActionBar().setTitle(
                                    song.getId().toUpperCase().replace("_", " "));
                        }

                        // Save to history
                        historyService.addHistory(song.getId(), "fihirana");
                    }
                });
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
        // Use the currently displayed song
        if (songsInCategory == null || currentSongIndex < 0) return;
        Song currentSong = songsInCategory.get(currentSongIndex);

        String category = "ff";
        String id = currentSong.getId();
        if (id.contains("_")) {
            String[] parts = id.split("_");
            if (parts.length >= 2) {
                category = parts[0];
                id = parts[1];
            }
        }

        Song song = new Song(id, 0, category, currentSong.getTitle());
        Intent intent = new Intent(this, SolfaActivity.class);
        intent.putExtra("song", song);
        startActivity(intent);
    }
}