package com.fa.baiboly.ui.mofonaina;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.history.HistoryService;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.perikopa.PerikopaService;
import com.fa.baiboly.data.reading.ReadingService;
import com.fa.baiboly.databinding.ActivityMofonainaCalendarBinding;
import com.fa.baiboly.models.PerikopaDay;
import com.fa.baiboly.ui.verses.VersesActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MofonainaCalendarActivity extends AppCompatActivity {

    private ActivityMofonainaCalendarBinding binding;

    private Calendar currentCalendar;
    private HistoryService historyService;

    private static final String PREFS_NAME = "app_settings";

    // Current selected tab
    private boolean isMofonainaTab = true;

    // Malagasy month names
    private static final String[] MALAGASY_MONTHS = {
            "Janoary", "Febroary", "Martsa", "Aprily", "May", "Jona",
            "Jolay", "Aogositra", "Septambra", "Oktobra", "Novambra", "Desambra"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyGlobalTheme();
        super.onCreate(savedInstanceState);

        binding = ActivityMofonainaCalendarBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        WindowManager.LayoutParams params =
                getWindow().getAttributes();

        params.preferredRefreshRate = 120.0f;

        getWindow().setAttributes(params);

        historyService = new HistoryService(this);

        // Initialize calendar to current month
        currentCalendar = Calendar.getInstance();

        // Setup toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // Setup month navigation
        binding.btnPrevMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            updateCalendar();
        });

        binding.btnNextMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            updateCalendar();
        });

        // Setup tabs
        binding.tabMofonaina.setOnClickListener(v -> {
            isMofonainaTab = true;
            updateTabStyles();
            updateCalendar();
        });

        binding.tabPerikopa.setOnClickListener(v -> {
            isMofonainaTab = false;
            updateTabStyles();
            updateCalendar();
        });

        // Initial load
        updateTabStyles();
        updateCalendar();
    }

    private void applyGlobalTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);

        // Couleurs de base choisies par l'utilisateur
        ColorManager.applyTheme(this);
    }

    private void updateTabStyles() {
        if (isMofonainaTab) {
            binding.tabMofonaina.setTextColor(ContextCompat.getColor(this, R.color.teal_700));
            binding.tabMofonaina.setBackgroundResource(R.drawable.bg_tab_selected);
            binding.tabPerikopa.setTextColor(ContextCompat.getColor(this, R.color.title_gray));
            binding.tabPerikopa.setBackgroundResource(R.drawable.bg_tab_unselected);
            // Show default legend, hide perikopa legend
            binding.legendDefault.setVisibility(View.VISIBLE);
            binding.legendPerikopa.setVisibility(View.GONE);
        } else {
            binding.tabPerikopa.setTextColor(ContextCompat.getColor(this, R.color.green));
            binding.tabPerikopa.setBackgroundResource(R.drawable.bg_tab_selected);
            binding.tabMofonaina.setTextColor(ContextCompat.getColor(this, R.color.title_gray));
            binding.tabMofonaina.setBackgroundResource(R.drawable.bg_tab_unselected);
            // Show perikopa legend, hide default legend
            binding.legendDefault.setVisibility(View.GONE);
            binding.legendPerikopa.setVisibility(View.VISIBLE);
        }
    }

    private void updateCalendar() {
        int year = currentCalendar.get(Calendar.YEAR);
        int month = currentCalendar.get(Calendar.MONTH) + 1; // 1-based

        // Update month title
        String monthName = MALAGASY_MONTHS[month - 1];
        binding.textMonthYear.setText(monthName + " " + year);

        if (isMofonainaTab) {
            loadMofonainaCalendar(year, month);
        } else {
            loadPerikopaCalendar(year, month);
        }
    }

    // =========================================================
    // MOFONAINA CALENDAR
    // =========================================================
    private void loadMofonainaCalendar(int year, int month) {
        new Thread(() -> {
            ReadingService readingService = new ReadingService(this);
            Map<Integer, String> readings = readingService.getAllReadingsForMonth(year, month);

            runOnUiThread(() -> {
                buildCalendarGrid(year, month, readings, null);
            });
        }).start();
    }

    // =========================================================
    // PERIKOPA CALENDAR
    // =========================================================
    private void loadPerikopaCalendar(int year, int month) {
        new Thread(() -> {
            PerikopaService perikopaService = new PerikopaService(this);
            Map<Integer, PerikopaDay> perikopaDays = perikopaService.getAllPerikopaForMonth(year, month);

            runOnUiThread(() -> {
                buildPerikopaGrid(year, month, perikopaDays);
            });
        }).start();
    }

    // =========================================================
    // BUILD MOFONAINA GRID
    // =========================================================
    private void buildCalendarGrid(int year, int month,
                                   Map<Integer, String> readings,
                                   Map<Integer, PerikopaDay> perikopaDays) {

        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, 1);

        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // 1=Sun
        // Convert to Monday-start (0=Mon)
        int startOffset = (firstDayOfWeek + 5) % 7;

        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        int todayDay = -1;
        Calendar today = Calendar.getInstance();
        if (today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) + 1 == month) {
            todayDay = today.get(Calendar.DAY_OF_MONTH);
        }

        // Build items: null for empty cells before first day, then 1..daysInMonth
        List<CalendarItem> items = new ArrayList<>();
        for (int i = 0; i < startOffset; i++) {
            items.add(null);
        }
        for (int d = 1; d <= daysInMonth; d++) {
            CalendarItem item = new CalendarItem();
            item.day = d;
            item.isToday = (d == todayDay);
            item.reading = readings != null ? readings.get(d) : null;
            item.hasReading = item.reading != null && !item.reading.isEmpty();
            items.add(item);
        }

        CalendarDayAdapter adapter = new CalendarDayAdapter(items, (item, day) -> {
            if (item.reading != null && !item.reading.isEmpty()) {
                // Save to history
                historyService.addHistory(item.reading, "baiboly");
                // Open verse reader
                Intent intent = new Intent(this, VersesActivity.class);
                intent.putExtra("reading", item.reading);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Tsy misy vakiteny androany", Toast.LENGTH_SHORT).show();
            }
        });

        binding.calendarGrid.setLayoutManager(new GridLayoutManager(this, 7));
        binding.calendarGrid.setAdapter(adapter);
    }

    // =========================================================
    // BUILD PERIKOPA GRID
    // =========================================================
    private void buildPerikopaGrid(int year, int month,
                                   Map<Integer, PerikopaDay> perikopaDays) {

        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, 1);

        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int startOffset = (firstDayOfWeek + 5) % 7;

        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        int todayDay = -1;
        Calendar today = Calendar.getInstance();
        if (today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) + 1 == month) {
            todayDay = today.get(Calendar.DAY_OF_MONTH);
        }

        List<CalendarItem> items = new ArrayList<>();
        for (int i = 0; i < startOffset; i++) {
            items.add(null);
        }
        for (int d = 1; d <= daysInMonth; d++) {
            CalendarItem item = new CalendarItem();
            item.day = d;
            item.isToday = (d == todayDay);
            item.perikopaDay = perikopaDays.get(d);
            item.hasReading = item.perikopaDay != null
                    && item.perikopaDay.getVerses() != null
                    && !item.perikopaDay.getVerses().isEmpty();
            item.perikopaName = item.perikopaDay != null ? item.perikopaDay.getName() : null;
            item.season = item.perikopaDay != null ? item.perikopaDay.getSeason() : null;
            items.add(item);
        }

        CalendarDayAdapter adapter = new CalendarDayAdapter(items, (item, day) -> {
            if (item.perikopaDay != null && item.perikopaDay.getVerses() != null
                    && !item.perikopaDay.getVerses().isEmpty()) {

                // Sort perikopa readings
                PerikopaService perikopaService = new PerikopaService(this);
                PerikopaDay sorted = perikopaService.sortPerikopa(item.perikopaDay);

                if (sorted.getVerses() != null && !sorted.getVerses().isEmpty()) {
                    // Open first verse
                    String firstVerse = sorted.getVerses().get(0);
                    historyService.addHistory(firstVerse, "baiboly");
                    Intent intent = new Intent(this, VersesActivity.class);
                    intent.putExtra("reading", firstVerse);
                    startActivity(intent);
                }
            } else {
                Toast.makeText(this, "Tsy misy perikopa androany", Toast.LENGTH_SHORT).show();
            }
        });

        binding.calendarGrid.setLayoutManager(new GridLayoutManager(this, 7));
        binding.calendarGrid.setAdapter(adapter);
    }

    // =========================================================
    // SEASON COLORS
    // =========================================================

    /**
     * Map des saisons liturgiques vers des couleurs.
     * Les noms viennent de la table liturgical_seasons.season dans la DB.
     */
    /**
     * Map des saisons liturgiques (noms malgasy de la DB) vers des couleurs.
     * Saisons DB: epifania, karemy, paska, pentekosta, advento, krismasy
     */
    /**
     * Couleur de base choisie dans les paramètres (plus de couleur par saison).
     */
    private static int getSeasonColor(Context context, String season) {
        return ColorManager.getPrimary(context);
    }

    /**
     * Mélange une couleur avec la couleur de surface du thème.
     * Retourne une couleur semi-transparente pour un tint léger.
     */
    private static int blendWithSurface(Context context, int color, float ratio) {
        // Résoudre la couleur de surface (clair ou sombre selon le thème)
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorSurface, tv, true);
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

    // =========================================================
    // DATA MODEL
    // =========================================================
    static class CalendarItem {
        int day;
        boolean isToday;
        String reading;
        boolean hasReading;
        PerikopaDay perikopaDay;
        String perikopaName;
        String season;
    }

    // =========================================================
    // ADAPTER
    // =========================================================
    static class CalendarDayAdapter extends RecyclerView.Adapter<CalendarDayAdapter.ViewHolder> {

        interface OnDayClickListener {
            void onDayClick(CalendarItem item, int day);
        }

        private final List<CalendarItem> items;
        private final OnDayClickListener listener;

        CalendarDayAdapter(List<CalendarItem> items, OnDayClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_calendar_day, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CalendarItem item = items.get(position);

            if (item == null) {
                holder.dayCard.setVisibility(View.INVISIBLE);
                holder.dayCard.setOnClickListener(null);
                return;
            }

            holder.dayCard.setVisibility(View.VISIBLE);
            holder.dayNumber.setText(String.valueOf(item.day));

            // Determine card background color
            int cardBg;
            int textColor;
            if (item.isToday) {
                cardBg = ContextCompat.getColor(holder.itemView.getContext(), R.color.teal_700);
                textColor = Color.WHITE;
            } else if (item.season != null && !item.season.isEmpty() && item.hasReading) {
                // Tint card with liturgical season color
                int seasonColor = getSeasonColor(holder.itemView.getContext(), item.season);
                cardBg = blendWithSurface(holder.itemView.getContext(), seasonColor, 0.08f);
                textColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.textPrimary);
            } else {
                cardBg = ContextCompat.getColor(holder.itemView.getContext(), R.color.cardColor);
                textColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.textPrimary);
            }
            holder.dayNumber.setTextColor(textColor);

            // Reading indicator dot
            if (item.hasReading) {
                holder.readingIndicator.setVisibility(View.VISIBLE);
                // Color the dot based on season
                int dotColor;
                if (item.season != null && !item.season.isEmpty()) {
                    dotColor = getSeasonColor(holder.itemView.getContext(), item.season);
                } else {
                    dotColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.green);
                }
                holder.readingIndicator.getBackground().setTint(dotColor);
            } else {
                holder.readingIndicator.setVisibility(View.GONE);
            }

            // Perikopa label
            if (item.perikopaName != null && !item.perikopaName.isEmpty()) {
                holder.dayLabel.setVisibility(View.VISIBLE);
                holder.dayLabel.setText(item.perikopaName);
            } else {
                holder.dayLabel.setVisibility(View.GONE);
            }

            // Click listener
            holder.dayCard.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDayClick(item, item.day);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            View dayCard;
            TextView dayNumber;
            View readingIndicator;
            TextView dayLabel;

            ViewHolder(View view) {
                super(view);
                dayCard = view.findViewById(R.id.dayCard);
                dayNumber = view.findViewById(R.id.dayNumber);
                readingIndicator = view.findViewById(R.id.readingIndicator);
                dayLabel = view.findViewById(R.id.dayLabel);
            }
        }
    }
}
