package com.fa.baiboly.data;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.fa.baiboly.R;

/**
 * Gestion centrale des deux couleurs de base choisies par l'utilisateur
 * (Paramètres > Loko).
 *
 * Chaque couleur est appliquée au niveau du THÈME de chaque Activity avant
 * l'inflation des vues (via des overlays de style) : elle est donc utilisée
 * partout — boutons, icônes, headers, sliders, barre de navigation...
 * Les livres (Testamenta Taloha = couleur 1, Vaovao = couleur 2) et les
 * perikopa lisent ces couleurs via {@link #getPrimary}/{@link #getSecondary}.
 *
 * La couleur d'erreur (colorError) n'est jamais touchée : elle reste celle
 * du thème Material.
 */
public final class ColorManager {

    private ColorManager() {}

    public static final String PREFS_NAME = "app_settings";
    public static final String KEY_PRIMARY = "color_primary";
    public static final String KEY_SECONDARY = "color_secondary";

    /** Index "- rien de choisi" : on garde les couleurs du thème. */
    public static final int UNSET = -1;

    /**
     * Palette proposée dans les réglages (tons 500-700, lisibles en blanc).
     * L'ordre correspond aux styles ColorPrimaryN / ColorSecondaryN de
     * color_overlays.xml.
     */
    public static final int[] SWATCHES = {
            0xFFC40E5A, // rose (couleur actuelle de l'app)
            0xFFE53935, // rouge
            0xFFD84315, // orange foncé
            0xFFEF6C00, // orange
            0xFF558B2F, // vert clair
            0xFF2E7D32, // vert
            0xFF00897B, // sarcelle
            0xFF33B8C4, // sarcelle clair
            0xFF0097A7, // cyan
            0xFF256EC7, // bleu
            0xFF1565C0, // bleu foncé
            0xFF3949AB, // indigo
            0xFF8E24AA, // violet
            0xFF6A1B9A, // violet foncé
            0xFFAD1457, // rose foncé
            0xFF795548  // brun
    };

    /** Overlays primaires correspondants (voir color_overlays.xml). */
    private static final int[] PRIMARY_OVERLAYS = {
            R.style.ColorPrimary0, R.style.ColorPrimary1, R.style.ColorPrimary2,
            R.style.ColorPrimary3, R.style.ColorPrimary4, R.style.ColorPrimary5,
            R.style.ColorPrimary6, R.style.ColorPrimary7, R.style.ColorPrimary8,
            R.style.ColorPrimary9, R.style.ColorPrimary10, R.style.ColorPrimary11,
            R.style.ColorPrimary12, R.style.ColorPrimary13, R.style.ColorPrimary14,
            R.style.ColorPrimary15
    };

    /** Overlays secondaires correspondants. */
    private static final int[] SECONDARY_OVERLAYS = {
            R.style.ColorSecondary0, R.style.ColorSecondary1, R.style.ColorSecondary2,
            R.style.ColorSecondary3, R.style.ColorSecondary4, R.style.ColorSecondary5,
            R.style.ColorSecondary6, R.style.ColorSecondary7, R.style.ColorSecondary8,
            R.style.ColorSecondary9, R.style.ColorSecondary10, R.style.ColorSecondary11,
            R.style.ColorSecondary12, R.style.ColorSecondary13, R.style.ColorSecondary14,
            R.style.ColorSecondary15
    };

    /** Couleurs actuelles du thème, tant que l'utilisateur n'a rien choisi. */
    private static final int DEFAULT_PRIMARY = 0xFFC40E5A;
    private static final int DEFAULT_SECONDARY = 0xFF256EC7;

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Couleur de base n°1 (accent principal, Testamenta Taloha, perikopa). */
    public static int getPrimary(Context context) {
        int index = prefs(context).getInt(KEY_PRIMARY, UNSET);
        return index >= 0 && index < SWATCHES.length ? SWATCHES[index] : DEFAULT_PRIMARY;
    }

    /** Couleur de base n°2 (accent secondaire, Testamenta Vaovao). */
    public static int getSecondary(Context context) {
        int index = prefs(context).getInt(KEY_SECONDARY, UNSET);
        return index >= 0 && index < SWATCHES.length ? SWATCHES[index] : DEFAULT_SECONDARY;
    }

    /**
     * Applique les couleurs choisies au thème de l'activité.
     * À appeler AVANT super.onCreate() (donc avant l'inflation des vues)
     * pour que tous les composants les prennent en compte.
     */
    public static void applyTheme(Activity activity) {
        SharedPreferences prefs = prefs(activity);

        int primary = prefs.getInt(KEY_PRIMARY, UNSET);
        int secondary = prefs.getInt(KEY_SECONDARY, UNSET);

        if (primary >= 0 && primary < PRIMARY_OVERLAYS.length) {
            activity.getTheme().applyStyle(PRIMARY_OVERLAYS[primary], true);
        }
        if (secondary >= 0 && secondary < SECONDARY_OVERLAYS.length) {
            activity.getTheme().applyStyle(SECONDARY_OVERLAYS[secondary], true);
        }
    }

    /**
     * Hook unique appelé en tout début de onCreate() dans les activités :
     * mode nuit + couleurs personnalisées.
     */
    public static void applyGlobalTheme(Activity activity) {
        int mode = prefs(activity).getInt("theme_mode",
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
        applyTheme(activity);
    }
}
