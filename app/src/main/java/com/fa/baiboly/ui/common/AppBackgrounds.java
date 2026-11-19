package com.fa.baiboly.ui.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.fa.baiboly.R;
import com.google.android.material.card.MaterialCardView;

/**
 * Applique les photos de fond de l'application (dossier images/ici) sur les
 * écrans et sur les cartes. Les écrans de lecture ne doivent PAS appeler ce
 * gestionnaire (voir BaibolyApp pour la liste des exclusions).
 *
 * Chaque écran garde toujours la même photo : plus de changement aléatoire.
 * L'accueil utilise un fond choisi dans les paramètres (par défaut une image
 * fixe) et chaque carte de l'accueil a sa propre photo.
 */
public final class AppBackgrounds {

    public static final String PREFS_NAME = "app_settings";
    public static final String KEY_HOME_BACKGROUND = "home_background";

    private static final int[] PHOTOS = {
            R.drawable.fond_01, R.drawable.fond_02, R.drawable.fond_03,
            R.drawable.fond_04, R.drawable.fond_05, R.drawable.fond_06,
            R.drawable.fond_07, R.drawable.fond_08, R.drawable.fond_09,
            R.drawable.fond_10, R.drawable.fond_11, R.drawable.fond_12,
            R.drawable.fond_13, R.drawable.fond_14, R.drawable.fond_15
    };

    /**
     * Choix du fond de l'accueil. On écarte les deux images "chant" (fond_12,
     * fond_13) : elles sont réservées aux écrans de chant.
     */
    private static final int[] HOME_CHOICES = {
            R.drawable.fond_02, R.drawable.fond_03,
            R.drawable.fond_04, R.drawable.fond_05, R.drawable.fond_06,
            R.drawable.fond_07, R.drawable.fond_08, R.drawable.fond_09,
            R.drawable.fond_10, R.drawable.fond_11, R.drawable.fond_14,
            R.drawable.fond_15
    };

    private static final int DEFAULT_HOME = R.drawable.fond_01;

    private static final String HOME = "com.fa.baiboly.ui.home.HomeFragment";
    private static final String FIHIRANA = "com.fa.baiboly.ui.fihirana.FihiranaFragment";
    private static final String SONG_PICKER = "com.fa.baiboly.ui.fandaharana.SongPickerSheet";
    private static final String BIBLE_PICKER = "com.fa.baiboly.ui.fandaharana.BiblePickerSheet";

    /** Voile posé sur le fond d'écran (le texte reste lisible). */
    private static final int SCRIM_LIGHT = 0x73FFFFFF; // blanc ~45 %
    private static final int SCRIM_DARK = 0xB3000000;  // noir ~70 %
    /** Voile plus dense sur les cartes, qui contiennent du texte. */
    private static final int CARD_SCRIM_LIGHT = 0xB3FFFFFF; // blanc ~70 %
    private static final int CARD_SCRIM_DARK = 0xCC000000;  // noir ~80 %

    private static final SparseArray<Bitmap> CACHE = new SparseArray<>();

    private AppBackgrounds() {
    }

    /** Images proposées pour le fond de l'accueil. */
    public static int[] getHomeBackgroundChoices() {
        return HOME_CHOICES.clone();
    }

    /** Image de fond de l'accueil actuellement choisie. */
    public static int getHomeBackground(Context context) {
        int saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_HOME_BACKGROUND, DEFAULT_HOME);
        return isHomeChoice(saved) ? saved : DEFAULT_HOME;
    }

    public static void setHomeBackground(Context context, int resId) {
        if (!isHomeChoice(resId)) {
            return;
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_HOME_BACKGROUND, resId).apply();
    }

    private static boolean isHomeChoice(int resId) {
        for (int choice : HOME_CHOICES) {
            if (choice == resId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pose la photo de l'écran et décore ses cartes. La photo dépend de la clé
     * de l'écran (classe du fragment ou de l'activité) : elle ne change pas
     * d'une ouverture à l'autre.
     */
    public static void applyScreen(@Nullable View root, @Nullable String screenKey) {
        applyScreen(root, screenKey, false);
    }

    public static void applyScreen(@Nullable View root, @Nullable String screenKey,
                                   boolean bottomSheet) {
        if (root == null || screenKey == null) {
            return;
        }
        Context context = root.getContext();
        boolean night = isNight(context);
        int photoRes = photoForKey(context, screenKey);
        Bitmap bitmap = getBitmap(context, photoRes);
        if (bitmap == null) {
            return;
        }

        String tag = screenKey + "#" + photoRes;
        if (!tag.equals(root.getTag(R.id.tag_bg_screen))) {
            root.setTag(R.id.tag_bg_screen, tag);
            float topRadius = bottomSheet
                    ? 20f * context.getResources().getDisplayMetrics().density : 0f;
            root.setBackground(new PhotoBackgroundDrawable(bitmap,
                    night ? SCRIM_DARK : SCRIM_LIGHT, 0f, topRadius));
        }

        // Sur l'écran de saisie du chant, seule la photo de fond compte : la
        // barre de recherche et les cartes gardent leur style.
        boolean allowCards = !bottomSheet && !FIHIRANA.equals(screenKey);
        decorateCards(root, night, photoRes, allowCards);
    }

    /** Réapplique le fond de l'accueil quand le choix a changé dans les paramètres. */
    public static void refreshHomeBackground(@Nullable View root) {
        applyScreen(root, HOME);
    }

    /** Dans ces écrans, seule la photo de fond compte : les cartes gardent leur style. */
    private static int photoForKey(Context context, String screenKey) {
        if (HOME.equals(screenKey)) {
            return getHomeBackground(context);
        }
        if (FIHIRANA.equals(screenKey)) {
            return R.drawable.fond_12; // "chant 1"
        }
        if (SONG_PICKER.equals(screenKey)) {
            return R.drawable.fond_13; // "chant"
        }
        if (BIBLE_PICKER.equals(screenKey)) {
            return R.drawable.fond_05;
        }
        int index = Math.floorMod(screenKey.hashCode(), PHOTOS.length);
        return PHOTOS[index];
    }

    private static void decorateCards(View view, boolean night, int screenPhoto, boolean allowCards) {
        // Sous-arbre à laisser intact (ex. grille des livres : couleurs d'origine).
        if ("bg_no_cards".equals(view.getTag())) {
            return;
        }
        if (view instanceof MaterialCardView) {
            applyCardBackground((MaterialCardView) view, night, screenPhoto, allowCards);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                decorateCards(group.getChildAt(i), night, screenPhoto, allowCards);
            }
        }
    }

    /**
     * Photo placée derrière le contenu de la carte. On remplace le fond de la
     * carte (et non un enfant) pour ne pas modifier sa mesure : les cartes en
     * wrap_content conservent leur hauteur.
     */
    private static void applyCardBackground(MaterialCardView card, boolean night,
                                            int screenPhoto, boolean allowCards) {
        if (!allowCards || card.getTag(R.id.tag_bg_card) != null) {
            return;
        }
        int photoRes = cardPhoto(card.getId(), screenPhoto);
        if (photoRes == 0) {
            return;
        }
        card.setTag(R.id.tag_bg_card, Boolean.TRUE);

        Context context = card.getContext();
        Bitmap bitmap = getBitmap(context, photoRes);
        if (bitmap == null) {
            return;
        }
        float radius = Math.max(0f, card.getRadius());
        card.setClipToOutline(true);
        card.setBackground(new PhotoBackgroundDrawable(bitmap,
                night ? CARD_SCRIM_DARK : CARD_SCRIM_LIGHT, radius));
    }

    /**
     * Chaque carte de l'accueil a sa propre photo ; la carte "Aina sy
     * fahasalamana" garde son image d'origine (id cardAina -> aucune photo).
     */
    private static int cardPhoto(int cardId, int screenPhoto) {
        if (cardId == R.id.cardAina) {
            return 0;
        }
        int res;
        if (cardId == R.id.cardReading) {
            res = R.drawable.fond_02;
        } else if (cardId == R.id.cardPerikopa) {
            res = R.drawable.fond_04; // perikopa.jpg
        } else if (cardId == R.id.cardHistory) {
            res = R.drawable.fond_02;
        } else if (cardId == R.id.cardMofonaina) {
            res = R.drawable.fond_08;
        } else {
            return screenPhoto;
        }
        if (res == screenPhoto) {
            int index = 0;
            for (int i = 0; i < PHOTOS.length; i++) {
                if (PHOTOS[i] == res) {
                    index = i;
                    break;
                }
            }
            res = PHOTOS[(index + 1) % PHOTOS.length];
        }
        return res;
    }

    private static boolean isNight(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private static Bitmap getBitmap(Context context, int resId) {
        synchronized (CACHE) {
            Bitmap cached = CACHE.get(resId);
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resId);
            if (bitmap != null) {
                CACHE.put(resId, bitmap);
            }
            return bitmap;
        }
    }
}
