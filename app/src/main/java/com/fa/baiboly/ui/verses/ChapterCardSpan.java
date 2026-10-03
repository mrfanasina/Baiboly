package com.fa.baiboly.ui.verses;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

public class ChapterCardSpan extends ReplacementSpan {

    private final int backgroundColor;
    private final int textColor;
    private final String text;

    // Paramètres de style
    private static final float CORNER_RADIUS = 25f; // Coins plus arrondis
    private static final int PADDING_H = 40;        // Padding Gauche/Droite
    private static final int PADDING_V = 60;        // Padding Haut/Bas
    private static final int MARGIN_TOP = 30;       // Espace au dessus de la carte

    public ChapterCardSpan(int backgroundColor, int textColor, String text) {
        this.backgroundColor = backgroundColor;
        this.textColor = textColor;
        this.text = text;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        // 1. On informe de la largeur (Texte + Padding)
        int width = (int) (paint.measureText(this.text) + (2 * PADDING_H));

        // 2. IMPORTANT : On informe le système de la hauteur de la ligne
        if (fm != null) {
            // On récupère les mesures réelles du texte
            Paint.FontMetricsInt realFm = paint.getFontMetricsInt();

            // On agrandit l'espace au-dessus (ascent) et en-dessous (descent)
            // C'est ici que le "Margin" et le "Padding" vertical s'appliquent réellement
            fm.ascent = realFm.ascent - PADDING_V - MARGIN_TOP;
            fm.top = fm.ascent;

            fm.descent = realFm.descent + PADDING_V;
            fm.bottom = fm.descent;
        }

        return width;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        // 1. Définir le rectangle de la carte
        float textWidth = paint.measureText(this.text);
        // On centre verticalement par rapport à la ligne de texte
        RectF rect = new RectF(x, y + paint.ascent() - PADDING_V, x + textWidth + (2 * PADDING_H), y + paint.descent() + PADDING_V);

        // 2. Dessiner le fond (Le rectangle arrondi)
        paint.setColor(backgroundColor);
        paint.setAntiAlias(true);
        canvas.drawRoundRect(rect, CORNER_RADIUS, CORNER_RADIUS, paint);

        // 3. Dessiner le texte par dessus
        paint.setColor(textColor);
        paint.setTextAlign(Paint.Align.CENTER);
        // On dessine le texte au milieu du rectangle créé
        canvas.drawText(this.text, rect.centerX(), y, paint);
    }
}