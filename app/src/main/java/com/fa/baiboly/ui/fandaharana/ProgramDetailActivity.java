package com.fa.baiboly.ui.fandaharana;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.fandaharana.FandaharanaService;
import com.fa.baiboly.databinding.ActivityProgramDetailBinding;
import com.fa.baiboly.models.ProgramItem;
import com.fa.baiboly.models.Song;
import com.fa.baiboly.ui.fihirana.SongDetailActivity;
import com.fa.baiboly.ui.fanekena.FanekenaActivity;
import com.fa.baiboly.ui.verses.VersesActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Détail d'un programme (Fandaharana) : liste d'items de types variés
 * (Bible, chants, prières, textes, liens...), réorganisables par
 * glisser-déposer, éditables, dupliquables, favoris, recherche.
 *
 * Lecture : chaque item s'ouvre dans son lecteur existant
 * (VersesActivity / SongDetailActivity / FanekenaActivity).
 */
public class ProgramDetailActivity extends AppCompatActivity {

    private ActivityProgramDetailBinding binding;
    private FandaharanaService service;
    private long programId;
    private String programName;
    private List<ProgramItem> items = new ArrayList<>();
    private List<ProgramItem> visibleItems = new ArrayList<>();
    private ItemAdapter adapter;
    private ItemTouchHelper touchHelper;
    private String searchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyGlobalTheme();
        super.onCreate(savedInstanceState);
        binding = ActivityProgramDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        service = new FandaharanaService(this);
        programId = getIntent().getLongExtra("programId", -1);
        programName = getIntent().getStringExtra("programName");

        if (programId == -1) { finish(); return; }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbarTitle.setText(programName);

        binding.recyclerItems.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ItemAdapter();
        binding.recyclerItems.setAdapter(adapter);

        // Drag & drop (inchangé)
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();

                // Le drag ne réordonne que les items visibles (non filtrés)
                if (from < 0 || to < 0
                        || from >= visibleItems.size()
                        || to >= visibleItems.size()) return false;

                ProgramItem moved = visibleItems.get(from);
                visibleItems.remove(from);
                visibleItems.add(to, moved);
                adapter.notifyItemMoved(from, to);

                // Fusionne le nouvel ordre des items visibles avec les items
                // masqués par la recherche, conservés à leur place relative.
                List<Long> visibleIds = new ArrayList<>();
                for (ProgramItem item : visibleItems) {
                    visibleIds.add(item.getId());
                }
                List<Long> merged = new ArrayList<>();
                int vi = 0;
                boolean inserted = false;
                for (ProgramItem item : items) {
                    boolean isVisible = false;
                    for (Long id : visibleIds) {
                        if (id.equals(item.getId())) { isVisible = true; break; }
                    }
                    if (isVisible) {
                        if (!inserted) {
                            // Insère le bloc des visibles à la place du 1er visible
                            merged.addAll(visibleIds);
                            inserted = true;
                        }
                        vi++;
                    } else {
                        merged.add(item.getId());
                    }
                }
                if (!inserted) merged.addAll(visibleIds);

                // Met à jour les positions locales puis persiste
                for (int i = 0; i < merged.size(); i++) {
                    for (ProgramItem item : items) {
                        if (item.getId() == merged.get(i)) {
                            item.setPosition(i);
                            break;
                        }
                    }
                }
                service.reorderItems(programId, merged);
                return true;
            }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
        };
        touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(binding.recyclerItems);

        // Ajout : ouvre le menu des types
        binding.btnAddItem.setOnClickListener(v -> showAddMenu());

        // Lire tout (lecture des vakiteny, inchangé)
        binding.btnLireTout.setOnClickListener(v -> lireTout());

        loadItems();
    }

    // =========================================================
    // MENU : recherche
    // =========================================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_program_detail, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint("Hikaroka ...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchQuery = query;
                applyFilter();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchQuery = newText;
                applyFilter();
                return true;
            }
        });
        searchView.setOnCloseListener(() -> {
            searchQuery = "";
            applyFilter();
            return false;
        });

        return true;
    }

    private void applyFilter() {
        visibleItems.clear();
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            visibleItems.addAll(items);
        } else {
            String q = searchQuery.toLowerCase().trim();
            for (ProgramItem item : items) {
                String title = item.getTitle() != null
                        ? item.getTitle().toLowerCase() : "";
                String desc = item.getDescription() != null
                        ? item.getDescription().toLowerCase() : "";
                String ref = item.getReference() != null
                        ? item.getReference().toLowerCase() : "";
                if (title.contains(q) || desc.contains(q) || ref.contains(q)) {
                    visibleItems.add(item);
                }
            }
        }
        adapter.notifyDataSetChanged();

        boolean empty = visibleItems.isEmpty();
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerItems.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // =========================================================
    // AJOUT : menu des types + formulaires adaptés
    // =========================================================

    private void showAddMenu() {
        new AddContentSheet(type -> {
            switch (type.id) {
                case "verse":
                    showBiblePicker();
                    break;
                case "song":
                    showSongPicker();
                    break;
                case "fanekena":
                    showFanekenaPicker();
                    break;
                default:
                    showGenericForm(type);
                    break;
            }
        }).show(getSupportFragmentManager(), "add_content");
    }

    /** Sélection Bible : livre -> chapitre -> versets (BottomSheet). */
    private void showBiblePicker() {
        new BiblePickerSheet((reference, title) -> {
            service.addItem(programId, "verse", reference, title);
            loadItems();
            Toast.makeText(this, " : " + title, Toast.LENGTH_SHORT).show();
        }).show(getSupportFragmentManager(), "bible_picker");
    }

    /** Sélection chant : recherche + choix (BottomSheet). */
    private void showSongPicker() {
        new SongPickerSheet(song -> {
            String ref = song.getId();
            String title = song.getNumber() + ". " + song.getTitle();
            service.addItem(programId, "song", ref, title);
            loadItems();
            Toast.makeText(this, "Hira voaongona", Toast.LENGTH_SHORT).show();
        }).show(getSupportFragmentManager(), "song_picker");
    }

    /** Sélection fanekena : saisie du code (form générique prérempli). */
    private void showFanekenaPicker() {
        ProgramContentTypes.TypeDef type = ProgramContentTypes.byId("fanekena");
        showGenericForm(type);
    }

    /** Formulaire générique : titre + référence/contenu + description. */
    private void showGenericForm(ProgramContentTypes.TypeDef type) {
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        View view = getLayoutInflater().inflate(R.layout.dialog_content_form, null);

        TextView dialogTitle = view.findViewById(R.id.dialogTitle);
        EditText inputTitle = view.findViewById(R.id.inputTitle);
        EditText inputReference = view.findViewById(R.id.inputReference);
        EditText inputDescription = view.findViewById(R.id.inputDescription);

        dialogTitle.setText(type.label + " vaovao");

        boolean isLink = "link".equals(type.id);
        boolean isPlainContent = "text".equals(type.id)
                || "preaching".equals(type.id)
                || "study".equals(type.id)
                || "announcement".equals(type.id)
                || "prayer".equals(type.id);

        if (isLink) {
            inputReference.setHint("https://...");
            inputReference.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT
                            | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        } else if (isPlainContent) {
            inputReference.setHint("Votoatiny");
            inputReference.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT
                            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        } else {
            inputReference.setHint("Antsipiriany (rohy, toerana, na inona na inona)");
        }

        view.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            String title = inputTitle.getText().toString().trim();
            String ref = inputReference.getText().toString().trim();
            String desc = inputDescription.getText().toString().trim();

            if (title.isEmpty()) {
                Toast.makeText(this, "Ampidiro ny lohateny", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ref.isEmpty()) ref = title;

            service.addItem(programId, type.id, ref, title,
                    desc.isEmpty() ? null : desc);
            loadItems();
            dialog.dismiss();
        });

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        dialog.setView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(
                    android.R.color.transparent);
        }
        dialog.show();
    }

    // =========================================================
    // LECTURE : chaque item s'ouvre dans son lecteur existant
    // =========================================================

    private void openItem(ProgramItem item) {
        switch (item.getType() != null ? item.getType() : "verse") {
            case "song":
                SongDetailActivity.open(this, item.getReference(),
                        item.getTitle() != null ? item.getTitle() : "");
                break;
            case "fanekena":
                Intent fanekenaIntent =
                        new Intent(this, FanekenaActivity.class);
                fanekenaIntent.putExtra("code", item.getReference());
                startActivity(fanekenaIntent);
                break;
            default:
                // Bible et tout autre type : référence lue par VersesActivity
                Intent intent = new Intent(this, VersesActivity.class);
                intent.putExtra("reading", item.getReference());
                startActivity(intent);
                break;
        }
    }

    private void lireTout() {
        StringBuilder sb = new StringBuilder();
        for (ProgramItem item : items) {
            if ("verse".equals(item.getType())) {
                if (sb.length() > 0) sb.append(",");
                sb.append(item.getReference());
            }
        }

        if (sb.length() == 0) {
            Toast.makeText(this,
                    "Tsy misy vakiteny ao amin'ny fandaharana",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, VersesActivity.class);
        intent.putExtra("readings", sb.toString());
        startActivity(intent);
    }

    // =========================================================
    // CHARGEMENT / ORDRE
    // =========================================================

    private void loadItems() {
        items = service.getItemsForProgram(programId);
        applyFilter();
        updateLireToutVisibility();
    }

    private void updateLireToutVisibility() {
        long verseCount = 0;
        for (ProgramItem item : items) {
            if ("verse".equals(item.getType())) verseCount++;
        }
        binding.btnLireTout.setVisibility(
                verseCount > 0 ? View.VISIBLE : View.GONE);
    }

    private void saveOrder() {
        List<Long> ids = new ArrayList<>();
        for (ProgramItem item : items) { ids.add(item.getId()); }
        service.reorderItems(programId, ids);
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

    // =========================================================
    // ÉDITION / SUPPRESSION / DUPLICATION
    // =========================================================

    private void showEditDialog(ProgramItem item) {
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        View view = getLayoutInflater().inflate(R.layout.dialog_item_details, null);

        EditText inputTitle = view.findViewById(R.id.inputTitle);
        EditText inputDescription = view.findViewById(R.id.inputDescription);

        inputTitle.setText(item.getTitle());
        inputDescription.setText(item.getDescription());

        view.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            String title = inputTitle.getText().toString().trim();
            String desc = inputDescription.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, "Ampidiro ny lohateny", Toast.LENGTH_SHORT).show();
                return;
            }
            service.updateItemDetails(item.getId(), title, desc, item.isFavorite());
            loadItems();
            dialog.dismiss();
        });

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        dialog.setView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(
                    android.R.color.transparent);
        }
        dialog.show();
    }

    private void confirmDelete(ProgramItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Fafao?")
                .setMessage("Hofafy \"" + (item.getTitle() != null
                        ? item.getTitle() : item.getReference()) + "\" ?")
                .setPositiveButton("Eny", (d, w) -> {
                    service.removeItem(item.getId());
                    loadItems();
                })
                .setNegativeButton("Tsia", null)
                .show();
    }

    // =========================================================
    // ADAPTER
    // =========================================================

    class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_program_item, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ProgramItem item = visibleItems.get(position);

            String typeColor = ProgramContentTypes.colorOf(item.getType());
            int color;
            try {
                color = Color.parseColor(typeColor);
            } catch (Exception e) {
                color = Color.parseColor(ProgramContentTypes.COLOR_VERSE);
            }

            holder.textTitle.setText(item.getTitle() != null
                    ? item.getTitle() : item.getReference());
            holder.textType.setText(ProgramContentTypes.labelOf(item.getType()));

            if (item.getDescription() != null && !item.getDescription().isEmpty()) {
                holder.textDescription.setText(item.getDescription());
                holder.textDescription.setVisibility(View.VISIBLE);
            } else {
                holder.textDescription.setVisibility(View.GONE);
            }

            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(color);
            holder.typeIconBg.setBackground(circle);
            holder.typeIcon.setImageResource(
                    ProgramContentTypes.iconOf(item.getType()));

            // Favori
            holder.imgFavorite.setVisibility(
                    item.isFavorite() ? View.VISIBLE : View.GONE);
            holder.btnFavorite.setImageResource(item.isFavorite()
                    ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);

            holder.btnFavorite.setOnClickListener(v -> {
                service.toggleFavorite(item.getId(), !item.isFavorite());
                loadItems();
            });

            // Duplication
            holder.btnDuplicate.setOnClickListener(v -> {
                service.duplicateItem(item);
                loadItems();
                Toast.makeText(ProgramDetailActivity.this,
                        "Dikany", Toast.LENGTH_SHORT).show();
            });

            // Clic -> lecture dans le lecteur existant
            holder.card.setOnClickListener(v -> openItem(item));

            // Clic long -> édition (titre + description)
            holder.card.setOnLongClickListener(v -> {
                showEditDialog(item);
                return true;
            });

            holder.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(holder);
                }
                return false;
            });

            holder.btnRemove.setOnClickListener(v ->
                    confirmDelete(item));
        }

        @Override
        public int getItemCount() { return visibleItems.size(); }

        class VH extends RecyclerView.ViewHolder {
            View typeIconBg, card;
            ImageView typeIcon, imgFavorite;
            // dragHandle est un ImageView dans item_program_item.xml
            // (un cast en ImageButton => ClassCastException au premier item)
            ImageView dragHandle;
            ImageButton btnRemove, btnFavorite, btnDuplicate;
            TextView textTitle, textType, textDescription;
            VH(View view) {
                super(view);
                card = view.findViewById(R.id.cardItem);
                typeIconBg = view.findViewById(R.id.typeIconBg);
                typeIcon = view.findViewById(R.id.typeIcon);
                dragHandle = view.findViewById(R.id.dragHandle);
                btnRemove = view.findViewById(R.id.btnRemove);
                btnFavorite = view.findViewById(R.id.btnFavorite);
                btnDuplicate = view.findViewById(R.id.btnDuplicate);
                imgFavorite = view.findViewById(R.id.imgFavorite);
                textTitle = view.findViewById(R.id.textTitle);
                textType = view.findViewById(R.id.textType);
                textDescription = view.findViewById(R.id.textDescription);
            }
        }
    }
}
