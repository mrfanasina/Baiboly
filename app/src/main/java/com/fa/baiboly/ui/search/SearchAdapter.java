package com.fa.baiboly.ui.search;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.models.GlobalSearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_VERSET = 0;
    private static final int TYPE_CHANT = 1;
    private static final int TYPE_FANEKENA = 2;
    private static final int TYPE_IA_CTA = 3;

    private static final int HIGHLIGHT_COLOR = 0xFFE53935; // rouge accent, ajuste selon ton thème

    private final List<GlobalSearchResult> items = new ArrayList<>();
    private final OnResultClickListener listener;
    private String currentQuery = "";

    public interface OnResultClickListener {
        void onVerseClick(GlobalSearchResult item);
        void onSongClick(GlobalSearchResult item);
        void onFanekenaClick(GlobalSearchResult item);
        void onAiCardClick(String query);
    }

    public SearchAdapter(OnResultClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<GlobalSearchResult> newItems, String query, boolean addAiCta) {
        this.currentQuery = query;
        items.clear();
        items.addAll(newItems);
        if (addAiCta && query != null && query.trim().length() >= 2) {
            items.add(new GlobalSearchResult(GlobalSearchResult.Type.IA_CTA, null, null, null, null, null));
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        switch (items.get(position).getType()) {
            case CHANT: return TYPE_CHANT;
            case FANEKENA: return TYPE_FANEKENA;
            case IA_CTA: return TYPE_IA_CTA;
            default: return TYPE_VERSET;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_IA_CTA) {
            return new AiViewHolder(inflater.inflate(R.layout.item_search_ai, parent, false));
        }
        return new ResultViewHolder(inflater.inflate(R.layout.item_search_result, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        GlobalSearchResult item = items.get(position);

        if (holder instanceof AiViewHolder) {
            ((AiViewHolder) holder).itemView.setOnClickListener(v -> listener.onAiCardClick(currentQuery));
            return;
        }

        ResultViewHolder h = (ResultViewHolder) holder;
        h.badge.setText(item.getReference());
        h.title.setText(highlight(item.getTitle(), currentQuery));
        h.snippet.setText(highlight(item.getSnippet(), currentQuery));

        int badgeBg;
        switch (item.getType()) {
            case CHANT:
                badgeBg = R.drawable.bg_badge_chant;
                // Affiche la catégorie du chant (ex: FF, KFF) en tag à côté du badge
                if (item.getCategory() != null && !item.getCategory().trim().isEmpty()) {
                    h.categoryTag.setText(item.getCategory().toUpperCase());
                    h.categoryTag.setVisibility(View.VISIBLE);
                } else {
                    h.categoryTag.setVisibility(View.GONE);
                }
                break;
            case FANEKENA:
                badgeBg = R.drawable.bg_badge_fanekena;
                h.categoryTag.setVisibility(View.GONE);
                break;
            default:
                badgeBg = R.drawable.bg_badge_verset;
                h.categoryTag.setVisibility(View.GONE);
                break;
        }
        h.badge.setBackgroundResource(badgeBg);

        h.itemView.setOnClickListener(v -> {
            switch (item.getType()) {
                case CHANT: listener.onSongClick(item); break;
                case FANEKENA: listener.onFanekenaClick(item); break;
                default: listener.onVerseClick(item); break;
            }
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    /**
     * Met en gras + colore chaque mot de la requête retrouvé dans le texte
     * (insensible à la casse), comme le fait YouTube sur ses suggestions.
     */
    private CharSequence highlight(String text, String query) {
        if (text == null) return "";
        if (query == null || query.trim().isEmpty()) return text;

        SpannableString spannable = new SpannableString(text);

        String[] words = query.trim().split("\\s+");
        for (String word : words) {
            if (word.length() < 2) continue;

            Pattern pattern = Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);

            while (matcher.find()) {
                spannable.setSpan(
                        new StyleSpan(Typeface.BOLD),
                        matcher.start(), matcher.end(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                spannable.setSpan(
                        new ForegroundColorSpan(HIGHLIGHT_COLOR),
                        matcher.start(), matcher.end(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }

        return spannable;
    }

    static class ResultViewHolder extends RecyclerView.ViewHolder {
        TextView badge, title, snippet, categoryTag;
        ResultViewHolder(View v) {
            super(v);
            badge = v.findViewById(R.id.tvBadge);
            title = v.findViewById(R.id.tvTitle);
            snippet = v.findViewById(R.id.tvSnippet);
            categoryTag = v.findViewById(R.id.tvCategoryTag);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        AiViewHolder(View v) { super(v); }
    }
}