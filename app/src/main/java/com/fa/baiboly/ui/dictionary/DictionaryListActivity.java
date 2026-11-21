package com.fa.baiboly.ui.dictionary;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.dictionary.DictionaryService;
import com.fa.baiboly.databinding.ActivityDictionaryListBinding;
import com.fa.baiboly.models.DictionaryEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * « Diksionera Baiboly » : la liste complète des mots (3 167 entrées) avec un
 * filtre de recherche instantané. Un tap ouvre la fiche détaillée stylée.
 */
public class DictionaryListActivity extends AppCompatActivity {

    private ActivityDictionaryListBinding binding;
    private DictionaryAdapter adapter;

    private final List<DictionaryEntry> allEntries = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ColorManager.applyGlobalTheme(this);
        super.onCreate(savedInstanceState);

        binding = ActivityDictionaryListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new DictionaryAdapter(
                entry -> DictionaryDetailActivity.open(this, entry.getId()));

        binding.recyclerDictionary.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerDictionary.setAdapter(adapter);

        binding.etSearchDictionary.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        loadEntries();
    }

    private void loadEntries() {
        binding.textDictCount.setText("Miandry…");

        executor.execute(() -> {
            final List<DictionaryEntry> entries =
                    DictionaryService.get(this).getAll();

            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;

                allEntries.clear();
                allEntries.addAll(entries);
                applyFilter(binding.etSearchDictionary.getText().toString());
            });
        });
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();

        List<DictionaryEntry> filtered = new ArrayList<>();
        for (DictionaryEntry entry : allEntries) {
            if (q.isEmpty() || matches(entry, q)) {
                filtered.add(entry);
            }
        }

        adapter.submit(filtered);

        boolean empty = filtered.isEmpty();
        binding.recyclerDictionary.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.textDictEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);

        binding.textDictCount.setText(q.isEmpty()
                ? allEntries.size() + " teny"
                : filtered.size() + " teny hita");
    }

    private static boolean matches(DictionaryEntry entry, String q) {
        String headword = entry.getHeadword();
        String norm = entry.getHeadwordNorm();
        return (headword != null && headword.toLowerCase().contains(q))
                || (norm != null && norm.toLowerCase().contains(q));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
