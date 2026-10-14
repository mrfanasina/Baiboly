package com.fa.baiboly.ui.home;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.color.MaterialColors;

import com.fa.baiboly.MainActivity;
import com.fa.baiboly.R;
import com.fa.baiboly.data.MofonainaRepository;
import com.fa.baiboly.data.fihirana.FihiranaService;
import com.fa.baiboly.data.history.HistoryService;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.perikopa.PerikopaService;
import com.fa.baiboly.databinding.FragmentHomeBinding;
import com.fa.baiboly.models.History;
import com.fa.baiboly.models.MofonainaData;
import com.fa.baiboly.models.PerikopaDay;
import com.fa.baiboly.models.Song;
import com.fa.baiboly.ui.fanekena.CodeAdapter;
import com.fa.baiboly.ui.fanekena.FanekenaActivity;
import com.fa.baiboly.ui.fihirana.SongDetailActivity;
import com.fa.baiboly.ui.reader.ProgramSession;
import com.fa.baiboly.ui.history.HistoryManageActivity;
import com.fa.baiboly.ui.ai.AiExplanationActivity;
import com.fa.baiboly.data.AinaMofonainaRepository;
import com.fa.baiboly.models.MofonainaDay;
import com.fa.baiboly.ui.mofonaina.AinaOfDayActivity;
import com.fa.baiboly.ui.mofonaina.MofonainaActivity;
import com.fa.baiboly.ui.mofonaina.MofonainaYearlyListActivity;
import com.fa.baiboly.ui.verses.VersesActivity;
import com.fa.baiboly.widgets.MofonainaOfflineWidget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fa.baiboly.SettingsFragment;
import com.fa.baiboly.data.CardOrderAdapter;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private FihiranaService fihiranaService;

    /** Données mofonaina actuellement affichées (pour la session maison). */
    private MofonainaData currentMofonaina;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);

        // Initializing data services
        fihiranaService = new FihiranaService(requireContext());

        // Load content
        showSkeleton(true);
        loadMofonainaPreview();
        loadAinaCard();
        loadPerikopa(inflater);
        loadHistory(inflater);
        hideSkeletonWhenReady();

        // Apply card order from settings
        applyCardOrder();

        // DAILY READING
        homeViewModel.loadTodayReading(requireContext());
        String currentReading = homeViewModel.getCurrentReading();
        binding.textHome.setText(currentReading);

        // Vakiteny anio -> session maison complète, ancrée dessus
        binding.cardReading.setOnClickListener(v -> {
            String ref = binding.textHome.getText().toString().trim();
            openHomeSession(GROUP_READING, "verse", ref);
        });

        // MOFONAINA - open full activity
        binding.cardMofonaina.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), MofonainaActivity.class));
        });

        // AI BUTTON - open AI with monthly theme as context
        //binding.fabAi.setOnClickListener(v -> openAiWithTheme());

        // VOIR TOUT HISTORIQUE - Open history management
        binding.btnVoirToutHistorique.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), HistoryManageActivity.class));
        });

        // MOFONAINA "Hijery manontolo" -> open yearly mofonaina
        binding.btnMofonainaVoirTout.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), MofonainaYearlyListActivity.class);
            intent.putExtra("listType", "mofonaina");
            startActivity(intent);
        });

        binding.btnMofonainaPreviewVoirTout.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), MofonainaYearlyListActivity.class);
            intent.putExtra("listType", "mofonaina");
            startActivity(intent);
        });

        // PERIKOPA "Hijery manontolo" -> open yearly perikopa
        binding.btnPerikopaVoirTout.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), MofonainaYearlyListActivity.class);
            intent.putExtra("listType", "perikopa");
            startActivity(intent);
        });

        // Perikopa -> session maison complète, ancrée sur la 1re lecture
        binding.cardPerikopa.setOnClickListener(v -> {
            List<String> allVerses = todayPerikopaVerses();
            if (!allVerses.isEmpty()) {
                String first = allVerses.get(0);
                openHomeSession(GROUP_PERIKOPA, "verse", first);
            }
        });

        // AINA CARD - open day detail
        binding.cardAina.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), AinaOfDayActivity.class));
        });

        // WIDGET PROMO
        loadWidgetPromo();

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            loadHistory(getLayoutInflater());
        }
    }

    // =========================================================
    // SKELETON DE CHARGEMENT
    // =========================================================

    /** Affiche/masque le skeleton de l'accueil (avec pulse). */
    private void showSkeleton(boolean show) {
        View skeleton = binding.skeletonHome.getRoot();
        skeleton.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            android.view.animation.Animation pulse =
                    android.view.animation.AnimationUtils.loadAnimation(
                            requireContext(), R.anim.skeleton_pulse);
            skeleton.startAnimation(pulse);
        } else {
            skeleton.clearAnimation();
        }
    }

    /** Masque le skeleton dès que le premier contenu réel est posé
     *  (mofonaina en cache ou perikopa/histoire déjà liés). */
    private void hideSkeletonWhenReady() {
        // Les cartes se remplissent de façon asynchrone : on laisse le
        // skeleton au moins un frame de plus pour éviter un flash, puis
        // on le retire dès que la vue est dessinée.
        binding.getRoot().postDelayed(
                () -> { if (binding != null) showSkeleton(false); },
                350);
    }

    /**
     * Helper method to add a stylized row into a container.
     * Used to keep a consistent Premium UI look throughout the fragment.
     */
    private void addPremiumRow(ViewGroup container, String text, int iconRes, View.OnClickListener listener) {
        View row = getLayoutInflater().inflate(R.layout.item_home_row, container, false);

        TextView tv = row.findViewById(R.id.rowText);
        ImageView img = row.findViewById(R.id.rowIcon);

        tv.setText(text);
        img.setImageResource(iconRes);

        row.setOnClickListener(listener);
        container.addView(row);
    }

    /**
     * Loads the Perikopa (liturgical readings) for the current day.
     * Shows ALL perikopa readings (up to 3) with liturgical color backgrounds.
     */
    private void loadPerikopa(LayoutInflater inflater) {
        PerikopaService service = new PerikopaService(requireContext());
        PerikopaDay perikopaDay = service.getTodayPerikopa();

        // Si pas de perikopa aujourd'hui, prendre la prochaine
        boolean isFallback = false;
        if (perikopaDay.getVerses() == null || perikopaDay.getVerses().isEmpty()) {
            perikopaDay = service.getNextPerikopa();
            isFallback = true;
        }

        PerikopaDay perikopa = service.sortPerikopa(perikopaDay);

        binding.lohahevitra.setText(service.getTodayLohahevitra());

        // Titre = nom de la perikopa
        String title = perikopa.getName() != null ? perikopa.getName() : "Perikopa";
        binding.textPerikopaTitle.setText(title);

        // Date (seulement si fallback = pas aujourd'hui)
        if (isFallback && perikopa.getDate() != null && !perikopa.getDate().isEmpty()) {
            binding.textPerikopaDate.setVisibility(View.VISIBLE);
            binding.textPerikopaDate.setText(formatDateShort(perikopa.getDate()));
        } else {
            binding.textPerikopaDate.setVisibility(View.GONE);
        }

        // Couleur de base choisie dans les paramètres (plus de couleur par saison)
        int seasonColor = ColorManager.getPrimary(requireContext());

        // Fond de la carte perikopa en couleur liturgique
        int cardBg = blendColor(seasonColor, 0.08f);
        binding.perikopaCardBg.setBackgroundColor(cardBg);

        List<String> verses = perikopa.getVerses();
        binding.perikopaContainer.removeAllViews();

        if (verses != null && !verses.isEmpty()) {
            for (int i = 0; i < verses.size(); i++) {
                String verse = verses.get(i);
                addPerikopaRow(binding.perikopaContainer, verse, seasonColor,
                        v -> openHomeSession(GROUP_PERIKOPA, "verse", verse));
            }
        }
    }

    /**
     * Format yyyy-MM-dd -> "dd MMM" in Malagasy.
     */
    private static String formatDateShort(String date) {
        try {
            String[] parts = date.split("-");
            int day = Integer.parseInt(parts[2]);
            int month = Integer.parseInt(parts[1]);
            String[] months = {"Janoary", "Febroary", "Martsa", "Aprily", "Mey", "Jona",
                    "Jolay", "Aogositra", "Septambra", "Oktobra", "Novambra", "Desambra"};
            return day + " " + months[ month - 1];
        } catch (Exception e) {
            return date;
        }
    }
    private static String formatDate(String date) {
        try {
            String[] parts = date.split("-");
            int day = Integer.parseInt(parts[2]);
            int month = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[0]);
            return day + "/" + month + "/" + year;
        } catch (Exception e) {
            return date;
        }
    }

    /**
     * Adds a perikopa row with liturgical color tinted background.
     */
    private void addPerikopaRow(ViewGroup container, String text, int seasonColor, View.OnClickListener listener) {
        View row = getLayoutInflater().inflate(R.layout.item_home_row, container, false);

        TextView tv = row.findViewById(R.id.rowText);
        ImageView img = row.findViewById(R.id.rowIcon);

        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.textPrimary));
        img.setImageResource(R.drawable.ic_bible);
        img.setImageTintList(ColorStateList.valueOf(seasonColor));

        // Apply semi-transparent liturgical season color as background
        int bgColor = blendColor(seasonColor, 0.10f);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(12f);
        row.setBackground(bg);

        // Margin between rows
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 6);
        row.setLayoutParams(lp);

        row.setOnClickListener(listener);
        container.addView(row);
    }

    /**
     * Blend a color with the theme surface color for semi-transparent effect.
     */
    private int blendColor(int color, float ratio) {
        // Resolve surface color from theme (works in both light & dark)
        android.util.TypedValue tv = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true);
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

    /**
     * Fetches Mofonaina (Daily Bread) data from the repository in a background thread.
     */
    private void loadMofonainaPreview() {
        MofonainaRepository repo = new MofonainaRepository(requireContext());

        // 1. Show cached data instantly (no network)
        try {
            MofonainaData cached = repo.getFromCache();
            if (cached != null) {
                displayMofonaina(cached);
            }
        } catch (Exception ignored) {}

        // 2. Then refresh from network in background
        new Thread(() -> {
            try {
                MofonainaData data = repo.get("https://www.fjkm.mg");

                requireActivity().runOnUiThread(() -> {
                    if (data == null) return;
                    displayMofonaina(data);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Loads Aina sy Fahasalamana data from the local JSON database
     * and displays the card with today's entry info.
     */
    private void loadAinaCard() {
        AinaMofonainaRepository repo = new AinaMofonainaRepository(requireContext());

        new Thread(() -> {
            repo.syncFromJson();
            MofonainaDay today = repo.getToday();
            if (today != null) {
                requireActivity().runOnUiThread(() ->
                        binding.cardAina.setVisibility(View.VISIBLE)
                );
                binding.ainaDate.setText(formatDate(today.getDateIso()));
            }

        }).start();
    }

    /**
     * Reorder home cards based on user preference from settings.
     * Cards are inside the NestedScrollView's LinearLayout.
     */
    private void applyCardOrder() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(SettingsFragment.KEY_HOME_CARD_ORDER, null);

        List<String> order;
        if (saved != null && !saved.isEmpty()) {
            String[] parts = saved.split(",");
            order = new ArrayList<>();
            for (String part : parts) {
                if (!part.trim().isEmpty()) order.add(part.trim());
            }
        } else {
            order = CardOrderAdapter.getDefaultOrder();
        }

        // Map preference keys to view IDs
        Map<String, Integer> viewMap = new HashMap<>();
        viewMap.put("mofonaina", R.id.cardMofonaina);
        viewMap.put("reading", R.id.cardReading);
        viewMap.put("aina", R.id.cardAina);
        viewMap.put("perikopa", R.id.cardPerikopa);
        viewMap.put("history", R.id.cardHistory);

        // Find the parent LinearLayout (inside NestedScrollView)
        android.view.ViewGroup root = binding.getRoot();
        android.widget.LinearLayout linearParent = null;
        if (root instanceof android.widget.LinearLayout) {
            // root -> NestedScrollView -> LinearLayout
            for (int i = 0; i < ((android.view.ViewGroup) root).getChildCount(); i++) {
                android.view.View child = ((android.view.ViewGroup) root).getChildAt(i);
                if (child instanceof androidx.core.widget.NestedScrollView) {
                    androidx.core.widget.NestedScrollView sv = (androidx.core.widget.NestedScrollView) child;
                    if (sv.getChildCount() > 0 && sv.getChildAt(0) instanceof android.widget.LinearLayout) {
                        linearParent = (android.widget.LinearLayout) sv.getChildAt(0);
                        break;
                    }
                }
            }
        }

        if (linearParent == null) return;

        // Reorder views
        for (int i = 0; i < order.size(); i++) {
            Integer viewId = viewMap.get(order.get(i));
            if (viewId != null) {
                android.view.View view = linearParent.findViewById(viewId);
                if (view != null) {
                    view.bringToFront();
                }
            }
        }

        linearParent.requestLayout();
        linearParent.invalidate();
    }

    private void displayMofonaina(MofonainaData data) {
        if (data == null) return;

        // Mémorise ce qui est affiché (session maison)
        currentMofonaina = data;

        // Show the mofonaina card
        binding.cardMofonaina.setVisibility(View.VISIBLE);

        binding.textMofonainaTitle.setText(data.getTitle());
        binding.textMofonainaSubTitle.setText(data.getDate());
        binding.mofonainaDetailsContainer.removeAllViews();

        // Store verseOfDay to compare with Vakiteny anio
        String verseOfDayStr = null;


        // Adding Bible Reading 1 (hide if same as verseOfDay)
        if (data.getBibleReading1() != null) {
            String reading1 = data.getBibleReading1().toString();
            if (verseOfDayStr == null || !reading1.equals(verseOfDayStr)) {
                addPremiumRow(binding.mofonainaDetailsContainer, reading1,
                        R.drawable.menu_book,
                        v -> openHomeSession(GROUP_MOFONAINA, "verse", reading1));
            }
        }

        // Adding Daily Verse — PROMINENT
        if (data.getVerseOfDay() != null) {
            verseOfDayStr = data.getVerseOfDay().toString();

            View verseRow = getLayoutInflater().inflate(R.layout.item_home_row, binding.mofonainaDetailsContainer, false);
            TextView verseTv = verseRow.findViewById(R.id.rowText);
            ImageView verseIcon = verseRow.findViewById(R.id.rowIcon);
            verseTv.setText(verseOfDayStr);
            verseTv.setTextSize(15);
            verseTv.setTypeface(null, Typeface.BOLD);
            int primaryColor = MaterialColors.getColor(verseTv, androidx.appcompat.R.attr.colorPrimary);
            verseTv.setTextColor(primaryColor);
            verseIcon.setImageResource(R.drawable.ic_bible);
            verseIcon.setImageTintList(ColorStateList.valueOf(primaryColor));

            // Prominent background
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(blendColor(Color.parseColor("#2E7D32"), 0.06f));
            bg.setCornerRadii(new float[]{12, 12, 12, 12, 12, 12, 12, 12});
            verseRow.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 8);
            verseRow.setLayoutParams(lp);

            final String vRef = verseOfDayStr;
            verseRow.setOnClickListener(v ->
                    openHomeSession(GROUP_MOFONAINA, "verse", vRef));
            binding.mofonainaDetailsContainer.addView(verseRow);
        }

        // Adding Song 1
        if (data.getSong1() != null) {
            String songName = data.getSong1().getCategory().toUpperCase() + " " + data.getSong1().getNumber();
            addPremiumRow(binding.mofonainaDetailsContainer, songName,
                    R.drawable.ic_music_note,
                    v -> openHomeSession(GROUP_MOFONAINA, "song",
                            data.getSong1().getId()));
        }

        // Adding Song 2
        if (data.getSong2() != null) {
            String songName = data.getSong2().getCategory().toUpperCase() + " " + data.getSong2().getNumber();
            addPremiumRow(binding.mofonainaDetailsContainer, songName,
                    R.drawable.ic_music_note,
                    v -> openHomeSession(GROUP_MOFONAINA, "song",
                            data.getSong2().getId()));
        }

        // Check if Vakiteny anio (textHome) is same as verseOfDay → hide card
        String homeReading = binding.textHome.getText().toString();
        if (verseOfDayStr != null && homeReading != null && homeReading.equals(verseOfDayStr)) {
            binding.cardReading.setVisibility(View.GONE);
        } else {
            binding.cardReading.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Loads the local browsing history and displays it in the history container.
     * Songs are formatted with category and number to match the Mofonaina card style.
     */
    private void loadHistory(LayoutInflater inflater) {
        HistoryService historyService = new HistoryService(requireContext());
        List<History> historyList = historyService.getAllHistory();

        binding.historyContainer.removeAllViews();

        if (historyList != null && !historyList.isEmpty()) {
            List<History> shown = new ArrayList<>(historyList);
            shown.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

            int displayed = 0;
            for (History history : shown) {
                if (displayed++ >= 5) break;
                // Default values
                int iconRes = R.drawable.history;
                String displayTitle = history.getReading();
                String historyType = history.getType();

                if ("baiboly".equals(historyType)) {
                    iconRes = R.drawable.book_bookmark_svgrepo_com;
                } else if ("fihirana".equals(historyType)) {
                    iconRes = R.drawable.ic_music_note;

                    // Fetch song details to get category and number like in the Mofonaina card
                    Song song = fihiranaService.getSongById(history.getReading());
                    if (song != null) {
                        // Format: CATEGORY NUMBER (e.g., FF 1)
                        displayTitle = history.getReading().toUpperCase().replace("_", " ");
                    }
                } else if ("fanekena".equals(historyType)) {
                    iconRes = R.drawable.ic_dashboard_black_24dp;
                    String pretty = CodeAdapter.getDisplayName(history.getReading());
                    if (pretty != null) displayTitle = pretty;
                }

                // Add the row with the formatted title
                addPremiumRow(binding.historyContainer, displayTitle, iconRes, v -> {
                    // Session DE LA CARTE hitory, ancrée sur l'élément cliqué
                    openHomeSession(GROUP_HISTORY,
                            readerTypeOf(historyType), history.getReading());
                });
            }
        } else {
            TextView empty = new TextView(requireContext());
            empty.setText("Tsy mbola misy novakiana");
            empty.setPadding(48, 24, 48, 24);
            binding.historyContainer.addView(empty);
        }
    }

    // =========================================================
    // SESSION MAISON : tout ce qui est affiché sur les cartes
    // =========================================================

    /** Type lisible pour la session à partir d'un type d'historique. */
    private static String readerTypeOf(String historyType) {
        if ("fihirana".equals(historyType)) return "song";
        if ("fanekena".equals(historyType)) return "fanekena";
        return "verse";
    }

    /** Cartes d'accueil : chaque carte forme son propre groupe de lecture. */
    private static final String GROUP_MOFONAINA = "mofonaina";
    private static final String GROUP_READING = "reading";
    private static final String GROUP_PERIKOPA = "perikopa";
    private static final String GROUP_HISTORY = "history";

    /** Ajoute un item à la session (sans doublon type+référence). */
    private static void addSessionItem(List<ProgramSession.Item> session,
                                       Set<String> seen,
                                       String type, String reference,
                                       String title) {
        if (type == null || reference == null || reference.trim().isEmpty()) return;
        String ref = reference.trim();
        if (!seen.add(type + "|" + ref)) return;
        session.add(new ProgramSession.Item(type, ref,
                title != null && !title.isEmpty() ? title : ref));
    }

    /**
     * Session D'UNE SEULE carte d'accueil : seuls les éléments affichés
     * dans cette carte sont retournés (mofonaina, vakiteny anio, perikopa
     * ou hitory), dans l'ordre d'affichage.
     */
    private List<ProgramSession.Item> buildHomeSession(String group) {
        List<ProgramSession.Item> session = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        if (GROUP_MOFONAINA.equals(group)) {
            // Carte mofonaina : andininy du jour, vakiteny, hira 1 et 2
            MofonainaData m = currentMofonaina;
            if (m != null) {
                if (m.getVerseOfDay() != null) {
                    String v = m.getVerseOfDay().toString();
                    addSessionItem(session, seen, "verse", v, v);
                }
                if (m.getBibleReading1() != null) {
                    String r = m.getBibleReading1().toString();
                    addSessionItem(session, seen, "verse", r, r);
                }
                if (m.getSong1() != null) {
                    addSessionItem(session, seen, "song", m.getSong1().getId(),
                            m.getSong1().getCategory().toUpperCase() + " "
                                    + m.getSong1().getNumber());
                }
                if (m.getSong2() != null) {
                    addSessionItem(session, seen, "song", m.getSong2().getId(),
                            m.getSong2().getCategory().toUpperCase() + " "
                                    + m.getSong2().getNumber());
                }
            }

        } else if (GROUP_READING.equals(group)) {
            // Carte vakiteny anio : cette seule lecture
            String homeReading = binding.textHome.getText().toString().trim();
            addSessionItem(session, seen, "verse", homeReading, homeReading);

        } else if (GROUP_PERIKOPA.equals(group)) {
            // Carte perikopa : les lectures du jour (ou prochaine)
            for (String verse : todayPerikopaVerses()) {
                addSessionItem(session, seen, "verse", verse, verse);
            }

        } else if (GROUP_HISTORY.equals(group)) {
            // Carte hitory : les entrées novakiana
            HistoryService historyService = new HistoryService(requireContext());
            List<History> historyList = historyService.getHistoryPrev();
            if (historyList != null) {
                for (History h : historyList) {
                    String type = readerTypeOf(h.getType());
                    String title = h.getReading();
                    if ("song".equals(type) && h.getReading() != null) {
                        title = h.getReading().toUpperCase().replace("_", " ");
                    } else if ("fanekena".equals(type) && h.getReading() != null) {
                        title = CodeAdapter.getDisplayName(h.getReading());
                    }
                    addSessionItem(session, seen, type, h.getReading(), title);
                }
            }
        }

        return session;
    }

    /** Lectures de la perikopa du jour (ou de la prochaine), triées. */
    private List<String> todayPerikopaVerses() {
        try {
            PerikopaService service = new PerikopaService(requireContext());
            PerikopaDay day = service.getTodayPerikopa();
            if (day.getVerses() == null || day.getVerses().isEmpty()) {
                day = service.getNextPerikopa();
            }
            PerikopaDay sorted = service.sortPerikopa(day);
            List<String> verses = sorted.getVerses();
            return verses != null ? verses : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Position d'un élément dans la session (-1 si absent). */
    private static int sessionIndexOf(List<ProgramSession.Item> session,
                                      String type, String reference) {
        if (type == null || reference == null) return -1;
        String ref = reference.trim();
        for (int i = 0; i < session.size(); i++) {
            ProgramSession.Item it = session.get(i);
            if (it.type != null && it.type.equals(type)
                    && it.reference != null && ref.equals(it.reference)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Clic sur un élément d'accueil : ouvre la session DE SA CARTE,
     * ancrée sur cet élément. Seuls les éléments de cette carte-là sont
     * pris (ex. clic dans le mofonaina -> seulement le mofonaina), et le
     * swipe (dans les deux sens) fait découvrir les autres de la carte.
     */
    private void openHomeSession(String group, String type, String reference) {
        List<ProgramSession.Item> session = buildHomeSession(group);
        int anchor = sessionIndexOf(session, type, reference);

        if (anchor >= 0) {
            int runStart = ProgramSession.runStart(session, anchor);
            ProgramSession.openReader(requireContext(),
                    ProgramSession.encode(session), runStart, anchor - runStart);
            return;
        }

        // Élément introuvable dans la session (données pas encore chargées) :
        // ouverture directe comme avant
        openSingleItem(type, reference);
    }

    /** Ancien comportement : ouverture d'un seul élément, animée. */
    private void openSingleItem(String type, String reference) {
        if (reference == null || reference.trim().isEmpty()) return;
        switch (type == null ? "verse" : type) {
            case "song":
                SongDetailActivity.open(requireContext(), reference, "");
                animateForward();
                break;
            case "fanekena":
                Intent fintent = new Intent(requireContext(), FanekenaActivity.class);
                fintent.putExtra("code", reference);
                startActivity(fintent);
                animateForward();
                break;
            default:
                Intent intent = new Intent(requireContext(), VersesActivity.class);
                intent.putExtra("reading", reference);
                startActivity(intent);
                animateForward();
                break;
        }
    }

    private void animateForward() {
        if (getActivity() != null) {
            // Glissement horizontal opaque : le lecteur entre par la droite,
            // pas de fondu -> pas de flash du fond de fenêtre.
            getActivity().overridePendingTransition(
                    R.anim.slide_in_right, R.anim.slide_out_left);
        }
    }

    private void openAiWithTheme() {
        String theme = binding.lohahevitra.getText().toString();
        if (theme != null && !theme.trim().isEmpty()) {
            String question = "Hazavao ny lohahevitra an-dray: " + theme.trim();
            AiExplanationActivity.startWithQuestion(requireContext(), question);
        } else {
            AiExplanationActivity.startWithQuestion(requireContext(), "");
        }
    }

    // =========================================================
    // WIDGET PROMO
    // =========================================================

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_WIDGET_PROMO_DISMISSED = "widget_promo_dismissed";
    private static final String KEY_LAUNCH_COUNT = "launch_count";

    private void loadWidgetPromo() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        boolean dismissed = prefs.getBoolean(KEY_WIDGET_PROMO_DISMISSED, false);
        if (dismissed) return;

        // Show after 3 launches
        int count = prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1;
        prefs.edit().putInt(KEY_LAUNCH_COUNT, count).apply();

        if (count < 3) return;

        // Inflate and add widget promo card
        View promoView = getLayoutInflater().inflate(R.layout.item_widget_promo, binding.getRoot(), false);

        // Find NestedScrollView inside LinearLayout root
        androidx.core.widget.NestedScrollView scrollView = null;
        android.view.ViewGroup rootGroup = (android.view.ViewGroup) binding.getRoot();
        for (int i = 0; i < rootGroup.getChildCount(); i++) {
            if (rootGroup.getChildAt(i) instanceof androidx.core.widget.NestedScrollView) {
                scrollView = (androidx.core.widget.NestedScrollView) rootGroup.getChildAt(i);
                break;
            }
        }
        if (scrollView == null) return;

        LinearLayout root = (LinearLayout) scrollView.getChildAt(0);
        root.addView(promoView, 1);

        // Dismiss button
        promoView.findViewById(R.id.btnDismissWidgetPromo).setOnClickListener(v -> {
            prefs.edit().putBoolean(KEY_WIDGET_PROMO_DISMISSED, true).apply();
            promoView.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                root.removeView(promoView);
            }).start();
        });

        // Pin Mofonaina widget
        promoView.findViewById(R.id.btnPinMofonainaWidget).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).pinWidgetByClass(MofonainaOfflineWidget.class);
            }
        });
    }
}

