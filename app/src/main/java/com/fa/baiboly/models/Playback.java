package com.fa.baiboly.models;

import java.io.Serializable;

/**
 * Playback (feo) d'un chant : fichier audio (mp3, m4a, wav...) identifié
 * par la catégorie et le numéro du chant (ex. "ffpm" + 49).
 *
 * Un chant peut avoir PLUSIEURS versions de playback (ex. "1", "2",
 * "Serveur") : {@link #label} distingue les versions,
 * {@link #docUri} pointe le fichier quand il est stocké dans un dossier
 * choisi par l'utilisateur (SAF).
 */
public class Playback implements Serializable {

    /** Ajouté depuis le stockage du téléphone. */
    public static final String SOURCE_LOCAL = "local";
    /** Téléchargé depuis le serveur (API catégorie + numéro). */
    public static final String SOURCE_SERVER = "server";
    /**
     * Détecté automatiquement dans un dossier choisi par l'utilisateur :
     * le fichier n'est PAS copié, on pointe directement son URI SAF.
     */
    public static final String SOURCE_FOLDER = "folder";

    private final long id;
    /** Catégorie du chant, en minuscules (ex. "ffpm"). */
    private final String category;
    private final int number;
    /** Titre affichable (ex. "FFPM 49"). */
    private final String title;
    /**
     * Chemin jouable : chemin absolu du fichier, ou URI "content://" quand
     * le fichier est dans un dossier choisi par l'utilisateur (SAF).
     */
    private final String filePath;
    private final String source;
    private final long createdAt;
    /** Nom de la version (ex. "1", "2", "Serveur"), null si non nommée. */
    private final String label;
    /** Uri SAF du fichier (dossier choisi par l'user), null sinon. */
    private final String docUri;

    public Playback(long id, String category, int number, String title,
                    String filePath, String source, long createdAt) {
        this(id, category, number, title, filePath, source, createdAt, null, null);
    }

    public Playback(long id, String category, int number, String title,
                    String filePath, String source, long createdAt,
                    String label, String docUri) {
        this.id = id;
        this.category = category;
        this.number = number;
        this.title = title;
        this.filePath = filePath;
        this.source = source;
        this.createdAt = createdAt;
        this.label = label;
        this.docUri = docUri;
    }

    public long getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getSource() {
        return source;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getLabel() {
        return label;
    }

    public String getDocUri() {
        return docUri;
    }

    /**
     * Libellé affichable d'une ligne de la liste :
     * le label si l'en a donné un ("1", "vihidy"…), "Serveur" pour un
     * téléchargement, sinon juste le TITRE du chant trouvé dans le nom du
     * fichier (ex. "Jehovah ô deraina Ianao") — jamais "Version 1".
     */
    public String displayLabel(int index) {
        if (label != null && !label.isEmpty()) return label;
        if (SOURCE_SERVER.equals(source)) return "Serveur";
        if (title != null && !title.trim().isEmpty()) return title.trim();
        return "Version " + (index + 1);
    }
}
