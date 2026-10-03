package com.fa.baiboly.ui.verses;

import android.app.AlertDialog;
import android.content.Context;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;

public class AnnotationClickableSpan extends ClickableSpan {

    private final Context context;
    private final String annotation;
    private final int color;

    public AnnotationClickableSpan(
            Context context,
            String annotation,
            int color
    ) {
        this.context = context;
        this.annotation = annotation;
        this.color = color;
    }

    @Override
    public void onClick(@NonNull View widget) {

        new AlertDialog.Builder(context)
                .setTitle("Annotation")
                .setMessage(annotation)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void updateDrawState(@NonNull TextPaint ds) {

        ds.setColor(color);

        // retire soulignement
        ds.setUnderlineText(false);
    }
}