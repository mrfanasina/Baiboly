package com.fa.baiboly.ui.dictionary;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.models.DictionaryEntry;

import java.util.ArrayList;
import java.util.List;

/** Liste alphabétique des mots du dictionnaire, avec aperçu de la définition. */
public class DictionaryAdapter extends RecyclerView.Adapter<DictionaryAdapter.ViewHolder> {

    public interface OnEntryClickListener {
        void onEntryClick(DictionaryEntry entry);
    }

    private final List<DictionaryEntry> items = new ArrayList<>();
    private final OnEntryClickListener listener;

    public DictionaryAdapter(OnEntryClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<DictionaryEntry> entries) {
        items.clear();
        if (entries != null) {
            items.addAll(entries);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dictionary, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DictionaryEntry entry = items.get(position);

        holder.headword.setText(entry.getHeadword());

        String qualifier = entry.getQualifier();
        if (qualifier != null && !qualifier.trim().isEmpty()) {
            holder.qualifier.setText(qualifier);
            holder.qualifier.setVisibility(View.VISIBLE);
        } else {
            holder.qualifier.setVisibility(View.GONE);
        }

        holder.definition.setText(entry.getPreview(180));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEntryClick(entry);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView headword;
        final TextView qualifier;
        final TextView definition;

        ViewHolder(View view) {
            super(view);
            headword = view.findViewById(R.id.tvDictHeadword);
            qualifier = view.findViewById(R.id.tvDictQualifier);
            definition = view.findViewById(R.id.tvDictDefinition);
        }
    }
}
