package com.fa.baiboly.ui.mofonaina;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.text.LineBreaker;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.SettingsFragment;
import com.fa.baiboly.data.AinaMofonainaRepository;
import com.fa.baiboly.databinding.ActivityAinaOfDayBinding;
import com.fa.baiboly.models.MofonainaDay;

/**
 * Displays the daily devotional explanation from "Aina sy Fahasalamana".
 * Dynamic text sizing + justification, same as MofonainaActivity.
 */
public class AinaOfDayActivity extends AppCompatActivity {

    private ActivityAinaOfDayBinding binding;
    private SharedPreferences preferences;

    private static final String PREFS_NAME = "app_settings";
    public static final String KEY_THEME = "theme_mode";
    public static final String KEY_TEXT_SIZE = "text_size";
    public static final String KEY_JUSTIFICATION = "justification_mode";

    public static void start(Context context) {
        context.startActivity(new Intent(context, AinaOfDayActivity.class));
    }

    public static void startWithDate(Context context, String dateIso) {
        Intent intent = new Intent(context, AinaOfDayActivity.class);
        intent.putExtra("date_iso", dateIso);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyGlobalTheme();
        super.onCreate(savedInstanceState);

        binding = ActivityAinaOfDayBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        WindowManager.LayoutParams params =
                getWindow().getAttributes();

        params.preferredRefreshRate = 120.0f;

        getWindow().setAttributes(params);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (isSettingsOpen()) {
                closeSettings();
            } else {
                finish();
            }
        });

        loadData();
    }

    private void applyGlobalTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int savedMode = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);

        // Couleurs de base choisies par l'utilisateur
        ColorManager.applyTheme(this);
    }

    // =========================================================
    // MENU
    // =========================================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            openSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // =========================================================
    // SETTINGS
    // =========================================================

    private void openSettings() {
        binding.settingsContainer.setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .addToBackStack(null)
                .commit();
    }

    private void closeSettings() {
        getSupportFragmentManager().popBackStack();
        binding.settingsContainer.setVisibility(View.GONE);
        applyTextSettings();
    }

    private boolean isSettingsOpen() {
        return binding.settingsContainer.getVisibility() == View.VISIBLE;
    }

    @Override
    public void onBackPressed() {
        if (isSettingsOpen()) {
            closeSettings();
        } else {
            super.onBackPressed();
        }
    }

    // =========================================================
    // DYNAMIC TEXT SIZING — same engine as MofonainaActivity
    // =========================================================

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

    private void refreshJustification(TextView tv, boolean justified) {
        if (tv.getId() == R.id.toolbarTitle) return;

        configureJustificationEngine(tv, justified);

        if (tv.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tv.getLayoutParams();
            if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                tv.setLayoutParams(lp);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence current = tv.getText();
            tv.setText(current);
        }

        tv.requestLayout();
        tv.invalidate();
    }

    private void applyTextSettings() {
        float size = preferences.getFloat(KEY_TEXT_SIZE, 18f);
        boolean isJustified = preferences.getBoolean(KEY_JUSTIFICATION, true);
        applyTextEngine(binding.mainScroll, size, isJustified);
    }

    private void applyTextEngine(View view, float size, boolean justified) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (tv.getId() != R.id.toolbarTitle) {
                tv.setTextSize(size);
            }
            tv.setTextIsSelectable(!justified);
            refreshJustification(tv, justified);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyTextEngine(group.getChildAt(i), size, justified);
            }
        }
    }

    // =========================================================
    // DATA LOADING
    // =========================================================

    private void loadData() {
        AinaMofonainaRepository repo = new AinaMofonainaRepository(this);

        binding.loader.setVisibility(View.VISIBLE);
        binding.mainScroll.setVisibility(View.GONE);

        new Thread(() -> {
            repo.syncFromJson();

            MofonainaDay result = null;
            String dateIso = getIntent().getStringExtra("date_iso");
            if (dateIso != null) {
                result = repo.getByDate(dateIso);
            }
            if (result == null) {
                result = repo.getToday();
            }

            final MofonainaDay finalDay = result;

            runOnUiThread(() -> {
                binding.loader.setVisibility(View.GONE);

                if (finalDay != null && !isFinishing()) {
                    updateUI(finalDay);
                } else {
                    Toast.makeText(this,
                            "Tsy misy mofonaina androany",
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        }).start();
    }

    // =========================================================
    // UI UPDATE
    // =========================================================

    private void updateUI(MofonainaDay day) {
        binding.mainScroll.setVisibility(View.VISIBLE);
        binding.mainScroll.setAlpha(0f);
        binding.mainScroll.setTranslationY(24f);
        binding.mainScroll.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(280)
                .start();

        boolean isJustified = preferences.getBoolean(KEY_JUSTIFICATION, true);
        float textSize = preferences.getFloat(KEY_TEXT_SIZE, 18f);

        binding.textDate.setText(day.getFormattedDate());

        // ---- THEME TITLE ----
        if (day.getThemeSlogan() != null && !day.getThemeSlogan().isEmpty()) {
            TextView themeView = new TextView(this);
            configureJustificationEngine(themeView, isJustified);
            themeView.setText(day.getThemeSlogan());
            themeView.setTypeface(Typeface.DEFAULT_BOLD);
            themeView.setTextSize(24);
            themeView.setTextColor(ContextCompat.getColor(this, R.color.teal_200));
            themeView.setPadding(0, 0, 0, 32);
            addJustifiableView(binding.contentContainer, themeView);
        }

        // ---- BIBLE REFERENCE ----
        if (day.getBibleReference() != null && !day.getBibleReference().isEmpty()) {
            TextView refText = new TextView(this);
            configureJustificationEngine(refText, isJustified);
            refText.setText(day.getBibleReference());
            refText.setTypeface(Typeface.DEFAULT_BOLD);
            refText.setTextSize(18);
            refText.setTextColor(ContextCompat.getColor(this, R.color.teal_700));
            refText.setPadding(0, 0, 0, 24);

            // Ripple background
            android.util.TypedValue outValue = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            refText.setBackgroundResource(outValue.resourceId);
            refText.setClickable(true);
            refText.setFocusable(true);

            refText.setOnClickListener(v -> {
                String ref = normalizeBibleReference(day.getBibleReference());
                Intent intent = new Intent(this,
                        com.fa.baiboly.ui.verses.VersesActivity.class);
                intent.putExtra("reading", ref);
                startActivity(intent);
            });

            addJustifiableView(binding.contentContainer, refText);
        }

        // ---- FANAMPINY (Meditation) — without label ----
        if (day.getMeditation() != null && !day.getMeditation().isEmpty()) {
            addBodyText(day.getMeditation(), isJustified);
        }

        // ---- HAFATRA ANIO (Message of the day) ----
        if (day.getMessageToday() != null && !day.getMessageToday().isEmpty()) {
            addSectionLabel("Hafatra anio", R.color.teal_700, isJustified);
            addBodyText(day.getMessageToday(), isJustified);
        }

        // ---- VAVAKA (Prayer) ----
        if (day.getPrayer() != null && !day.getPrayer().isEmpty()) {
            addSectionLabel("Vavaka", R.color.orange, isJustified);

            TextView prayerView = new TextView(this);
            configureJustificationEngine(prayerView, isJustified);
            prayerView.setText(day.getPrayer());
            prayerView.setTextSize(18);
            prayerView.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
            prayerView.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            prayerView.setLineSpacing(1.3f, 1.3f);
            prayerView.setPadding(0, 0, 0, 32);

            // Card background
            android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
            cardBg.setColor(blendColor(Color.parseColor("#EF6C00"), 0.06f));
            cardBg.setCornerRadius(16f);
            prayerView.setBackground(cardBg);
            prayerView.setPadding(24, 24, 24, 24);

            addJustifiableView(binding.contentContainer, prayerView);
        }

        // ---- AUTHOR ----
        AinaMofonainaRepository repo = new AinaMofonainaRepository(this);
        String author = repo.getAuthor();
        if (author != null && !author.isEmpty()) {
            addAuthorCard(author);
        }

        // ---- APPLY USER SETTINGS ----
        applyTextSettings();
    }    private void addAuthorCard(String author) {
        // Separator line with spacing
        View separator = new View(this);
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
        );
        sepLp.setMargins(0, 64, 0, 24);
        separator.setLayoutParams(sepLp);
        separator.setBackgroundColor(blendColor(Color.parseColor("#2E7D32"), 0.15f));
        binding.contentContainer.addView(separator);

        // Author row: icon + name
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        row.setLayoutParams(rowLp);

        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(R.drawable.highlight_svgrepo_com);
        icon.setColorFilter(ContextCompat.getColor(this, R.color.title_gray));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                50, 50
        );
        iconLp.setMarginEnd(10);
        icon.setLayoutParams(iconLp);
        row.addView(icon);

        // Author name
        android.widget.TextView nameView = new android.widget.TextView(this);
        nameView.setText(author);
        nameView.setTextSize(15);
        nameView.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        nameView.setTextColor(ContextCompat.getColor(this, R.color.title_gray));
        row.addView(nameView);

        binding.contentContainer.addView(row);
    }

    // =========================================================
    // UI HELPERS
    // =========================================================

    private void addSectionLabel(String text, int colorRes, boolean isJustified) {
        TextView label = new TextView(this);
        configureJustificationEngine(label, isJustified);
        label.setText(text);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextSize(14);
        label.setTextColor(ContextCompat.getColor(this, colorRes));
        label.setLetterSpacing(0.08f);
        label.setPadding(0, 24, 0, 8);
        addJustifiableView(binding.contentContainer, label);
    }

    private void addBodyText(String text, boolean isJustified) {
        TextView body = new TextView(this);
        configureJustificationEngine(body, isJustified);
        body.setText(text);
        body.setTextSize(18);
        body.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
        body.setLineSpacing(1.3f, 1.3f);
        body.setPadding(0, 0, 0, 24);
        body.setTextColor(ContextCompat.getColor(
                this,
                R.color.text_full
        ));
        addJustifiableView(binding.contentContainer, body);
    }

    private void addJustifiableView(LinearLayout container, TextView view) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        view.setLayoutParams(lp);
        container.addView(view);
    }

    /**
     * Normalize Bible reference format from JSON to what ReadingParser expects.
     * e.g. "Jao. 11:25–37" → "Jao 11:25-37"
     */
    private String normalizeBibleReference(String ref) {
        if (ref == null) return null;
        // Replace en-dash and em-dash with hyphen
        ref = ref.replace('\u2013', '-').replace('\u2014', '-');
        // Remove dots after book abbreviations (e.g. "Jao." → "Jao")
        ref = ref.replaceAll("([A-Za-zÀ-ÿ])\\.", "$1");
        // Normalize spaces around colon and hyphen
        ref = ref.replaceAll("\\s*:\\s*", ":");
        ref = ref.replaceAll("\\s*-\\s*", "-");
        return ref.trim();
    }

    private int blendColor(int color, float ratio) {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true);
        int surface = tv.data;

        int sr = (surface >> 16) & 0xFF;
        int sg = (surface >> 8) & 0xFF;
        int sb = surface & 0xFF;

        int cr = (color >> 16) & 0xFF;
        int cg = (color >> 8) & 0xFF;
        int cb = color & 0xFF;

        int r = (int) (sr + (cr - sr) * ratio);
        int g = (int) (sg + (cg - sg) * ratio);
        int b = (int) (sb + (cb - sb) * ratio);

        return Color.rgb(r, g, b);
    }
}
