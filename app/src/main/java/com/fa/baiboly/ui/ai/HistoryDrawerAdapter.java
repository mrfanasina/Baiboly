package com.fa.baiboly.ui.ai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ai.ChatHistoryDbHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Liste des conversations sauvegardées, affichée dans le tiroir de droite
 * (burger). Tap = rouvrir ; poubelle = supprimer (avec confirmation via
 * le callback de l'activity).
 */
public class HistoryDrawerAdapter
        extends RecyclerView.Adapter<HistoryDrawerAdapter.HistoryVH> {

    public interface OnOpenListener {
        void onOpen(long conversationId);
    }

    public interface OnDeleteListener {
        void onDelete(ChatHistoryDbHelper.ConversationEntry entry);
    }

    private final List<ChatHistoryDbHelper.ConversationEntry> entries =
            new ArrayList<>();

    private final OnOpenListener openListener;
    private final OnDeleteListener deleteListener;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());

    public HistoryDrawerAdapter(OnOpenListener openListener,
                                OnDeleteListener deleteListener) {
        this.openListener = openListener;
        this.deleteListener = deleteListener;
    }

    /** Remplace le contenu de la liste (rafraîchissement du tiroir). */
    public void submit(List<ChatHistoryDbHelper.ConversationEntry> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HistoryVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.item_history_conversation, parent, false);
        return new HistoryVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryVH holder, int position) {

        ChatHistoryDbHelper.ConversationEntry entry = entries.get(position);

        holder.tvTitle.setText(entry.title);
        holder.tvDate.setText(dateFormat.format(entry.updatedAt));

        holder.itemView.setOnClickListener(v -> {
            if (openListener != null) openListener.onOpen(entry.id);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDelete(entry);
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class HistoryVH extends RecyclerView.ViewHolder {

        final TextView tvTitle;
        final TextView tvDate;
        final ImageButton btnDelete;

        HistoryVH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvHistoryTitle);
            tvDate = itemView.findViewById(R.id.tvHistoryDate);
            btnDelete = itemView.findViewById(R.id.btnDeleteConversation);
        }
    }
}
