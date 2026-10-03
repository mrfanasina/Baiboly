package com.fa.baiboly.ui.fihirana;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.fihirana.FihiranaService;
import com.fa.baiboly.databinding.ActivitySongListBinding;
import com.fa.baiboly.models.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SongListActivity extends AppCompatActivity {

    private static final long DEBOUNCE_MS = 300;

    private ActivitySongListBinding binding;
    private FihiranaService service;
    private SongAdapter adapter;
    private List<Song> allSongs = new ArrayList<>();
    private String currentCategory;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Mode nuit + couleurs de base choisies (avant inflation)
        ColorManager.applyGlobalTheme(this);
        super.onCreate(savedInstanceState);
        binding = ActivitySongListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Performance & Refresh Rate
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = 120.0f;
        getWindow().setAttributes(params);

        // Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        service = new FihiranaService(this);
        currentCategory = getIntent().getStringExtra("category");
        if (currentCategory == null) currentCategory = "Tous";
        setTitle(currentCategory.toUpperCase());

        setupRecyclerView();
        loadSongs(currentCategory);
        setupSearchFilter();
    }

    private void setupRecyclerView() {
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void loadSongs(String category) {
        allSongs = service.getSongsByCategory(category);

        // Initial setup of adapter with full list
        adapter = new SongAdapter(new ArrayList<>(allSongs), song -> {
            SongDetailActivity.open(this, song.getId(), song.getTitle());
        });

        binding.recyclerView.setAdapter(adapter);
    }

    private void setupSearchFilter() {
        binding.etSearchNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingFilter != null) mainHandler.removeCallbacks(pendingFilter);
                String query = s.toString();

                if (query.trim().length() < 2) {
                    // Recherche courte → filtrage local rapide (numéro/titre)
                    filterLocal(query);
                    return;
                }

                // Recherche plus longue → cherche dans les paroles aussi (asynchrone)
                pendingFilter = () -> searchLyrics(query);
                mainHandler.postDelayed(pendingFilter, DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * Filtrage local rapide : correspond au numéro OU au titre.
     */
    private void filterLocal(String query) {
        List<Song> filteredList = new ArrayList<>();

        if (query.isEmpty()) {
            filteredList.addAll(allSongs);
        } else {
            String lowerQuery = query.toLowerCase();

            for (Song song : allSongs) {
                String songNumber = String.valueOf(song.getNumber());
                String title = song.getTitle() != null ? song.getTitle().toLowerCase() : "";

                if (songNumber.contains(lowerQuery) || title.contains(lowerQuery)) {
                    filteredList.add(song);
                }
            }
        }

        adapter.updateList(filteredList);
    }

    /**
     * Recherche dans les paroles via la base de données (asynchrone).
     * Retourne les chants dont les couplets contiennent le texte recherché.
     */
    private void searchLyrics(String query) {
        executor.execute(() -> {
            List<Song> results = service.search(query);
            mainHandler.post(() -> {
                if (results.isEmpty()) {
                    // Pas de résultat dans les paroles → fallback sur filtre local
                    filterLocal(query);
                } else {
                    adapter.updateList(results);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingFilter != null) mainHandler.removeCallbacks(pendingFilter);
        executor.shutdownNow();
    }
}