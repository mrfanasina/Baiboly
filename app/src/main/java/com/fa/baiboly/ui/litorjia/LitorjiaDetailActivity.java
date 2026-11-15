package com.fa.baiboly.ui.litorjia;

import android.app.AlertDialog;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.litorjia.LitorjiaRepository;
import com.fa.baiboly.data.litorjia.LitorjiaJsonService;
import com.fa.baiboly.data.litorjia.NodePayloadParser;
import com.fa.baiboly.data.litorjia.PerikopaFiller;
import com.fa.baiboly.data.litorjia.Program;
import com.fa.baiboly.data.litorjia.ProgramNode;
import com.fa.baiboly.data.litorjia.ReferenceResolver;
import com.fa.baiboly.data.litorjia.TemplateCatalog;
import com.fa.baiboly.models.PerikopaDay;
import com.fa.baiboly.data.perikopa.PerikopaService;
import com.fa.baiboly.ui.fandaharana.BiblePickerSheet;
import com.fa.baiboly.ui.fandaharana.SongPickerSheet;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Écran d'un programme de culte (§4.3–4.4) :
 *  - arbre indenté pliable, pastille « à compléter » + compteur d'en-tête ;
 *  - mode lecture (consultation pendant le culte) / mode édition ;
 *  - tap = détail / éditeur, appui long = menu d'actions ;
 *  - autosave transactionnel à chaque action + annulation (20 actions).
 */
public class LitorjiaDetailActivity extends AppCompatActivity
        implements TreeFlatAdapter.Listener, NodeEditorDialog.OnRestorePerikopa {

    private static final int UNDO_LIMIT = 20;
    private static final int REQ_PICK_JSON = 41;

    public static final String EXTRA_PROGRAM_ID = "programId";

    public static void openProgram(Context ctx, String programId) {
        Intent i = new Intent(ctx, LitorjiaDetailActivity.class);
        i.putExtra(EXTRA_PROGRAM_ID, programId);
        ctx.startActivity(i);
    }

    private LitorjiaRepository repo;
    private Program program;
    private ProgramNode root;
    private TreeFlatAdapter adapter;
    private android.view.Menu toolbarMenu;
    private boolean editMode = false;
    /** Vrai pendant un glisser-déposer qui a réellement déplacé un noeud. */
    private boolean dragArranging = false;

    /** Pile d'annulation : snapshots JSON de tout l'arbre. */
    private final List<String> undoStack = new ArrayList<>();

    /** Pinceau du fond rouge révélé par le glissement de suppression. */
    private final android.graphics.Paint swipePaint = new android.graphics.Paint();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyGlobalTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_litorjia_detail);

        WindowManager.LayoutParams wp = getWindow().getAttributes();
        wp.preferredRefreshRate = 120.0f;
        getWindow().setAttributes(wp);

        repo = new LitorjiaRepository(this);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_litorjia_detail);
        toolbarMenu = toolbar.getMenu();
        toolbar.setOnMenuItemClickListener(this::onMenuItem);

        String id = getIntent().getStringExtra(EXTRA_PROGRAM_ID);
        program = repo.getProgram(id);
        if (program == null || program.root == null) {
            Toast.makeText(this, "Fandaharana tsy hita", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        root = program.root;

        adapter = new TreeFlatAdapter(root, this);
        RecyclerView recycler = findViewById(R.id.recyclerTree);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        setupDragAndDrop(recycler);

        findViewById(R.id.fabAdd).setOnClickListener(v -> showAddMenu(root, 0));
        findViewById(R.id.btnEmptyAdd).setOnClickListener(v -> {
            if (!editMode) toggleEditMode();
            showAddMenu(root, 0);
        });
        findViewById(R.id.btnEmptyImport).setOnClickListener(v -> startActivityForResult(
                LitorjiaImportActivity.intentForMerge(this), REQ_PICK_JSON));

        refreshHeader();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    // =========================================================
    // En-tête + compteurs
    // =========================================================

    private void refreshHeader() {
        TextView tvTitle = findViewById(R.id.textTitle);
        TextView tvMeta = findViewById(R.id.textMeta);
        TextView badge = findViewById(R.id.badgeTodo);

        List<ProgramNode> all = new ArrayList<>();
        program.flatten(all);
        all.remove(root);

        int incomplete = 0;
        int visible = 0;
        for (ProgramNode n : all) {
            if (n.hidden) continue;
            visible++;
            if (n.isIncomplete()) incomplete++;
        }

        tvTitle.setText(program.title == null ? "" : program.title);
        StringBuilder meta = new StringBuilder();
        if (program.serviceDate != null && !program.serviceDate.isEmpty()) {
            meta.append(prettyDate(program.serviceDate)).append(" · ");
        }
        String tpl = repo.templateLabel(program.templateId);
        meta.append(tpl == null ? "—" : tpl);
        meta.append(" · ").append(getString(R.string.lit_item_count, visible));
        tvMeta.setText(meta.toString());

        if (incomplete > 0) {
            badge.setVisibility(View.VISIBLE);
            badge.setText(getString(R.string.lit_todo_badge_count, incomplete));
        } else {
            badge.setVisibility(View.GONE);
        }

        // Programme vide (ou entièrement masqué en lecture) : actions plutôt
        // qu'un écran blanc (§4.8).
        boolean empty = editMode ? all.isEmpty() : visible == 0;
        findViewById(R.id.emptyState).setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.recyclerTree).setVisibility(empty ? View.GONE : View.VISIBLE);

        // FAB : visible en édition, et toujours sur un programme vide
        findViewById(R.id.fabAdd).setVisibility(editMode || empty ? View.VISIBLE : View.GONE);
    }

    /** Bascule lecture/édition depuis le menu ou l'état vide. */
    private void toggleEditMode() {
        editMode = true;
        adapter.setEditMode(true);
        android.view.MenuItem item = toolbarMenu == null ? null : toolbarMenu.findItem(R.id.menu_edit_toggle);
        if (item != null) {
            item.setTitle(R.string.lit_mode_lecture);
            item.setIcon(R.drawable.ic_eye_icon);
        }
        refreshHeader();
    }

    private String prettyDate(String iso) {
        try {
            String[] p = iso.split("-");
            Calendar c = Calendar.getInstance();
            c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            String[] months = {"Janoary", "Febroary", "Martsa", "Aprily", "May",
                    "Jona", "Jolay", "Aogositra", "Septambra", "Oktobra",
                    "Novambra", "Desambra"};
            return c.get(Calendar.DAY_OF_MONTH) + " " + months[c.get(Calendar.MONTH)]
                    + " " + c.get(Calendar.YEAR);
        } catch (Exception e) {
            return iso;
        }
    }

    // =========================================================
    // Autosave + undo (20 dernières actions, §4.3)
    // =========================================================

    private void pushUndoState() {
        undoStack.add(LitorjiaJsonService.export(program));
        while (undoStack.size() > UNDO_LIMIT) undoStack.remove(0);
    }

    private void saveNow() {
        program.root = root;
        repo.saveTree(program, root);
        refreshHeader();
    }

    private void undoLast() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, getString(R.string.lit_nothing_to_undo), Toast.LENGTH_SHORT).show();
            return;
        }
        String snapshot = undoStack.remove(undoStack.size() - 1);
        try {
            LitorjiaJsonService.Report r = LitorjiaJsonService.parse(snapshot, repo);
            if (r.ok && r.root != null) {
                r.root.id = root.id; // garde l'identité racine
                root = r.root;
                program.root = root;
                adapter = new TreeFlatAdapter(root, this);
                RecyclerView recycler = findViewById(R.id.recyclerTree);
                recycler.setAdapter(adapter);
                setupDragAndDrop(recycler);
                adapter.setEditMode(editMode);
                repo.saveTree(program, root);
                refreshHeader();
                Toast.makeText(this, "Niova", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) {}
    }

    // =========================================================
    // Interactions arbre
    // =========================================================

    @Override
    public void onNodeTap(ProgramNode node) {
        // Un appui montre TOUJOURS le détail en consultation (avec « Ovay »
        // et « Hofafana »), y compris en mode édition. L'éditeur ne s'ouvre
        // que sur demande explicite : bouton Ovay, menu d'appui long ou
        // ajout d'un élément. On ne tombe donc jamais directement sur le
        // formulaire d'édition au premier appui.
        NodeDetailDialog.show(this, node,
                () -> startEditing(node), () -> confirmDelete(node));
    }

    /** Passe en mode édition (si besoin) puis ouvre l'éditeur de l'élément. */
    private void startEditing(ProgramNode node) {
        if (!editMode) toggleEditMode();
        openEditor(node);
    }

    /** Remplace le chant avec la feuille Fihirana (sokajy + numéro). */
    private void pickHymn(ProgramNode node) {
        new SongPickerSheet(song -> {
            pushUndoState();
            node.payload = NodePayloadParser.hymnPayload(song.getCategory(),
                    song.getNumber(), new ArrayList<>(), node.hymnLabel());
            if (node.title == null || node.title.trim().isEmpty()) {
                node.title = song.getTitle();
            }
            commit();
            Toast.makeText(this, "Hira nosoloina", Toast.LENGTH_SHORT).show();
        }, node.hymnBook(), node.hymnNumber())
                .show(getSupportFragmentManager(), "lit_song_picker");
    }

    /** Remplace la (les) lecture(s) avec la feuille Bible de l'app. */
    private void pickReading(ProgramNode node) {
        new BiblePickerSheet((reference, title) -> {
            List<ProgramNode.BibleRef> refs = PerikopaFiller.parse(reference,
                    new ReferenceResolver(this));
            if (refs.isEmpty()) {
                // Référence non reconnue : conservée telle quelle
                ProgramNode.BibleRef raw = new ProgramNode.BibleRef();
                raw.book = reference;
                raw.label = title;
                refs.add(raw);
            }
            pushUndoState();
            node.payload = NodePayloadParser.biblePayload(refs);
            if (node.title == null || node.title.trim().isEmpty()) node.title = title;
            commit();
            Toast.makeText(this, "Vakiteny nosoloina", Toast.LENGTH_SHORT).show();
        }).show(getSupportFragmentManager(), "lit_bible_picker");
    }

    @Override
    public void onNodeLongTap(ProgramNode node) {
        showNodeMenu(node);
    }

    @Override
    public void onNodeMove(ProgramNode from, ProgramNode to) {
        pushUndoState();
        moveNodeBefore(from, to);
    }

    /** Icône corbeille d'une ligne (édition) : même confirmation que le menu. */
    @Override
    public void onNodeDelete(ProgramNode node) {
        confirmDelete(node);
    }

    // =========================================================
    // Glisser-déposer (§4.3, alternative aux boutons monter/descendre)
    // =========================================================

    /**
     * La poignée de chaque ligne démarre un glisser vertical. Le noeud est
     * déplacé avant la cible, ce qui permet aussi de changer de section en
     * le déposant sur une autre branche.
     */
    private void setupDragAndDrop(RecyclerView recycler) {
        androidx.recyclerview.widget.ItemTouchHelper.Callback callback =
                new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                        androidx.recyclerview.widget.ItemTouchHelper.UP
                                | androidx.recyclerview.widget.ItemTouchHelper.DOWN,
                        androidx.recyclerview.widget.ItemTouchHelper.LEFT) {
                    @Override
                    public int getMovementFlags(@NonNull RecyclerView rv,
                                                @NonNull RecyclerView.ViewHolder vh) {
                        // Le glissement de suppression n'existe qu'en édition :
                        // en lecture, on ne risque pas d'effacer par mégarde.
                        // Tout se glisse, y compris une section : elle emmène
                        // alors tout son contenu (voir onMove).
                        ProgramNode n = adapter.nodeAtPosition(nodePosition(vh));
                        boolean canDrag = editMode && n != null;
                        return makeMovementFlags(
                                canDrag
                                        ? androidx.recyclerview.widget.ItemTouchHelper.UP
                                                | androidx.recyclerview.widget.ItemTouchHelper.DOWN
                                        : 0,
                                editMode ? androidx.recyclerview.widget.ItemTouchHelper.LEFT : 0);
                    }

                    @Override
                    public boolean onMove(@NonNull RecyclerView rv,
                                          @NonNull RecyclerView.ViewHolder viewHolder,
                                          @NonNull RecyclerView.ViewHolder target) {
                        int fromPos = nodePosition(viewHolder);
                        int toPos = nodePosition(target);
                        if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
                            return false;
                        }
                        ProgramNode from = adapter.nodeAtPosition(fromPos);
                        ProgramNode to = adapter.nodeAtPosition(toPos);
                        // Sens du geste : vers le bas on passe APRÈS la cible,
                        // vers le haut AVANT. (Insérer toujours « avant »
                        // rendait tout déplacement vers le bas sans effet.)
                        boolean after = toPos > fromPos;

                        // Un seul point d'annulation pour tout le geste, pris
                        // AVANT la première modification de l'arbre.
                        boolean pushed = false;
                        if (!dragArranging) {
                            pushUndoState();
                            pushed = true;
                        }
                        if (!reorderNextTo(from, to, after)) {
                            if (pushed) undoStack.remove(undoStack.size() - 1);
                            return false;
                        }

                        // Bloc emporté : une section déplace tout son
                        // sous-arbre avec elle.
                        int blockSize = Math.max(1, adapter.blockSizeAt(fromPos));
                        int dest;
                        if (after) {
                            // Vers le bas sur une section : on atterrit après
                            // tout son sous-arbre, comme le fait l'arbre.
                            int toDepth = adapter.depthAt(toPos);
                            int endOfTarget = toPos;
                            while (endOfTarget + 1 < adapter.getItemCount()
                                    && adapter.depthAt(endOfTarget + 1) > toDepth) {
                                endOfTarget++;
                            }
                            // Décalage dû au retrait du bloc, situé avant.
                            dest = endOfTarget + 1 - blockSize;
                        } else {
                            dest = toPos;
                        }
                        if (dest < 0) dest = 0;

                        // Arbre réordonné en mémoire + liste qui suit le doigt
                        // sans reconstruction : le glissement reste fluide.
                        // Enregistrement et reconstruction au relâchement.
                        dragArranging = true;
                        adapter.moveBlock(fromPos, blockSize, dest);
                        return true;
                    }

                    @Override
                    public void clearView(@NonNull RecyclerView rv,
                                          @NonNull RecyclerView.ViewHolder viewHolder) {
                        super.clearView(rv, viewHolder);
                        if (dragArranging) {
                            dragArranging = false;
                            // Après l'animation : enregistrement de la nouvelle
                            // disposition puis reconstruction fidèle de l'arbre
                            // (profondeurs et sous-arbres qui ont suivi).
                            rv.post(() -> {
                                saveNow();
                                adapter.rebuild();
                            });
                        }
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        ProgramNode n = adapter.nodeAtPosition(nodePosition(viewHolder));
                        // La ligne revient en place : la confirmation s'affiche
                        // sur un arbre intact, annuler ne laisse pas de trace.
                        adapter.notifyDataSetChanged();
                        if (n != null) confirmDelete(n);
                    }

                    @Override
                    public void onChildDraw(@NonNull android.graphics.Canvas c,
                                            @NonNull RecyclerView rv,
                                            @NonNull RecyclerView.ViewHolder vh,
                                            float dX, float dY, int actionState,
                                            boolean isCurrentlyActive) {
                        if (actionState
                                == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE
                                && dX < 0) {
                            drawSwipeDelete(c, vh.itemView, dX);
                        }
                        super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
                    }

                    @Override
                    public boolean isLongPressDragEnabled() {
                        return false; // appui long = menu d'actions
                    }

                    @Override
                    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder vh) {
                        // Un glissement franc suffit, sans parcourir toute la
                        // largeur de l'écran.
                        return 0.35f;
                    }

                    @Override
                    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                        if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG
                                && viewHolder != null) {
                            // Confirme la prise en main de la poignée.
                            viewHolder.itemView.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.LONG_PRESS);
                        }
                        super.onSelectedChanged(viewHolder, actionState);
                    }
                };
        androidx.recyclerview.widget.ItemTouchHelper helper =
                new androidx.recyclerview.widget.ItemTouchHelper(callback);
        helper.attachToRecyclerView(recycler);
        adapter.setTouchHelper(helper);
    }

    /**
     * Position fiable d'une ligne : la position « binding » peut être
     * NO_POSITION juste après une mise à jour de la liste, ce qui
     * désactivait le geste au premier essai.
     */
    private static int nodePosition(RecyclerView.ViewHolder vh) {
        int pos = vh.getBindingAdapterPosition();
        if (pos == RecyclerView.NO_POSITION) pos = vh.getLayoutPosition();
        return pos;
    }

    /** Fond rouge + corbeille blanche révélés par le glissement gauche. */
    private void drawSwipeDelete(android.graphics.Canvas c, View item, float dX) {
        float density = getResources().getDisplayMetrics().density;
        swipePaint.setColor(androidx.core.content.ContextCompat.getColor(
                this, R.color.lit_swipe_delete));
        c.drawRect(Math.max(item.getLeft(), item.getRight() + dX), item.getTop(),
                item.getRight(), item.getBottom(), swipePaint);

        android.graphics.drawable.Drawable icon =
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_delete);
        if (icon == null) return;
        int size = (int) (24 * density);
        int margin = (int) (16 * density);
        int left = item.getRight() - margin - size;
        int top = item.getTop() + (item.getHeight() - size) / 2;
        icon.setBounds(left, top, left + size, top + size);
        androidx.core.graphics.drawable.DrawableCompat.setTint(icon, android.graphics.Color.WHITE);
        icon.draw(c);
    }

    /** Déplace {@code from} juste avant {@code to} (sans étape d'annulation). */
    private void moveNodeBefore(ProgramNode from, ProgramNode to) {
        if (reorderBefore(from, to)) {
            saveNow();
            adapter.rebuild();
        }
    }

    /**
     * Réordonne l'arbre en mémoire seulement : {@code from} devient le frère
     * qui précède {@code to}, quelle que soit la branche de celui-ci.
     * @return vrai si l'arbre a changé
     */
    private boolean reorderBefore(ProgramNode from, ProgramNode to) {
        return reorderNextTo(from, to, false);
    }

    /**
     * Place {@code from} juste avant ou juste après {@code to}, dans la
     * branche de celui-ci.
     */
    private boolean reorderNextTo(ProgramNode from, ProgramNode to, boolean after) {
        if (from == null || to == null || from == to) return false;
        if (isAncestor(from, to)) return false; // pas un noeud dans son propre sous-arbre
        ProgramNode fromParent = parentOf(from);
        int fi = indexOf(fromParent, from);
        if (fi < 0) return false;
        fromParent.children.remove(fi);
        ProgramNode toParent = parentOf(to);
        int ti = indexOf(toParent, to);
        if (ti < 0) ti = toParent.children.size();
        toParent.children.add(after ? ti + 1 : ti, from);
        from.parentId = toParent.id;
        return true;
    }

    private boolean isAncestor(ProgramNode ancestor, ProgramNode node) {
        for (ProgramNode c : ancestor.children) {
            if (c == node || isAncestor(c, node)) return true;
        }
        return false;
    }

    private void openEditor(ProgramNode node) {
        pushUndoState();
        NodeEditorDialog.show(this, program, node, saved -> {
            saveNow();
            adapter.refresh();
        }, this, this::confirmDelete);
    }

    // =========================================================
    // Menu d'actions d'un noeud (accessible sans geste, §4.3)
    // =========================================================

    private void showNodeMenu(ProgramNode node) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (editMode) {
            labels.add(getString(R.string.lit_act_edit)); actions.add(() -> openEditor(node));
            // Raccourci : remplacer directement, sans passer par l'éditeur.
            if (ProgramNode.TYPE_HYMN.equals(node.type)) {
                labels.add("Hanolo hira"); actions.add(() -> pickHymn(node));
            } else if (ProgramNode.TYPE_BIBLE.equals(node.type)) {
                labels.add("Hanolo vakiteny"); actions.add(() -> pickReading(node));
            }
        }
        labels.add(getString(R.string.lit_act_consult));
        actions.add(() -> NodeDetailDialog.show(this, node,
                () -> startEditing(node), () -> confirmDelete(node)));

        if (editMode) {
            // Ajouter à n'importe quel niveau : enfant (sections) ou
            // frère juste après le noeud courant.
            if (node.isContainer()) {
                labels.add(getString(R.string.lit_act_add_child));
                actions.add(() -> showAddMenu(node, node.children.size()));
            }
            labels.add(getString(R.string.lit_act_add_after));
            actions.add(() -> {
                ProgramNode parent = parentOf(node);
                int at = indexOf(parent, node);
                showAddMenu(parent, at < 0 ? parent.children.size() : at + 1);
            });
            labels.add(getString(R.string.lit_act_rename));
            actions.add(() -> showRenameNode(node));
            labels.add(getString(R.string.lit_act_up)); actions.add(() -> moveNode(node, -1));
            labels.add(getString(R.string.lit_act_down)); actions.add(() -> moveNode(node, +1));
            labels.add(getString(R.string.lit_act_indent)); actions.add(() -> indent(node, true));
            labels.add(getString(R.string.lit_act_outdent)); actions.add(() -> indent(node, false));
            labels.add(getString(R.string.lit_act_duplicate)); actions.add(() -> duplicate(node));
            labels.add(node.hidden ? getString(R.string.lit_act_show)
                    : getString(R.string.lit_act_hide));
            actions.add(() -> { node.hidden = !node.hidden; commit(); });
            if (node.isContainer()) {
                labels.add(getString(R.string.lit_act_expand)); actions.add(() -> adapter.setExpanded(node, true));
                labels.add(getString(R.string.lit_act_collapse)); actions.add(() -> adapter.setExpanded(node, false));
            }
            labels.add(getString(R.string.lit_act_delete));
            actions.add(() -> confirmDelete(node));
        }

        final String[] items = labels.toArray(new String[0]);
        final List<Runnable> acts = actions;
        new AlertDialog.Builder(this)
                .setTitle(node.displayTitle())
                .setItems(items, (d, w) -> {
                    if (w >= 0 && w < acts.size()) acts.get(w).run();
                })
                .show();
    }

    private void commit() {
        saveNow();
        adapter.refresh();
    }

    // =========================================================
    // Mouvements dans l'arbre
    // =========================================================

    private ProgramNode parentOf(ProgramNode n) {
        ProgramNode p = findParent(root, n.id);
        return p == null ? root : p;
    }

    private ProgramNode findParent(ProgramNode cur, String id) {
        for (ProgramNode c : cur.children) {
            if (c.id.equals(id)) return cur;
            ProgramNode deep = findParent(c, id);
            if (deep != null) return deep;
        }
        return null;
    }

    private int indexOf(ProgramNode parent, ProgramNode n) {
        for (int i = 0; i < parent.children.size(); i++) {
            if (parent.children.get(i).id.equals(n.id)) return i;
        }
        return -1;
    }

    private void moveNode(ProgramNode n, int delta) {
        ProgramNode parent = parentOf(n);
        int i = indexOf(parent, n);
        if (i < 0) return;
        int j = i + delta;
        if (j < 0 || j >= parent.children.size()) return;
        pushUndoState();
        parent.children.remove(i);
        parent.children.add(j, n);
        commit();
    }

    /** Indenter = dernier enfant du frère précédent ; désindenter = inverse. */
    private void indent(ProgramNode n, boolean in) {
        ProgramNode parent = parentOf(n);
        int i = indexOf(parent, n);
        if (i < 0) return;
        pushUndoState();
        if (in) {
            if (i == 0) { undoStack.remove(undoStack.size() - 1); return; }
            ProgramNode prev = parent.children.get(i - 1);
            if (!prev.isContainer()) { undoStack.remove(undoStack.size() - 1); return; }
            parent.children.remove(i);
            prev.children.add(n);
            n.parentId = prev.id;
        } else {
            if (parent == root) {
                // remonter à la racine impossible : transformer en frère de 
                // l'élément supérieur n'a pas de sens non plus ; rien à faire
                undoStack.remove(undoStack.size() - 1);
                return;
            }
            ProgramNode grand = findParent(root, parent.id);
            if (grand == null) { undoStack.remove(undoStack.size() - 1); return; }
            int gi = indexOf(grand, parent);
            parent.children.remove(i);
            grand.children.add(gi + 1, n);
            n.parentId = grand.id;
        }
        commit();
    }

    private void duplicate(ProgramNode n) {
        pushUndoState();
        ProgramNode copy = deepCopy(n);
        copy.id = UUID.randomUUID().toString();
        copy.parentId = n.parentId;
        ProgramNode parent = parentOf(n);
        parent.children.add(indexOf(parent, n) + 1, copy);
        commit();
    }

    private ProgramNode deepCopy(ProgramNode n) {
        ProgramNode c = new ProgramNode();
        c.type = n.type;
        c.title = n.title;
        c.payload = n.payload;
        c.hidden = n.hidden;
        c.origin = n.origin;
        c.role = n.role;
        c.parentId = n.parentId;
        for (ProgramNode ch : n.children) {
            ProgramNode cc = deepCopy(ch);
            cc.id = UUID.randomUUID().toString();
            cc.parentId = c.id;
            c.children.add(cc);
        }
        return c;
    }

    private void confirmDelete(ProgramNode n) {
        int kids = countDescendants(n);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.lit_delete_node_title))
                .setMessage(kids > 0
                        ? getString(R.string.lit_delete_node_children, kids)
                        : getString(R.string.lit_delete_node_msg))
                .setPositiveButton("Eny", (d, w) -> {
                    pushUndoState();
                    ProgramNode parent = parentOf(n);
                    parent.children.remove(n);
                    commit();
                })
                .setNegativeButton("Tsia", null)
                .show();
    }

    private int countDescendants(ProgramNode n) {
        int k = 0;
        for (ProgramNode c : n.children) {
            k += 1 + countDescendants(c);
        }
        return k;
    }

    private void showRenameNode(ProgramNode node) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_litorjia_rename, null);
        EditText input = view.findViewById(R.id.inputName);
        input.setText(node.title == null ? "" : node.title);
        input.setHint(node.defaultTitle());
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            pushUndoState();
            node.title = input.getText().toString().trim();
            commit();
            dialog.dismiss();
        });
        dialog.show();
    }

    // =========================================================
    // Ajout d'un élément (n'importe quel niveau, §4.3)
    // =========================================================

    private void showAddMenu(ProgramNode parent, int at) {
        String[] entries = {
                getString(R.string.lit_add_section),
                getString(R.string.lit_add_title),
                getString(R.string.lit_add_hymn),
                getString(R.string.lit_add_bible),
                getString(R.string.lit_add_prayer),
                getString(R.string.lit_add_creed),
                getString(R.string.lit_add_choir),
                getString(R.string.lit_add_text),
                getString(R.string.lit_add_offering),
                getString(R.string.lit_add_custom)};
        String[] types = {
                ProgramNode.TYPE_SECTION, ProgramNode.TYPE_TITLE, ProgramNode.TYPE_HYMN,
                ProgramNode.TYPE_BIBLE, ProgramNode.TYPE_PRAYER, ProgramNode.TYPE_CREED,
                ProgramNode.TYPE_CHOIR, ProgramNode.TYPE_TEXT, ProgramNode.TYPE_OFFERING,
                ProgramNode.TYPE_CUSTOM};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.lit_add_element))
                .setItems(entries, (d, w) -> {
                    pushUndoState();
                    ProgramNode n = new ProgramNode();
                    n.id = UUID.randomUUID().toString();
                    n.parentId = parent.id;
                    n.type = types[Math.min(w, types.length - 1)];
                    n.origin = ProgramNode.ORIGIN_MANUAL;
                    n.expanded = true;
                    if (ProgramNode.TYPE_HYMN.equals(n.type)) {
                        n.payload = NodePayloadParser.hymnPayload(null, null, null, null);
                    } else if (ProgramNode.TYPE_BIBLE.equals(n.type)) {
                        n.payload = NodePayloadParser.biblePayload(
                                new ArrayList<>());
                    } else if (ProgramNode.TYPE_PRAYER.equals(n.type)) {
                        n.payload = NodePayloadParser.prayerPayload(null, null);
                    } else if (ProgramNode.TYPE_CREED.equals(n.type)) {
                        n.payload = NodePayloadParser.creedPayload(
                                TemplateCatalog.defaultCreedVersion(null));
                    }
                    int pos = Math.max(0, Math.min(at, parent.children.size()));
                    parent.children.add(pos, n);
                    commit();
                    openEditor(n);
                })
                .show();
    }

    // =========================================================
    // Rétablir le perikopa (présélection calendar, §4.3)
    // =========================================================

    @Override
    public void restorePerikopa(ProgramNode node) {
        pushUndoState();
        PerikopaService svc = new PerikopaService(this);
        PerikopaDay day = svc.getPerikopaByDate(program.serviceDate);
        if (day == null || day.getVerses() == null || day.getVerses().isEmpty()) {
            node.payload = NodePayloadParser.biblePayload(new ArrayList<>());
            node.origin = ProgramNode.ORIGIN_MANUAL;
            commit();
            Toast.makeText(this, getString(R.string.lit_no_perikopa), Toast.LENGTH_SHORT).show();
            return;
        }
        List<ProgramNode.BibleRef> refs = new ArrayList<>();
        ReferenceResolver resolver = new ReferenceResolver(this);
        for (String v : day.getVerses()) {
            refs.addAll(PerikopaFiller.parse(v, resolver));
        }
        node.payload = NodePayloadParser.biblePayload(refs);
        node.origin = ProgramNode.ORIGIN_CALENDAR;
        commit();
    }

    // =========================================================
    // Menu de la toolbar
    // =========================================================

    private boolean onMenuItem(android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_edit_toggle) {
            editMode = !editMode;
            adapter.setEditMode(editMode);
            item.setTitle(editMode ? R.string.lit_mode_lecture : R.string.lit_mode_edition);
            item.setIcon(editMode ? R.drawable.ic_eye_icon : R.drawable.ic_edit);
            Toast.makeText(this, editMode
                            ? getString(R.string.lit_mode_edition_toast)
                            : getString(R.string.lit_mode_lecture_toast),
                    Toast.LENGTH_SHORT).show();
            refreshHeader();
            return true;
        }
        if (id == R.id.menu_undo) {
            undoLast();
            return true;
        }
        if (id == R.id.menu_numbers) {
            adapter.setShowNumbers(!adapter.isShowNumbers());
            return true;
        }
        if (id == R.id.menu_expand) {
            adapter.expandAll();
            return true;
        }
        if (id == R.id.menu_collapse) {
            adapter.collapseAll();
            return true;
        }
        if (id == R.id.menu_rename) {
            View v = LayoutInflater.from(this).inflate(R.layout.dialog_litorjia_rename, null);
            EditText input = v.findViewById(R.id.inputName);
            input.setText(program.title);
            new AlertDialog.Builder(this)
                    .setView(v)
                    .setPositiveButton("Eny", (d, w) -> {
                        String t = input.getText().toString().trim();
                        if (!t.isEmpty()) {
                            repo.renameProgram(program.id, t, null);
                            program.title = t;
                            root.title = t;
                            refreshHeader();
                        }
                    })
                    .setNegativeButton("Foana", null)
                    .show();
            return true;
        }
        if (id == R.id.menu_export) {
            exportJson();
            return true;
        }
        if (id == R.id.menu_export_copy) {
            copyJson();
            return true;
        }
        if (id == R.id.menu_import_here) {
            startActivityForResult(
                    LitorjiaImportActivity.intentForMerge(this), REQ_PICK_JSON);
            return true;
        }
        if (id == R.id.menu_duplicate_program) {
            Program copy = repo.duplicateProgram(program.id,
                    (program.title == null ? "Fivoriana" : program.title) + " (dika)",
                    program.serviceDate);
            if (copy != null) {
                Toast.makeText(this, getString(R.string.lit_duplicate_done),
                        Toast.LENGTH_SHORT).show();
                LitorjiaDetailActivity.openProgram(this, copy.id);
            }
            return true;
        }
        if (id == R.id.menu_delete_program) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.lit_delete_title)
                    .setMessage(R.string.lit_delete_message)
                    .setPositiveButton("Eny", (d, w) -> {
                        repo.deleteProgram(program.id);
                        Toast.makeText(this, getString(R.string.lit_delete_done),
                                Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .setNegativeButton("Tsia", null)
                    .show();
            return true;
        }
        if (id == R.id.menu_restore_perikopa) {
            refetchPerikopaIntoReadings();
            return true;
        }
        return false;
    }

    /** Remplace les enfants du noeud readings par le perikopa de la date. */
    private void refetchPerikopaIntoReadings() {
        pushUndoState();
        PerikopaFiller.fillReadings(root, this, program, this::commit);
    }

    // =========================================================
    // Export (§4.6 : symétrique de l'import)
    // =========================================================

    private String currentJson() {
        program.root = root;
        return LitorjiaJsonService.export(program);
    }

    private void copyJson() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(
                "litorjia", currentJson()));
        Toast.makeText(this, getString(R.string.lit_copied), Toast.LENGTH_SHORT).show();
    }

    private void exportJson() {
        try {
            java.io.File f = new java.io.File(getCacheDir(),
                    "litorjia-" + safeName(program.title) + ".json");
            java.io.FileWriter w = new java.io.FileWriter(f);
            w.write(currentJson());
            w.close();

            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, "com.fa.baiboly.fileprovider", f);

            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.lit_share_json)));
        } catch (Exception e) {
            copyJson();
        }
    }

    private String safeName(String s) {
        String n = s == null ? "fivoriana" : s.replaceAll("[^A-Za-z0-9]+", "_").toLowerCase(Locale.US);
        if (n.length() > 40) n = n.substring(0, 40);
        return n;
    }

    // =========================================================
    // Import fusionné (retour depuis l'écran importer)
    // =========================================================

    @Override
    @Deprecated
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_JSON
                && resultCode == LitorjiaImportActivity.RESULT_MERGED
                && data != null) {
            String json = data.getStringExtra("json");
            if (json == null || json.isEmpty()) return;
            LitorjiaJsonService.Report r = LitorjiaJsonService.parse(json, repo);
            if (!r.ok || r.root == null) {
                Toast.makeText(this, r.error == null ? "Erreur" : r.error,
                        Toast.LENGTH_LONG).show();
                return;
            }
            pushUndoState();
            for (ProgramNode c : r.root.children) {
                c.parentId = root.id;
                c.position = root.children.size();
                root.children.add(c);
            }
            commit();
        }
    }

    // =========================================================
    // Thème
    // =========================================================

    private void applyGlobalTheme() {
        SharedPreferences prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        int savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);
        ColorManager.applyTheme(this);
    }
}
