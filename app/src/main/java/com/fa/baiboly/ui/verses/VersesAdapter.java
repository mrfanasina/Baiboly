package com.fa.baiboly.ui.verses;

import android.graphics.text.LineBreaker;
import android.os.Build;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.models.Verse;

import java.util.List;

public class VersesAdapter extends RecyclerView.Adapter<VersesAdapter.ViewHolder> {

    private final List<Verse> verses;

    public VersesAdapter(List<Verse> verses) {
        this.verses = verses;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView number;
        TextView text;

        public ViewHolder(View view) {
            super(view);
            number = view.findViewById(R.id.verseNumber);
            text = view.findViewById(R.id.verseText);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_verse, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Verse verse = verses.get(position);

        // 1. Set the verse number
        holder.number.setText(String.valueOf(verse.getNumber()));

        // 2. Prepare the main text
        String rawText = verse.getText() != null ? verse.getText() : "";
        rawText = rawText.replace("<n>", "<br>").replace("</n>", "");

        // Convert HTML to Spanned
        CharSequence formattedVerse;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            formattedVerse = Html.fromHtml(rawText, Html.FROM_HTML_MODE_LEGACY);
        } else {
            formattedVerse = Html.fromHtml(rawText);
        }

        // 3. Handle the Title if it exists
        if (verse.getTitle() != null && !verse.getTitle().isEmpty()) {
            SpannableStringBuilder finalLayout = new SpannableStringBuilder();

            // Add Title in Bold + two new lines for the space (espace)
            finalLayout.append(verse.getTitle().toUpperCase());
            finalLayout.append("\n\n");

            // Apply Bold Style to the Title part
            finalLayout.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0, verse.getTitle().length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            // Apply Relative Size (make title slightly bigger)
            finalLayout.setSpan(new android.text.style.RelativeSizeSpan(1.1f),
                    0, verse.getTitle().length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            // Add the Verse text after the title
            finalLayout.append(formattedVerse);

            holder.text.setText(finalLayout);
        } else {
            // No title? Just set the verse text
            holder.text.setText(formattedVerse);
        }

        // 4. Justify the text
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            holder.text.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD);
        }
    }
    @Override
    public int getItemCount() {
        return verses != null ? verses.size() : 0;
    }
}