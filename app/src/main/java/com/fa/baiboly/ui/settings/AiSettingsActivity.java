package com.fa.baiboly.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.ai.AiPreferencesService;
import com.fa.baiboly.databinding.ActivityAiSettingsBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Écran dédié aux réglages de l'assistant IA. Chaque catégorie (style,
 * niveau, langue, longueur) présente ses options sous forme de grandes
 * cartes : icône ronde colorée + titre + description + coche. Le choix
 * est enregistré immédiatement dans AiPreferencesService.
 */
public class AiSettingsActivity extends AppCompatActivity {

    private ActivityAiSettingsBinding binding;
    private AiPreferencesService aiPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyGlobalTheme();
        super.onCreate(savedInstanceState);
        binding = ActivityAiSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

            WindowManager.LayoutParams params =
                    getWindow().getAttributes();

            params.preferredRefreshRate = 120.0f;

            getWindow().setAttributes(params);
        }

        aiPrefs = new AiPreferencesService(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.recyclerStyle.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerLevel.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerLength.setLayoutManager(new LinearLayoutManager(this));

        binding.recyclerStyle.setAdapter(new OptionAdapter(buildStyleOptions()));
        binding.recyclerLevel.setAdapter(new OptionAdapter(buildLevelOptions()));
        binding.recyclerLength.setAdapter(new OptionAdapter(buildLengthOptions()));
    }

    // =========================================================
    // DONNÉES DES OPTIONS
    // =========================================================

    private List<Option> buildStyleOptions() {
        List<Option> options = new ArrayList<>();
        options.add(new Option(AiPreferencesService.STYLE_DEFAULT,
                "Tsotra",
                "Fanazavana mazava sy tsotra isaky ny andininy",
                R.drawable.ic_bible, "#2E7D32"));
        options.add(new Option(AiPreferencesService.STYLE_HISTORY,
                "Tantara fahiny",
                "Ny tontolon'ny tantara sy ny kolontsaina tamin'ny andron'ny Baiboly",
                R.drawable.ic_history, "#B45309"));
        options.add(new Option(AiPreferencesService.STYLE_CHILDREN,
                "Ho an'ny ankizy",
                "Teny tsotra, ohatra hita maso, lesona mora tadidina",
                R.drawable.ic_prayer, "#0F766E"));
        options.add(new Option(AiPreferencesService.STYLE_DEEP,
                "Lalina",
                "Fandalinana lalina: lahatsoratra tany am-boalohany, teolojia, fifandraisana amin'ny andininy hafa",
                R.drawable.ic_school, "#1D4ED8"));
        options.add(new Option(AiPreferencesService.STYLE_SUMMARY,
                "Famintinana",
                "Ny tena zava-dehibe ihany, fohy sy mora vakina malaky",
                R.drawable.ic_note, "#6B7280"));
        return options;
    }

    private List<Option> buildLevelOptions() {
        List<Option> options = new ArrayList<>();
        options.add(new Option(AiPreferencesService.LEVEL_BEGINNER,
                "Vaovao",
                "Hazavaina tsara ny zavatra rehetra, anisan'izany ny Soratra Masina sy ny andininy hafa voatonona",
                R.drawable.ic_star_outline, "#0EA5E9"));
        options.add(new Option(AiPreferencesService.LEVEL_INTERMEDIATE,
                "Antonony",
                "Tsy tsotra loatra, tsy sarotra loatra",
                R.drawable.ic_settings, "#2E7D32"));
        options.add(new Option(AiPreferencesService.LEVEL_ADVANCED,
                "Theolojika",
                "Fanazavana lalina, misy teny teolojika sy andininy mifandraika maro",
                R.drawable.ic_school, "#7B1FA2"));
        return options;
    }

    private List<Option> buildLanguageOptions() {
        List<Option> options = new ArrayList<>();
        options.add(new Option(AiPreferencesService.LANG_MALAGASY,
                "Malagasy",
                "Ny fanazavana rehetra amin'ny teny malagasy",
                R.drawable.ic_bible, "#C40E5A"));
        options.add(new Option(AiPreferencesService.LANG_FRENCH,
                "Français",
                "Toutes les explications en français",
                R.drawable.ic_bible, "#1D4ED8"));
        options.add(new Option(AiPreferencesService.LANG_SAME_AS_TEXT,
                "Auto",
                "La langue du texte que vous envoyez",
                R.drawable.ic_auto_mode, "#6B7280"));
        return options;
    }

    private List<Option> buildLengthOptions() {
        List<Option> options = new ArrayList<>();
        options.add(new Option(AiPreferencesService.LENGTH_SHORT,
                "Fohy",
                "Fehezanteny vitsivitsy, ny zava-dehibe ihany",
                R.drawable.ic_note, "#0891B2"));
        options.add(new Option(AiPreferencesService.LENGTH_STANDARD,
                "Antonony",
                "Valiny voalanjalanja, tsy fohy loatra na lava loatra",
                R.drawable.ic_settings, "#2E7D32"));
        options.add(new Option(AiPreferencesService.LENGTH_DETAILED,
                "Antsipiriany",
                "Fanazavana feno sy voalahatra misy lohateny kely",
                R.drawable.ic_study, "#7C3AED"));
        return options;
    }

    // =========================================================
    // ADAPTER GÉNÉRIQUE D'OPTIONS
    // =========================================================

    class Option {
        final String value;
        final String title;
        final String description;
        final int iconRes;
        final String colorHex;

        Option(String value, String title, String description,
               int iconRes, String colorHex) {
            this.value = value;
            this.title = title;
            this.description = description;
            this.iconRes = iconRes;
            this.colorHex = colorHex;
        }
    }

    class OptionAdapter extends RecyclerView.Adapter<OptionAdapter.VH> {

        private final List<Option> options;

        OptionAdapter(List<Option> options) {
            this.options = options;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ai_option, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Option option = options.get(position);

            holder.textTitle.setText(option.title);
            holder.textDescription.setText(option.description);
            holder.icon.setImageResource(option.iconRes);

            try {
                GradientDrawable circle = new GradientDrawable();
                circle.setShape(GradientDrawable.OVAL);
                circle.setColor(Color.parseColor(option.colorHex));
                holder.iconBg.setBackground(circle);
            } catch (Exception e) {
                holder.iconBg.setBackgroundColor(Color.GRAY);
            }

            renderSelection(holder, option);

            holder.itemView.setOnClickListener(v -> {
                select(option);
                notifyDataSetChanged();
            });
        }

        private void renderSelection(VH holder, Option option) {
            boolean selected = isSelected(option);
            holder.check.setVisibility(selected ? View.VISIBLE : View.GONE);
            holder.card.setStrokeWidth(selected ? dp(2) : dp(1));
            holder.card.setStrokeColor(selected
                    ? ContextCompat.getColor(AiSettingsActivity.this, R.color.green)
                    : 0xFFE5E7EB);
        }

        private boolean isSelected(Option option) {
            // Chaque adapter connaît sa catégorie via la valeur courante
            return option.value.equals(currentValueFor(option));
        }

        /** Renvoie la valeur actuellement sauvegardée pour cette option. */
        private String currentValueFor(Option option) {
            // Simple : teste chaque catégorie — la comparaison se fait sur
            // la valeur, les espaces de valeurs sont disjoints.
            if (isStyleValue(option.value)) return aiPrefs.getStyle();
            if (isLevelValue(option.value)) return aiPrefs.getLevel();
            if (isLanguageValue(option.value)) return aiPrefs.getLanguage();
            return aiPrefs.getLength();
        }

        private boolean isStyleValue(String v) {
            return AiPreferencesService.STYLE_DEFAULT.equals(v)
                    || AiPreferencesService.STYLE_HISTORY.equals(v)
                    || AiPreferencesService.STYLE_CHILDREN.equals(v)
                    || AiPreferencesService.STYLE_DEEP.equals(v)
                    || AiPreferencesService.STYLE_SUMMARY.equals(v);
        }

        private boolean isLevelValue(String v) {
            return AiPreferencesService.LEVEL_BEGINNER.equals(v)
                    || AiPreferencesService.LEVEL_INTERMEDIATE.equals(v)
                    || AiPreferencesService.LEVEL_ADVANCED.equals(v);
        }

        private boolean isLanguageValue(String v) {
            return AiPreferencesService.LANG_MALAGASY.equals(v)
                    || AiPreferencesService.LANG_FRENCH.equals(v)
                    || AiPreferencesService.LANG_SAME_AS_TEXT.equals(v);
        }

        private void select(Option option) {
            if (isStyleValue(option.value)) aiPrefs.setStyle(option.value);
            else if (isLevelValue(option.value)) aiPrefs.setLevel(option.value);
            else if (isLanguageValue(option.value)) aiPrefs.setLanguage(option.value);
            else aiPrefs.setLength(option.value);
        }

        @Override
        public int getItemCount() {
            return options.size();
        }

        class VH extends RecyclerView.ViewHolder {
            MaterialCardView card;
            View iconBg;
            ImageView icon, check;
            TextView textTitle, textDescription;

            VH(View v) {
                super(v);
                card = (MaterialCardView) v;
                iconBg = v.findViewById(R.id.optionIconBg);
                icon = v.findViewById(R.id.optionIcon);
                check = v.findViewById(R.id.optionCheck);
                textTitle = v.findViewById(R.id.optionTitle);
                textDescription = v.findViewById(R.id.optionDescription);
            }
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void applyGlobalTheme() {
        SharedPreferences prefs = getSharedPreferences(
                "app_settings", Context.MODE_PRIVATE);
        int savedMode = prefs.getInt("theme_mode",
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);

        // Couleurs de base choisies par l'utilisateur
        ColorManager.applyTheme(this);
    }
}
