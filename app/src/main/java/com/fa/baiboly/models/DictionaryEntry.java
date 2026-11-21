package com.fa.baiboly.models;

/**
 * Une entrée du dictionnaire biblique malgache : le mot, sa forme normalisée
 * (pour la recherche), une précision éventuelle et sa définition.
 */
public class DictionaryEntry {

    private final int id;
    private final String headword;
    private final String headwordNorm;
    private final String qualifier;
    private final String definition;

    public DictionaryEntry(int id, String headword, String headwordNorm,
                           String qualifier, String definition) {
        this.id = id;
        this.headword = headword;
        this.headwordNorm = headwordNorm;
        this.qualifier = qualifier;
        this.definition = definition;
    }

    public int getId() {
        return id;
    }

    public String getHeadword() {
        return headword;
    }

    public String getHeadwordNorm() {
        return headwordNorm;
    }

    public String getQualifier() {
        return qualifier;
    }

    public String getDefinition() {
        return definition;
    }

    /** Aperçu tronqué de la définition, pour les cartes et les résultats. */
    public String getPreview(int maxChars) {
        if (definition == null) return "";
        String text = definition.trim();
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars).trim() + "\u2026";
    }
}
