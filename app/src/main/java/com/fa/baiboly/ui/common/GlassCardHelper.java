package com.fa.baiboly.ui.common;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

/**
 * Réglages centralisés du style Liquid Glass des cartes.
 *
 * <p>Le matériau est appliqué par {@link GlassSurfaceDrawable} : vrai flou de
 * l'arrière-plan (précalculé sur la photo de fond de l'écran, aligné sur la
 * position réelle de la carte), teinte translucide, bordure lumineuse, reflet
 * supérieur et ombre douce. Seule l'apparence des cartes change : aucune autre
 * couche de fond de l'application n'est touchée.</p>
 */
public final class GlassCardHelper {

    /** Réglages du verre, résolus une fois par mode clair/sombre. */
    public static final class Config {
        public final boolean night;
        /** Teinte translucide posée sur le fond flouté. */
        public int tintColor;
        /** Fond de secours quand la carte n'a pas de photo derrière elle. */
        public final int fallbackColor;
        /** Bordure : lumineuse en haut à gauche, plus discrète en bas à droite. */
        public final int borderColor;
        public final int borderColorEnd;
        /** Reflet lumineux en haut de la carte. */
        public final int highlightColor;
        /** Assombrissement très léger en bas de la carte (profondeur). */
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
                // opaque : on doit voir la photo de l'écran à travers la carte.
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

        /** Même configuration, avec la teinte du verre remplacée. */
        @NonNull
        Config withTint(int color) {
            tintColor = color;
            return this;
        }
    }

    /** Tag de la vue enfant qui porte le verre (index 0 de la carte). */
    private static final String LAYER_TAG = "glass_layer";

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
     * Applique (ou met à jour) le matériau verre sur une carte.
     *
     * @param backgroundRoot vue qui porte la photo de fond de l'écran
     * @param photoRes       photo de fond de l'écran (0 = pas de photo)
     * @param scrim          voile appliqué par l'écran au-dessus de la photo
     */
    public static void apply(@NonNull MaterialCardView card,
                             @Nullable View backgroundRoot,
                             int photoRes, int scrim) {
        apply(card, backgroundRoot, photoRes, scrim, 0);
    }

    /**
     * Variante teintée (cartes de livres colorées) : le fond flouté est
     * coloré par {@code tintColor} au lieu du blanc/noir habituel.
     */
    public static void apply(@NonNull MaterialCardView card,
                             @Nullable View backgroundRoot,
                             int photoRes, int scrim, int tintColor) {
        Config config = config(card.getContext());
        if (tintColor != 0) {
            config.withTint(tintColor);
        }

        // Le verre est posé sur une vue dont les limites sont EXACTEMENT celles
        // de la carte : l'enfant unique de la carte quand il existe. Cela évite
        // de dépendre de la remesure d'une couche match_parent dans un
        // conteneur wrap_content (hauteur 0 ou viewport au lieu de la carte).
        View target = targetOf(card);

        Drawable current = target.getBackground();
        if (current instanceof GlassSurfaceDrawable) {
            GlassSurfaceDrawable glass = (GlassSurfaceDrawable) current;
            glass.setGlassTint(config.tintColor);
            glass.update(backgroundRoot, photoRes, scrim);
            return;
        }

        float radius = card.getRadius();
        if (radius <= 0f) {
            radius = dp(card.getContext(), config.cornerRadius);
        }

        // Coins arrondis garantis côté vue (le fond de la carte n'assure plus
        // l'outline puisqu'il est transparent).
        target.setClipToOutline(true);

        // La vue cible sert d'ancre : l'alignement du flou suit sa position
        // réelle à l'écran, y compris pendant le défilement.
        target.setBackground(new GlassSurfaceDrawable(config, target, backgroundRoot,
                photoRes, scrim, radius));
    }

    /** Retire le verre (couche ou fond direct de l'enfant unique). */
    public static void remove(@NonNull MaterialCardView card) {
        View layer = layerOf(card);
        if (layer != null) {
            card.removeView(layer);
        }
        View child = singleContentChild(card);
        if (child != null && !LAYER_TAG.equals(child.getTag())) {
            Drawable background = child.getBackground();
            if (background instanceof GlassSurfaceDrawable
                    || background instanceof PhotoBackgroundDrawable) {
                child.setBackground(null);
            }
        }
    }

    /** Unique enfant "contenu" de la carte (hors couche de verre). */
    @Nullable
    private static View singleContentChild(@NonNull MaterialCardView card) {
        int children = card.getChildCount();
        if (children == 1) {
            View only = card.getChildAt(0);
            return LAYER_TAG.equals(only.getTag()) ? null : only;
        }
        if (children == 2) {
            View first = card.getChildAt(0);
            if (LAYER_TAG.equals(first.getTag())) {
                return card.getChildAt(1);
            }
        }
        return null;
    }

    /**
     * Vue qui portera le verre : le contenu unique de la carte (mêmes limites,
     * donc mesure fiable) ou, à défaut, une couche dédiée.
     */
    @NonNull
    private static View targetOf(@NonNull MaterialCardView card) {
        View child = singleContentChild(card);
        if (card.getChildCount() == 1 && child != null) {
            Drawable background = child.getBackground();
            if (background == null || background instanceof GlassSurfaceDrawable
                    || background instanceof PhotoBackgroundDrawable) {
                prepareCard(card);
                android.util.Log.d("GlassDbg", "targetOf ".concat(idName(card))
                        .concat(" -> contenu ")
                        .concat(child.getClass().getSimpleName())
                        .concat(" cardBg=")
                        .concat(String.valueOf(card.getBackground())));
                return child;
            }
            android.util.Log.d("GlassDbg", "targetOf ".concat(idName(card))
                    .concat(" -> contenu a deja un fond ")
                    .concat(String.valueOf(background)));
        }
        View layer = ensureLayer(card);
        android.util.Log.d("GlassDbg", "targetOf ".concat(idName(card))
                .concat(" -> couche dediee, children=")
                .concat(String.valueOf(card.getChildCount())));
        return layer;
    }

    private static String idName(View view) {
        int id = view.getId();
        if (id == View.NO_ID) {
            return view.getClass().getSimpleName();
        }
        try {
            return view.getResources().getResourceEntryName(id);
        } catch (android.content.res.Resources.NotFoundException e) {
            return String.valueOf(id);
        }
    }

    /** Neutralise tout ce que la carte dessine elle-même. */
    private static void prepareCard(@NonNull MaterialCardView card) {
        if (card.getBackground() instanceof PhotoBackgroundDrawable) {
            card.setBackground(null);
        }
        // setCardBackgroundColor(0) seul ne suffit pas : si le fond Material de
        // la carte a été "surchargé", l'appel est ignoré et la carte garde le
        // blanc/noir du thème. setBackgroundColor remplace le fond de la vue
        // par un ColorDrawable réellement transparent, sans dépendre de l'état
        // interne de MaterialCardView.
        card.setCardBackgroundColor(0x00000000);
        card.setBackgroundColor(0x00000000);
        card.setCardElevation(0f);
        card.setStrokeWidth(0);
        card.setUseCompatPadding(false);
        card.setPreventCornerOverlap(false);
        card.setClipToOutline(true);
    }

    /**
     * Vue enfant transparente qui porte le fond décoré de la carte. Créer une
     * couche plutôt que d'utiliser {@code card.setBackground(...)} évite le
     * comportement de MaterialCardView, qui réapplique son propre fond Material
     * par-dessus et ignore le fond personnalisé.
     */
    @NonNull
    private static View ensureLayer(@NonNull MaterialCardView card) {
        View layer = layerOf(card);
        if (layer == null) {
            layer = new View(card.getContext());
            layer.setTag(LAYER_TAG);
            layer.setClickable(false);
            layer.setFocusable(false);
            layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.addView(layer, 0, new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        }
        // Plus aucun fond opaque derrière la couche, sinon la translucidité du
        // verre est perdue : ni le fond Material (blanc/noir), ni la photo du
        // style "Sary" ne doivent rester sous le verre.
        prepareCard(card);
        return layer;
    }

    @Nullable
    private static View layerOf(@NonNull MaterialCardView card) {
        if (card.getChildCount() == 0) {
            return null;
        }
        View first = card.getChildAt(0);
        return LAYER_TAG.equals(first.getTag()) ? first : null;
    }

    /** Style des lignes à l'intérieur des cartes (Mofonaina, Perikopa, Historique…). */
    public static void styleRow(@NonNull View row, boolean emphasized) {
        Config config = config(row.getContext());
        int color = emphasized ? config.rowEmphasizedColor : config.rowColor;

        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        float radius = dp(row.getContext(), 14f);
        background.setCornerRadius(radius);
        background.setStroke(Math.max(1, Math.round(dp(row.getContext(), 0.7f))),
                config.rowBorderColor);
        row.setBackground(background);

        // Le retour tactile (ripple) est conservé même si le fond est remplacé.
        if (row.getForeground() == null) {
            TypedValue value = new TypedValue();
            if (row.getContext().getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, value, true)
                    && value.resourceId != 0) {
                row.setForeground(ContextCompat.getDrawable(row.getContext(), value.resourceId));
            }
        }
    }

    static float dp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }
}
