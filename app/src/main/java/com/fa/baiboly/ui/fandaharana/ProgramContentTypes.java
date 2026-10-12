package com.fa.baiboly.ui.fandaharana;

import com.fa.baiboly.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalogue central des types de contenus que peut contenir un programme.
 * Chaque type porte son libellé, son icône et sa couleur (cercle coloré
 * du même style que les items existants).
 */
public final class ProgramContentTypes {

    public static class TypeDef {
        public final String id;
        public final String label;
        public final int iconRes;
        public final String colorHex;

        public TypeDef(String id, String label, int iconRes, String colorHex) {
            this.id = id;
            this.label = label;
            this.iconRes = iconRes;
            this.colorHex = colorHex;
        }
    }

    // Couleurs de l'app (mêmes teintes que les items existants)
    public static final String COLOR_VERSE = "#2E7D32";
    public static final String COLOR_SONG = "#C40E5A";
    public static final String COLOR_FANEKENA = "#7B1FA2";

    /** Tous les types (catalogue complet, pour afficher les anciens items). */
    public static final List<TypeDef> TYPES = new ArrayList<>();

    /**
     * Types proposés à l'ajout : uniquement ce que l'app sait lire
     * (vakiteny, hira, fanekena). Les autres types ne servaient à rien :
     * aucun lecteur ne peut les ouvrir.
     */
    public static final List<TypeDef> SELECTABLE = new ArrayList<>();

    static {
        SELECTABLE.add(new TypeDef("verse", "Vakiteny", R.drawable.ic_bible, COLOR_VERSE));
        SELECTABLE.add(new TypeDef("song", "Hira", R.drawable.ic_music_note, COLOR_SONG));
        SELECTABLE.add(new TypeDef("fanekena", "Fanekena", R.drawable.ic_dashboard_black_24dp, COLOR_FANEKENA));

        // Catalogue complet conservé pour l'affichage des anciens items
        TYPES.addAll(SELECTABLE);
        TYPES.add(new TypeDef("prayer", "Vavaka", R.drawable.ic_prayer, "#0F766E"));
        TYPES.add(new TypeDef("text", "Lahatsoratra", R.drawable.ic_note, "#374151"));
        TYPES.add(new TypeDef("preaching", "Toriteny", R.drawable.ic_mic, "#B45309"));
        TYPES.add(new TypeDef("study", "Fianarana", R.drawable.ic_study, "#1D4ED8"));
        TYPES.add(new TypeDef("announcement", "Fanambarana", R.drawable.ic_apps, "#6D28D9"));
        TYPES.add(new TypeDef("event", "Zava-nisy", R.drawable.ic_event, "#BE123C"));
        TYPES.add(new TypeDef("image", "Sary", R.drawable.ic_image, "#0891B2"));
        TYPES.add(new TypeDef("audio", "Feo", R.drawable.ic_audio, "#7C3AED"));
        TYPES.add(new TypeDef("video", "Sarimihetsika", R.drawable.ic_video, "#DC2626"));
        TYPES.add(new TypeDef("document", "Antontan-taratasy", R.drawable.ic_document, "#4D7C0F"));
        TYPES.add(new TypeDef("link", "Rohy", R.drawable.ic_link, "#2563EB"));
        TYPES.add(new TypeDef("other", "Hafa", R.drawable.ic_apps, "#6B7280"));
    }

    /** Vrai si l'app a un lecteur pour ce type (lisible en session). */
    public static boolean isReadable(String typeId) {
        return "verse".equals(typeId)
                || "song".equals(typeId)
                || "fanekena".equals(typeId);
    }

    /** Type par id (null si inconnu). */
    public static TypeDef byId(String id) {
        if (id == null) return null;
        for (TypeDef t : TYPES) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    /** Couleur hexadécimale d'un type (fallback vert Vakiteny). */
    public static String colorOf(String typeId) {
        TypeDef t = byId(typeId);
        return t != null ? t.colorHex : COLOR_VERSE;
    }

    /** Icône d'un type (fallback icône Bible). */
    public static int iconOf(String typeId) {
        TypeDef t = byId(typeId);
        return t != null ? t.iconRes : R.drawable.ic_bible;
    }

    /** Libellé d'un type (fallback "Vakiteny"). */
    public static String labelOf(String typeId) {
        TypeDef t = byId(typeId);
        return t != null ? t.label : "Vakiteny";
    }
}
