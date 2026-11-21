package com.fa.baiboly.ui.common;

import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

/**
 * Permute le titre de la barre du haut avec le même glissement vertical que
 * Fihirana : l'ancien texte sort vers le haut en s'estompant, le nouveau
 * entre par le bas.
 */
public final class ScrollTitleAnimator {

    private ScrollTitleAnimator() {
    }

    public static void animate(TextView title, String newTitle) {
        if (title == null) {
            return;
        }

        String value = newTitle == null ? "" : newTitle;
        if (value.contentEquals(title.getText())) {
            return;
        }

        title.animate().cancel();
        title.setTranslationY(0f);
        title.setAlpha(1f);

        float slide = Math.max(title.getHeight() * 0.6f, 20f);

        title.animate()
                .translationY(-slide)
                .alpha(0f)
                .setDuration(140)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    title.setText(value);
                    title.setTranslationY(slide);
                    title.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(180)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                })
                .start();
    }
}
