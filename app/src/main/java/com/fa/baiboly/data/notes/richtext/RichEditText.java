package com.fa.baiboly.data.notes.richtext;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatEditText;

/**
 * ✏️ Champ de texte enrichi des notes : en plus du texte, un TAP sur la
 * case ☐/☑ d'une ligne "cb" la coche/décoche (comme dans les apps de
 * notes modernes). Tout le reste se comporte comme un EditText normal.
 */
public class RichEditText extends AppCompatEditText {

    public interface OnCheckboxToggled {
        void onCheckboxToggled();
    }

    private OnCheckboxToggled checkboxListener;

    public RichEditText(Context context) {
        super(context);
    }

    public RichEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RichEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnCheckboxToggled(OnCheckboxToggled l) {
        checkboxListener = l;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (checkboxListener != null
                && event.getAction() == MotionEvent.ACTION_UP
                && tryToggleCheckbox(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    /** L'utilisateur a-t-il tapé la case ☐/☑ d'une ligne cb ? */
    private boolean tryToggleCheckbox(MotionEvent event) {
        if (getText() == null || getLayout() == null) return false;

        int offset = getOffsetForPosition(event.getX(), event.getY());
        if (offset < 0 || offset > length()) return false;

        int line = getLayout().getLineForOffset(offset);
        int lineStart = getLayout().getLineStart(line);
        int lineEnd = getLayout().getLineEnd(line);
        String text = getText().toString();

        // Ligne de bloc "cb" ?
        if (!isCheckboxLine(lineStart, lineEnd)) return false;

        // Case touchée ? (1er caractère de la ligne : ☐/☑)
        boolean hasCheckedPrefix = text.startsWith(
                RichTextCodec.CB_CHECKED, lineStart);
        boolean hasUncheckedPrefix = text.startsWith(
                RichTextCodec.CB_UNCHECKED, lineStart);
        if (!hasCheckedPrefix && !hasUncheckedPrefix) return false;

        // La case se termine à lineStart + 1 (un char) ; on accepte un
        // tap un peu large (jusqu'au début du 1er mot du texte).
        boolean touchedBox = offset <= lineStart + 1
                || (offset - lineStart <= 2
                && text.length() > lineStart + 2
                && text.charAt(lineStart + 2) == ' ');
        if (!touchedBox) return false;

        setChecked(lineStart, lineEnd, !hasCheckedPrefix);
        checkboxListener.onCheckboxToggled();
        return true;
    }

    private boolean isCheckboxLine(int lineStart, int lineEnd) {
        if (getText() == null) return false;
        int searchEnd = Math.min(length() + 1, lineEnd + 1);
        for (RichTextCodec.BlockSpan bs : getText().getSpans(
                lineStart, searchEnd, RichTextCodec.BlockSpan.class)) {
            if (getText().getSpanStart(bs) <= lineStart
                    && getText().getSpanEnd(bs) > lineStart
                    && RichTextCodec.BLOCK_CB.equals(bs.block)) {
                return true;
            }
        }
        return false;
    }

    /** Coche/décoche la ligne : échange le 1er caractère ☐ ⇄ ☑. */
    public void setChecked(int lineStart, int lineEnd, boolean checked) {
        if (getText() == null) return;
        String text = getText().toString();
        boolean hasCheckedPrefix = text.startsWith(RichTextCodec.CB_CHECKED, lineStart);
        boolean hasUncheckedPrefix = text.startsWith(RichTextCodec.CB_UNCHECKED, lineStart);
        if (checked == hasCheckedPrefix) return; // déjà dans le bon état
        if (!hasCheckedPrefix && !hasUncheckedPrefix) return;

        String replacement = checked
                ? RichTextCodec.CB_CHECKED : RichTextCodec.CB_UNCHECKED;
        getText().replace(lineStart, lineStart + 1,
                replacement, 0, 1);
    }
}
