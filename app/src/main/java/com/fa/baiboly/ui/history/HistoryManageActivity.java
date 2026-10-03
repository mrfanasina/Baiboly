package com.fa.baiboly.ui.history;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.fihirana.FihiranaService;
import com.fa.baiboly.data.history.HistoryService;
import com.fa.baiboly.databinding.ActivityHistoryManageBinding;
import com.fa.baiboly.models.History;
import com.fa.baiboly.models.Song;
import com.fa.baiboly.ui.fanekena.FanekenaActivity;
import com.fa.baiboly.ui.fihirana.SongDetailActivity;
import com.fa.baiboly.ui.verses.VersesActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HistoryManageActivity extends AppCompatActivity {

    private ActivityHistoryManageBinding binding;
    private HistoryService historyService;
    private FihiranaService fihiranaService;
    private List<History> historyList = new ArrayList<>();
    private Set<Integer> selectedIds = new HashSet<>();
    private boolean isSelectMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyGlobalTheme();

        WindowManager.LayoutParams params =
                getWindow().getAttributes();

        params.preferredRefreshRate = 120.0f;

        getWindow().setAttributes(params);

        super.onCreate(savedInstanceState);

        binding = ActivityHistoryManageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        historyService = new HistoryService(this);
        fihiranaService = new FihiranaService(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // Select all checkbox
        binding.checkSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                for (History h : historyList) {
                    selectedIds.add(h.getId());
                }
            } else {
                selectedIds.clear();
            }
            updateActionBar();
            binding.recyclerHistory.getAdapter().notifyDataSetChanged();
        });

        // SwipeRefreshLayout
        binding.swipeRefresh.setColorSchemeColors(
                ContextCompat.getColor(this, R.color.orange)
        );
        binding.swipeRefresh.setOnRefreshListener(() -> {
            selectedIds.clear();
            isSelectMode = false;
            loadData();
            binding.swipeRefresh.setRefreshing(false);
        });

        // Delete button
        binding.btnDelete.setOnClickListener(v -> {
            if (selectedIds.isEmpty()) return;
            new AlertDialog.Builder(this)
                    .setTitle("Hamafa?")
                    .setMessage("Hofafina ny " + selectedIds.size() + " isam-bolana?")
                    .setPositiveButton("Eny", (dialog, which) -> {
                        List<Integer> ids = new ArrayList<>(selectedIds);
                        historyService.deleteHistoryByIds(ids);
                        selectedIds.clear();
                        isSelectMode = false;
                        loadData();
                        Toast.makeText(this, "Efa afaina", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Tsia", null)
                    .show();
        });

        loadData();
    }

    private void applyGlobalTheme() {
        SharedPreferences prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        int savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);

        // Couleurs de base choisies par l'utilisateur
        ColorManager.applyTheme(this);
    }

    private void loadData() {
        historyList = historyService.getAllHistory();

        if (historyList.isEmpty()) {
            binding.recyclerHistory.setVisibility(View.GONE);
            binding.textEmpty.setVisibility(View.VISIBLE);
        } else {
            binding.recyclerHistory.setVisibility(View.VISIBLE);
            binding.textEmpty.setVisibility(View.GONE);
            binding.recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
            binding.recyclerHistory.setAdapter(new HistoryAdapter());
            setupSwipeToDelete(); // re-attach with new adapter
        }

        binding.actionBar.setVisibility(isSelectMode && !selectedIds.isEmpty() ? View.VISIBLE : View.GONE);
        binding.checkSelectAll.setChecked(false);
    }

    private void updateActionBar() {
        int count = selectedIds.size();
        isSelectMode = count > 0;
        binding.actionBar.setVisibility(isSelectMode ? View.VISIBLE : View.GONE);
        binding.textSelectedCount.setText(count + " sarina");
        binding.checkSelectAll.setChecked(count == historyList.size() && !historyList.isEmpty());
    }

    // =========================================================
    // SWIPE TO DELETE
    // =========================================================
    private void setupSwipeToDelete() {
        Drawable deleteIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_delete);
        ColorDrawable bgRed = new ColorDrawable(Color.parseColor("#D32F2F"));

        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false; // no drag & drop
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position < 0 || position >= historyList.size()) return;

                History removed = historyList.get(position);
                int removedId = removed.getId();

                // Remove from list
                historyList.remove(position);
                binding.recyclerHistory.getAdapter().notifyItemRemoved(position);

                // Update empty state
                if (historyList.isEmpty()) {
                    binding.recyclerHistory.setVisibility(View.GONE);
                    binding.textEmpty.setVisibility(View.VISIBLE);
                }

                // Also remove from selection if it was selected
                selectedIds.remove(removedId);
                updateActionBar();

                // Delete from DB
                List<Integer> singleId = new ArrayList<>();
                singleId.add(removedId);
                historyService.deleteHistoryByIds(singleId);

                // Undo via Snackbar
                Snackbar.make(binding.getRoot(),
                                "Efa afaina", Snackbar.LENGTH_LONG)
                        .setAction("Dikan'ao", v -> {
                            // Re-insert into DB
                            historyService.addHistory(removed.getReading(), removed.getType());
                            // Reload
                            loadData();
                        })
                        .show();
            }

            @Override
            public void onChildDraw(@NonNull Canvas canvas,
                                    @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {

                View itemView = viewHolder.itemView;
                int cornerRadius = 24;

                if (dX < 0) { // Swiping left
                    // Draw red background
                    bgRed.setBounds(
                            itemView.getRight() + (int) dX,
                            itemView.getTop(),
                            itemView.getRight(),
                            itemView.getBottom()
                    );
                    bgRed.draw(canvas);

                    // Draw delete icon
                    if (deleteIcon != null) {
                        int iconMargin = 32;
                        int iconSize = 48;
                        int iconTop = itemView.getTop() + (itemView.getHeight() - iconSize) / 2;
                        int iconLeft = itemView.getRight() - iconMargin - iconSize;
                        int iconRight = itemView.getRight() - iconMargin;
                        int iconBottom = iconTop + iconSize;

                        deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);

                        // Tint icon white
                        DrawableCompat.setTint(deleteIcon, Color.WHITE);
                        deleteIcon.draw(canvas);
                    }
                }

                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.7f; // need to swipe 70% to trigger delete
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerHistory);
    }

    // =========================================================
    // ADAPTER
    // =========================================================
    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history_manage, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            History history = historyList.get(position);
            String type = history.getType();

            // Set icon and display text
            int iconRes = android.R.drawable.ic_menu_recent_history;
            String displayTitle = history.getReading();
            String typeLabel = "";

            if ("baiboly".equals(type)) {
                iconRes = R.drawable.book_bookmark_svgrepo_com;
                typeLabel = "Baiboly";
            } else if ("fihirana".equals(type)) {
                iconRes = R.drawable.ic_music_note;
                Song song = fihiranaService.getSongById(history.getReading());
                if (song != null) {
                    displayTitle = song.getCategory().toUpperCase() + " " + song.getNumber();
                }
                typeLabel = "Fihirana";
            } else if ("fanekena".equals(type)) {
                iconRes = R.drawable.ic_dashboard_black_24dp;
                typeLabel = "Fanekena";
            }

            holder.itemIcon.setImageResource(iconRes);
            holder.textReading.setText(displayTitle);
            holder.textType.setText(typeLabel + " • " + formatDate(history.getTimestamp()));

            // Checkbox state
            boolean isSelected = selectedIds.contains(history.getId());
            holder.checkItem.setChecked(isSelected);

            // Long press to enter select mode
            holder.itemView.setOnLongClickListener(v -> {
                if (!isSelectMode) {
                    isSelectMode = true;
                    selectedIds.add(history.getId());
                    updateActionBar();
                    notifyDataSetChanged();
                    return true;
                }
                return false;
            });

            // Click behavior
            holder.itemView.setOnClickListener(v -> {
                if (isSelectMode) {
                    // Toggle selection
                    if (selectedIds.contains(history.getId())) {
                        selectedIds.remove(history.getId());
                    } else {
                        selectedIds.add(history.getId());
                    }
                    updateActionBar();
                    notifyItemChanged(holder.getAdapterPosition());
                } else {
                    // Open item
                    openHistoryItem(history, type);
                }
            });

            // Checkbox click
            holder.checkItem.setOnClickListener(v -> {
                if (selectedIds.contains(history.getId())) {
                    selectedIds.remove(history.getId());
                } else {
                    selectedIds.add(history.getId());
                }
                updateActionBar();
            });
        }

        @Override
        public int getItemCount() {
            return historyList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            CheckBox checkItem;
            ImageView itemIcon;
            TextView textReading, textType;

            VH(View view) {
                super(view);
                checkItem = view.findViewById(R.id.checkItem);
                itemIcon = view.findViewById(R.id.itemIcon);
                textReading = view.findViewById(R.id.textReading);
                textType = view.findViewById(R.id.textType);
            }
        }
    }

    private void openHistoryItem(History history, String type) {
        if (type == null) return;

        switch (type) {
            case "baiboly":
                Intent intent = new Intent(this, VersesActivity.class);
                intent.putExtra("reading", history.getReading());
                startActivity(intent);
                break;

            case "fihirana":
                Song song = fihiranaService.getSongById(history.getReading());
                if (song != null) {
                    SongDetailActivity.open(this, history.getReading(), "");
                }
                break;

            case "fanekena":
                Intent fintent = new Intent(this, FanekenaActivity.class);
                fintent.putExtra("code", history.getReading());
                startActivity(fintent);
                break;
        }
    }

    private String formatDate(long timestamp) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        } catch (Exception e) {
            return "";
        }
    }
}
