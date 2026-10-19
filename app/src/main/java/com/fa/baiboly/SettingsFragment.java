package com.fa.baiboly;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.GridLayout;
import android.widget.ScrollView;

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
 *
 * La taille du texte et la justification sont enregistrées immédiatement,
 * puis l'écran hôte (s'il implémente {@link TextSettingsListener}) est
 * prévenu pour rafraîchir son propre contenu — le fragment ne mute jamais
 * l'arbre de vues de son hôte ni des autres écrans.
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
    /** "Un verset par ligne" dans le lecteur (voir VerseTextRenderer). */
    public static final String KEY_VERSE_PER_LINE = "verse_per_line";

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
        setupVersePerLine();
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
    // THEME (REAL-TIME) : trois cartes tapables
    // =========================
    private void setupThemeChanger() {

        binding.btnThemeLight.setOnClickListener(v -> setThemeMode(AppCompatDelegate.MODE_NIGHT_NO));
        binding.btnThemeDark.setOnClickListener(v -> setThemeMode(AppCompatDelegate.MODE_NIGHT_YES));
        binding.btnThemeSystem.setOnClickListener(v -> setThemeMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
    }

    /** Enregistre, applique et met à jour l'état visuel des cartes. */
    private void setThemeMode(int mode) {
        preferences.edit().putInt(KEY_THEME, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
        refreshThemeSelection();
    }

    /** Reflète le mode actif sur les trois cartes (fond + couleur du contenu). */
    private void refreshThemeSelection() {
        int savedMode = preferences.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        binding.btnThemeLight.setSelected(savedMode == AppCompatDelegate.MODE_NIGHT_NO);
        binding.btnThemeDark.setSelected(savedMode == AppCompatDelegate.MODE_NIGHT_YES);
        binding.btnThemeSystem.setSelected(savedMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        // Force les drawables d'état (fond, couleur du texte) à se retracer.
        binding.btnThemeLight.jumpDrawablesToCurrentState();
        binding.btnThemeDark.jumpDrawablesToCurrentState();
        binding.btnThemeSystem.jumpDrawablesToCurrentState();
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

    /** Écran hôte (lecteur, mofonaina...) à prévenir quand un réglage
     *  de texte change, pour qu'il rafraîchisse son propre contenu. */
    public interface TextSettingsListener {
        void onTextSettingsChanged();
    }

    /** Trouve l'écran hôte qui implémente TextSettingsListener (s'il y en a un). */
    @Nullable
    private TextSettingsListener findTextSettingsListener() {
        android.app.Activity activity = getActivity();
        if (activity instanceof TextSettingsListener) {
            return (TextSettingsListener) activity;
        }
        return null;
    }

    /** Notifie l'écran hôte que taille/justification ont changé. */
    private void notifyTextSettingsChanged() {
        TextSettingsListener listener = findTextSettingsListener();
        if (listener != null) {
            listener.onTextSettingsChanged();
        }
    }

    // =========================
    // TEXT SIZE (REAL-TIME)
    // =========================
    private void setupTextSizeRealTime() {
        binding.textSizeSlider.addOnChangeListener((slider, value, fromUser) -> {
            // 1. Update Preview (aperçu local uniquement)
            binding.txtSizePreview.setTextSize(value);

            // 2. Save Preference
            preferences.edit().putFloat(KEY_TEXT_SIZE, value).apply();

            // 3. L'écran hôte (lecteur...) se rafraîchit lui-même — on ne
            //    touche plus à l'arbre de vues de tout le monde ici.
            notifyTextSettingsChanged();
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

                // 2. L'écran hôte se rafraîchit lui-même
                notifyTextSettingsChanged();
            }
        });
    }

    // =========================
    // VERSE PER LINE (REAL-TIME)
    // =========================
    private void setupVersePerLine() {
        binding.switchVersePerLine.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_VERSE_PER_LINE, isChecked).apply();
            notifyTextSettingsChanged();
        });
    }

    // =========================
    // INITIAL LOAD
    // =========================
    private void loadAndApplySettings() {
        // Theme
        refreshThemeSelection();

        // Text Size : borné aux limites du slider (12–58 sp) — une valeur
        // sauvegardée hors bornes (ancien réglage) serait refusée par le
        // com.google.android.material.slider.Slider.
        float size = preferences.getFloat(KEY_TEXT_SIZE, 18f);
        size = Math.max(12f, Math.min(58f, size));
        binding.textSizeSlider.setValue(size);
        binding.txtSizePreview.setTextSize(size);

        // Justification
        boolean isJustified = preferences.getBoolean(KEY_JUSTIFICATION, true);
        binding.toggleJustification.check(isJustified ? R.id.btnAlignJustify : R.id.btnAlignLeft);

        // Verse per line
        binding.switchVersePerLine.setChecked(
                preferences.getBoolean(KEY_VERSE_PER_LINE, false));
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