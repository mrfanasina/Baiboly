package com.fa.baiboly.ui.dictionary;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.models.DictionaryOccurrence;
import com.fa.baiboly.ui.common.GlassCardHelper;

import java.util.ArrayList;
import java.util.List;

/** Liste des versets où une entrée du dictionnaire apparaît. */
public class DictionaryOccurrenceAdapter
        extends RecyclerView.Adapter<DictionaryOccurrenceAdapter.ViewHolder> {

    public interface OnOccurrenceClickListener {
        void onOccurrenceClick(DictionaryOccurrence occurrence);
    }

    private final List<DictionaryOccurrence> items = new ArrayList<>();
    private final OnOccurrenceClickListener listener;

    public DictionaryOccurrenceAdapter(OnOccurrenceClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<DictionaryOccurrence> occurrences) {
        items.clear();
        if (occurrences != null) {
            items.addAll(occurrences);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dictionary_verse, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DictionaryOccurrence occurrence = items.get(position);
        holder.reference.setText(occurrence.getReference());

        GlassCardHelper.styleRow(holder.itemView, false);

        int gap = Math.round(6 * holder.itemView.getResources()
                .getDisplayMetrics().density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, gap);
        holder.itemView.setLayoutParams(lp);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOccurrenceClick(occurrence);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView reference;

        ViewHolder(View view) {
            super(view);
            reference = view.findViewById(R.id.tvOccurrenceRef);
        }
    }
}
