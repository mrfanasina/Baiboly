package com.fa.baiboly.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.search.GlobalSearchService;
import com.fa.baiboly.databinding.ActivitySearchBinding;
import com.fa.baiboly.models.Fanekena;
import com.fa.baiboly.models.GlobalSearchResult;
import com.fa.baiboly.models.SearchResult;
import com.fa.baiboly.models.Song;
import com.fa.baiboly.ui.ai.AiExplanationActivity;
import com.fa.baiboly.ui.fihirana.SongDetailActivity;
import com.fa.baiboly.ui.verses.VersesActivity;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchActivity extends AppCompatActivity implements SearchAdapter.OnResultClickListener {

    private static final long DEBOUNCE_MS = 350;
    private static final int MIN_QUERY_LEN = 2;

    private ActivitySearchBinding binding;
    private GlobalSearchService searchService;
    private SearchAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    // Jeton d'annulation : ignore les résultats d'une recherche périmée
    private int searchToken = 0;

    private List<GlobalSearchResult> lastFullResults = new ArrayList<>();
    private String currentQuery = "";

    /** Filtre actif : null = "Tout". type=CHANT + category=null = tous les chants. */
    private FilterTag activeFilter = null;

    private static class FilterTag {
        final GlobalSearchResult.Type type; // null = Tout
        final String category;              // pour CHANT uniquement, null = toutes catégories
        FilterTag(GlobalSearchResult.Type type, String category) {
            this.type = type;
            this.category = category;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Mode nuit + couleurs de base choisies (avant inflation)
        ColorManager.applyGlobalTheme(this);
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = 120.0f;
        getWindow().setAttributes(params);

        searchService = new GlobalSearchService(this);

        adapter = new SearchAdapter(this);
        binding.recyclerResults.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerResults.setAdapter(adapter);
        binding.recyclerResults.setItemAnimator(null); // évite le clignotement au filtrage

        // Masque le clavier dès que l'utilisateur scrolle les résultats
        binding.recyclerResults.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@androidx.annotation.NonNull RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard();
            }
        });

        setupSearchFilter();
        setupButtons();
        showState(State.IDLE, "");

        binding.etSearchNumber.requestFocus();
        showKeyboard();
    }

    private void setupButtons() {
        binding.btnBack.setOnClickListener(v -> onBackPressed());

        binding.btnClearSearch.setOnClickListener(v -> {
            binding.etSearchNumber.setText("");
            binding.etSearchNumber.requestFocus();
            showKeyboard();
        });

        binding.btnAskAiEmpty.setOnClickListener(v -> onAiCardClick(currentQuery));

        binding.etSearchNumber.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void setupSearchFilter() {
        binding.etSearchNumber.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);

                if (pendingSearch != null) mainHandler.removeCallbacks(pendingSearch);
                String query = s.toString();

                if (query.trim().length() < MIN_QUERY_LEN) {
                    searchToken++; // annule toute recherche en cours
                    binding.progressSearch.setVisibility(View.INVISIBLE);
                    lastFullResults = new ArrayList<>();
                    activeFilter = null;
                    binding.chipGroupCategories.setVisibility(View.GONE);
                    showState(State.IDLE, query);
                    return;
                }

                pendingSearch = () -> runSearch(query);
                mainHandler.postDelayed(pendingSearch, DEBOUNCE_MS);
            }

            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void runSearch(String query) {
        final int myToken = ++searchToken;
        binding.progressSearch.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            List<GlobalSearchResult> results = searchService.searchAll(query, "mg");
            mainHandler.post(() -> {
                if (myToken != searchToken) return; // une recherche plus récente a pris le relais

                binding.progressSearch.setVisibility(View.INVISIBLE);
                lastFullResults = results;
                activeFilter = null;
                buildCategoryChips(results);
                applyFilterAndSubmit(query);
            });
        });
    }

    /**
     * Reconstruit les chips selon ce qui est réellement présent dans les
     * résultats : Tout / Andalana / Fanekena / une chip par catégorie de
     * chant trouvée (sokajy). Comportement type YouTube.
     */
    private void buildCategoryChips(List<GlobalSearchResult> results) {
        binding.chipGroupCategories.removeAllViews();

        if (results.isEmpty()) {
            binding.chipGroupCategories.setVisibility(View.GONE);
            return;
        }
        binding.chipGroupCategories.setVisibility(View.VISIBLE);

        boolean hasVerset = false, hasFanekena = false;
        Set<String> songCategories = new LinkedHashSet<>();

        for (GlobalSearchResult r : results) {
            switch (r.getType()) {
                case VERSET: hasVerset = true; break;
                case FANEKENA: hasFanekena = true; break;
                case CHANT:
                    if (r.getCategory() != null && !r.getCategory().trim().isEmpty()) {
                        songCategories.add(r.getCategory());
                    }
                    break;
                default: break;
            }
        }

        Chip chipAll = createChip("Rehetra", new FilterTag(null, null));
        chipAll.setChecked(true);
        binding.chipGroupCategories.addView(chipAll);

        if (hasVerset) {
            binding.chipGroupCategories.addView(
                    createChip("Baiboly", new FilterTag(GlobalSearchResult.Type.VERSET, null))
            );
        }

        // Une chip par catégorie de chant réellement trouvée (ex: FF, FFPM, KFF...)
        for (String cat : songCategories) {
            binding.chipGroupCategories.addView(
                    createChip(cat.toUpperCase(), new FilterTag(GlobalSearchResult.Type.CHANT, cat))
            );
        }

        if (hasFanekena) {
            binding.chipGroupCategories.addView(
                    createChip("Fanekena", new FilterTag(GlobalSearchResult.Type.FANEKENA, null))
            );
        }
    }

    private Chip createChip(String label, FilterTag filter) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setClickable(true);
        chip.setOnClickListener(v -> {
            activeFilter = filter;
            for (int i = 0; i < binding.chipGroupCategories.getChildCount(); i++) {
                Chip c = (Chip) binding.chipGroupCategories.getChildAt(i);
                c.setChecked(c == chip);
            }
            // Fait défiler la chip sélectionnée dans la zone visible
            binding.scrollChips.post(() ->
                    binding.scrollChips.smoothScrollTo((int) chip.getX(), 0));

            applyFilterAndSubmit(binding.etSearchNumber.getText().toString());
        });
        return chip;
    }

    private void applyFilterAndSubmit(String query) {
        currentQuery = query;
        List<GlobalSearchResult> filtered = new ArrayList<>();

        for (GlobalSearchResult r : lastFullResults) {
            if (matchesFilter(r)) filtered.add(r);
        }

        adapter.submit(filtered, query, true);

        if (filtered.isEmpty()) {
            showState(State.EMPTY, query);
        } else {
            showState(State.RESULTS, query);
        }
    }

    private boolean matchesFilter(GlobalSearchResult r) {
        if (activeFilter == null || activeFilter.type == null) return true; // "Tout"
        if (r.getType() != activeFilter.type) return false;

        // Filtre par catégorie précise, uniquement pertinent pour CHANT
        if (activeFilter.type == GlobalSearchResult.Type.CHANT && activeFilter.category != null) {
            return activeFilter.category.equalsIgnoreCase(r.getCategory());
        }

        return true;
    }

    // =========================
    // Gestion des états visuels
    // =========================
    private enum State { IDLE, EMPTY, RESULTS }

    private void showState(State state, String query) {
        View toShow;
        switch (state) {
            case IDLE:
                toShow = binding.stateIdle;
                break;
            case EMPTY:
                binding.tvEmptyQuery.setText(
                        query.isEmpty() ? "Tsy nahitana valiny" : "Tsy nahitana valiny ho an'i \"" + query + "\"");
                toShow = binding.stateEmpty;
                break;
            default:
                toShow = binding.recyclerResults;
                break;
        }

        View[] all = { binding.stateIdle, binding.stateEmpty, binding.recyclerResults };
        for (View v : all) {
            boolean shouldShow = (v == toShow);
            if (shouldShow && v.getVisibility() != View.VISIBLE) {
                v.setVisibility(View.VISIBLE);
                v.startAnimation(fadeIn());
            } else if (!shouldShow && v.getVisibility() != View.GONE) {
                v.setVisibility(View.GONE);
            }
        }
    }

    private AlphaAnimation fadeIn() {
        AlphaAnimation anim = new AlphaAnimation(0f, 1f);
        anim.setDuration(150);
        return anim;
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(binding.etSearchNumber, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(binding.etSearchNumber.getWindowToken(), 0);
    }

    // =========================
    // Navigation directe au clic
    // =========================
    @Override
    public void onVerseClick(GlobalSearchResult item) {
        SearchResult v = (SearchResult) item.getPayload();

        // Le format attendu par ReadingParser dans VersesActivity : "Livre chapitre:verset"
        String readingStr = v.getBookName() + " " + v.getChapter() + ":" + v.getVerse();

        Intent intent = new Intent(this, VersesActivity.class);
        intent.putExtra("reading", readingStr);
        startActivity(intent);
    }

    @Override
    public void onSongClick(GlobalSearchResult item) {
        Song s = (Song) item.getPayload();
        SongDetailActivity.open(this, s.getId(), s.getTitle());
    }

    @Override
    public void onFanekenaClick(GlobalSearchResult item) {
        Fanekena f = (Fanekena) item.getPayload();

        // Pas d'activité dédiée détectée dans le projet -> aperçu en dialog.
        // Si tu as/veux une écran dédié (FanekenaActivity), dis-le moi et je
        // remplace ce dialog par un vrai Intent.
        new AlertDialog.Builder(this)
                .setTitle(f.getCode().toUpperCase())
                .setMessage(f.getText())
                .setPositiveButton("Hidio", null)
                .show();
    }

    @Override
    public void onAiCardClick(String query) {
        if (query == null || query.trim().isEmpty()) {
            Toast.makeText(this, "Ataovy hoe inona no anontaniana ny AI", Toast.LENGTH_SHORT).show();
            return;
        }
        AiExplanationActivity.startWithQuestion(this, query.trim());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingSearch != null) mainHandler.removeCallbacks(pendingSearch);
        executor.shutdownNow();
    }
}