package com.fa.baiboly.ui.fanekena;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.models.Fanekena;

import java.util.List;

public class CodeAdapter extends RecyclerView.Adapter<CodeAdapter.VH> {

    public interface OnClick {
        void onClick(String code);
    }

    private final List<Fanekena> list;
    private final OnClick listener;

    public CodeAdapter(List<Fanekena> list, OnClick listener) {
        this.list = list;
        this.listener = listener;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView badge;
        TextView text;
        TextView preview;

        public VH(View v) {
            super(v);
            badge = v.findViewById(R.id.codeBadge);
            text = v.findViewById(R.id.codeName);
            preview = v.findViewById(R.id.codePreview);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_code, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Fanekena item = list.get(position);
        String code = item.getCode();

        // Titre du credo (colonne titre), sinon nom deduit du code
        holder.text.setText(getItemTitle(item));

        // Numero seul dans le badge ("V12" -> "12")
        holder.badge.setText(getBadgeText(code));

        // Extrait du debut du credo
        holder.preview.setText(buildPreview(item.getText()));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(code);
        });
    }

    /**
     * Titre a afficher : la colonne titre du credo si elle est renseignee,
     * sinon le nom deduit du code.
     */
    public static String getItemTitle(Fanekena item) {
        if (item == null) return "Fanekena";

        String titre = item.getTitre();
        if (titre != null && !titre.trim().isEmpty()) {
            return titre.trim();
        }

        return getDisplayName(item.getCode());
    }

    /**
     * Convertit le code en nom d'affichage lisible.
     * Ex: "V1" -> "Fanekem-pinoana 1"
     *     "ABC" -> "ABC" (si pas de format reconnu)
     */
    public static String getDisplayName(String code) {
        if (code == null) return "Fanekena";

        String upper = code.toUpperCase();

        // Pattern: lettre(s) + chiffre(s) -> "Fanekem-pinoana X"
        if (upper.matches("^[A-Z]+\\d+$")) {
            String number = upper.replaceAll("^[A-Z]+", "");
            return "Fanekem-pinoana " + number;
        }

        // Sinon, retourner tel quel
        return code;
    }

    /**
     * Texte court du badge : les chiffres du code, sinon les 2 premiers caracteres.
     */
    private String getBadgeText(String code) {
        if (code == null) return "";
        String digits = code.replaceAll("\\D", "");
        if (!digits.isEmpty()) return digits;
        return code.length() > 2 ? code.substring(0, 2) : code;
    }

    /**
     * Aplatit le texte (retours a la ligne/espaces) et le tronque pour l'extrait.
     */
    private String buildPreview(String text) {
        if (text == null) return "";
        String flat = text.replaceAll("\\s+", " ").trim();
        if (flat.length() > 100) {
            flat = flat.substring(0, 100).trim() + "…";
        }
        return flat;
    }

    @Override
    public int getItemCount() {
        return list.size();
    }
}
