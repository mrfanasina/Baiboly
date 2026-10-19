package com.fa.baiboly.ui.baiboly;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.models.Book;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.ViewHolder> {

    private final List<Book> books;
    private final OnBookClickListener listener;

    public interface OnBookClickListener {
        void onClick(Book book);
    }

    public BookAdapter(List<Book> books, OnBookClickListener listener) {
        this.books = books;
        this.listener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtBookName;
        MaterialCardView card;

        public ViewHolder(View view) {
            super(view);
            txtBookName = view.findViewById(R.id.txtBookName);
            card = (MaterialCardView) view;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_book, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Book book = books.get(position);

        // 🔤 Nom court (GEN, EXO…)
        holder.txtBookName.setText(book.getLongName());

        // 🎨 Couleur dynamique depuis DB
        applyColor(holder, book.getColor());

        // 🖱 Click
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(book);
            }
        });
    }

    // =========================
    // 🎨 Gestion couleur propre
    // =========================
    private void applyColor(ViewHolder holder, String colorString) {
        try {
            if (colorString != null && !colorString.trim().isEmpty()) {

                int color = Color.parseColor(colorString.trim());

                // Effet pastel léger
                int bgColor = Color.argb(
                        30,
                        Color.red(color),
                        Color.green(color),
                        Color.blue(color)
                );

                holder.card.setCardBackgroundColor(bgColor);

                // Style clean
                holder.card.setStrokeWidth(0);
                holder.card.setCardElevation(0);
                holder.card.setMaxCardElevation(0);

            } else {
                resetCard(holder);
            }

        } catch (Exception e) {
            resetCard(holder);
        }
    }

    // =========================
    // 🔄 Reset visuel (safe)
    // =========================
    private void resetCard(ViewHolder holder) {
        holder.card.setCardBackgroundColor(Color.TRANSPARENT);
        holder.card.setStrokeWidth(0);
        holder.card.setCardElevation(0);
        holder.card.setMaxCardElevation(0);
    }

    @Override
    public int getItemCount() {
        return books.size();
    }
}