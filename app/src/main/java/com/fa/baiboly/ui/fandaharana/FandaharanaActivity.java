package com.fa.baiboly.ui.fandaharana;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
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
import com.fa.baiboly.data.fandaharana.FandaharanaService;
import com.fa.baiboly.databinding.ActivityFandaharanaBinding;
import com.fa.baiboly.models.Program;

import java.util.ArrayList;
import java.util.List;

public class FandaharanaActivity extends AppCompatActivity {

    private ActivityFandaharanaBinding binding;
    private FandaharanaService service;
    private List<Program> programs = new ArrayList<>();
    private ProgramAdapter adapter;

    private static final int[] COLORS = {
            Color.parseColor("#7B1FA2"), // purple
            Color.parseColor("#1565C0"), // blue
            Color.parseColor("#2E7D32"), // green
            Color.parseColor("#D32F2F"), // red
            Color.parseColor("#EF6C00"), // orange
            Color.parseColor("#C40E5A"), // pink
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyGlobalTheme();
        super.onCreate(savedInstanceState);
        binding = ActivityFandaharanaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        // Force high refresh rate for smoother scrolling experience
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = 120.0f;
        getWindow().setAttributes(params);

        service = new FandaharanaService(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.recyclerPrograms.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProgramAdapter();
        binding.recyclerPrograms.setAdapter(adapter);

        binding.fabAdd.setOnClickListener(v -> showCreateDialog());

        loadPrograms();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPrograms();
    }

    private void loadPrograms() {
        programs = service.getAllPrograms();
        adapter.notifyDataSetChanged();

        binding.emptyState.setVisibility(programs.isEmpty() ? View.VISIBLE : View.GONE);
        binding.recyclerPrograms.setVisibility(programs.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showCreateDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .create();

        View view = getLayoutInflater().inflate(R.layout.dialog_create_program, null);
        EditText input = view.findViewById(R.id.inputName);

        view.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Anaran'ny fanadaharana", Toast.LENGTH_SHORT).show();
                return;
            }
            service.createProgram(name);
            loadPrograms();
            dialog.dismiss();
        });

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        dialog.setView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private void showRenameDialog(Program program) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .create();

        View view = getLayoutInflater().inflate(R.layout.dialog_create_program, null);
        EditText input = view.findViewById(R.id.inputName);
        input.setText(program.getName());
        TextView title = view.findViewById(R.id.dialogTitle);
        title.setText("Manova anarana");

        view.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) return;
            service.updateProgram(program.getId(), name);
            loadPrograms();
            dialog.dismiss();
        });

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        dialog.setView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private int getColorForIndex(int index) {
        return COLORS[index % COLORS.length];
    }

    private void applyGlobalTheme() {
        SharedPreferences prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        int savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);

        // Couleurs de base choisies par l'utilisateur
        ColorManager.applyTheme(this);
    }

    // =========================================================
    // ADAPTER
    // =========================================================

    class ProgramAdapter extends RecyclerView.Adapter<ProgramAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_program, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Program program = programs.get(position);
            int color = getColorForIndex(position);

            holder.textName.setText(program.getName());
            int count = program.getItemCount();
            holder.textCount.setText(count + (count == 1 ? " item" : " items"));

            // Color circle
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(color);
            holder.iconBg.setBackground(circle);

            // Card click -> open detail
            holder.card.setOnClickListener(v -> {
                Intent intent = new Intent(FandaharanaActivity.this, ProgramDetailActivity.class);
                intent.putExtra("programId", program.getId());
                intent.putExtra("programName", program.getName());
                startActivity(intent);
            });

            // Long click -> rename
            holder.card.setOnLongClickListener(v -> {
                showRenameDialog(program);
                return true;
            });

            // Delete
            holder.btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(FandaharanaActivity.this)
                        .setTitle("Fafao?")
                        .setMessage("Hofafy io fandaharana io?")
                        .setPositiveButton("Eny", (d, w) -> {
                            service.deleteProgram(program.getId());
                            loadPrograms();
                        })
                        .setNegativeButton("Tsia", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() { return programs.size(); }

        class VH extends RecyclerView.ViewHolder {
            View iconBg;
            TextView textName, textCount;
            View card;
            ImageView btnDelete;
            VH(View view) {
                super(view);
                card = view.findViewById(R.id.cardProgram);
                iconBg = view.findViewById(R.id.programIconBg);
                textName = view.findViewById(R.id.textProgramName);
                textCount = view.findViewById(R.id.textItemCount);
                btnDelete = view.findViewById(R.id.btnDelete);
            }
        }
    }
}
