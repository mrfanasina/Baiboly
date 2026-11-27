package com.fa.baiboly.ui.dictionary;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.text.LineBreaker;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.dictionary.DictionaryService;
import com.fa.baiboly.databinding.ActivityDictionaryDetailBinding;
import com.fa.baiboly.models.DictionaryEntry;
import com.fa.baiboly.models.DictionaryOccurrence;
import com.fa.baiboly.ui.common.EdgeBars;
import com.fa.baiboly.ui.common.GlassCardHelper;
import com.fa.baiboly.ui.common.ReadingLinks;
import com.fa.baiboly.ui.common.ScrollTitleAnimator;
import com.fa.baiboly.ui.verses.VersesActivity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fiche d'un mot du dictionnaire : grande entrée, définition complète liée
 * (références bibliques, renvois « Jereo … » et tous les autres mots du
 * dictionnaire soulignés), et un bouton vers la liste complète des versets
 * où le mot apparaît. Le titre du mot glisse dans la barre du haut au
 * défilement, comme dans Fihirana.
 */
public class DictionaryDetailActivity extends AppCompatActivity {

    public static final String EXTRA_DICT_ID = "dict_id";

    /** Aperçu sur la fiche : au-delà, le bouton ouvre la liste complète. */
    private static final int PREVIEW_OCCURRENCES = 3;

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_TEXT_SIZE = "text_size";
    private static final String KEY_JUSTIFICATION = "justification_mode";
    private static final String DEFAULT_TITLE = "Diksionera Baiboly";

    private ActivityDictionaryDetailBinding binding;
    private SharedPreferences preferences;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int dictId;
    private String currentHeadword = "";
    private boolean titleShowing;

    public static void open(Context context, int dictId) {
        Intent intent = new Intent(context, DictionaryDetailActivity.class);
        intent.putExtra(EXTRA_DICT_ID, dictId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ColorManager.applyGlobalTheme(this);
        super.onCreate(savedInstanceState);

        binding = ActivityDictionaryDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        setupEdgeToEdge();

        binding.toolbarTitle.setText(DEFAULT_TITLE);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.mainScroll.setOnScrollChangeListener(
                (View.OnScrollChangeListener)
                        (v, scrollX, scrollY, oldScrollX, oldScrollY)
                                -> updateToolbarTitle());

        dictId = getIntent().getIntExtra(EXTRA_DICT_ID, -1);
        if (dictId <= 0) {
            finish();
            return;
        }

        loadEntry();
    }

    /**
     * Bord à bord : la barre d'état est transparente, la barre translucide la
     * recouvre et le contenu défile dessous.
     */
    private void setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        boolean light = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int top = EdgeBars.applyAppBarInset(binding.appBar, bars.top);
            EdgeBars.scrollUnderBars(binding.mainScroll, top, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    private void loadEntry() {
        executor.execute(() -> {
            DictionaryService service = DictionaryService.get(this);

            final DictionaryEntry entry = service.getById(dictId);
            final int total = service.countOccurrences(dictId);
            final List<DictionaryOccurrence> preview =
                    service.getOccurrences(dictId, PREVIEW_OCCURRENCES);
            final Map<String, Integer> wordIndex = service.getWordIndex();

            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                bind(entry, total, preview, wordIndex);
            });
        });
    }

    private void bind(DictionaryEntry entry, int total,
                      List<DictionaryOccurrence> preview,
                      Map<String, Integer> wordIndex) {

        if (entry == null) {
            finish();
            return;
        }

        currentHeadword = entry.getHeadword() == null ? "" : entry.getHeadword();
        titleShowing = false;
        binding.toolbarTitle.setText(DEFAULT_TITLE);

        binding.tvDictWord.setText(entry.getHeadword());

        String qualifier = entry.getQualifier();
        if (qualifier != null && !qualifier.trim().isEmpty()) {
            binding.tvDictQualifier.setText(qualifier);
            binding.tvDictQualifier.setVisibility(View.VISIBLE);
        } else {
            binding.tvDictQualifier.setVisibility(View.GONE);
        }

        // Même confort de lecture que dans Verses : taille réglable et
        // justification à gauche et à droite.
        float size = preferences.getFloat(KEY_TEXT_SIZE, 18f);
        boolean justified = preferences.getBoolean(KEY_JUSTIFICATION, true);
        binding.tvDictDefinition.setTextSize(size);
        configureJustificationEngine(binding.tvDictDefinition, justified);

        ReadingLinks.apply(
                binding.tvDictDefinition,
                entry.getDefinition(),
                wordIndex,
                entry.getId(),
                this::openVerse,
                id -> open(this, id));

        // Trois versets en aperçu ; le bouton ouvre la liste complète sur son
        // propre écran.
        binding.containerDictVerses.removeAllViews();

        if (total <= 0 || preview == null || preview.isEmpty()) {
            binding.cardDictVerses.setVisibility(View.GONE);
        } else {
            binding.cardDictVerses.setVisibility(View.VISIBLE);
            binding.tvDictCount.setText(total + " andininy");
            binding.btnDictAllVerses.setText("Hijery ny andininy rehetra (" + total + ")");
            binding.btnDictAllVerses.setOnClickListener(v ->
                    DictionaryOccurrencesActivity.open(this, dictId, currentHeadword));

            LayoutInflater inflater = getLayoutInflater();
            int gap = Math.round(6 * getResources().getDisplayMetrics().density);

            for (DictionaryOccurrence occurrence : preview) {
                View row = inflater.inflate(
                        R.layout.item_dictionary_verse,
                        binding.containerDictVerses,
                        false);

                TextView reference = row.findViewById(R.id.tvOccurrenceRef);
                reference.setText(occurrence.getReference());

                GlassCardHelper.styleRow(row, false);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, gap);
                row.setLayoutParams(lp);

                row.setOnClickListener(v -> openVerse(occurrence.getReference()));
                binding.containerDictVerses.addView(row);
            }
        }

        binding.mainScroll.post(this::updateToolbarTitle);
    }

    private void configureJustificationEngine(TextView tv, boolean justified) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tv.setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
            tv.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            tv.setJustificationMode(
                    justified
                            ? LineBreaker.JUSTIFICATION_MODE_INTER_WORD
                            : LineBreaker.JUSTIFICATION_MODE_NONE
            );
        }
    }

    private void openVerse(String reference) {
        Intent intent = new Intent(this, VersesActivity.class);
        intent.putExtra("reading", reference);
        startActivity(intent);
    }

    /**
     * Quand l'en-tête du mot passe sous la barre, son titre prend la place de
     * « Diksionera Baiboly » dans la toolbar ; inversement en remontant.
     */
    private void updateToolbarTitle() {
        if (binding.appBar.getHeight() == 0
                || binding.cardDictHeader.getHeight() == 0) {
            return;
        }

        int[] header = new int[2];
        binding.cardDictHeader.getLocationOnScreen(header);
        int[] bar = new int[2];
        binding.appBar.getLocationOnScreen(bar);

        boolean show = header[1] + binding.cardDictHeader.getHeight()
                <= bar[1] + binding.appBar.getHeight();

        if (show == titleShowing) {
            return;
        }
        titleShowing = show;

        String title = show && !currentHeadword.isEmpty()
                ? currentHeadword
                : DEFAULT_TITLE;
        ScrollTitleAnimator.animate(binding.toolbarTitle, title);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
