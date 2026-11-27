package com.fa.baiboly.ui.dictionary;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.dictionary.DictionaryService;
import com.fa.baiboly.databinding.ActivityDictionaryOccurrencesBinding;
import com.fa.baiboly.models.DictionaryOccurrence;
import com.fa.baiboly.ui.verses.VersesActivity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tous les versets où un mot du dictionnaire apparaît. La fiche détaillée ne
 * déroule plus cette liste : elle renvoie ici via un bouton. Un tap sur une
 * ligne ouvre le verset dans le lecteur.
 */
public class DictionaryOccurrencesActivity extends AppCompatActivity {

    public static final String EXTRA_DICT_ID = "dict_id";
    public static final String EXTRA_HEADWORD = "headword";

    private ActivityDictionaryOccurrencesBinding binding;
    private DictionaryOccurrenceAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void open(Context context, int dictId, String headword) {
        Intent intent = new Intent(context, DictionaryOccurrencesActivity.class);
        intent.putExtra(EXTRA_DICT_ID, dictId);
        intent.putExtra(EXTRA_HEADWORD, headword);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ColorManager.applyGlobalTheme(this);
        super.onCreate(savedInstanceState);

        binding = ActivityDictionaryOccurrencesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        String headword = getIntent().getStringExtra(EXTRA_HEADWORD);
        binding.toolbarTitle.setText(
                headword == null || headword.trim().isEmpty()
                        ? "Andininy misy azy"
                        : headword);

        adapter = new DictionaryOccurrenceAdapter(this::openVerse);
        binding.recyclerOccurrences.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerOccurrences.setAdapter(adapter);

        int dictId = getIntent().getIntExtra(EXTRA_DICT_ID, -1);
        if (dictId <= 0) {
            finish();
            return;
        }

        loadOccurrences(dictId);
    }

    private void loadOccurrences(int dictId) {
        binding.textOccurrenceCount.setText("Miandry…");

        executor.execute(() -> {
            final List<DictionaryOccurrence> occurrences =
                    DictionaryService.get(this).getAllOccurrences(dictId);

            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;

                adapter.submit(occurrences);

                boolean empty = occurrences.isEmpty();
                binding.recyclerOccurrences.setVisibility(empty ? View.GONE : View.VISIBLE);
                binding.textOccurrenceEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.textOccurrenceCount.setText(occurrences.size() + " andininy");
            });
        });
    }

    private void openVerse(DictionaryOccurrence occurrence) {
        Intent intent = new Intent(this, VersesActivity.class);
        intent.putExtra("reading", occurrence.getReference());
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
