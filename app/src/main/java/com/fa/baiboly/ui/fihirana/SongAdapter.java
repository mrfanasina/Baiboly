package com.fa.baiboly.ui.fihirana;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.models.Song;

import java.util.ArrayList;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.VH> {

    public interface OnClick {
        void onClick(Song song);
    }

    private final List<Song> list;
    private final OnClick listener;

    public SongAdapter(List<Song> list, OnClick listener) {
        // 🔥 sécurité: éviter null + permettre update
        this.list = (list != null) ? new ArrayList<>(list) : new ArrayList<>();
        this.listener = listener;
    }

    // =========================
    // VIEW HOLDER
    // =========================
    static class VH extends RecyclerView.ViewHolder {

        TextView tvNumber, tvTitle, tvPreview;

        public VH(View v) {
            super(v);
            tvNumber = v.findViewById(R.id.songNumber);
            tvTitle = v.findViewById(R.id.songTitle);
            tvPreview = v.findViewById(R.id.songPreview);
        }
    }

    // =========================
    // CREATE VIEW
    // =========================
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new VH(v);
    }

    // =========================
    // BIND DATA
    // =========================
    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {

        Song song = list.get(position);

        if (song == null) return;

        holder.tvNumber.setText(String.valueOf(song.getNumber()));
        holder.tvTitle.setText(song.getTitle());

        if (holder.tvPreview != null) {
            // Affiche la catégorie + numéro, ou un extrait du premier couplet si disponible
            String preview = song.getCategory() != null ? song.getCategory().toUpperCase() : "";
            if (song.getFirstVerse() != null && !song.getFirstVerse().trim().isEmpty()) {
                String verse = song.getFirstVerse().trim();
                if (verse.length() > 80) verse = verse.substring(0, 80) + "\u2026";
                preview = preview.isEmpty() ? verse : preview + " \u2022 " + verse;
            }
            holder.tvPreview.setText(preview);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(song);
            }
        });
    }

    // =========================
    // SIZE
    // =========================
    @Override
    public int getItemCount() {
        return list.size();
    }

    // =========================
    // UPDATE LIST (IMPORTANT)
    // =========================
    public void updateList(List<Song> newList) {
        list.clear();
        if (newList != null) {
            list.addAll(newList);
        }
        notifyDataSetChanged();
    }
}