package com.fa.baiboly.ui.litorjia;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.data.litorjia.ProgramNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Arbre stocké en parent/position, aplati en liste (FlatNode, §6.2) :
 * plier une section retire ses descendants. Affiche icône par type,
 * titre, résumé, numérotation hiérarchique et pastille « à compléter ».
 */
class TreeFlatAdapter extends RecyclerView.Adapter<TreeFlatAdapter.VH> {

    interface Listener {
        void onNodeTap(ProgramNode node);

        void onNodeLongTap(ProgramNode node);
    }

    /** Nœud + profondeur (liste aplatie). */
    static class FlatNode {
        final ProgramNode node;
        final int depth;

        FlatNode(ProgramNode node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }

    private final List<FlatNode> flat = new ArrayList<>();
    private final ProgramNode root;
    private final Set<String> collapsedIds = new HashSet<>();
    private final Listener listener;
    private boolean showNumbers = false;

    TreeFlatAdapter(ProgramNode root, Listener listener) {
        this.root = root;
        this.listener = listener;
        rebuild();
    }

    void setShowNumbers(boolean b) {
        showNumbers = b;
        notifyDataSetChanged();
    }

    boolean isShowNumbers() {
        return showNumbers;
    }

    void toggleExpand(ProgramNode n) {
        if (!n.isContainer() || n.children.isEmpty()) return;
        if (collapsedIds.contains(n.id)) collapsedIds.remove(n.id);
        else collapsedIds.add(n.id);
        rebuild();
    }

    void setExpanded(ProgramNode n, boolean expand) {
        if (expand) collapsedIds.remove(n.id);
        else if (n.isContainer() && n.children.isEmpty()) return;
        else collapsedIds.add(n.id);
        rebuild();
    }

    void expandAll() {
        collapsedIds.clear();
        rebuild();
    }

    void collapseAll() {
        collapsedIds.clear();
        markCollapsed(root);
        rebuild();
    }

    private void markCollapsed(ProgramNode n) {
        if (n.isContainer() && !n.children.isEmpty()) collapsedIds.add(n.id);
        for (ProgramNode c : n.children) markCollapsed(c);
    }

    void rebuild() {
        flat.clear();
        if (root != null) walk(root, 0);
        notifyDataSetChanged();
    }

    private void walk(ProgramNode n, int depth) {
        for (ProgramNode c : n.children) {
            boolean hidden = c.hidden && (!showHidden);
            if (!hidden) flat.add(new FlatNode(c, depth));
            if (c.isContainer() && !collapsedIds.contains(c.id)) walk(c, depth + 1);
        }
    }

    private boolean showHidden = false;

    void setShowHidden(boolean show) {
        showHidden = show;
        rebuild();
    }

    boolean isCollapsed(ProgramNode n) {
        return collapsedIds.contains(n.id);
    }

    /** Raffraîchit des données modifiées (sauvegarde faite par l'activité). */
    void refresh() {
        HashSet<String> keep = new HashSet<>(collapsedIds);
        collapsedIds.clear();
        collapsedIds.addAll(keep);
        rebuild();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_litorjia_node, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        FlatNode fn = flat.get(position);
        ProgramNode n = fn.node;

        // Indentation proportionnelle (plafonnée à 5 niveaux, §4.4)
        int indent = Math.min(fn.depth, 5) * 28;
        final int widthPx = (int) (indent * h.itemView.getResources().getDisplayMetrics().density) + 1;
        h.indentArea.setMinimumWidth(widthPx);

        // Chevron
        boolean expandable = n.isContainer() && !n.children.isEmpty();
        h.imgChevron.setVisibility(expandable ? View.VISIBLE : View.INVISIBLE);
        h.imgChevron.animate().rotation(expandable && !isCollapsed(n) ? 90f : 0f).setDuration(120).start();

        // Icône type
        h.imgIcon.setImageResource(n.iconRes());
        DrawableCompat.setTint(h.imgIcon.getDrawable(),
                ContextCompat.getColor(h.itemView.getContext(), R.color.lit_icon_tint));

        // Numérotation hiérarchique (optionnelle)
        if (showNumbers) {
            h.textNumber.setVisibility(View.VISIBLE);
            h.textNumber.setText(numberOf(position));
        } else {
            h.textNumber.setVisibility(View.GONE);
        }

        // Titre + style (section = titre de niveau)
        h.textTitle.setText(n.displayTitle());
        boolean hasTitle = n.title != null && !n.title.trim().isEmpty();
        if (hasTitle && n.type.equals(ProgramNode.TYPE_SECTION)) {
            h.textTitle.setTypeface(h.textTitle.getTypeface(), Typeface.BOLD);
            h.textTitle.setTextSize(15.5f);
        } else if (n.type.equals(ProgramNode.TYPE_TITLE)) {
            h.textTitle.setTypeface(h.textTitle.getTypeface(), Typeface.BOLD);
            h.textTitle.setTextSize(14.5f);
        } else {
            h.textTitle.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            h.textTitle.setTextSize(15f);
        }

        // Résumé
        String s = n.summary();
        h.textSummary.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
        h.textSummary.setText(s);

        // Pastille « à compléter »
        boolean incomplete = n.isIncomplete();
        h.badgeTodo.setVisibility(incomplete ? View.VISIBLE : View.GONE);
        if (incomplete) h.badgeTodo.setText(R.string.lit_badge_todo);

        // Noeud masqué : ligne atténuée (visible seulement en édition)
        h.itemView.setAlpha(n.hidden ? 0.45f : 1f);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onNodeTap(n);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onNodeLongTap(n);
            return true;
        });
    }

    /** Numérotation 1, 1.1, 1.2.1 calculée par branche. */
    private String numberOf(int position) {
        // Simple : on recalcule à la volée en remontant sur l'arbre réel
        FlatNode fn = flat.get(position);
        StringBuilder sb = new StringBuilder();
        buildNumber(root, fn.node, new int[16], 0, sb);
        return sb.toString();
    }

    private boolean buildNumber(ProgramNode cur, ProgramNode target, int[] counters,
                                int depth, StringBuilder sb) {
        for (int i = 0; i < cur.children.size(); i++) {
            ProgramNode c = cur.children.get(i);
            counters[depth] = i + 1;
            if (c == target) {
                for (int d = 0; d <= depth; d++) {
                    if (d > 0) sb.append('.');
                    sb.append(counters[d]);
                }
                return true;
            }
            if (buildNumber(c, target, counters, depth + 1, sb)) return true;
        }
        return false;
    }

    @Override
    public int getItemCount() { return flat.size(); }

    ProgramNode nodeAt(int position) {
        return flat.get(position).node;
    }

    static class VH extends RecyclerView.ViewHolder {
        View indentArea;
        ImageView imgChevron, imgIcon;
        TextView textNumber, textTitle, textSummary, badgeTodo;

        VH(@NonNull View v) {
            super(v);
            indentArea = v.findViewById(R.id.indentArea);
            imgChevron = v.findViewById(R.id.imgChevron);
            imgIcon = v.findViewById(R.id.imgIcon);
            textNumber = v.findViewById(R.id.textNumber);
            textTitle = v.findViewById(R.id.textTitle);
            textSummary = v.findViewById(R.id.textSummary);
            badgeTodo = v.findViewById(R.id.badgeTodo);
        }
    }
}
