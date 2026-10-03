package com.fa.baiboly.ui.ai;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.RelativeSizeSpan;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.data.parser.BibleRefParser;
import com.fa.baiboly.models.ChatMessage;
import com.fa.baiboly.ui.verses.VerseBottomSheetFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.Markwon;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatMessage> messages = new ArrayList<>();

    private final FragmentManager fragmentManager;

    @Nullable private final String contextBook;
    @Nullable private final String contextChapter;

    /** Callback quand l'utilisateur veut une réponse plus ciblée (chip sous la réponse IA). */
    public interface OnNotRelevantListener {
        void onNotRelevant(String question);
    }

    /** Callback d'ouverture des réglages IA (chip sous la réponse IA). */
    public interface OnOpenAiSettingsListener {
        void onOpenAiSettings();
    }

    @Nullable
    private OnNotRelevantListener notRelevantListener;

    @Nullable
    private OnOpenAiSettingsListener settingsListener;

    private Markwon markwon;

    private float textSizeSp = 18f;

    // =====================================================
    // DÉTECTION DE RÉFÉRENCES BIBLIQUES
    // =====================================================

    // Marqueur "and" / "andininy" — le numéro devient OBLIGATOIRE :
    // "and12", "and 12-13", "and:12", "andininy 5-6"... En revanche un
    // simple emploi du mot dans une phrase ("andininy manontolo") ne
    // matche pas, ce n'est pas une référence mais juste le mot malgache.
    // NB : pas de \b APRÈS "and" — il n'y a PAS de frontière de mot entre
    // "d" et un chiffre, "and12" ne matcherait jamais. [ \t]* (et non
    // \s*) : jamais de saut de ligne, pour ne pas "traverser" un
    // paragraphe.
    private static final Pattern AND_MARKER_PATTERN = Pattern.compile(
            "\\b(?:andininy|and)[ \\t]*[.:]?[ \\t]*(\\d+(?:[ \\t]?-[ \\t]?\\d+)?)"
    );

    // Références classiques : détection centralisée via BibleRefParser
    // (table des livres officielle, "Heb11,1", "1Kor13,4-7"...). L'ancienne
    // regex locale est remplacée par detectReferences() qui valide chaque
    // match contre la table des livres et renvoie la référence normalisée.
    // NB : le marqueur "and"/"andininy" reste géré localement (ci-dessus),
    // il n'est pas un nom de livre.

    public ChatAdapter(FragmentManager fragmentManager,
                       @Nullable String contextBook,
                       @Nullable String contextChapter) {
        this.fragmentManager = fragmentManager;
        this.contextBook = contextBook;
        this.contextChapter = contextChapter;
    }

    /** Enregistre le callback du chip "Tsy dia mifandraika loatra?". */
    public void setOnNotRelevantListener(@Nullable OnNotRelevantListener listener) {
        this.notRelevantListener = listener;
    }

    /** Enregistre le callback du chip "Fikirakiran'ny IA". */
    public void setOnOpenAiSettingsListener(@Nullable OnOpenAiSettingsListener listener) {
        this.settingsListener = listener;
    }

    /**
     * Dernière question de l'utilisateur (pour le chip "plus ciblé").
     * Sert aussi de marqueur : le chip n'est montré que sur la dernière
     * réponse IA de la conversation.
     */
    @Nullable
    public String getLastUserQuestion() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m.getType() == ChatMessage.TYPE_USER) return m.getText();
            if (m.getType() == ChatMessage.TYPE_AI) return null;
        }
        return null;
    }

    /** Notifie seulement la dernière cellule (le chip apparaît/disparaît). */
    public void refreshLastAiItem() {
        if (!messages.isEmpty()) {
            notifyItemChanged(messages.size() - 1);
        }
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void setTextSize(float sp) {
        if (this.textSizeSp != sp) {
            this.textSizeSp = sp;
            notifyDataSetChanged();
        }
    }

    public void removeLoadingIfPresent() {
        if (!messages.isEmpty()
                && messages.get(messages.size() - 1).getType() == ChatMessage.TYPE_LOADING) {
            int index = messages.size() - 1;
            messages.remove(index);
            notifyItemRemoved(index);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (markwon == null) {
            markwon = MarkwonProvider.get(parent.getContext());
        }

        if (viewType == ChatMessage.TYPE_USER) {
            return new UserHolder(inflater.inflate(R.layout.item_chat_user, parent, false));
        } else if (viewType == ChatMessage.TYPE_LOADING) {
            return new LoadingHolder(inflater.inflate(R.layout.item_chat_loading, parent, false));
        } else {
            return new AiHolder(inflater.inflate(R.layout.item_chat_ai, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {

        ChatMessage message = messages.get(position);

        if (holder instanceof UserHolder) {

            UserHolder userHolder = (UserHolder) holder;
            userHolder.text.setTextSize(textSizeSp);
            userHolder.text.setText(message.getText());
            setBibleLinks(userHolder.text);

        } else if (holder instanceof AiHolder) {

            AiHolder aiHolder = (AiHolder) holder;

            aiHolder.text.setTextSize(textSizeSp);

            markwon.setMarkdown(aiHolder.text, message.getText());

            setBibleLinks(aiHolder.text);

            aiHolder.btnCopy.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                copyToClipboard(v.getContext(), aiHolder.text.getText().toString());
            });

            // Rangée d'actions sous la réponse :
            // - chip "Tsy dia mifandraika loatra?" : uniquement sous la
            //   dernière réponse IA (il re-posera la dernière question).
            // - chip "Fikirakiran'ny IA" : toujours visible, ouvre les
            //   réglages IA (style, niveau, longueur de réponse...).
            boolean isLastMessage = position == messages.size() - 1;
            String lastQuestion = isLastMessage ? getLastUserQuestion() : null;

            if (lastQuestion != null && notRelevantListener != null) {
                aiHolder.chipNotRelevant.setVisibility(View.VISIBLE);
                aiHolder.chipNotRelevant.setOnClickListener(v ->
                        notRelevantListener.onNotRelevant(lastQuestion));
            } else {
                aiHolder.chipNotRelevant.setVisibility(View.GONE);
                aiHolder.chipNotRelevant.setOnClickListener(null);
            }

            if (settingsListener != null) {
                aiHolder.chipAiSettings.setVisibility(View.VISIBLE);
                aiHolder.chipAiSettings.setOnClickListener(v -> settingsListener.onOpenAiSettings());
            } else {
                aiHolder.chipAiSettings.setVisibility(View.GONE);
                aiHolder.chipAiSettings.setOnClickListener(null);
            }
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder instanceof LoadingHolder) {
            ((LoadingHolder) holder).startAnimating();
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        if (holder instanceof LoadingHolder) {
            ((LoadingHolder) holder).stopAnimating();
        }
    }

    private void setBibleLinks(TextView textView) {

        CharSequence current = textView.getText();
        if (current == null || current.length() == 0) {
            return;
        }

        String plain = current.toString();

        SpannableString spannable = new SpannableString(current);

        List<int[]> excludedRanges = new ArrayList<>();
        if (current instanceof Spanned) {
            Spanned sourceSpanned = (Spanned) current;
            RelativeSizeSpan[] headingSpans = sourceSpanned.getSpans(
                    0, sourceSpanned.length(), RelativeSizeSpan.class
            );
            for (RelativeSizeSpan headingSpan : headingSpans) {
                excludedRanges.add(new int[]{
                        sourceSpanned.getSpanStart(headingSpan),
                        sourceSpanned.getSpanEnd(headingSpan)
                });
            }
        }

        List<int[]> handledRanges = new ArrayList<>();
        boolean foundAny = false;

        if (contextBook != null && contextChapter != null) {

            Matcher andMatcher = AND_MARKER_PATTERN.matcher(plain);

            while (andMatcher.find()) {

                int start = andMatcher.start();
                int end = andMatcher.end();

                if (overlapsAnyRange(excludedRanges, start, end)) {
                    continue;
                }

                String verseSuffix = andMatcher.group(1); // toujours non-null désormais

                String reference = contextBook + " " + contextChapter
                        + ":" + verseSuffix;

                addBibleClickableSpan(spannable, start, end, reference);
                handledRanges.add(new int[]{start, end});
                foundAny = true;
            }
        }

        List<BibleRefParser.DetectedRef> detected =
                BibleRefParser.detectReferences(plain);

        for (BibleRefParser.DetectedRef dr : detected) {

            int start = dr.start;
            int end = dr.end;

            if (overlapsAnyRange(excludedRanges, start, end)) {
                continue;
            }

            if (overlapsAnyRange(handledRanges, start, end)) {
                continue;
            }

            addBibleClickableSpan(spannable, start, end, dr.reference);
            foundAny = true;
        }

        if (!foundAny) {
            return;
        }

        textView.setText(spannable);

        textView.setHighlightColor(Color.TRANSPARENT);
        textView.setOnTouchListener(this::handleChatTextTouch);
        textView.setClickable(true);
        textView.setFocusable(true);
    }

    private boolean handleChatTextTouch(View view, MotionEvent motionEvent) {

        if (motionEvent == null || motionEvent.getAction() != MotionEvent.ACTION_UP) {
            return false;
        }

        TextView textView = (TextView) view;

        CharSequence text = textView.getText();
        if (!(text instanceof Spanned)) {
            return false;
        }
        Spanned spanned = (Spanned) text;

        Layout layout = textView.getLayout();
        if (layout == null) {
            return false;
        }

        int x = (int) motionEvent.getX();
        int y = (int) motionEvent.getY();

        x -= textView.getTotalPaddingLeft();
        y -= textView.getTotalPaddingTop();

        x += textView.getScrollX();
        y += textView.getScrollY();

        int line = layout.getLineForVertical(y);
        int offset = layout.getOffsetForHorizontal(line, x);

        if (x > layout.getLineWidth(line)) {
            return false;
        }

        ClickableSpan[] spans = spanned.getSpans(
                Math.max(0, offset - 1),
                Math.min(spanned.length(), offset + 1),
                ClickableSpan.class
        );

        for (ClickableSpan span : spans) {

            int start = spanned.getSpanStart(span);
            int end = spanned.getSpanEnd(span);

            if (offset >= (start - 1) && offset <= (end + 1)) {
                span.onClick(textView);
                return true;
            }
        }

        return false;
    }

    private boolean overlapsAnyRange(List<int[]> ranges, int start, int end) {

        for (int[] r : ranges) {
            if (start < r[1] && end > r[0]) {
                return true;
            }
        }

        return false;
    }

    private void addBibleClickableSpan(SpannableString spannable,
                                       int start,
                                       int end,
                                       String reference) {

        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                VerseBottomSheetFragment.display(fragmentManager, reference);
            }
        };

        spannable.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void copyToClipboard(Context context, String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Valiny AI", text);
        if (cm != null) {
            cm.setPrimaryClip(clip);
        }
        Toast.makeText(context, "Napiesaina", Toast.LENGTH_SHORT).show();
    }

    static class UserHolder extends RecyclerView.ViewHolder {
        final TextView text;
        UserHolder(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.tvChatMessage);
        }
    }

    static class AiHolder extends RecyclerView.ViewHolder {
        final TextView text;
        final ImageButton btnCopy;
        final com.google.android.material.chip.Chip chipNotRelevant;
        final com.google.android.material.chip.Chip chipAiSettings;
        AiHolder(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.tvChatMessage);
            btnCopy = itemView.findViewById(R.id.btnCopyMessage);
            chipNotRelevant = itemView.findViewById(R.id.chipNotRelevant);
            chipAiSettings = itemView.findViewById(R.id.chipAiSettings);
            text.setTextIsSelectable(true);
        }
    }

    static class LoadingHolder extends RecyclerView.ViewHolder {

        private final View dot1, dot2, dot3;
        private ObjectAnimator animator1, animator2, animator3;

        LoadingHolder(@NonNull View itemView) {
            super(itemView);
            dot1 = itemView.findViewById(R.id.dot1);
            dot2 = itemView.findViewById(R.id.dot2);
            dot3 = itemView.findViewById(R.id.dot3);
        }

        void startAnimating() {
            animator1 = createBounce(dot1, 0);
            animator2 = createBounce(dot2, 150);
            animator3 = createBounce(dot3, 300);
        }

        void stopAnimating() {
            if (animator1 != null) animator1.cancel();
            if (animator2 != null) animator2.cancel();
            if (animator3 != null) animator3.cancel();
        }

        private ObjectAnimator createBounce(View dot, long startDelay) {
            PropertyValuesHolder translateY = PropertyValuesHolder.ofFloat(
                    View.TRANSLATION_Y, 0f, -14f, 0f
            );
            ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(dot, translateY);
            animator.setDuration(600);
            animator.setStartDelay(startDelay);
            animator.setRepeatCount(ObjectAnimator.INFINITE);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.start();
            return animator;
        }
    }
}