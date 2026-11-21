package com.fa.baiboly.ui.common;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Matériau Liquid Glass d'une carte : l'arrière-plan de l'écran est réellement
 * flouté (bitmap précalculé et aligné sur la position de la carte), puis recouvert
 * d'une teinte translucide, d'un reflet supérieur, de bords lumineux et d'une
 * ombre interne discrète.
 *
 * <p>L'alignement est recalculé à chaque dessin à partir de la position réelle de
 * la carte : le verre réfracte bien la portion de fond qui se trouve derrière lui,
 * y compris pendant le défilement. Aucun rendu de l'écran n'est capturé.</p>
 */
public class GlassSurfaceDrawable extends Drawable {

    /** Léger grossissement de la zone réfractée (effet loupe du verre). */
    private static final float REFRACTION_ZOOM = 1.05f;
    /**
     * Part du voile de l'écran conservée dans le fond réfracté. Le verre doit
     * montrer la photo plus vivement que la surface environnante, sinon la
     * carte se confond avec l'arrière-plan et l'effet est invisible.
     */
    private static final int BACKDROP_SCRIM_PERCENT = 45;
    /**
     * ÉTAPE DE MISE AU POINT. {@code false} : aucune image de fond n'est
     * dessinée, la carte n'est qu'une teinte translucide — on vérifie d'abord
     * qu'on voit bien la photo de l'écran à travers. {@code true} : verre
     * complet (fond flouté + reflet + ombre).
     */
    private static final boolean BLUR_ENABLED = true;

    private final GlassCardHelper.Config config;
    private final View card;

    private View backgroundRoot;
    private int photoRes;
    private int scrim;

    private Bitmap backdrop;
    private int backdropWidth;
    private int backdropHeight;
    private int backdropRootWidth;
    private int backdropRootHeight;

    private final Paint bitmapPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Le fond est dessiné en CLAMP : hors du bitmap, le bord est prolongé,
     *  jamais étiré (aucune déformation quand la carte dépasse du fond). */
    private android.graphics.BitmapShader backdropShader;
    private Bitmap shaderBitmap;

    private final RectF cardRect = new RectF();
    private final RectF srcRect = new RectF();
    private final RectF dstRect = new RectF();
    private final android.graphics.Matrix bitmapMatrix = new android.graphics.Matrix();
    private final Path clipPath = new Path();

    private final float radius;
    private final float borderWidth;
    private int alpha = 255;
    private boolean shadersReady;
    private boolean logged;

    GlassSurfaceDrawable(@NonNull GlassCardHelper.Config config,
                         @NonNull View card,
                         @Nullable View backgroundRoot,
                         int photoRes, int scrim, float radius) {
        this.config = config;
        this.card = card;
        this.backgroundRoot = backgroundRoot;
        this.photoRes = photoRes;
        this.scrim = scrim;
        this.radius = radius;
        this.borderWidth = GlassCardHelper.dp(card.getContext(), config.borderWidth);

        tintPaint.setColor(config.tintColor);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidth);
    }

    /** Change la teinte du verre (cartes colorées) sans recréer le drawable. */
    public void setGlassTint(int color) {
        config.tintColor = color;
        tintPaint.setColor(color);
        applyAlpha();
        invalidateSelf();
    }

    /** Met à jour la photo de fond sans recréer le drawable. */
    void update(@Nullable View backgroundRoot, int photoRes, int scrim) {
        if (this.backgroundRoot == backgroundRoot
                && this.photoRes == photoRes && this.scrim == scrim) {
            return;
        }
        this.backgroundRoot = backgroundRoot;
        this.photoRes = photoRes;
        this.scrim = scrim;
        this.backdrop = null;
        this.backdropShader = null;
        this.shaderBitmap = null;
        this.backdropWidth = 0;
        this.backdropHeight = 0;
        this.backdropRootWidth = 0;
        this.backdropRootHeight = 0;
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(@NonNull android.graphics.Rect bounds) {
        super.onBoundsChange(bounds);
        cardRect.set(bounds);
        dstRect.set(0f, 0f, bounds.width(), bounds.height());

        clipPath.reset();
        clipPath.addRoundRect(cardRect, radius, radius, Path.Direction.CW);

        int width = bounds.width();
        int height = bounds.height();
        float half = height * 0.55f;

        if (width > 0 && height > 0) {
            highlightPaint.setShader(new LinearGradient(0f, 0f, 0f, half,
                    config.highlightColor, config.highlightColor & 0x00FFFFFF,
                    Shader.TileMode.CLAMP));
            shadePaint.setShader(new LinearGradient(0f, height * 0.65f, 0f, height,
                    0x00000000, config.shadeColor, Shader.TileMode.CLAMP));
            borderPaint.setShader(new LinearGradient(0f, 0f, width, height,
                    config.borderColor, config.borderColorEnd, Shader.TileMode.CLAMP));
        } else {
            highlightPaint.setShader(null);
            shadePaint.setShader(null);
            borderPaint.setShader(null);
            borderPaint.setColor(config.borderColor);
        }
        shadersReady = true;
        applyAlpha();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (getBounds().isEmpty()) {
            return;
        }
        if (!shadersReady) {
            onBoundsChange(getBounds());
        }

        if (BLUR_ENABLED) {
            ensureBackdrop();
        }

        int width = getBounds().width();
        int height = getBounds().height();

        if (!logged) {
            logged = true;
            View root = backgroundRoot;
            android.util.Log.d("GlassDbg", "draw bounds=" + getBounds()
                    + " root=" + (root == null ? "null"
                    : root.getWidth() + "x" + root.getHeight())
                    + " photoRes=" + photoRes + " blur=" + BLUR_ENABLED
                    + " backdrop=" + (backdrop == null ? "null" : backdrop.getWidth()
                    + "x" + backdrop.getHeight())
                    + " tint=" + Integer.toHexString(config.tintColor));
        }

        int save = canvas.save();
        canvas.clipPath(clipPath);

        int inner = canvas.save();
        canvas.translate(getBounds().left, getBounds().top);

        if (BLUR_ENABLED && backdrop != null && !backdrop.isRecycled()) {
            computeSourceRect(width, height);
            if (!srcRect.isEmpty()) {
                // Shader en CLAMP : la zone lue correspond exactement à la carte.
                // Si elle dépasse du bitmap, le bord est prolongé — la carte
                // n'étire jamais quelques pixels sur toute sa surface.
                if (backdropShader == null || shaderBitmap != backdrop) {
                    backdropShader = new android.graphics.BitmapShader(backdrop,
                            Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    shaderBitmap = backdrop;
                    bitmapPaint.setShader(backdropShader);
                }
                float sx = width / srcRect.width();
                float sy = height / srcRect.height();
                bitmapMatrix.setScale(sx, sy);
                bitmapMatrix.postTranslate(-srcRect.left * sx, -srcRect.top * sy);
                backdropShader.setLocalMatrix(bitmapMatrix);
                canvas.drawRect(dstRect, bitmapPaint);
            }
        }


        // Teinte translucide seule : le fond de l'écran doit rester visible.
        canvas.drawRect(dstRect, tintPaint);
        if (BLUR_ENABLED) {
            canvas.drawRect(dstRect, highlightPaint);
            canvas.drawRect(dstRect, shadePaint);
        }

        canvas.restoreToCount(inner);

        // Bordure dessinée à l'intérieur du bord arrondi.
        float inset = borderWidth / 2f;
        cardRect.set(getBounds());
        cardRect.inset(inset, inset);
        float innerRadius = Math.max(0f, radius - inset);
        canvas.drawRoundRect(cardRect, innerRadius, innerRadius, borderPaint);

        canvas.restoreToCount(save);
    }

    /** Construit (une seule fois par photo/taille) le fond flouté. */
    private void ensureBackdrop() {
        if (backgroundRoot == null || photoRes == 0) {
            return;
        }
        int width = backgroundRoot.getWidth();
        int height = backgroundRoot.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (backdrop != null && !backdrop.isRecycled()
                && backdropWidth > 0 && backdropHeight > 0
                && backdropRootWidth == width && backdropRootHeight == height) {
            return;
        }
        int scrimAlpha = android.graphics.Color.alpha(scrim);
        int backdropScrim = (scrim & 0x00FFFFFF)
                | ((scrimAlpha * BACKDROP_SCRIM_PERCENT / 100) << 24);
        Bitmap blurred = GlassBlur.backdrop(card.getContext(), photoRes,
                width, height, backdropScrim, config.saturation);
        if (blurred != null) {
            backdrop = blurred;
            backdropWidth = blurred.getWidth();
            backdropHeight = blurred.getHeight();
            backdropRootWidth = width;
            backdropRootHeight = height;
        }
    }

    /**
     * Région du fond flouté réellement située derrière la carte. Le calcul suit
     * la position de la carte dans l'écran, donc le verre reste cohérent quand
     * l'utilisateur fait défiler le contenu.
     */
    private void computeSourceRect(int width, int height) {
        View root = backgroundRoot;
        if (root == null) {
            srcRect.set(0f, 0f, backdropWidth, backdropHeight);
            return;
        }
        int rootWidth = root.getWidth();
        int rootHeight = root.getHeight();
        if (rootWidth <= 0 || rootHeight <= 0) {
            srcRect.set(0f, 0f, backdropWidth, backdropHeight);
            return;
        }

        int[] cardLocation = new int[2];
        int[] rootLocation = new int[2];
        card.getLocationInWindow(cardLocation);
        root.getLocationInWindow(rootLocation);

        float dx = cardLocation[0] - rootLocation[0];
        float dy = cardLocation[1] - rootLocation[1];

        float scaleX = backdropWidth / (float) rootWidth;
        float scaleY = backdropHeight / (float) rootHeight;

        srcRect.set(dx * scaleX, dy * scaleY,
                (dx + width) * scaleX, (dy + height) * scaleY);

        // Effet de réfraction : on agrandit très légèrement la zone lue.
        float zoomW = srcRect.width() * REFRACTION_ZOOM;
        float zoomH = srcRect.height() * REFRACTION_ZOOM;
        float cx = srcRect.centerX();
        float cy = srcRect.centerY();
        srcRect.set(cx - zoomW / 2f, cy - zoomH / 2f, cx + zoomW / 2f, cy + zoomH / 2f);

        // Volontairement pas de intersect() ici : raccourcir la zone lue
        // reviendrait à l'étirer sur la carte. Le shader en CLAMP gère les
        // débordements en prolongeant les bords du bitmap.
    }

    private void applyAlpha() {
        bitmapPaint.setAlpha(alpha);
        tintPaint.setAlpha(alpha);
        highlightPaint.setAlpha(alpha);
        shadePaint.setAlpha(alpha);
        borderPaint.setAlpha(alpha);
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        outline.setRoundRect(getBounds(), radius);
        outline.setAlpha(1f);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        applyAlpha();
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        bitmapPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
