package com.fa.baiboly.data;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapter for the home card order list in settings.
 * Supports drag-and-drop reordering via ItemTouchHelper.
 */
public class CardOrderAdapter extends RecyclerView.Adapter<CardOrderAdapter.ViewHolder> {

    private List<String> cardIds;
    private final List<String> cardLabels;
    private OnOrderChangedListener listener;

    public interface OnOrderChangedListener {
        void onOrderChanged(List<String> newOrder);
    }

    public CardOrderAdapter(List<String> cardIds, OnOrderChangedListener listener) {
        this.cardIds = new ArrayList<>(cardIds);
        this.cardLabels = new ArrayList<>();
        this.listener = listener;

        // Map IDs to display labels
        for (String id : cardIds) {
            cardLabels.add(getLabel(id));
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_card_order, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.textCardName.setText(cardLabels.get(position));
    }

    @Override
    public int getItemCount() {
        return cardIds.size();
    }

    /**
     * Move item from one position to another (for drag-and-drop)
     */
    public void moveItem(int fromPosition, int toPosition) {
        Collections.swap(cardIds, fromPosition, toPosition);
        Collections.swap(cardLabels, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);

        if (listener != null) {
            listener.onOrderChanged(new ArrayList<>(cardIds));
        }
    }

    /**
     * Get the current card order
     */
    public List<String> getCurrentOrder() {
        return new ArrayList<>(cardIds);
    }

    /**
     * Map card ID to display label
     */
    private static String getLabel(String id) {
        switch (id) {
            case "aina": return "Aina sy Fahasalamana";
            case "mofonaina": return "Mofonaina";
            case "reading": return "Vakiteny anio";
            case "perikopa": return "Perikopa";
            case "history": return "Novakiana farany";
            default: return id;
        }
    }

    /**
     * Get default card order
     */
    public static List<String> getDefaultOrder() {
        List<String> order = new ArrayList<>();
        order.add("mofonaina");
        order.add("reading");
        order.add("aina");
        order.add("perikopa");
        order.add("history");
        return order;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textCardName;

        ViewHolder(View itemView) {
            super(itemView);
            textCardName = itemView.findViewById(R.id.textCardName);
        }
    }

    /**
     * ItemTouchHelper.Callback for drag-and-drop
     */
    public static class CardTouchHelperCallback extends ItemTouchHelper.Callback {

        private final CardOrderAdapter adapter;

        public CardTouchHelperCallback(CardOrderAdapter adapter) {
            this.adapter = adapter;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
            return makeMovementFlags(dragFlags, 0);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView,
                              RecyclerView.ViewHolder viewHolder,
                              RecyclerView.ViewHolder target) {
            adapter.moveItem(viewHolder.getAdapterPosition(), target.getAdapterPosition());
            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            // No swipe action
        }
    }
}
