package com.fa.baiboly.models;

import java.util.ArrayList;
import java.util.List;

/**
 * 📝 Note de lecture : texte enrichi attaché à un verset, un chant ou un
 * programme — ou note libre sans référence.
 * Le corps est sérialisé en JSON par le moteur de texte enrichi
 * ({@code com.fa.baiboly.data.notes.richtext.RichTextCodec}).
 */
public class Note {

    // Types de source (à quoi la note est-elle attachée ?)
    public static final String TYPE_FREE = "free";
    public static final String TYPE_VERSE = "verse";
    public static final String TYPE_SONG = "song";
    public static final String TYPE_PROGRAM = "program";

    /** Couleurs de carte (dans l'ordre du sélecteur de l'éditeur). */
    public static final String[] CARD_COLORS = {
            null, "#FFFFFF", "#FFF9C4", "#C8E6C9",
            "#BBDEFB", "#F8BBD0", "#D1C4E9", "#FFE0B2"
    };

    public long id = -1;
    public String sourceType = TYPE_FREE;
    /** Référence courte affichée (ex. "MAT 3:16", "FFPM 49", "Fandaharana"). */
    public String refLabel = "";
    /** Clé stable de la source (ex. "verse:MAT:3:16:17", "song:ffpm_49"). */
    public String refKey = "";
    /** Corps en JSON (segments enrichis). */
    public String bodyJson = "";
    /** Extrait en texte brut (recherche + aperçu de liste). */
    public String plainText = "";
    /** Couleur de carte (hex ou null = défaut). */
    public String color;
    public boolean pinned;
    public long createdAt;
    public long updatedAt;

    /** Premier titre extrait (H1/H2 sinon première ligne non vide), pour la liste. */
    public transient String displayTitle = "";
    /** Première ligne de contenu (après le titre), pour la liste. */
    public transient String displaySnippet = "";

    public boolean isAttachable() {
        return !TYPE_FREE.equals(sourceType);
    }
}
