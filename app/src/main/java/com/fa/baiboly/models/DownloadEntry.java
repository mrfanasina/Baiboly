package com.fa.baiboly.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Un élément téléchargeable proposé par le serveur
 * (manifest.json) — par ex. "perikopa" d'une année,
 * ou le mofonaina du jour téléchargeable à l'avance.
 *
 * Le serveur doit exposer à BASE_URL un fichier manifest.json :
 * {
 *   "items": [
 *     { "type": "perikopa", "title": "Perikopa 2026", "url": "perikopa/2026.json", "size": 243000 },
 *     { "type": "mofonaina", "title": "Mofonaina 14 Oktobra 2026", "url": "mofonaina/2026-10-14.json", "size": 12000 }
 *   ]
 * }
 * Les urls sont relatives à BASE_URL.
 */
public class DownloadEntry {

    // Types connus (le champ type sert à choisir l'icône / la future importation)
    public static final String TYPE_APP = "app";
    public static final String TYPE_PERIKOPA = "perikopa";
    public static final String TYPE_MOFONAINA = "mofonaina";
    public static final String TYPE_DATA = "data";
    /** Entrée spéciale (pas un vrai téléchargement) : titre de catégorie. */
    public static final String TYPE_HEADER = "__header";

    private String type;
    private String title;
    private String subtitle;
    private String url;
    private long size;
    /**
     * Uniquement pour type = "app" (paquet de mise à jour de l'app) :
     * versionCode de la version proposée. 0 si absent/non-app.
     */
    private int versionCode;
    /** Affichage "1.2.0" (optionnel, pour la carte de mise à jour). */
    private String versionName;
    /** Date de publication (downloads.json : "2026-11-24"), optionnelle. */
    private String publishedAt;
    /** Catégorie d'appartenance (downloads.json : "Application Baiboly"…). */
    private String categoryTitle;
    /** Liste des nouveautés (update.json : champ "changelog"). */
    private List<String> changelog = new ArrayList<>();

    public DownloadEntry() {
    }

    public DownloadEntry(String type, String title, String subtitle, String url, long size) {
        this.type = type;
        this.title = title;
        this.subtitle = subtitle;
        this.url = url;
        this.size = size;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(int versionCode) {
        this.versionCode = versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getCategoryTitle() {
        return categoryTitle;
    }

    public void setCategoryTitle(String categoryTitle) {
        this.categoryTitle = categoryTitle;
    }

    public List<String> getChangelog() {
        return changelog;
    }

    public void setChangelog(List<String> changelog) {
        this.changelog = changelog != null ? changelog : new ArrayList<>();
    }

    /** Fabrique une ligne d'en-tête de catégorie pour l'écran Downloads. */
    public static DownloadEntry header(String title) {
        DownloadEntry e = new DownloadEntry();
        e.setType(TYPE_HEADER);
        e.setTitle(title);
        return e;
    }

    public boolean isHeader() {
        return TYPE_HEADER.equals(type);
    }

    /** Résolution de l'URL relative par rapport à BASE_URL. */
    public String absoluteUrl(String baseUrl) {
        if (url == null || url.isEmpty()) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        String base = baseUrl == null ? "" : baseUrl;
        if (!base.endsWith("/") && !url.startsWith("/") && !base.isEmpty()) base += "/";
        return base + (url.startsWith("/") ? url.substring(1) : url);
    }
}
