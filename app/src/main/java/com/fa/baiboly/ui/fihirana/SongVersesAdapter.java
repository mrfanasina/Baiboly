package com.fa.baiboly.ui.fihirana;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.models.SongVerse;
import com.google.android.material.color.MaterialColors;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter des versets d'un chant. La position 0 est l'en-tête cliquable
 * ("Fihirana FFPM | 49") qui scrolle avec les paroles ; le tap ouvre le
 * pad de sélection d'un autre chant.
 *
 * Comme dans la Bible : le tap sur un verset le sélectionne (fond
 * surligné), le tap à nouveau le désélectionne. L'activity hôte reçoit
 * chaque changement via OnSelectionChangedListener pour piloter sa barre
 * flottante (Copier / Fermer).
 */
public class SongVersesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** Variante italique du serif (fiverenany), créée une seule fois. */
    private static final Typeface SERIF_ITALIC =
            Typeface.create(Typeface.SERIF, Typeface.ITALIC);

    /** Types de vue : en-tête cliquable (position 0) puis les versets. */
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_VERSE = 1;

    /** Callback quand l'utilisateur tape l'en-tête (changement de chant). */
    public interface OnHeaderClickListener {
        void onHeaderClick();
    }

    /** Callback quand la sélection de versets change. */
    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    private final List<SongVerse> verses;
    private final String songId;
    private final OnHeaderClickListener headerClickListener;
    private final OnSelectionChangedListener selectionListener;

    /** Versets sélectionnés (index dans la liste, cf. onItemClick). */
    private final Set<Integer> selected = new HashSet<>();

    /** Fond arrondi du surlignage de sélection (créé une fois). */
    private GradientDrawable selectedBg;

    // Dynamic text scaling factor (controlled by Activity / user settings)
    private final float textSize;

    /**
     * Constructor with text scaling support
     * @param verses list of song verses
     * @param textSize global scaling factor for text size
     * @param songId id du chant affiché dans l'en-tête (ex. "ffpm_49")
     * @param headerClickListener callback du tap sur l'en-tête
     * @param selectionListener callback des changements de sélection
     */
    public SongVersesAdapter(List<SongVerse> verses,
                             float textSize,
                             String songId,
                             OnHeaderClickListener headerClickListener,
                             @Nullable OnSelectionChangedListener selectionListener) {
        this.verses = verses;
        this.textSize = textSize;
        this.songId = songId;
        this.headerClickListener = headerClickListener;
        this.selectionListener = selectionListener;
    }

    /** Constructeur de compatibilité (aucun suivi de sélection). */
    public SongVersesAdapter(List<SongVerse> verses,
                             float textSize,
                             String songId,
                             OnHeaderClickListener headerClickListener) {
        this(verses, textSize, songId, headerClickListener, null);
    }

    /** Nombre de versets actuellement sélectionnés. */
    public int getSelectedCount() {
        return selected.size();
    }

    /** Versements sélectionnés, dans l'ordre d'affichage. */
    public List<SongVerse> getSelectedVerses() {
        List<SongVerse> out = new java.util.ArrayList<>();
        for (int i = 0; i < (verses != null ? verses.size() : 0); i++) {
            if (selected.contains(i)) out.add(verses.get(i));
        }
        return out;
    }

    /** Vide la sélection et rafraîchit les lignes concernées. */
    public void clearSelection() {
        if (selected.isEmpty()) return;
        java.util.Set<Integer> previous = new java.util.HashSet<>(selected);
        selected.clear();
        for (Integer idx : previous) {
            notifyItemChanged(idx + 1);
        }
        if (selectionListener != null) selectionListener.onSelectionChanged(0);
    }

    /** ViewHolder pattern for performance optimization
     * Holds references to UI components for each item */
    static class VH extends RecyclerView.ViewHolder {
        TextView tvNumber, tvText;

        public VH(View v) {
            super(v);
            tvNumber = v.findViewById(R.id.verseNumber);
            tvText = v.findViewById(R.id.verseText);
        }
    }

    /** ViewHolder de l'en-tête cliquable ("Fihirana FFPM | 49"). */
    static class HeaderVH extends RecyclerView.ViewHolder {
        final View pill;
        final TextView category, number;
        final View divider, numberCell;

        HeaderVH(@NonNull View itemView) {
            super(itemView);
            pill = itemView.findViewById(R.id.songHeader);
            category = itemView.findViewById(R.id.headerCategory);
            number = itemView.findViewById(R.id.headerNumber);
            divider = itemView.findViewById(R.id.headerDivider);
            numberCell = itemView.findViewById(R.id.headerNumberCell);
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_HEADER) {
            HeaderVH holder = new HeaderVH(
                    inflater.inflate(R.layout.item_song_header, parent, false));
            bindHeader(holder);
            return holder;
        }

        // Inflate item layout for each verse row
        View v = inflater.inflate(R.layout.item_verse, parent, false);
        return new VH(v);
    }

    /** Remplit l'en-tête : catégorie + numéro du chant (ex. "FFPM | 49"). */
    private void bindHeader(HeaderVH holder) {
        // "ffpm_49" -> catégorie "FFPM", numéro "49"
        String categoryPart = "";
        String numberPart = "";
        if (songId != null && !songId.isEmpty()) {
            String[] parts = songId.split("_");
            categoryPart = parts[0].toUpperCase();
            if (parts.length > 1) numberPart = parts[1];
        }

        holder.category.setText(categoryPart.isEmpty() ? "" : " " + categoryPart);

        if (numberPart.isEmpty()) {
            // Pas de numéro connu : masquer le séparateur et la cellule
            holder.divider.setVisibility(View.GONE);
            holder.numberCell.setVisibility(View.GONE);
        } else {
            holder.number.setText(numberPart);
        }

        // Couleur principale du thème (overlays ColorManager inclus)
        int primary = MaterialColors.getColor(holder.itemView,
                androidx.appcompat.R.attr.colorPrimary);

        // Fond arrondi : simple teinte de la couleur principale (moins vive)
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setShape(GradientDrawable.RECTANGLE);
        pillBg.setCornerRadius(dp(holder.itemView, 16));
        pillBg.setColor(MaterialColors.compositeARGBWithAlpha(primary, 46));
        holder.pill.setBackground(pillBg);

        // Séparateur discret (alpha réduit)
        holder.divider.setBackgroundColor(
                MaterialColors.compositeARGBWithAlpha(primary, 90));

        // Clic sur l'en-tête -> sélection d'un autre chant (pad numérique)
        holder.pill.setOnClickListener(v -> {
            if (headerClickListener != null) headerClickListener.onHeaderClick();
        });
    }

    /** Conversion dp -> px. */
    private static float dp(View view, int dp) {
        return dp * view.getResources().getDisplayMetrics().density;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderVH) return; // En-tête déjà lié à la création

        VH vh = (VH) holder;
        int verseIndex = position - 1;
        SongVerse verse = verses.get(verseIndex);

        // -----------------------------
        // APPLY DYNAMIC TEXT SIZE
        // -----------------------------
        // Base sizes (in SP) scaled dynamically

        // Taille des versets : réglage utilisateur (Paramètres -> taille du
        // texte), avec un bonus de 20 % sur le texte (paroles plus grandes
        // que la valeur de base, reste modulable par le curseur 12-40 sp).
        vh.tvText.setTextSize(textSize * 1.4f);
        vh.tvNumber.setTextSize(textSize);

        // -----------------------------
        // POLICE SERIF (comme les livres) + STYLE
        // -----------------------------
        // Le XML pose fontFamily="serif" ; setTypeface(null, style) dans
        // l'ancien code l'écrasait par la police par défaut : on repose donc
        // la famille explicitement à chaque bind (fiverenany = italique).
        vh.tvText.setTypeface(
                verse.getFiverenany() ? SERIF_ITALIC : Typeface.SERIF);

        // -----------------------------
        // DETERMINE VERSE TYPE
        // -----------------------------

        // Check if it's a chorus (fiverenany)
        if (verse.getFiverenany()) {

            // Chorus style
            vh.tvNumber.setText("Fiv:");
            vh.tvNumber.setVisibility(View.VISIBLE);

        } else if (verse.getAndininy() == 0) {

            // Special case: no verse number
            vh.tvNumber.setVisibility(View.GONE);

        } else {

            // Normal verse style
            vh.tvNumber.setText(verse.getAndininy() + ".");
            vh.tvNumber.setVisibility(View.VISIBLE);
        }

        // -----------------------------
        // SET VERSE CONTENT
        // -----------------------------
        vh.tvText.setText(verse.getTononkira());

        // -----------------------------
        // SÉLECTION AU TAP (comme dans la Bible)
        // -----------------------------
        vh.itemView.setOnClickListener(v -> {
            boolean isSelected = selected.contains(verseIndex);
            if (isSelected) {
                selected.remove(verseIndex);
            } else {
                selected.add(verseIndex);
            }
            notifyItemChanged(verseIndex + 1);
            if (selectionListener != null) {
                selectionListener.onSelectionChanged(selected.size());
            }
        });

        applySelectionVisual(vh, selected.contains(verseIndex));
    }

    /** Surlignage du verset sélectionné (fond arrondi, teinte primaire). */
    private void applySelectionVisual(VH vh, boolean isSelected) {
        if (selectedBg == null) {
            int primary = MaterialColors.getColor(vh.itemView,
                    androidx.appcompat.R.attr.colorPrimary);
            selectedBg = new GradientDrawable();
            selectedBg.setShape(GradientDrawable.RECTANGLE);
            selectedBg.setCornerRadius(dp(vh.itemView, 12));
            selectedBg.setColor(
                    MaterialColors.compositeARGBWithAlpha(primary, 40));
        }
        vh.itemView.setBackground(isSelected ? selectedBg : null);
    }

    @Override
    public int getItemCount() {
        return (verses != null ? verses.size() : 0) + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_HEADER : TYPE_VERSE;
    }
}
