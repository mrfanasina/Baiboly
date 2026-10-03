package com.fa.baiboly.ui.ai;

import android.content.Context;
import android.graphics.Typeface;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.fa.baiboly.R;

import org.commonmark.node.Heading;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.core.CoreProps;

/**
 * Instance unique de Markwon pour tout le chat IA, avec un style de titres
 * personnalisé (gras + couleur d'accent + taille dégressive selon le niveau
 * ###/##/#).
 */
public class MarkwonProvider {

    private static volatile Markwon instance;

    public static Markwon get(Context context) {

        if (instance == null) {
            synchronized (MarkwonProvider.class) {
                if (instance == null) {
                    instance = build(context.getApplicationContext());
                }
            }
        }

        return instance;
    }

    private static Markwon build(Context appContext) {

        final int accentColor = ContextCompat.getColor(appContext, R.color.teal_200);

        return Markwon.builder(appContext)
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {

                        // Remplace le style par défaut des titres (# .. ######)
                        // par : gras + couleur d'accent + taille selon le niveau.
                        builder.setFactory(Heading.class, (configuration, props) -> {

                            int level = CoreProps.HEADING_LEVEL.require(props);

                            float sizeMultiplier;
                            switch (level) {
                                case 1: sizeMultiplier = 1.45f; break;
                                case 2: sizeMultiplier = 1.30f; break;
                                case 3: sizeMultiplier = 1.18f; break;
                                default: sizeMultiplier = 1.08f; break;
                            }

                            return new Object[]{
                                    new StyleSpan(Typeface.BOLD),
                                    new ForegroundColorSpan(accentColor),
                                    new RelativeSizeSpan(sizeMultiplier)
                            };
                        });
                    }
                })
                .build();
        // NB : pas besoin d'ajouter CorePlugin explicitement, Markwon.builder(context)
        // l'inclut déjà par défaut — c'est lui qui gère **gras**, *italique*,
        // les listes à puces, etc. On ne fait ici que personnaliser les titres.
    }
}