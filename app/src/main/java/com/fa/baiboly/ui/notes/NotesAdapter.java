package com.fa.baiboly.ui.notes;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.models.Note;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 🗒️ Liste des notes : bandeau couleur, référence de la lecture, titre
 * + extrait, horodatage, épinglage. Tap = éditer, tap long = menu.
 */
public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.VH> {

    public interface Listener {
        void onNoteClicked(Note note);

        void onNoteLongClicked(Note note);
    }

    private final List<Note> items = new ArrayList<>();
    private final Listener listener;

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault());

    public NotesAdapter(Listener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItems(List<Note> notes) {
        items.clear();
        if (notes != null) items.addAll(notes);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        if (position < 0 || position >= items.size()) return;
        final Note n = items.get(position);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onNoteClicked(n);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onNoteLongClicked(n);
            return true;
        });

        // 🎨 Bandeau couleur (défaut gris si null)
        try {
            int argb = n.color == null
                    ? 0xFF808080 : Color.parseColor(n.color);
            h.colorBar.getBackground().setTint(argb);
        } catch (Exception ignored) {
        }

        h.textNoteRef.setVisibility(n.refLabel == null || n.refLabel.isEmpty()
                ? View.GONE : View.VISIBLE);
        h.textNoteRef.setText(n.refLabel);

        String[] ts = com.fa.baiboly.data.notes.richtext.RichTextCodec
                .titleAndSnippet(n.bodyJson, n.isAttachable());
        h.textNoteTitle.setText(ts[0].isEmpty() ? "Fanamarihana" : ts[0]);
        h.textNoteSnippet.setText(ts[1]);
        h.textNoteSnippet.setVisibility(ts[1].isEmpty() ? View.GONE : View.VISIBLE);

        h.textNoteDate.setText(DATE_FMT.format(new Date(n.updatedAt)));
        h.textNotePin.setVisibility(n.pinned ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class VH extends RecyclerView.ViewHolder {
        View colorBar;
        TextView textNoteRef, textNoteTitle, textNoteSnippet, textNoteDate, textNotePin;

        VH(@NonNull View v) {
            super(v);
            colorBar = v.findViewById(R.id.colorBar);
            textNoteRef = v.findViewById(R.id.textNoteRef);
            textNoteTitle = v.findViewById(R.id.textNoteTitle);
            textNoteSnippet = v.findViewById(R.id.textNoteSnippet);
            textNoteDate = v.findViewById(R.id.textNoteDate);
            textNotePin = v.findViewById(R.id.textNotePin);
        }
    }
}
