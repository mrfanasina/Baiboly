package com.fa.baiboly.ui.litorjia;

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
import com.fa.baiboly.data.litorjia.LitorjiaJsonService;
import com.fa.baiboly.data.litorjia.LitorjiaRepository;
import com.fa.baiboly.data.litorjia.Program;
import com.fa.baiboly.data.litorjia.ProgramGenerator;
import com.fa.baiboly.data.litorjia.ProgramNode;
import com.fa.baiboly.data.litorjia.TemplateCatalog;
import com.fa.baiboly.databinding.ActivityLitorjiaListBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Ecran « Litorjia » — Programme de culte (module séparé du fandaharana
 * existant) : liste des programmes + création (date, gabarit, titre).
 */
public class LitorjiaListActivity extends AppCompatActivity {

    private ActivityLitorjiaListBinding binding;
    private LitorjiaRepository repo;
    private List<Program> programs = new ArrayList<>();
    private ProgramAdapter adapter;

    private static final int[] COLORS = {
            Color.parseColor("#7B1FA2"), Color.parseColor("#1565C0"),
            Color.parseColor("#2E7D32"), Color.parseColor("#EF6C00"),
            Color.parseColor("#C40E5A"), Color.parseColor("#455A64"),
    };

    public static void open(Context context) {
        context.startActivity(new Intent(context, LitorjiaListActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyGlobalTheme();
        super.onCreate(savedInstanceState);
        binding = ActivityLitorjiaListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = 120.0f;
        getWindow().setAttributes(params);

        repo = new LitorjiaRepository(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle(R.string.lit_title);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.recyclerPrograms.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProgramAdapter();
        binding.recyclerPrograms.setAdapter(adapter);

        binding.fabAdd.setOnClickListener(v -> showCreateDialog());

        // Import / aide toujours accessibles en un geste (§4.7) : menu ⋮.
        binding.toolbar.inflateMenu(R.menu.menu_litorjia_list);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_import) {
                LitorjiaImportActivity.openCreate(this);
                return true;
            }
            if (item.getItemId() == R.id.menu_import_help) {
                LitorjiaImportActivity.openHelp(this);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPrograms();
    }

    private void loadPrograms() {
        programs = repo.getAllPrograms();
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty = programs.isEmpty();
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerPrograms.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // =========================================================
    // Création (date, gabarit, titre)
    // =========================================================

    private void showCreateDialog() {
        final List<TemplateCatalog.TemplateInfo> templates = repo.templates();
        if (templates.isEmpty()) {
            // §4.1/§4.8 : le catalogue est la seule source ; jamais de
            // gabarit codé en dur en secours.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.lit_create_dialog_title)
                    .setMessage(R.string.lit_no_catalog)
                    .setPositiveButton("Eny", null)
                    .show();
            return;
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_litorjia_create, null);
        RecyclerView recycler = view.findViewById(R.id.recyclerTemplates);
        TextView hint = view.findViewById(R.id.textTemplateHint);
        com.google.android.material.button.MaterialButtonToggleGroup family =
                view.findViewById(R.id.toggleFamily);

        // Étape 1 : familles réellement présentes, les litorjia d'abord.
        final List<String> kinds = new ArrayList<>();
        if (hasKind(templates, "litorjia")) kinds.add("litorjia");
        if (hasKind(templates, "level")) kinds.add("level");
        family.removeAllViews();
        family.setSingleSelection(true);
        family.setSelectionRequired(true);
        for (String kind : kinds) {
            com.google.android.material.button.MaterialButton btn =
                    new com.google.android.material.button.MaterialButton(this, null,
                            com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(getString("litorjia".equals(kind)
                    ? R.string.lit_family_litorjia : R.string.lit_family_level));
            btn.setAllCaps(false);
            btn.setId(View.generateViewId());
            btn.setTag(kind);
            family.addView(btn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            styleChoice(btn, false);
        }

        // Étape 2 : gabarits de la famille choisie, en grille de deux.
        final List<TemplateCatalog.TemplateInfo> shown = new ArrayList<>();
        final String[] selected = {templates.get(0).id};
        TemplateChipAdapter chipAdapter = new TemplateChipAdapter(shown, selected, hint);
        recycler.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 2));
        recycler.setAdapter(chipAdapter);

        Runnable loadFamily = () -> {
            String kind = kinds.isEmpty() ? null : kinds.get(0);
            List<Integer> checked = family.getCheckedButtonIds();
            if (!checked.isEmpty()) {
                View b = family.findViewById(checked.get(0));
                if (b != null && b.getTag() instanceof String) kind = (String) b.getTag();
            }
            shown.clear();
            for (TemplateCatalog.TemplateInfo t : templates) {
                if (kind != null && kind.equals(t.kind)) shown.add(t);
            }
            selected[0] = shown.isEmpty() ? null : shown.get(0).id;
            chipAdapter.notifyDataSetChanged();
            showPreview(hint, selected[0]);
        };
        family.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof com.google.android.material.button.MaterialButton) {
                    styleChoice((com.google.android.material.button.MaterialButton) child,
                            child.getId() == checkedId);
                }
            }
            loadFamily.run();
        });
        if (family.getChildCount() > 0) family.check(family.getChildAt(0).getId());
        loadFamily.run();

        // Date : par défaut le prochain dimanche
        EditText inputDate = view.findViewById(R.id.inputDate);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        int today = cal.get(Calendar.DAY_OF_WEEK);
        int daysToSunday = (Calendar.SUNDAY - today + 7) % 7;
        cal.add(Calendar.DAY_OF_MONTH, daysToSunday);
        Date initial = cal.getTime();
        inputDate.setText(displayDate(initial));
        final String[] dateString = {fmt.format(initial)};

        inputDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            try {
                c.setTime(fmt.parse(dateString[0]));
            } catch (Exception ignored) {}
            new android.app.DatePickerDialog(this,
                    (dp, y, m, d) -> {
                        c.set(y, m, d);
                        dateString[0] = fmt.format(c.getTime());
                        inputDate.setText(displayDate(c.getTime()));
                    },
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                    .show();
        });

        // Titre : proposé depuis le nom du perikopa du jour (si connu)
        EditText inputName = view.findViewById(R.id.inputName);
        loadDefaultTitle(dateString[0], inputName);

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            String dateStr = dateString[0];
            String title = inputName.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, getString(R.string.lit_create_title), Toast.LENGTH_SHORT).show();
                return;
            }
            if (selected[0] == null) {
                Toast.makeText(this, getString(R.string.lit_no_catalog), Toast.LENGTH_SHORT).show();
                return;
            }
            Program p = new ProgramGenerator(this)
                    .generate(this, dateStr, selected[0], title);
            if (p == null) {
                // Génération impossible : on ne ferme pas le dialogue et on
                // n'ouvre JAMAIS un écran vide (§4.8).
                Toast.makeText(this, getString(R.string.lit_generation_failed),
                        Toast.LENGTH_LONG).show();
                return;
            }
            dialog.dismiss();
            loadPrograms();
            LitorjiaDetailActivity.openProgram(this, p.id);
        });

        dialog.show();
    }

    /**
     * Apparence d'un choix (famille ou gabarit) selon qu'il est retenu ou
     * non : la sélection se voit immédiatement, avec les couleurs du thème.
     */
    private void styleChoice(com.google.android.material.button.MaterialButton btn, boolean on) {
        int bg = com.google.android.material.color.MaterialColors.getColor(btn, on
                ? androidx.appcompat.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorSurfaceVariant);
        int fg = com.google.android.material.color.MaterialColors.getColor(btn, on
                ? com.google.android.material.R.attr.colorOnPrimary
                : com.google.android.material.R.attr.colorOnSurfaceVariant);
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bg));
        btn.setTextColor(fg);
    }

    private boolean hasKind(List<TemplateCatalog.TemplateInfo> list, String kind) {
        for (TemplateCatalog.TemplateInfo t : list) {
            if (kind.equals(t.kind)) return true;
        }
        return false;
    }

    /** Date lisible en malgache, sans dépendre de la locale du téléphone. */
    private String displayDate(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        String[] months = {"Janoary", "Febroary", "Martsa", "Aprily", "May",
                "Jona", "Jolay", "Aogositra", "Septambra", "Oktobra",
                "Novambra", "Desambra"};
        return c.get(Calendar.DAY_OF_MONTH) + " " + months[c.get(Calendar.MONTH)]
                + " " + c.get(Calendar.YEAR);
    }

    /** Aperçu des sections du gabarit sélectionné (depuis la base). */
    private void showPreview(TextView hint, String templateId) {
        if (hint == null) return;
        if (templateId == null) {
            hint.setText("");
            return;
        }
        String preview = TemplateCatalog.preview(repo.templateTree(templateId));
        hint.setText(preview.isEmpty() ? getString(R.string.lit_create_preview_hint) : preview);
    }

    /** Pré-remplit le titre : "Alahady 25 Oktobra" ou nom du perikopa. */
    private void loadDefaultTitle(String isoDate, EditText input) {
        try {
            String[] parts = isoDate.split("-");
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int d = Integer.parseInt(parts[2]);
            Calendar c = Calendar.getInstance();
            c.set(y, m - 1, d);
            String[] months = {"Janoary", "Febroary", "Martsa", "Aprily", "May",
                    "Jona", "Jolay", "Aogositra", "Septambra", "Oktobra",
                    "Novambra", "Desambra"};
            String[] dow = {"Alahady", "Alatsinainy", "Talata", "Alarobia",
                    "Alakamisy", "Zoma", "Asabotsy"};
            int dowIdx = c.get(Calendar.DAY_OF_WEEK) - 1;
            input.setText(dowIdx >= 0 && dowIdx < 7
                    ? dow[dowIdx] + " " + d + " " + months[m - 1] : "Fivoriana");
        } catch (Exception e) {
            input.setText("Fivoriana");
        }
        input.setSelection(input.getText().length());
    }

    // =========================================================
    // Menus long-press : renommer / dupliquer / import
    // =========================================================

    private void showRenameDialog(Program program) {
        View view = getLayoutInflater().inflate(R.layout.dialog_litorjia_rename, null);
        EditText input = view.findViewById(R.id.inputName);
        input.setText(program.title);
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) repo.renameProgram(program.id, name, null);
            loadPrograms();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void applyGlobalTheme() {
        SharedPreferences prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        int savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);
        ColorManager.applyTheme(this);
    }

    // =========================================================
    // Adapter de la liste
    // =========================================================

    class ProgramAdapter extends RecyclerView.Adapter<ProgramAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_litorjia_program, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Program p = programs.get(position);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(COLORS[position % COLORS.length]);
            holder.iconBg.setBackground(circle);
            holder.iconColor = COLORS[position % COLORS.length];

            holder.textName.setText(p.title);
            StringBuilder meta = new StringBuilder();
            if (p.serviceDate != null && !p.serviceDate.isEmpty()) {
                meta.append(prettyIso(p.serviceDate)).append(" · ");
            }
            String tpl = repo.templateLabel(p.templateId);
            meta.append(tpl == null ? "" : tpl);
            holder.textMeta.setText(meta.toString());

            // Pastille "à compléter" : comptage rapide
            int incomplete = quickIncomplete(p.id);
            holder.textBadge.setVisibility(incomplete > 0 ? View.VISIBLE : View.GONE);
            if (incomplete > 0) {
                holder.textBadge.setText(getString(R.string.lit_todo_badge_count, incomplete));
            }

            holder.card.setOnClickListener(v ->
                    LitorjiaDetailActivity.openProgram(LitorjiaListActivity.this, p.id));
            holder.card.setOnLongClickListener(v -> {
                showProgramMenu(p);
                return true;
            });
            holder.btnDelete.setOnClickListener(v -> confirmDelete(p));
        }

        @Override
        public int getItemCount() { return programs.size(); }

        class VH extends RecyclerView.ViewHolder {
            View card, iconBg;
            TextView textName, textMeta, textBadge;
            ImageView btnDelete;
            int iconColor;
            VH(View v) {
                super(v);
                card = v.findViewById(R.id.cardProgram);
                iconBg = v.findViewById(R.id.iconBg);
                textName = v.findViewById(R.id.textProgramName);
                textMeta = v.findViewById(R.id.textProgramMeta);
                textBadge = v.findViewById(R.id.textProgramBadge);
                btnDelete = v.findViewById(R.id.btnDelete);
            }
        }
    }

    /** "2026-11-08" -> "8 Novambra 2026" (null si format inattendu). */
    private String prettyIso(String iso) {
        try {
            String[] p = iso.split("-");
            Calendar c = Calendar.getInstance();
            c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            return displayDate(c.getTime());
        } catch (Exception e) {
            return iso;
        }
    }

    private int quickIncomplete(String programId) {
        Program p = repo.getProgram(programId);
        if (p == null || p.root == null) return 0;
        int n = 0;
        List<ProgramNode> all = new ArrayList<>();
        p.flatten(all);
        for (ProgramNode node : all) {
            if (!node.hidden && node.isIncomplete()) n++;
        }
        return n;
    }

    private void showProgramMenu(Program p) {
        new AlertDialog.Builder(this)
                .setTitle(p.title)
                .setItems(new CharSequence[]{
                        getString(R.string.lit_menu_rename),
                        getString(R.string.lit_menu_duplicate),
                        getString(R.string.lit_menu_export),
                        getString(R.string.lit_menu_change_date),
                        getString(R.string.lit_menu_delete)}, (d, w) -> {
                    if (w == 0) showRenameDialog(p);
                    else if (w == 1) {
                        Program copy = repo.duplicateProgram(p.id, null, p.serviceDate);
                        if (copy != null) {
                            Toast.makeText(this, getString(R.string.lit_duplicate_done),
                                    Toast.LENGTH_SHORT).show();
                            loadPrograms();
                        }
                    } else if (w == 2) {
                        shareJson(repo.getProgram(p.id));
                    } else if (w == 3) {
                        showDatePicker(p);
                    } else {
                        confirmDelete(p);
                    }
                })
                .show();
    }

    private void confirmDelete(Program p) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.lit_delete_title)
                .setMessage(R.string.lit_delete_message)
                .setPositiveButton("Eny", (d, w) -> {
                    repo.deleteProgram(p.id);
                    Toast.makeText(this, getString(R.string.lit_delete_done),
                            Toast.LENGTH_SHORT).show();
                    loadPrograms();
                })
                .setNegativeButton("Tsia", null)
                .show();
    }

    /** Export JSON depuis la liste (partage, enregistrement, copie). */
    private void shareJson(Program program) {
        if (program == null) return;
        try {
            String safe = program.title == null ? "fivoriana"
                    : program.title.replaceAll("[^A-Za-z0-9]+", "_").toLowerCase(Locale.US);
            java.io.File f = new java.io.File(getCacheDir(), "litorjia-" + safe + ".json");
            java.io.FileWriter w = new java.io.FileWriter(f);
            w.write(LitorjiaJsonService.export(program));
            w.close();
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, "com.fa.baiboly.fileprovider", f);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.lit_share_json)));
        } catch (Exception e) {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText(
                    "litorjia", LitorjiaJsonService.export(program)));
            Toast.makeText(this, getString(R.string.lit_copied), Toast.LENGTH_SHORT).show();
        }
    }

    /** Changement de date du culte (le perikopa ne suit pas automatiquement). */
    private void showDatePicker(Program p) {
        Calendar c = Calendar.getInstance();
        if (p.serviceDate != null) {
            try {
                String[] x = p.serviceDate.split("-");
                c.set(Integer.parseInt(x[0]), Integer.parseInt(x[1]) - 1, Integer.parseInt(x[2]));
            } catch (Exception ignored) {}
        }
        new android.app.DatePickerDialog(this, (dp, y, m, d) -> {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
            c.set(y, m, d);
            repo.renameProgram(p.id, p.title, f.format(c.getTime()));
            loadPrograms();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    // =========================================================
    // Adapter des chips gabarits
    // =========================================================

    class TemplateChipAdapter extends RecyclerView.Adapter<TemplateChipAdapter.VH> {

        private final List<TemplateCatalog.TemplateInfo> templates;
        private final String[] selected;
        private final TextView hint;

        TemplateChipAdapter(List<TemplateCatalog.TemplateInfo> templates,
                            String[] selected, TextView hint) {
            this.templates = templates;
            this.selected = selected;
            this.hint = hint;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_litorjia_template, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            TemplateCatalog.TemplateInfo t = templates.get(position);
            holder.btn.setText(t.label);
            boolean on = t.id.equals(selected[0]);
            styleChoice(holder.btn, on);
            holder.btn.setChecked(on);
            holder.btn.setOnClickListener(v -> {
                selected[0] = t.id;
                notifyDataSetChanged();
                showPreview(hint, t.id);
            });
        }

        @Override
        public int getItemCount() { return templates.size(); }

        class VH extends RecyclerView.ViewHolder {
            com.google.android.material.button.MaterialButton btn;
            VH(View v) {
                super(v);
                btn = v.findViewById(R.id.btnTemplate);
            }
        }
    }
}
