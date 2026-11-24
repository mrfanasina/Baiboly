package com.fa.baiboly.ui.common;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Fond d'écran photo : l'image est recadrée (center-crop) pour remplir la vue,
 * puis un voile de couleur est appliqué par-dessus pour garder le texte lisible.
 * Les coins peuvent être arrondis (cartes).
 */
public class PhotoBackgroundDrawable extends Drawable {

    private final Bitmap bitmap;
    private final int scrimColor;
    private final float cornerRadius;
    private float topCornerRadius;
    private float bottomCornerRadius;

    private final Paint bitmapPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final Path clipPath = new Path();

    public PhotoBackgroundDrawable(@NonNull Bitmap bitmap, int scrimColor, float cornerRadius) {
        this(bitmap, scrimColor, cornerRadius, 0f);
    }

    /** Variante avec coins supérieurs arrondis seulement (bottom sheets). */
    public PhotoBackgroundDrawable(@NonNull Bitmap bitmap, int scrimColor,
                                   float cornerRadius, float topCornerRadius) {
        this.bitmap = bitmap;
        this.scrimColor = scrimColor;
        this.cornerRadius = cornerRadius;
        this.topCornerRadius = topCornerRadius;
        scrimPaint.setColor(scrimColor);
    }

    /** Arrondit le bas du fond (barre du haut qui plonge sur le contenu). */
    public void setBottomCornerRadius(float radius) {
        this.bottomCornerRadius = radius;
        clipPath.reset();
        buildClipPath(getBounds());
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        rect.set(bounds);
        updateMatrix(bounds);
        buildClipPath(bounds);
    }

    private void buildClipPath(Rect bounds) {
        clipPath.reset();
        rect.set(bounds);
        if (cornerRadius > 0f) {
            clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW);
        } else if (topCornerRadius > 0f || bottomCornerRadius > 0f) {
            float[] radii = {
                    topCornerRadius, topCornerRadius,
                    topCornerRadius, topCornerRadius,
                    bottomCornerRadius, bottomCornerRadius,
                    bottomCornerRadius, bottomCornerRadius
            };
            clipPath.addRoundRect(rect, radii, Path.Direction.CW);
        }
    }

    private void updateMatrix(Rect bounds) {
        int bw = bitmap.getWidth();
        int bh = bitmap.getHeight();
        if (bw <= 0 || bh <= 0 || bounds.isEmpty()) {
            return;
        }
        float scale = Math.max(bounds.width() / (float) bw, bounds.height() / (float) bh);
        float dx = bounds.left + (bounds.width() - bw * scale) / 2f;
        float dy = bounds.top + (bounds.height() - bh * scale) / 2f;
        matrix.reset();
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (bitmap.isRecycled()) {
            return;
        }
        int save = canvas.save();
        if (cornerRadius > 0f || topCornerRadius > 0f || bottomCornerRadius > 0f) {
            canvas.clipPath(clipPath);
        }
        canvas.drawBitmap(bitmap, matrix, bitmapPaint);
        canvas.drawRect(rect, scrimPaint);
        canvas.restoreToCount(save);
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        if (cornerRadius > 0f) {
            outline.setRoundRect(getBounds(), cornerRadius);
        } else {
            outline.setRect(getBounds());
        }
        outline.setAlpha(1f);
    }

    @Override
    public void setAlpha(int alpha) {
        bitmapPaint.setAlpha(alpha);
        scrimPaint.setAlpha(Math.round(alpha * (android.graphics.Color.alpha(scrimColor) / 255f)));
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

    // Volontairement aucune taille intrinsèque : ce fond est purement
    // décoratif. S'il en exposait une (les pixels de la photo), une carte
    // wrap_content (CardView est un FrameLayout) serait mesurée au moins à la
    // hauteur de l'image, d'où des cartes énormes avec du vide sous le texte.
}
