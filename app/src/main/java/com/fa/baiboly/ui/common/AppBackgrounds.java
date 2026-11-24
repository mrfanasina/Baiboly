package com.fa.baiboly.ui.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fa.baiboly.R;
import com.google.android.material.card.MaterialCardView;

/**
 * Applique les photos de fond de l'application (dossier images/ici) sur les
 * écrans et le style choisi sur les cartes. Les écrans de lecture ne doivent
 * PAS appeler ce gestionnaire (voir BaibolyApp pour les exclusions).
 *
 * Chaque écran garde toujours la même photo : plus de changement aléatoire.
 * L'accueil utilise un fond choisi dans les paramètres (par défaut une image
 * fixe). Le fond des cartes se règle de deux façons :
 * <ul>
 *   <li>{@link #CARD_STYLE_IMAGE} (par défaut) : photo de chaque carte, lue
 *       dans {@code res/values/card_backgrounds.xml} ;</li>
 *   <li>{@link #CARD_STYLE_SOLID} : fond uni noir (mode sombre) ou blanc
 *       (mode clair), sans photo.</li>
 * </ul>
 */
public final class AppBackgrounds {

    public static final String PREFS_NAME = "app_settings";
    public static final String KEY_HOME_BACKGROUND = "home_background";
    /** Style des cartes : photo individuelle ou fond uni noir/blanc. */
    public static final String KEY_CARD_STYLE = "card_background_style";
    public static final String CARD_STYLE_IMAGE = "image";
    /** Cartes sur fond uni : noir en mode sombre, blanc en mode clair. */
    public static final String CARD_STYLE_SOLID = "solid";
    /** Ancienne valeur enregistrée du style supprimé (Liquid Glass). */
    private static final String LEGACY_CARD_STYLE_GLASS = "glass";

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
            R.drawable.fond_100, R.drawable.fond_101, R.drawable.fond_102,
            R.drawable.fond_103, R.drawable.fond_104, R.drawable.fond_105,
            R.drawable.fond_107, R.drawable.fond_106, R.drawable.fond_108,
            R.drawable.fond_15, R.drawable.fond_109
    };

    private static final int DEFAULT_HOME = R.drawable.fond_08;

    private static final String HOME = "com.fa.baiboly.ui.home.HomeFragment";
    private static final String BAIBOLY = "com.fa.baiboly.ui.baiboly.BaibolyFragment";
    private static final String CHAPTER = "com.fa.baiboly.ui.baiboly.ChapterFragment";
    private static final String FIHIRANA = "com.fa.baiboly.ui.fihirana.FihiranaFragment";
    private static final String SONG_PICKER = "com.fa.baiboly.ui.fandaharana.SongPickerSheet";
    private static final String BIBLE_PICKER = "com.fa.baiboly.ui.fandaharana.BiblePickerSheet";

    /** Voile posé sur le fond d'écran (le texte reste lisible). */
    private static final int SCRIM_LIGHT = 0x73FFFFFF; // blanc ~45 %
    private static final int SCRIM_DARK = 0xB3000000;  // noir ~70 %
    /** Voile plus dense sur les cartes à photo, qui contiennent du texte. */
    private static final int CARD_SCRIM_LIGHT = 0xB3FFFFFF; // blanc ~70 %
    private static final int CARD_SCRIM_DARK = 0xCC000000;  // noir ~80 %
    /** Fond uni des cartes (style "Mainty na fotsy"). */
    private static final int CARD_SOLID_LIGHT = 0xFFFFFFFF; // blanc
    private static final int CARD_SOLID_DARK = 0xFF000000;  // noir
    /** Voile du verre des barres (toolbar) : translucide, la photo reste visible. */
    private static final int BAR_SCRIM_LIGHT = 0x99FFFFFF; // blanc ~60 %
    private static final int BAR_SCRIM_DARK = 0xB3000000;  // noir ~70 %
    /**
     * Opacité du verre des barres : le contenu qui défile dessous reste
     * deviné (texte en filigrane), au lieu d'être totalement masqué.
     */
    private static final int BAR_ALPHA = 0xEF;
    /** Rayon des coins BAS des barres (barre du haut, bottom nav). */
    private static final float BAR_CORNER_RADIUS_DP = 22f;

    private static final SparseArray<Bitmap> CACHE = new SparseArray<>();

    private AppBackgrounds() {
    }

    /** Style actuellement choisi pour les cartes (photo ou fond uni). */
    public static String getCardBackgroundStyle(Context context) {
        String saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_CARD_STYLE, CARD_STYLE_IMAGE);
        boolean solid = CARD_STYLE_SOLID.equals(saved)
                || LEGACY_CARD_STYLE_GLASS.equals(saved);
        return solid ? CARD_STYLE_SOLID : CARD_STYLE_IMAGE;
    }

    public static void setCardBackgroundStyle(Context context, String style) {
        String value = CARD_STYLE_SOLID.equals(style)
                ? CARD_STYLE_SOLID : CARD_STYLE_IMAGE;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_CARD_STYLE, value).apply();
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
        int scrim = night ? SCRIM_DARK : SCRIM_LIGHT;
        int photoRes = photoForKey(context, screenKey);
        Bitmap bitmap = getBitmap(context, photoRes);
        android.util.Log.d("GlassDbg", "applyScreen ".concat(String.valueOf(screenKey))
                .concat(" photo=").concat(String.valueOf(photoRes))
                .concat(" bitmap=").concat(bitmap == null ? "null" : "ok")
                .concat(" root=").concat(root.getWidth() + "x" + root.getHeight())
                .concat(" style=").concat(getCardBackgroundStyle(context)));

        if (bitmap != null) {
            String tag = screenKey + "#" + photoRes;
            if (!tag.equals(root.getTag(R.id.tag_bg_screen))) {
                root.setTag(R.id.tag_bg_screen, tag);
                float topRadius = bottomSheet
                        ? 20f * context.getResources().getDisplayMetrics().density : 0f;
                root.setBackground(new PhotoBackgroundDrawable(bitmap, scrim, 0f, topRadius));
            }
        }

        // Sur l'écran de saisie du chant, seule la photo de fond compte : la
        // barre de recherche et les cartes gardent leur style.
        boolean allowCards = !bottomSheet && !FIHIRANA.equals(screenKey);
        decorateCards(root, root, photoRes, night, allowCards);
    }

    /**
     * Fond photographique d'une carte isolée, sur un écran qui n'a pas de
     * photo de fond (le voile reste le même que sur les cartes d'accueil).
     */
    public static void applyPhotoToCard(@NonNull MaterialCardView card, int photoRes) {
        if (photoRes == 0) {
            return;
        }
        Bitmap bitmap = getBitmap(card.getContext(), photoRes);
        if (bitmap == null) {
            return;
        }
        float radius = Math.max(0f, card.getRadius());
        card.setTag(R.id.tag_bg_card, Boolean.TRUE);
        card.setClipToOutline(true);
        card.setBackground(new PhotoBackgroundDrawable(bitmap,
                isNight(card.getContext()) ? CARD_SCRIM_DARK : CARD_SCRIM_LIGHT,
                radius));
    }

    /**
     * Photo d'une carte isolée, choisie dans {@code card_backgrounds.xml}.
     * Ne fait rien si la carte n'y figure pas (ou y vaut {@code @null}).
     */
    public static void applyPhotoToCard(@NonNull MaterialCardView card) {
        int photoRes = mappedPhotoFor(card.getContext(), card.getId());
        if (photoRes > 0) {
            applyPhotoToCard(card, photoRes);
        }
    }

    /**
     * Applique le verre flouté de l'écran à une vue quelconque (champ de
     * saisie, barre…). La photo de référence est lue sur le fond de l'écran.
     */
    public static void applyGlassSurface(@NonNull View view, float radiusDp) {
        View root = view;
        while (root.getTag(R.id.tag_bg_screen) == null) {
            android.view.ViewParent parent = root.getParent();
            if (!(parent instanceof View)) {
                break;
            }
            root = (View) parent;
        }

        int photoRes = 0;
        Object tag = root.getTag(R.id.tag_bg_screen);
        if (tag instanceof String) {
            String value = (String) tag;
            int separator = value.lastIndexOf('#');
            if (separator >= 0) {
                try {
                    photoRes = Integer.parseInt(value.substring(separator + 1));
                } catch (NumberFormatException ignored) {
                    photoRes = 0;
                }
            }
        }

        Context context = view.getContext();
        GlassCardHelper.Config config = GlassCardHelper.config(context);
        int scrim = config.night ? SCRIM_DARK : SCRIM_LIGHT;
        float radius = radiusDp * context.getResources().getDisplayMetrics().density;
        view.setClipToOutline(true);
        view.setBackground(new GlassSurfaceDrawable(config, view, root, photoRes, scrim,
                radius));
    }

    /**
     * Pose la photo de l'écran derrière la barre du bas : les coins arrondis du
     * verre laissent ainsi voir le décor (au lieu du fond de fenêtre, noir en
     * mode sombre).
     */
    public static void applyBarBackdrop(@Nullable View root, int photoRes) {
        if (root == null || photoRes == 0) {
            return;
        }
        String tag = "backdrop#" + photoRes;
        if (tag.equals(root.getTag(R.id.tag_bg_screen))) {
            return;
        }
        Bitmap bitmap = getBitmap(root.getContext(), photoRes);
        if (bitmap == null) {
            return;
        }
        root.setTag(R.id.tag_bg_screen, tag);
        root.setBackground(new PhotoBackgroundDrawable(bitmap,
                isNight(root.getContext()) ? SCRIM_DARK : SCRIM_LIGHT, 0f, 0f));
    }

    /** Réapplique le fond de l'accueil quand le choix a changé dans les paramètres. */
    public static void refreshHomeBackground(@Nullable View root) {
        applyScreen(root, HOME);
    }

    /**
     * Applique un verre flouté à une barre (toolbar) : la photo de l'écran est
     * recadrée sur les dimensions de la barre, floutée puis voilée. La barre
     * reste transparente (on devine la photo) tout en gardant un texte lisible.
     */
    public static void applyGlassBar(@Nullable View bar, int photoRes) {
        applyGlassBar(bar, photoRes, 0f, BAR_CORNER_RADIUS_DP);
    }

    /**
     * Variante avec coins arrondis sur mesure : la barre du haut arrondit le
     * bas, la barre de navigation arrondit le haut.
     */
    public static void applyGlassBar(@Nullable View bar, int photoRes,
                                     float topRadiusDp, float bottomRadiusDp) {
        if (bar == null || photoRes == 0) {
            return;
        }
        Context context = bar.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        int width = bar.getWidth() > 0
                ? bar.getWidth()
                : context.getResources().getDisplayMetrics().widthPixels;
        int height = bar.getHeight() > 0 ? bar.getHeight() : Math.round(56 * density);
        boolean night = isNight(context);
        int scrim = night ? BAR_SCRIM_DARK : BAR_SCRIM_LIGHT;

        Bitmap blurred = GlassBlur.backdrop(context, photoRes, width, height, scrim, 1.1f);
        if (blurred != null) {
            // Le voile est déjà dans le bitmap flouté : pas de second voile.
            // Légère transparence : le contenu qui défile sous la barre
            // transparaît en filigrane.
            PhotoBackgroundDrawable background =
                    new PhotoBackgroundDrawable(blurred, 0x00000000, 0f,
                            topRadiusDp * density);
            background.setBottomCornerRadius(bottomRadiusDp * density);
            background.setAlpha(BAR_ALPHA);
            bar.setBackground(background);
        } else {
            bar.setBackgroundColor(scrim);
            bar.getBackground().setAlpha(BAR_ALPHA);
        }
    }

    /** Dans ces écrans, seule la photo de fond compte : les cartes gardent leur style. */
    private static int photoForKey(Context context, String screenKey) {
        if (HOME.equals(screenKey)) {
            return getHomeBackground(context);
        }
        if (BAIBOLY.equals(screenKey) || CHAPTER.equals(screenKey)) {
            return R.drawable.fond_12;
        }
        if (FIHIRANA.equals(screenKey)) {
            return R.drawable.fond_12; // "chant 1"
        }
        if (SONG_PICKER.equals(screenKey)) {
            return R.drawable.fond_12; // "chant"
        }
        if (BIBLE_PICKER.equals(screenKey)) {
            return R.drawable.fond_08;
        }
        int index = Math.floorMod(screenKey.hashCode(), PHOTOS.length);
        return PHOTOS[index];
    }

    private static void decorateCards(View view, View backgroundRoot, int screenPhoto,
                                      boolean night, boolean allowCards) {
        // Sous-arbre à laisser intact (ex. grille des livres : couleurs d'origine).
        if ("bg_no_cards".equals(view.getTag())) {
            return;
        }
        if (view instanceof MaterialCardView) {
            applyCardBackground((MaterialCardView) view, backgroundRoot, screenPhoto,
                    night, allowCards);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                decorateCards(group.getChildAt(i), backgroundRoot, screenPhoto, night,
                        allowCards);
            }
        }
    }

    /**
     * Applique le style choisi pour les cartes. On remplace le fond de la
     * carte (et non un enfant) pour ne pas modifier sa mesure : les cartes en
     * wrap_content conservent leur hauteur.
     *
     * <p>Photo : celle déclarée dans {@code res/values/card_backgrounds.xml}.
     * Une carte absente du fichier reçoit la photo de l'écran, sauf "Aina sy
     * Fahasalamana" (cardAina) qui garde son image de contenu.</p>
     */
    private static void applyCardBackground(MaterialCardView card, View backgroundRoot,
                                            int screenPhoto, boolean night,
                                            boolean allowCards) {
        if (!allowCards) {
            return;
        }

        if (CARD_STYLE_SOLID.equals(getCardBackgroundStyle(card.getContext()))) {
            applySolidCard(card, night);
            return;
        }

        // Déjà décorée par applyPhotoToCard ou un passage précédent.
        if (card.getBackground() instanceof PhotoBackgroundDrawable
                && card.getTag(R.id.tag_bg_card) != null) {
            return;
        }
        card.setTag(R.id.tag_bg_card, null);

        int photoRes = mappedPhotoFor(card.getContext(), card.getId());
        if (photoRes < 0) {
            // Absente du fichier : photo de l'écran, aucune pour cardAina.
            photoRes = card.getId() == R.id.cardAina ? 0 : screenPhoto;
        }
        if (photoRes == 0) {
            return;
        }
        Bitmap bitmap = getBitmap(card.getContext(), photoRes);
        if (bitmap == null) {
            return;
        }
        card.setTag(R.id.tag_bg_card, Boolean.TRUE);
        float radius = Math.max(0f, card.getRadius());
        card.setClipToOutline(true);
        card.setBackground(new PhotoBackgroundDrawable(bitmap,
                night ? CARD_SCRIM_DARK : CARD_SCRIM_LIGHT, radius));
    }

    /** Style "Mainty na fotsy" : fond uni, noir ou blanc selon le thème. */
    private static void applySolidCard(MaterialCardView card, boolean night) {
        card.setTag(R.id.tag_bg_card, null);
        GradientDrawable background = new GradientDrawable();
        background.setColor(night ? CARD_SOLID_DARK : CARD_SOLID_LIGHT);
        background.setCornerRadius(Math.max(0f, card.getRadius()));
        card.setClipToOutline(true);
        card.setBackground(background);
    }

    /**
     * Photo déclarée pour une carte dans {@code res/values/card_backgrounds.xml}.
     *
     * @return la ressource photo, {@code 0} si la carte y vaut {@code @null},
     *         ou {@code -1} si la carte n'y figure pas.
     */
    private static int mappedPhotoFor(Context context, int cardId) {
        if (cardId == View.NO_ID) {
            return -1;
        }
        TypedArray views = context.getResources()
                .obtainTypedArray(R.array.card_background_views);
        TypedArray photos = context.getResources()
                .obtainTypedArray(R.array.card_background_photos);
        try {
            int count = Math.min(views.length(), photos.length());
            for (int i = 0; i < count; i++) {
                if (views.getResourceId(i, 0) == cardId) {
                    return photos.getResourceId(i, 0);
                }
            }
            return -1;
        } finally {
            views.recycle();
            photos.recycle();
        }
    }

    static boolean isNight(Context context) {
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
