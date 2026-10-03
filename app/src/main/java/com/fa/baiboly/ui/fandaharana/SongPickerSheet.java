package com.fa.baiboly.ui.fandaharana;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.data.fihirana.FihiranaService;
import com.fa.baiboly.models.Song;
import com.fa.baiboly.ui.fihirana.SongAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Sélecteur de chant en BottomSheet : réutilise FihiranaService et
 * SongAdapter de la bibliothèque de chants existante. Recherche par
 * numéro, titre ou paroles, puis choix -> callback vers le programme.
 */
public class SongPickerSheet extends BottomSheetDialogFragment {

    public interface OnSongPicked {
        void onSongPicked(Song song);
    }

    private static final long DEBOUNCE_MS = 300;

    private final OnSongPicked listener;

    private FihiranaService service;
    private SongAdapter adapter;
    private List<Song> allSongs = new ArrayList<>();

    private EditText etSearch;
    private ImageButton btnClearSearch;
    private TextView emptyState;
    private RecyclerView recycler;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private android.os.Handler mainHandler;
    private Runnable pendingFilter;

    public SongPickerSheet(OnSongPicked listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_song_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        service = new FihiranaService(requireContext());
        mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

        etSearch = view.findViewById(R.id.etSearch);
        btnClearSearch = view.findViewById(R.id.btnClearSearch);
        emptyState = view.findViewById(R.id.emptyState);
        recycler = view.findViewById(R.id.recyclerSongs);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        // SongAdapter réutilisé : le clic renvoie le chant au programme
        adapter = new SongAdapter(new ArrayList<>(), song -> {
            dismiss();
            if (listener != null) listener.onSongPicked(song);
        });
        recycler.setAdapter(adapter);

        loadAllSongs();
        setupSearch();
    }

    private void loadAllSongs() {
        executor.execute(() -> {
            List<Song> songs = service.getAllSongs();
            mainHandler.post(() -> {
                allSongs = songs != null ? songs : new ArrayList<>();
                adapter.updateList(allSongs);
                emptyState.setVisibility(allSongs.isEmpty()
                        ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClearSearch.setVisibility(
                        s.length() > 0 ? View.VISIBLE : View.GONE);

                if (pendingFilter != null) {
                    mainHandler.removeCallbacks(pendingFilter);
                }

                String query = s.toString();

                if (query.trim().length() < 2) {
                    // Recherche courte : filtrage local rapide (numéro/titre)
                    filterLocal(query);
                    return;
                }

                // Recherche longue : paroles aussi (asynchrone, avec debounce)
                pendingFilter = () -> searchLyrics(query);
                mainHandler.postDelayed(pendingFilter, DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));
    }

    /** Filtre local rapide : numéro ou titre. */
    private void filterLocal(String query) {
        List<Song> filtered = new ArrayList<>();

        if (query.isEmpty()) {
            filtered.addAll(allSongs);
        } else {
            String lower = query.toLowerCase();
            for (Song song : allSongs) {
                String number = String.valueOf(song.getNumber());
                String title = song.getTitle() != null
                        ? song.getTitle().toLowerCase() : "";
                if (number.contains(lower) || title.contains(lower)) {
                    filtered.add(song);
                }
            }
        }

        adapter.updateList(filtered);
        emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Recherche dans les paroles via la base (comme SongListActivity). */
    private void searchLyrics(String query) {
        executor.execute(() -> {
            List<Song> results = service.search(query);
            mainHandler.post(() -> {
                if (results == null || results.isEmpty()) {
                    filterLocal(query);
                } else {
                    adapter.updateList(results);
                    emptyState.setVisibility(View.GONE);
                }
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingFilter != null && mainHandler != null) {
            mainHandler.removeCallbacks(pendingFilter);
        }
        executor.shutdownNow();
    }
}
