package com.fa.baiboly.ui.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Produit l'arrière-plan flouté utilisé par les cartes Liquid Glass.
 *
 * <p>Le fond d'écran de Baiboly est une photo statique déjà connue : on découpe
 * la même région que {@link PhotoBackgroundDrawable} (center-crop), on applique
 * le voile de lisibilité de l'écran, puis on floute. Le résultat est mis en
 * cache par (photo, taille, voile) : le flou n'est calculé qu'une seule fois et
 * aucun rendu à l'écran n'est capturé à chaque frame.</p>
 */
final class GlassBlur {

    /** Résolution maximale du bitmap flouté (le flou masque l'agrandissement). */
    private static final int MAX_DIMENSION = 360;
    /** Rayon du flou, en pixels du bitmap réduit. */
    private static final int RADIUS = 10;
    private static final int PASSES = 3;
    private static final int MAX_CACHE = 8;

    private static final LinkedHashMap<String, Bitmap> CACHE =
            new LinkedHashMap<String, Bitmap>(MAX_CACHE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
                    return size() > MAX_CACHE;
                }
            };

    private GlassBlur() {
    }

    /**
     * @param width  largeur de la vue qui porte la photo de fond
     * @param height hauteur de cette vue
     * @param scrim  voile appliqué par l'écran au-dessus de la photo
     * @return bitmap flouté couvrant exactement le fond de l'écran, ou null
     */
    @Nullable
    static Bitmap backdrop(Context context, int resId, int width, int height, int scrim,
                           float saturation) {
        if (resId == 0 || width <= 0 || height <= 0) {
            return null;
        }
        final String key = resId + "@" + width + "x" + height + "#" + scrim
                + "s" + saturation;
        synchronized (CACHE) {
            Bitmap cached = CACHE.get(key);
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
        }

        Bitmap blurred = build(context, resId, width, height, scrim, saturation);
        if (blurred != null) {
            synchronized (CACHE) {
                CACHE.put(key, blurred);
            }
        }
        return blurred;
    }

    @Nullable
    private static Bitmap build(Context context, int resId, int width, int height, int scrim,
                                float saturation) {
        try {
            int outW = width;
            int outH = height;
            float factor = Math.min(1f,
                    MAX_DIMENSION / (float) Math.max(width, height));
            outW = Math.max(1, Math.round(outW * factor));
            outH = Math.max(1, Math.round(outH * factor));

            Bitmap source = decode(context, resId, outW, outH);
            if (source == null) {
                return null;
            }

            Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

            int bw = source.getWidth();
            int bh = source.getHeight();
            float scale = Math.max(outW / (float) bw, outH / (float) bh);
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate((outW - bw * scale) / 2f, (outH - bh * scale) / 2f);
            canvas.drawBitmap(source, matrix, paint);

            // Voile de lisibilité : plus léger que celui de l'écran, pour que le
            // verre laisse réellement voir la photo derrière la carte.
            canvas.drawColor(scrim);

            if (source != out) {
                source.recycle();
            }

            if (saturation > 0f) {
                applySaturation(out, saturation);
            }
            blur(out, RADIUS);
            return out;
        } catch (OutOfMemoryError | IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    private static Bitmap decode(Context context, int resId, int targetW, int targetH) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(context.getResources(), resId, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= targetW
                && bounds.outHeight / (sample * 2) >= targetH) {
            sample *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeResource(context.getResources(), resId, options);
    }

    /** Rend les couleurs du fond plus vivantes, comme un vrai verre réfringent. */
    private static void applySaturation(Bitmap bitmap, float saturation) {
        android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
        matrix.setSaturation(saturation);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));

        Bitmap saturated = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                Bitmap.Config.ARGB_8888);
        new Canvas(saturated).drawBitmap(bitmap, 0f, 0f, paint);

        int w = saturated.getWidth();
        int h = saturated.getHeight();
        int[] pixels = new int[w * h];
        saturated.getPixels(pixels, 0, w, 0, 0, w, h);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        saturated.recycle();
    }

    /** Box blur séparable (3 passes : rendu très proche d'un flou gaussien). */
    private static void blur(Bitmap bitmap, int radius) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w <= 0 || h <= 0 || radius <= 0) {
            return;
        }
        int[] pixels = new int[w * h];
        int[] scratch = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        for (int i = 0; i < PASSES; i++) {
            horizontal(pixels, scratch, w, h, radius);
            vertical(scratch, pixels, w, h, radius);
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    private static void horizontal(int[] in, int[] out, int w, int h, int radius) {
        int div = radius * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -radius; i <= radius; i++) {
                int color = in[row + clamp(i, 0, w - 1)];
                sa += (color >>> 24);
                sr += (color >> 16) & 0xFF;
                sg += (color >> 8) & 0xFF;
                sb += color & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                out[row + x] = ((sa / div) << 24) | ((sr / div) << 16)
                        | ((sg / div) << 8) | (sb / div);

                int add = in[row + clamp(x + radius + 1, 0, w - 1)];
                int remove = in[row + clamp(x - radius, 0, w - 1)];
                sa += (add >>> 24) - (remove >>> 24);
                sr += ((add >> 16) & 0xFF) - ((remove >> 16) & 0xFF);
                sg += ((add >> 8) & 0xFF) - ((remove >> 8) & 0xFF);
                sb += (add & 0xFF) - (remove & 0xFF);
            }
        }
    }

    private static void vertical(int[] in, int[] out, int w, int h, int radius) {
        int div = radius * 2 + 1;
        for (int x = 0; x < w; x++) {
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -radius; i <= radius; i++) {
                int color = in[clamp(i, 0, h - 1) * w + x];
                sa += (color >>> 24);
                sr += (color >> 16) & 0xFF;
                sg += (color >> 8) & 0xFF;
                sb += color & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = ((sa / div) << 24) | ((sr / div) << 16)
                        | ((sg / div) << 8) | (sb / div);

                int add = in[clamp(y + radius + 1, 0, h - 1) * w + x];
                int remove = in[clamp(y - radius, 0, h - 1) * w + x];
                sa += (add >>> 24) - (remove >>> 24);
                sr += ((add >> 16) & 0xFF) - ((remove >> 16) & 0xFF);
                sg += ((add >> 8) & 0xFF) - ((remove >> 8) & 0xFF);
                sb += (add & 0xFF) - (remove & 0xFF);
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
