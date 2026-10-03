package com.fa.baiboly.ui.baiboly;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter used to display verses for selection.
 *
 * Features:
 * - Supports current chapter + next chapter
 * - Displays a visual separator/header for next chapter
 * - Applies different styling for next chapter verses
 * - Prevents invalid selections before start verse
 */
public class VerseNumberAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /**
     * View type for normal verse items.
     */
    private static final int TYPE_VERSE = 0;

    /**
     * View type for chapter separator/header.
     */
    private static final int TYPE_HEADER = 1;

    /**
     * Represents one RecyclerView item.
     * Can be either:
     * - a verse item
     * - a header item
     */
    private static class Item {

        boolean isHeader;
        int verse;
        String title;

        Item(boolean isHeader, int verse, String title) {
            this.isHeader = isHeader;
            this.verse = verse;
            this.title = title;
        }
    }

    /**
     * Callback used when a verse is selected.
     */
    public interface OnVerseClick {

        /**
         * Called when a verse is selected.
         *
         * @param chapterOffset 0 = current chapter, 1 = next chapter
         * @param verse selected verse number
         */
        void onClick(int chapterOffset, int verse);
    }
    private final List<Item> items = new ArrayList<>();
    private final OnVerseClick listener;

    /**
     * Minimum selectable verse.
     * Used to prevent selecting verses before start verse.
     */
    private int minVerse = 1;

    /**
     * Currently selected item position.
     */
    private int selectedPosition = -1;

    /**
     * Index where next chapter begins.
     * Used for styling and chapter detection.
     */
    private int splitIndex = -1;

    /**
     * Constructor.
     *
     * @param currentChapterVerseCount Number of verses in current chapter
     * @param nextChapterVerseCount Number of verses in next chapter
     * @param nextChapterNumber Next chapter number
     * @param listener Click listener
     */
    public VerseNumberAdapter(
            int currentChapterVerseCount,
            int nextChapterVerseCount,
            int nextChapterNumber,
            OnVerseClick listener
    ) {

        this.listener = listener;

        // Add current chapter verses
        for (int i = 1; i <= currentChapterVerseCount; i++) {
            items.add(new Item(false, i, null));
        }

        // Add next chapter if available
        if (nextChapterVerseCount > 0) {

            // Save separator index
            splitIndex = items.size();

            // Add header item
            items.add(new Item(
                    true,
                    -1,
                    "Toko faha " + nextChapterNumber
            ));

            // Add next chapter verses
            for (int i = 1; i <= nextChapterVerseCount; i++) {
                items.add(new Item(false, i, null));
            }
        }
    }

    /**
     * Defines the minimum selectable verse.
     *
     * Example:
     * If start verse is 10,
     * verses 1-9 become disabled.
     */
    public void setMinVerse(int minVerse) {

        this.minVerse = minVerse + 1;

        // Reset current selection
        selectedPosition = -1;

        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {

        return items.get(position).isHeader
                ? TYPE_HEADER
                : TYPE_VERSE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        LayoutInflater inflater =
                LayoutInflater.from(parent.getContext());

        // Create header view
        if (viewType == TYPE_HEADER) {

            View view = inflater.inflate(
                    R.layout.item_header,
                    parent,
                    false
            );

            return new HeaderHolder(view);
        }

        // Create verse item view
        View view = inflater.inflate(
                R.layout.item_chapter,
                parent,
                false
        );

        return new VerseHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position
    ) {

        Item item = items.get(position);

        /**
         * =========================
         * HEADER ITEM
         * =========================
         */
        if (holder instanceof HeaderHolder) {

            HeaderHolder headerHolder =
                    (HeaderHolder) holder;

            headerHolder.text.setText(item.title);

            return;
        }

        /**
         * =========================
         * VERSE ITEM
         * =========================
         */
        VerseHolder verseHolder =
                (VerseHolder) holder;

        int verse = item.verse;

        verseHolder.text.setText(String.valueOf(verse));

        /**
         * Detect whether this verse belongs
         * to the next chapter.
         */
        boolean isNextChapter =
                splitIndex != -1 &&
                        position > splitIndex;

        /**
         * Apply chapter-specific styling (NO HARDCODED COLORS).
         * We rely on default theme + drawable styling.
         */
        if (isNextChapter) {

            verseHolder.text.setBackgroundResource(
                    R.drawable.bg_next_chapter_item
            );

        } else {

            // Default style (theme-based)
            verseHolder.text.setBackgroundResource(0);
        }

        /**
         * Disable verses before selected start verse.
         */
        if (!isNextChapter && verse < minVerse) {

            verseHolder.itemView.setAlpha(0.3f);
            verseHolder.itemView.setEnabled(false);
            verseHolder.itemView.setClickable(false);

            return;
        }

        /**
         * Enable valid verse item.
         */
        verseHolder.itemView.setAlpha(1f);
        verseHolder.itemView.setEnabled(true);
        verseHolder.itemView.setClickable(true);

        /**
         * Highlight selected item.
         */
        if (selectedPosition == position) {

            verseHolder.text.setBackgroundResource(
                    R.drawable.bg_selected_item
            );
            verseHolder.text.setTextColor(Color.WHITE);

        }

        /**
         * Handle verse selection.
         */
        verseHolder.itemView.setOnClickListener(v -> {

            selectedPosition = position;

            notifyDataSetChanged();

            if (isNextChapter) {

                // Next chapter verse
                listener.onClick(1, verse);

            } else {

                // Current chapter verse
                listener.onClick(0, verse);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * ViewHolder for verse items.
     */
    static class VerseHolder extends RecyclerView.ViewHolder {

        TextView text;

        VerseHolder(@NonNull View itemView) {
            super(itemView);

            text = itemView.findViewById(R.id.txtChapter);
        }
    }

    /**
     * ViewHolder for chapter header items.
     */
    static class HeaderHolder extends RecyclerView.ViewHolder {

        TextView text;

        HeaderHolder(@NonNull View itemView) {
            super(itemView);

            text = itemView.findViewById(R.id.txtHeader);
        }
    }
}