package com.fa.baiboly.data.transfer;

import android.net.Uri;

import com.fa.baiboly.models.Program;

import java.io.File;

/**
 * Un élément échangeable dans le transfert WiFi (send/receive).
 *
 * - type "program"  : un fandaharana complet (name + items), servi en JSON
 * - type "playback" : un fichier audio de chant (mp3/m4a/…), servi en binaire
 *   soit via File local, soit directement via l'URI content:// du dossier
 *   SAF choisi par l'utilisateur (streamé à la volée, sans copie).
 */
public class TransferItem {

    public static final String TYPE_PROGRAM = "program";
    public static final String TYPE_PLAYBACK = "playback";

    /** "program" ou "playback". */
    public String type;
    /** Titre affichable. */
    public String title;
    /** Sous-titre (nb d'items, taille, version…). */
    public String subtitle;
    /** Taille en octets (fichier audio). */
    public long size;

    // ---- program ----
    public Program program;

    // ---- playback ----
    /** Fichier audio résolu (fichier direct lisible). */
    public File file;
    /** URI content:// (dossier SAF) : streamé par le serveur à la demande. */
    public Uri contentUri;
    /** Type MIME de l'URI content:// (optionnel). */
    public String contentType;
    /** Extension audio ("mp3", "m4a"…). */
    public String ext;
    public String category;
    public int number;
    public String playTitle;
    /** Label de version ("1", "Serveur"…), null si non nommée. */
    public String label;

    /**
     * Clé stable de l'item (persistance de la sélection d'un écran à
     * l'autre, filtrage, etc.) sans dépendre d'une position d'index.
     */
    public String key() {
        if (TYPE_PROGRAM.equals(type)) {
            return "p|" + (title == null ? "" : title);
        }
        return "b|" + category + "|" + number + "|"
                + (label == null ? "" : label) + "|"
                + (playTitle == null ? "" : playTitle);
    }

    /** Nom de fichier proposé au récepteur. */
    public String fileName() {
        if (TYPE_PLAYBACK.equals(type) && file != null && file.getName() != null) {
            return file.getName();
        }
        if (TYPE_PROGRAM.equals(type)) {
            return (title == null ? "program" : title)
                    .replaceAll("[^a-zA-Z0-9-_ ]", "").trim() + ".json";
        }
        return "item.bin";
    }
}
