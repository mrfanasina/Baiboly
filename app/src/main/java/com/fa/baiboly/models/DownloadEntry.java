package com.fa.baiboly.models;

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
    public static final String TYPE_PERIKOPA = "perikopa";
    public static final String TYPE_MOFONAINA = "mofonaina";
    public static final String TYPE_DATA = "data";

    private String type;
    private String title;
    private String subtitle;
    private String url;
    private long size;

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

    /** Résolution de l'URL relative par rapport à BASE_URL. */
    public String absoluteUrl(String baseUrl) {
        if (url == null || url.isEmpty()) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        String base = baseUrl == null ? "" : baseUrl;
        if (!base.endsWith("/") && !url.startsWith("/") && !base.isEmpty()) base += "/";
        return base + (url.startsWith("/") ? url.substring(1) : url);
    }
}
