package com.fa.baiboly;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.view.*;
import android.widget.GridLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.fa.baiboly.data.CardOrderAdapter;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.ai.AiPreferencesService;
import com.fa.baiboly.databinding.FragmentSettingsBinding;
import com.fa.baiboly.widgets.MofonainaOfflineWidget;
import com.fa.baiboly.widgets.MofonainaOnlineWidget;

import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;

/**
 * Fragment for settings with real-time saving and instant UI updates.
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SharedPreferences preferences;

    private static final String PREFS_NAME = "app_settings";

    // Keys
    public static final String KEY_THEME = "theme_mode";
    public static final String KEY_TEXT_SIZE = "text_size";
    public static final String KEY_JUSTIFICATION = "justification_mode";
    public static final String KEY_HOME_CARD_ORDER = "home_card_order";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Back navigation
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });

        loadAndApplySettings();
        setupThemeChanger();
        setupColors();
        setupTextSizeRealTime();
        setupJustificationRealTime();
        setupCardOrder();
        setupWidgetButtons();
        setupAiSettingsEntry();
    }

    // =========================
    // AI SETTINGS ENTRY (écran dédié)
    // =========================
    private void setupAiSettingsEntry() {
        binding.cardAiSettings.setOnClickListener(v -> {
            startActivity(
                    new android.content.Intent(requireContext(),
                            com.fa.baiboly.ui.settings.AiSettingsActivity.class));
        });

        refreshAiSummary();
    }

    /** Résumé des réglages IA courants, affiché sous le titre de la carte. */
    private void refreshAiSummary() {
        AiPreferencesService aiPrefs =
                new AiPreferencesService(requireContext());

        String style;
        switch (aiPrefs.getStyle()) {
            case AiPreferencesService.STYLE_HISTORY: style = "Tantara"; break;
            case AiPreferencesService.STYLE_CHILDREN: style = "Ankizy"; break;
            case AiPreferencesService.STYLE_DEEP: style = "Lalina"; break;
            case AiPreferencesService.STYLE_SUMMARY: style = "Famintinana"; break;
            default: style = "Tsotra"; break;
        }

        String level;
        switch (aiPrefs.getLevel()) {
            case AiPreferencesService.LEVEL_BEGINNER: level = "Vaovao"; break;
            case AiPreferencesService.LEVEL_ADVANCED: level = "Mpampianatra"; break;
            default: level = "Antonony"; break;
        }

        String length;
        switch (aiPrefs.getLength()) {
            case AiPreferencesService.LENGTH_SHORT: length = "Fohy"; break;
            case AiPreferencesService.LENGTH_DETAILED: length = "Antsipirihany"; break;
            default: length = "Antonony"; break;
        }

        binding.txtAiSummary.setText(style + " • " + level
                + " • "  + length);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            refreshAiSummary();
        }
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setBottomNavVisibility(false);
        }
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity.getSupportActionBar() != null) activity.getSupportActionBar().hide();
        }
    }

    // =========================
    // THEME (REAL-TIME)
    // =========================
    private void setupThemeChanger() {
        binding.themeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                int mode;
                if (checkedId == R.id.btnThemeLight) mode = AppCompatDelegate.MODE_NIGHT_NO;
                else if (checkedId == R.id.btnThemeDark) mode = AppCompatDelegate.MODE_NIGHT_YES;
                else mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

                preferences.edit().putInt(KEY_THEME, mode).apply();
                AppCompatDelegate.setDefaultNightMode(mode);
            }
        });
    }

    // =========================
    // COLORS (LOKO) : deux couleurs de base appliquées partout
    // =========================
    private void setupColors() {
        updateColorPreviews();
        binding.rowColorPrimary.setOnClickListener(v -> showColorPicker(true));
        binding.rowColorSecondary.setOnClickListener(v -> showColorPicker(false));
    }

    /** Met à jour les deux pastilles de la carte. */
    private void updateColorPreviews() {
        tintSwatch(binding.swatchPrimary, ColorManager.getPrimary(requireContext()));
        tintSwatch(binding.swatchSecondary, ColorManager.getSecondary(requireContext()));
    }

    private void tintSwatch(View swatch, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        swatch.setBackground(d);
    }

    /** Dialogue de choix : grille des nuances disponibles. */
    private void showColorPicker(boolean primary) {
        String key = primary ? ColorManager.KEY_PRIMARY : ColorManager.KEY_SECONDARY;
        int current = preferences.getInt(key, ColorManager.UNSET);

        final androidx.appcompat.app.AlertDialog[] holder = new androidx.appcompat.app.AlertDialog[1];

        float density = getResources().getDisplayMetrics().density;
        int size = (int) (44 * density);
        int pad = (int) (10 * density);

        GridLayout grid = new GridLayout(requireContext());
        grid.setColumnCount(4);

        for (int i = 0; i < ColorManager.SWATCHES.length; i++) {
            final int index = i;
            View swatch = new View(requireContext());
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = size;
            lp.height = size;
            lp.setMargins(pad, pad, pad, pad);
            swatch.setLayoutParams(lp);

            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(ColorManager.SWATCHES[i]);
            if (i == current) {
                d.setStroke((int) (3 * density), android.graphics.Color.WHITE);
            }
            swatch.setBackground(d);

            swatch.setOnClickListener(v -> {
                preferences.edit().putInt(key, index).apply();
                updateColorPreviews();
                if (holder[0] != null) holder[0].dismiss();
                if (getActivity() != null) getActivity().recreate();
            });

            grid.addView(swatch);
        }

        ScrollView scroll = new ScrollView(requireContext());
        scroll.addView(grid);

        holder[0] = new AlertDialog.Builder(requireContext())
                .setTitle(primary ? "Loko voalohany" : "Loko faharoa")
                .setView(scroll)
                .setNeutralButton("Tadim-panahy", (d2, w) -> {
                    // Retour aux couleurs par défaut du thème
                    preferences.edit().putInt(key, ColorManager.UNSET).apply();
                    updateColorPreviews();
                    if (getActivity() != null) getActivity().recreate();
                })
                .setNegativeButton("Hanafoana", null)
                .show();
    }

    // =========================
    // TEXT SIZE (REAL-TIME)
    // =========================
    private void setupTextSizeRealTime() {
        binding.textSizeSlider.addOnChangeListener((slider, value, fromUser) -> {
            // 1. Update Preview
            binding.txtSizePreview.setTextSize(value);

            // 2. Save Preference
            preferences.edit().putFloat(KEY_TEXT_SIZE, value).apply();

            // 3. Apply to all visible views
            applyGlobalStyle(value, null);
        });
    }

    // =========================
    // JUSTIFICATION (REAL-TIME)
    // =========================
    private void setupJustificationRealTime() {
        binding.toggleJustification.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                boolean isJustified = (checkedId == R.id.btnAlignJustify);

                // 1. Save Preference
                preferences.edit().putBoolean(KEY_JUSTIFICATION, isJustified).apply();

                // 2. Apply to all visible views
                applyGlobalStyle(null, isJustified);
            }
        });
    }

    // =========================
    // INITIAL LOAD
    // =========================
    private void loadAndApplySettings() {
        // Theme
        int savedMode = preferences.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (savedMode == AppCompatDelegate.MODE_NIGHT_NO) binding.themeToggleGroup.check(R.id.btnThemeLight);
        else if (savedMode == AppCompatDelegate.MODE_NIGHT_YES) binding.themeToggleGroup.check(R.id.btnThemeDark);
        else binding.themeToggleGroup.check(R.id.btnThemeSystem);

        // Text Size
        float size = preferences.getFloat(KEY_TEXT_SIZE, 18f);
        binding.textSizeSlider.setValue(size);
        binding.txtSizePreview.setTextSize(size);

        // Justification
        boolean isJustified = preferences.getBoolean(KEY_JUSTIFICATION, true);
        binding.toggleJustification.check(isJustified ? R.id.btnAlignJustify : R.id.btnAlignLeft);

        applyGlobalStyle(size, isJustified);
    }

    // =========================
    // HOME CARD ORDER
    // =========================
    private void setupCardOrder() {
        List<String> order = getCardOrder();
        CardOrderAdapter adapter = new CardOrderAdapter(order, newOrder -> {
            saveCardOrder(newOrder);
        });

        binding.recyclerCardOrder.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerCardOrder.setAdapter(adapter);

        ItemTouchHelper touchHelper = new ItemTouchHelper(
                new CardOrderAdapter.CardTouchHelperCallback(adapter)
        );
        touchHelper.attachToRecyclerView(binding.recyclerCardOrder);
    }

    private List<String> getCardOrder() {
        String saved = preferences.getString(KEY_HOME_CARD_ORDER, null);
        if (saved != null && !saved.isEmpty()) {
            String[] parts = saved.split(",");
            List<String> order = new ArrayList<>();
            for (String part : parts) {
                if (!part.trim().isEmpty()) {
                    order.add(part.trim());
                }
            }
            if (!order.isEmpty()) return order;
        }
        return CardOrderAdapter.getDefaultOrder();
    }

    private void saveCardOrder(List<String> order) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(order.get(i));
        }
        preferences.edit().putString(KEY_HOME_CARD_ORDER, sb.toString()).apply();
    }

    // =========================
    // WIDGET PINNING
    // =========================
    private void setupWidgetButtons() {
        binding.btnPinMofonainaWidget.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).pinWidgetByClass(MofonainaOfflineWidget.class);
            }
        });

        binding.btnPinMofonainaOnlineWidget.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).pinWidgetByClass(MofonainaOnlineWidget.class);
            }
        });
    }

    // =========================
    // GLOBAL UI UPDATER
    // =========================
    private void applyGlobalStyle(Float size, Boolean justified) {
        if (getActivity() == null) return;
        View root = getActivity().findViewById(android.R.id.content);
        if (root instanceof ViewGroup) {
            updateViewsRecursive((ViewGroup) root, size, justified);
        }
    }

    private void updateViewsRecursive(ViewGroup group, Float size, Boolean justified) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View view = group.getChildAt(i);

            if (view instanceof TextView) {
                TextView tv = (TextView) view;
                int id = tv.getId();

                // Skip system/header views to keep UI clean
                if (id != R.id.txtPageTitle && id != binding.toolbar.getId()) {
                    // Update size if provided
                    if (size != null) tv.setTextSize(size);

                    // Update justification if provided
                    if (justified != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        tv.setJustificationMode(justified ?
                                Layout.JUSTIFICATION_MODE_INTER_WORD : Layout.JUSTIFICATION_MODE_NONE);
                    }
                }
            }

            if (view instanceof ViewGroup &&
                    !(view instanceof com.google.android.material.bottomnavigation.BottomNavigationView)) {
                updateViewsRecursive((ViewGroup) view, size, justified);
            }
        }
    }



    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setBottomNavVisibility(true);
        }
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity.getSupportActionBar() != null) activity.getSupportActionBar().show();
        }
        binding = null;
    }
}