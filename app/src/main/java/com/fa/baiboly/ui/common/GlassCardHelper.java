package com.fa.baiboly.ui.common;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Réglages des surfaces translucides de l'application : le verre flouté du
 * champ de saisie ({@link GlassSurfaceDrawable}) et le fond des lignes à
 * l'intérieur des cartes. Le style "Liquid Glass" des cartes, qui ne
 * fonctionnait pas, a été supprimé.
 */
public final class GlassCardHelper {

    /** Réglages du verre, résolus une fois par mode clair/sombre. */
    public static final class Config {
        public final boolean night;
        /** Teinte translucide posée sur le fond flouté. */
        public int tintColor;
        /** Fond de secours quand la surface n'a pas de photo derrière elle. */
        public final int fallbackColor;
        /** Bordure : lumineuse en haut à gauche, plus discrète en bas à droite. */
        public final int borderColor;
        public final int borderColorEnd;
        /** Reflet lumineux en haut de la surface. */
        public final int highlightColor;
        /** Assombrissement très léger en bas de la surface (profondeur). */
        public final int shadeColor;
        public final float borderWidth;
        public final float cornerRadius;
        /** Fond discret des lignes internes des cartes. */
        public final int rowColor;
        /** Fond un peu plus lumineux de la ligne mise en avant (verset du jour). */
        public final int rowEmphasizedColor;
        public final int rowBorderColor;
        /** Saturation du fond réfracté (le verre avive les couleurs). */
        public final float saturation;

        Config(boolean night) {
            this.night = night;
            if (night) {
                // ÉTAPE 1 : transparence seule. Aucune couleur, aucun fond
                // opaque : on doit voir la photo de l'écran à travers le verre.
                tintColor = 0x1A000000;
                fallbackColor = 0x00000000;
                borderColor = 0x66FFFFFF;
                borderColorEnd = 0x14FFFFFF;
                highlightColor = 0x26FFFFFF;
                shadeColor = 0x33000000;
                rowColor = 0x14FFFFFF;
                rowEmphasizedColor = 0x26FFFFFF;
                rowBorderColor = 0x1FFFFFFF;
                saturation = 1.25f;
            } else {
                // ÉTAPE 1 : transparence seule, sans couleur.
                tintColor = 0x14FFFFFF;
                fallbackColor = 0x00000000;
                borderColor = 0x8CFFFFFF;
                borderColorEnd = 0x1FFFFFFF;
                highlightColor = 0x38FFFFFF;
                shadeColor = 0x14000000;
                rowColor = 0x46FFFFFF;
                rowEmphasizedColor = 0x40FFFFFF;
                rowBorderColor = 0x33FFFFFF;
                saturation = 1.4f;
            }
            borderWidth = 1f;
            cornerRadius = 16f;
        }
    }

    private GlassCardHelper() {
    }

    public static boolean isNight(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    @NonNull
    public static Config config(Context context) {
        return new Config(isNight(context));
    }

    /**
     * Style des lignes à l'intérieur des cartes (Mofonaina, Perikopa,
     * Historique…). Sur une carte à photo, la ligne reste claire et translucide
     * (le voile de la carte est sombre) ; sur une carte à fond uni blanc, elle
     * prend une teinte neutre pour rester visible.
     */
    public static void styleRow(@NonNull View row, boolean emphasized) {
        Context context = row.getContext();
        Config config = config(context);

        boolean solid = AppBackgrounds.CARD_STYLE_SOLID.equals(
                AppBackgrounds.getCardBackgroundStyle(context));

        int color;
        int borderColor;
        if (solid) {
            color = emphasized
                    ? (config.night ? 0x1FFFFFFF : 0x14000000)
                    : (config.night ? 0x14FFFFFF : 0x0D000000);
            borderColor = config.night ? 0x1FFFFFFF : 0x1F000000;
        } else {
            color = emphasized ? config.rowEmphasizedColor : config.rowColor;
            borderColor = config.rowBorderColor;
        }

        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(context, 14f));
        background.setStroke(Math.max(1, Math.round(dp(context, 0.7f))), borderColor);
        row.setBackground(background);

        // Le retour tactile (ripple) est conservé même si le fond est remplacé.
        if (row.getForeground() == null) {
            TypedValue value = new TypedValue();
            if (context.getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, value, true)
                    && value.resourceId != 0) {
                row.setForeground(ContextCompat.getDrawable(context, value.resourceId));
            }
        }
    }

    static float dp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }
}
