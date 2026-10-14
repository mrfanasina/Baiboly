package com.fa.baiboly.ui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.menu.MenuBuilder;

import com.fa.baiboly.R;

/**
 * Utilitaires partagés par les toolbars de toutes les activités :
 *
 * 1) AppCompat cache par défaut les icônes des items "never" (menu
 *    3 points). setOptionalIconsVisible(true) les fait apparaître, et
 *    on applique la teinte couleur de texte du thème pour qu'elles
 *    restent visibles en mode clair ET sombre.
 *
 * 2) Dialog "Mombamomba" (à propos) partagé par les menus de toutes
 *    les activités (avant : un simple Toast placeholder).
 */
public final class MenuHelper {

    private MenuHelper() {
    }

    /**
     * À appeler à la fin de onCreateOptionsMenu(), après inflate() :
     * rend visibles les icônes dans le menu 3 points et les teinte
     * avec la couleur de texte du thème.
     */
    public static void enableOverflowIcons(Context context, Menu menu) {

        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }

        int tint = resolveTextColorPrimary(context);

        for (int i = 0; i < menu.size(); i++) {

            MenuItem item = menu.getItem(i);

            Drawable icon = item.getIcon();
            if (icon != null) {
                icon = icon.mutate();
                icon.setTint(tint);
                item.setIcon(icon);
            }
        }
    }

    /**
     * Couleur de texte principale du thème courant (clair ou sombre).
     */
    private static int resolveTextColorPrimary(Context context) {

        TypedValue value = new TypedValue();

        context.getTheme().resolveAttribute(
                android.R.attr.textColorPrimary,
                value,
                true
        );

        try {
            if (value.resourceId != 0) {
                return androidx.core.content.ContextCompat.getColor(context, value.resourceId);
            }
        } catch (Exception ignored) {
        }
        // Résolution directe en couleur (pas de resource)
        return value.data;
    }

    /**
     * Dialog "Mombamomba" : nom, version et objet de l'application.
     */
    public static void showAbout(Context context) {

        String version = "";
        try {
            PackageManager pm = context.getPackageManager();
            String v = pm.getPackageInfo(context.getPackageName(), 0).versionName;
            if (v != null && !v.isEmpty()) version = "  ·  v" + v;
        } catch (Exception ignored) {
        }

        new AlertDialog.Builder(context)
                .setTitle("Mombamomba")
                .setIcon(R.drawable.ic_info)
                .setMessage(
                        "Baiboly Malagasy" + version + "\n\n"
                                + "Vakio ny Baiboly amin'ny teny malagasy : "
                                + "Mofonaina, perikopa, fandaharana ara-potoana, "
                                + "fihirana, fanekempinoana, mofonaina, ary IA hanazavana "
                                + "ny andininy sy ny teny sarotra.")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
