package com.fa.baiboly.ui.common;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

/**
 * Petits calculs d'insets partagés par les écrans de lecture : la barre du
 * haut occupe la status bar + une toolbar, et le contenu (texte) passe
 * dessous pendant le défilement.
 */
public final class EdgeBars {

    private EdgeBars() {
    }

    /** Hauteur réelle de la barre d'action du thème, en pixels. */
    public static int actionBarSizePx(Context context) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(
                android.R.attr.actionBarSize, value, true);
        return TypedValue.complexToDimensionPixelSize(
                value.data, context.getResources().getDisplayMetrics());
    }

    /**
     * Décale la barre sous la status bar et renvoie la hauteur totale qu'elle
     * occupe (status bar incluse) : c'est la marge à laisser au contenu.
     */
    public static int applyAppBarInset(View appBar, int statusTopPx) {
        appBar.setPadding(0, statusTopPx, 0, 0);
        return statusTopPx + actionBarSizePx(appBar.getContext());
    }

    /**
     * Marge le conteneur défilant : le texte démarre sous les barres mais
     * remonte dessous quand on défile (clip désactivé).
     */
    public static void scrollUnderBars(ViewGroup scroll, int topPx, int bottomPx) {
        scroll.setPadding(
                scroll.getPaddingLeft(), topPx,
                scroll.getPaddingRight(), bottomPx);
        scroll.setClipToPadding(false);
    }
}
