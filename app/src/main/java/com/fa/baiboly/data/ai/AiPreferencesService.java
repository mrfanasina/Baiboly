package com.fa.baiboly.data.ai;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Préférences de personnalisation de l'assistant IA (choisies dans les
 * Fikirakiràna) : style d'explication, niveau de profondeur, langue de
 * réponse et longueur. Chaque valeur est convertie en consigne de prompt
 * injectée dans le system instruction de Gemini.
 */
public class AiPreferencesService {

    private static final String PREFS_NAME = "app_settings";

    // =========================
    // CLÉS SharedPreferences
    // =========================
    public static final String KEY_AI_STYLE = "ai_style";
    public static final String KEY_AI_LEVEL = "ai_level";
    public static final String KEY_AI_LANGUAGE = "ai_language";
    public static final String KEY_AI_LENGTH = "ai_length";

    // =========================
    // STYLE (comment expliquer)
    // =========================
    public static final String STYLE_DEFAULT = "default";       // explication classique
    public static final String STYLE_HISTORY = "history";       // histoire ancienne / contexte
    public static final String STYLE_CHILDREN = "children";     // pour les enfants
    public static final String STYLE_DEEP = "deep";             // approfondi / théologique
    public static final String STYLE_SUMMARY = "summary";       // résumé simple

    // =========================
    // NIVEAU (public visé)
    // =========================
    public static final String LEVEL_BEGINNER = "beginner";     // nouveau croyant
    public static final String LEVEL_INTERMEDIATE = "intermediate"; // cathéchumène
    public static final String LEVEL_ADVANCED = "advanced";     // prédicateur / étudiant

    // =========================
    // LANGUE
    // =========================
    public static final String LANG_MALAGASY = "mg";
    public static final String LANG_FRENCH = "fr";
    public static final String LANG_SAME_AS_TEXT = "auto";

    // =========================
    // LONGUEUR
    // =========================
    public static final String LENGTH_SHORT = "short";          // réponse courte
    public static final String LENGTH_STANDARD = "standard";    // par défaut
    public static final String LENGTH_DETAILED = "detailed";    // détaillée

    private final SharedPreferences prefs;

    public AiPreferencesService(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // =========================
    // GETTERS (avec valeurs par défaut)
    // =========================

    public String getStyle() {
        return prefs.getString(KEY_AI_STYLE, STYLE_DEFAULT);
    }

    public String getLevel() {
        return prefs.getString(KEY_AI_LEVEL, LEVEL_INTERMEDIATE);
    }

    public String getLanguage() {
        return prefs.getString(KEY_AI_LANGUAGE, LANG_MALAGASY);
    }

    public String getLength() {
        return prefs.getString(KEY_AI_LENGTH, LENGTH_STANDARD);
    }

    // =========================
    // SETTERS
    // =========================

    public void setStyle(String style) {
        prefs.edit().putString(KEY_AI_STYLE, style).apply();
    }

    public void setLevel(String level) {
        prefs.edit().putString(KEY_AI_LEVEL, level).apply();
    }

    public void setLanguage(String language) {
        prefs.edit().putString(KEY_AI_LANGUAGE, language).apply();
    }

    public void setLength(String length) {
        prefs.edit().putString(KEY_AI_LENGTH, length).apply();
    }

    // =========================
    // CONSIGNES DE PROMPT
    // =========================

    /**
     * Consigne de style d'explication (histoire, enfants, approfondi,
     * résumé...). Chaîne vide pour le style par défaut.
     */
    public String stylePrompt() {
        switch (getStyle()) {
            case STYLE_HISTORY:
                return "Adopte un ton d'enseignement sur l'histoire ancienne : "
                        + "explique le contexte historique et culturel de l'époque "
                        + "du passage (peuples, coutumes, langues, géographie), "
                        + "l'arrière-plan historique du texte et les événements de "
                        + "l'époque qui éclairent sa signification.";

            case STYLE_CHILDREN:
                return "Explique comme à des enfants de 6 à 10 ans : utilise des "
                        + "mots très simples, des images concrètes et une petite "
                        + "histoire pour illustrer. Termine par une leçon facile à "
                        + "retenir. Évite les termes théologiques.";

            case STYLE_DEEP:
                return "Va en profondeur : analyse exégétique (texte original, "
                        + "grec/hebreu quand pertinent), contexte littéraire, "
                        + "structure du passage, théologie développée, et liens "
                        + "avec l'ensemble de la Bible. Niveau étudiant en théologie.";

            case STYLE_SUMMARY:
                return "Fais un résumé : l'idée principale du passage en une "
                        + "phrase, puis 2 ou 3 points clés maximum, puis une "
                        + "application pratique courte. Sois très concis.";

            default:
                return "Explique le passage de manière claire et accessible, "
                        + "avec le contexte utile et une application pratique.";
        }
    }

    /**
     * Consigne de niveau du public (ajustement du vocabulaire).
     * Chaîne vide pour le niveau intermédiaire.
     */
    public String levelPrompt() {
        switch (getLevel()) {
            case LEVEL_BEGINNER:
                return "Public : personne qui découvre la Bible. Vocabulaire "
                        + "simple, explique les mots chrétiens (grâce, alliance, "
                        + "sainteté...) et les références bibliques auxquelles tu "
                        + "fais allusion.";

            case LEVEL_ADVANCED:
                return "Public : catéchiste, prédicateur ou étudiant en théologie. "
                        + "Tu peux utiliser un vocabulaire théologique précis et "
                        + "des références croisées détaillées.";

            default:
                return ""; // intermédiaire : comportement standard
        }
    }

    /**
     * Consigne de langue de réponse.
     */
    public String languagePrompt() {
        switch (getLanguage()) {
            case LANG_FRENCH:
                return "Réponds en français.";
            case LANG_SAME_AS_TEXT:
                return "Réponds dans la langue du texte fourni par l'utilisateur.";
            default:
                return "Réponds en malgache (utilisant le vocabulaire biblique "
                        + "de la version MG1865 quand tu cites la Bible).";
        }
    }

    /**
     * Consigne de longueur de réponse.
     */
    public String lengthPrompt() {
        switch (getLength()) {
            case LENGTH_SHORT:
                return "Réponds brièvement : quelques phrases seulement.";
            case LENGTH_DETAILED:
                return "Réponds de façon détaillée et structurée : plusieurs "
                        + "paragraphes avec des sous-titres si utile.";
            default:
                return ""; // standard : pas de contrainte supplémentaire
        }
    }

    /**
     * Bloc complet de personnalisation à injecter dans le system
     * instruction (les lignes vides sont omises).
     */
    public String fullPrompt() {
        StringBuilder sb = new StringBuilder();
        appendLine(sb, stylePrompt());
        appendLine(sb, levelPrompt());
        appendLine(sb, languagePrompt());
        appendLine(sb, lengthPrompt());
        return sb.toString().trim();
    }

    private void appendLine(StringBuilder sb, String line) {
        if (line != null && !line.trim().isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(line.trim());
        }
    }
}
