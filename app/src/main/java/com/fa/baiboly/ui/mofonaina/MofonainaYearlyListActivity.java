package com.fa.baiboly.ui.mofonaina;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.perikopa.PerikopaService;
import com.fa.baiboly.data.reading.ReadingService;
import com.fa.baiboly.databinding.ActivityMofonainaYearlyListBinding;
import com.fa.baiboly.models.PerikopaDay;
import com.fa.baiboly.ui.verses.VersesActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MofonainaYearlyListActivity extends AppCompatActivity {

    private ActivityMofonainaYearlyListBinding binding;
    private String listType = "mofonaina";
    private List<YearlyItem> items = new ArrayList<>();
    private Map<String, String> lohahevitra;
    private List<ListItem> flatList;

    private static final String[] MALAGASY_MONTHS = {
            "Janoary", "Febroary", "Martsa", "Aprily", "May", "Jona",
            "Jolay", "Aogositra", "Septambra", "Oktobra", "Novambra", "Desambra"
    };

    private static final String[] DAY_NAMES = {
            "Alahady", "Alatsinainy", "Talata", "Alarobia", "Alakamisy", "Zoma", "Asabotsy"
    };

    private static final int COLOR_ADVENT = Color.parseColor("#1BCF25");
    private static final int COLOR_KAREM = Color.parseColor("#7B1FA2");

    private static final int COLOR_CHRISTMAS = Color.parseColor("#1565C0");
    private static final int COLOR_EASTER = Color.parseColor("#E0E0E0");
    private static final int COLOR_PENTECOST = Color.parseColor("#D32F2F");
    private static final int COLOR_EPIPHANY = Color.parseColor("#2E7D32");
    private static final int COLOR_ORDINARY = Color.parseColor("#2E7D32");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyGlobalTheme();
        WindowManager.LayoutParams params =
                getWindow().getAttributes();

        params.preferredRefreshRate = 120.0f;

        getWindow().setAttributes(params);

        super.onCreate(savedInstanceState);

        binding = ActivityMofonainaYearlyListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        if (getIntent() != null) {
            String type = getIntent().getStringExtra("listType");
            if (type != null) {
                listType = type;
            } else if (getIntent().getBooleanExtra("openPerikopa", false)) {
                listType = "perikopa";
            }
        }

        if ("perikopa".equals(listType)) {
            binding.toolbarTitle.setText("Perikopa isam-bolana");
        } else {
            binding.toolbarTitle.setText("Mofonaina isam-bolana");
        }

        loadData();
        binding.btnCalendar.setOnClickListener(v -> showDatePicker());
    }

    private void applyGlobalTheme() {
        SharedPreferences prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        int savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);

        // Couleurs de base choisies par l'utilisateur
        ColorManager.applyTheme(this);
    }

    // =========================================================
    // DATA LOADING
    // =========================================================

    private void loadData() {
        binding.loader.setVisibility(View.VISIBLE);
        binding.recyclerList.setVisibility(View.GONE);
        binding.textEmpty.setVisibility(View.GONE);

        int year = Calendar.getInstance().get(Calendar.YEAR);
        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        new Thread(() -> {
            PerikopaService perikopaService = new PerikopaService(this);
            Map<String, String> lohahevitraMap = perikopaService.getAllLohahevitraForYear(year);

            items.clear();

            if ("perikopa".equals(listType)) {
                List<PerikopaDay> perikopaDays = perikopaService.getAllPerikopaForYear(year);
                for (PerikopaDay day : perikopaDays) {
                    if (day.getVerses() != null && !day.getVerses().isEmpty()) {
                        PerikopaDay sorted = perikopaService.sortPerikopa(day);
                        YearlyItem item = new YearlyItem();
                        item.date = day.getDate();
                        item.reading = sorted.getVerses().get(0);
                        item.perikopaName = day.getName();
                        item.season = day.getSeason();
                        item.allVerses = sorted.getVerses();
                        item.type = "perikopa";
                        item.isToday = item.date.equals(todayStr);
                        items.add(item);
                    }
                }
            } else {
                ReadingService readingService = new ReadingService(this);
                Map<String, String> readings = readingService.getAllReadingsForYear(year);
                for (Map.Entry<String, String> entry : readings.entrySet()) {
                    YearlyItem item = new YearlyItem();
                    item.date = entry.getKey();
                    item.reading = entry.getValue();
                    item.type = "mofonaina";
                    item.isToday = item.date.equals(todayStr);
                    items.add(item);
                }
            }

            Collections.sort(items, (a, b) -> a.date.compareTo(b.date));

            runOnUiThread(() -> {
                binding.loader.setVisibility(View.GONE);
                lohahevitra = lohahevitraMap;
                showList();
            });
        }).start();
    }

    // =========================================================
    // LIST DISPLAY
    // =========================================================

    private void showList() {
        if (items.isEmpty()) {
            binding.recyclerList.setVisibility(View.GONE);
            binding.textEmpty.setVisibility(View.VISIBLE);
            binding.textEmpty.setText("perikopa".equals(listType)
                    ? "Tsy misy perikopa" : "Tsy misy vakiteny isam-bolana");
            return;
        }

        binding.textEmpty.setVisibility(View.GONE);
        binding.recyclerList.setVisibility(View.VISIBLE);
        binding.recyclerList.setLayoutManager(new LinearLayoutManager(this));

        flatList = buildListWithHeaders();
        binding.recyclerList.setAdapter(new YearlyAdapter(flatList));

        // Scroll to today (or nearest) — centered vertically
        int scrollToIndex = findClosestItemIndex();
        if (scrollToIndex >= 0) {
            scrollToCentered(scrollToIndex);
        }
    }

    /**
     * Center the item at 35% from top of screen.
     */
    private void scrollToCentered(int index) {
        binding.recyclerList.post(() -> {
            LinearLayoutManager lm = (LinearLayoutManager) binding.recyclerList.getLayoutManager();
            if (lm == null) return;
            int listHeight = binding.recyclerList.getHeight();
            int offset = (int) (listHeight * 0.35);
            lm.scrollToPositionWithOffset(index, offset);
        });
    }

    /**
     * Find today, or the nearest past item.
     */
    private int findClosestItemIndex() {
        for (int i = 0; i < flatList.size(); i++) {
            if (flatList.get(i).yearlyItem != null && flatList.get(i).yearlyItem.isToday) {
                return i;
            }
        }
        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        int lastIndex = -1;
        for (int i = 0; i < flatList.size(); i++) {
            if (flatList.get(i).yearlyItem != null
                    && flatList.get(i).yearlyItem.date.compareTo(todayStr) <= 0) {
                lastIndex = i;
            }
        }
        return lastIndex;
    }

    // =========================================================
    // BUILD FLAT LIST
    // =========================================================

    private List<ListItem> buildListWithHeaders() {
        List<ListItem> result = new ArrayList<>();
        String lastMonth = "";

        for (YearlyItem item : items) {
            String monthKey = item.date.length() >= 7 ? item.date.substring(0, 7) : "";

            if (!monthKey.equals(lastMonth)) {
                ListItem header = new ListItem();
                header.type = ListItem.TYPE_HEADER;
                header.monthKey = monthKey;
                header.monthName = getMonthDisplayName(monthKey);
                if (lohahevitra != null && lohahevitra.containsKey(monthKey)) {
                    header.lohahevitra = lohahevitra.get(monthKey);
                }
                result.add(header);
                lastMonth = monthKey;
            }

            ListItem li = new ListItem();
            li.type = ListItem.TYPE_ITEM;
            li.yearlyItem = item;
            result.add(li);
        }
        return result;
    }

    private String getMonthDisplayName(String monthKey) {
        try {
            String[] parts = monthKey.split("-");
            int month = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[0]);
            return MALAGASY_MONTHS[month - 1] + " " + year;
        } catch (Exception e) { return monthKey; }
    }

    private String getDayName(String date) {
        try {
            String[] parts = date.split("-");
            Calendar cal = Calendar.getInstance();
            cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            return DAY_NAMES[cal.get(Calendar.DAY_OF_WEEK) - 1];
        } catch (Exception e) { return ""; }
    }

    // =========================================================
    // DATA MODELS
    // =========================================================

    static class YearlyItem {
        String date;
        String reading;
        String perikopaName;
        String season;
        List<String> allVerses;
        String type;
        boolean isToday;
    }

    static class ListItem {
        static final int TYPE_HEADER = 0;
        static final int TYPE_ITEM = 1;
        int type;
        String monthKey;
        String monthName;
        String lohahevitra;
        YearlyItem yearlyItem;
    }

    // =========================================================
    // ADAPTER
    // =========================================================

    class YearlyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        final List<ListItem> items;
        YearlyAdapter(List<ListItem> items) { this.items = items; }

        @Override
        public int getItemViewType(int position) { return items.get(position).type; }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == ListItem.TYPE_HEADER) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_month_header, parent, false);
                return new HeaderVH(view);
            } else {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_mofonaina_yearly, parent, false);
                return new ItemVH(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ListItem listItem = items.get(position);

            // ── HEADER ──
            if (holder instanceof HeaderVH) {
                HeaderVH h = (HeaderVH) holder;
                h.textMonthHeader.setText(listItem.monthName);

                if (listItem.lohahevitra != null && !listItem.lohahevitra.isEmpty()) {
                    h.cardLohahevitra.setVisibility(View.VISIBLE);
                    h.textLohahevitra.setText(listItem.lohahevitra);

                    int seasonColor = COLOR_ORDINARY;
                    for (int i = position + 1; i < items.size(); i++) {
                        if (items.get(i).yearlyItem != null) {
                            seasonColor = getSeasonColor(items.get(i).yearlyItem.season);
                            break;
                        }
                    }

                    GradientDrawable dot = new GradientDrawable();
                    dot.setShape(GradientDrawable.OVAL);
                    dot.setColor(seasonColor);
                    h.lohahevitraDot.setBackground(dot);

                    h.cardLohahevitra.setCardBackgroundColor(blendColor(seasonColor, 0.08f));
                } else {
                    h.cardLohahevitra.setVisibility(View.GONE);
                }
                return;
            }

            // ── ITEM ──
            ItemVH vh = (ItemVH) holder;
            YearlyItem item = listItem.yearlyItem;

            String dayNum = "";
            try { dayNum = String.valueOf(Integer.parseInt(item.date.split("-")[2])); }
            catch (Exception ignored) {}
            vh.textDayNumber.setText(dayNum);
            vh.textDayName.setText(getDayName(item.date));
            if (item.isToday) {
                vh.textDate.setVisibility(View.GONE);
            } else {
                vh.textDate.setVisibility(View.VISIBLE);
                vh.textDate.setText(formatDateShort(item.date));
            }

            int seasonColor = getSeasonColor(item.season);
            GradientDrawable circleBg = new GradientDrawable();
            circleBg.setShape(GradientDrawable.OVAL);
            circleBg.setColor(seasonColor);
            vh.seasonBarBg.setBackground(circleBg);

            // Adapt text color: dark on light bg, white on dark bg
            vh.textDayNumber.setTextColor(isLightColor(seasonColor)
                    ? Color.parseColor("#333333") : Color.WHITE);

            if (item.isToday) {
                vh.cardItem.setCardBackgroundColor(blendColor(seasonColor, 0.15f));
                vh.cardItem.setStrokeWidth(2);
                vh.cardItem.setStrokeColor(seasonColor);
                vh.cardItem.setCardElevation(4f);
                vh.textTodayBadge.setVisibility(View.VISIBLE);
                vh.textDayName.setTypeface(null, Typeface.BOLD);
            } else {
                TypedValue tv = new TypedValue();
                getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true);
                vh.cardItem.setCardBackgroundColor(tv.data);
                vh.cardItem.setStrokeWidth(0);
                vh.cardItem.setCardElevation(0f);
                vh.textTodayBadge.setVisibility(View.GONE);
                vh.textDayName.setTypeface(null, Typeface.NORMAL);
            }

            // ── PERIKOPA ──
            if ("perikopa".equals(item.type)) {
                vh.textReading.setVisibility(View.GONE);
                vh.perikopaInfoRow.setVisibility(View.VISIBLE);
                vh.textPerikopaName.setText(item.perikopaName != null ? item.perikopaName : "Perikopa");
                vh.textPerikopaName.setTextColor(seasonColor);

                // All verses as clickable chips
                vh.versesContainer.setVisibility(View.VISIBLE);
                vh.versesContainer.removeAllViews();

                if (item.allVerses != null) {
                    for (int i = 0; i < item.allVerses.size(); i++) {
                        String verse = item.allVerses.get(i);
                        final String verseText = verse;

                        // Create chip container
                        LinearLayout chip = new LinearLayout(MofonainaYearlyListActivity.this);
                        chip.setOrientation(LinearLayout.HORIZONTAL);
                        chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
                        chip.setBackgroundResource(R.drawable.bg_verse_chip);
                        chip.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));

                        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        chipLp.bottomMargin = dpToPx(6);
                        chip.setLayoutParams(chipLp);

                        // Arrow icon
                        TextView arrow = new TextView(MofonainaYearlyListActivity.this);
                        arrow.setText(">");
                        arrow.setTextSize(14);
                        arrow.setTextColor(seasonColor);
                        arrow.setTypeface(null, Typeface.BOLD);
                        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        arrowLp.setMarginEnd(dpToPx(10));
                        arrow.setLayoutParams(arrowLp);
                        chip.addView(arrow);

                        // Verse text
                        TextView verseTv = new TextView(MofonainaYearlyListActivity.this);
                        verseTv.setText(verse);
                        verseTv.setTextSize(14);
                        verseTv.setTextColor(ContextCompat.getColor(
                                MofonainaYearlyListActivity.this, R.color.textPrimary));
                        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                        verseTv.setLayoutParams(textLp);
                        chip.addView(verseTv);

                        // Click to open verses
                        chip.setOnClickListener(v -> {
                            Intent intent = new Intent(MofonainaYearlyListActivity.this, VersesActivity.class);
                            intent.putExtra("reading", verseText);
                            startActivity(intent);
                        });

                        // Ripple effect
                        chip.setClickable(true);
                        chip.setFocusable(true);
                        chip.setForeground(ContextCompat.getDrawable(
                                MofonainaYearlyListActivity.this, android.R.drawable.list_selector_background));

                        vh.versesContainer.addView(chip);
                    }
                }

                vh.cardItem.setOnClickListener(null);
                vh.cardItem.setClickable(false);
                vh.cardItem.setFocusable(false);

            // ── MOFONAINA ──
            } else {
                vh.textReading.setVisibility(View.VISIBLE);
                vh.textReading.setText(item.reading);
                vh.perikopaInfoRow.setVisibility(View.GONE);
                vh.versesContainer.setVisibility(View.GONE);

                vh.cardItem.setOnClickListener(v -> {
                    if (item.reading != null) {
                        Intent intent = new Intent(MofonainaYearlyListActivity.this, VersesActivity.class);
                        intent.putExtra("reading", item.reading);
                        startActivity(intent);
                    }
                });
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        class HeaderVH extends RecyclerView.ViewHolder {
            TextView textMonthHeader, textLohahevitra;
            com.google.android.material.card.MaterialCardView cardLohahevitra;
            View lohahevitraDot;
            HeaderVH(View view) {
                super(view);
                textMonthHeader = view.findViewById(R.id.textMonthHeader);
                cardLohahevitra = view.findViewById(R.id.cardLohahevitra);
                textLohahevitra = view.findViewById(R.id.textLohahevitra);
                lohahevitraDot = view.findViewById(R.id.lohahevitraDot);
            }
        }

        class ItemVH extends RecyclerView.ViewHolder {
            View seasonBarBg;
            TextView textDayNumber, textDayName, textDate, textReading, textPerikopaName, textTodayBadge, textVerseCount;
            LinearLayout versesContainer, perikopaInfoRow;
            com.google.android.material.card.MaterialCardView cardItem;
            ItemVH(View view) {
                super(view);
                seasonBarBg = view.findViewById(R.id.seasonBarBg);
                textDayNumber = view.findViewById(R.id.textDayNumber);
                textDayName = view.findViewById(R.id.textDayName);
                textDate = view.findViewById(R.id.textDate);
                textReading = view.findViewById(R.id.textReading);
                textPerikopaName = view.findViewById(R.id.textPerikopaName);
                textTodayBadge = view.findViewById(R.id.textTodayBadge);
                textVerseCount = view.findViewById(R.id.textVerseCount);
                versesContainer = view.findViewById(R.id.versesContainer);
                perikopaInfoRow = view.findViewById(R.id.perikopaInfoRow);
                cardItem = view.findViewById(R.id.cardItem);
            }
        }
    }

    // =========================================================
    // DATE PICKER
    // =========================================================

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, y, m, d) -> {
                    String dateStr = y + "-"
                            + (m + 1 < 10 ? "0" : "") + (m + 1) + "-"
                            + (d < 10 ? "0" : "") + d;
                    scrollToSelectedDate(dateStr);
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void scrollToSelectedDate(String dateStr) {
        if (flatList == null) return;

        for (int i = 0; i < flatList.size(); i++) {
            if (flatList.get(i).yearlyItem != null && dateStr.equals(flatList.get(i).yearlyItem.date)) {
                scrollToCentered(i);
                return;
            }
        }
        Toast.makeText(this, "Tsy misy vakiteny tamin'ny " + formatDateShort(dateStr),
                Toast.LENGTH_SHORT).show();
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private String formatDateShort(String date) {
        try {
            String[] parts = date.split("-");
            int day = Integer.parseInt(parts[2]);
            int month = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[0]);
            return day + " " + MALAGASY_MONTHS[month - 1] + " " + year;
        } catch (Exception e) { return date; }
    }

    /**
     * Couleur de base choisie dans les paramètres (plus de couleur par saison).
     */
    private int getSeasonColor(String season) {
        return ColorManager.getPrimary(this);
    }

    private int blendColor(int color, float ratio) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true);
        int surface = tv.data;
        int sr = (surface >> 16) & 0xFF, sg = (surface >> 8) & 0xFF, sb = surface & 0xFF;
        int cr = (color >> 16) & 0xFF, cg = (color >> 8) & 0xFF, cb = color & 0xFF;
        return Color.rgb(
                (int) (sr + (cr - sr) * ratio),
                (int) (sg + (cg - sg) * ratio),
                (int) (sb + (cb - sb) * ratio));
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    /**
     * Check if a color is light (needs dark text) or dark (needs white text).
     */
    private static boolean isLightColor(int color) {
        double luminance = 0.299 * ((color >> 16) & 0xFF)
                + 0.587 * ((color >> 8) & 0xFF)
                + 0.114 * (color & 0xFF);
        return luminance > 160;
    }
}
