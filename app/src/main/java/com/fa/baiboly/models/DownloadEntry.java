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
    /** Aina sy Fahasalamana (méditation quotidienne, JSON). */
    public static final String TYPE_AINA = "aina";
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

    /** Jeu de données importable : "perikopa", "aina"… (null si fichier simple). */
    private String dataset;
    /** Année couverte par le fichier (0 si inconnue). */
    private int year;

    // ---- état local (rempli par DownloadHistoryStore) ----
    /** Fichier écrit sur l'appareil (null si rien / importé puis effacé). */
    private String localPath;
    /** Dernier état connu dans la file (voir DownloadQueue.STATE_*). */
    private int state;
    /** true : le contenu a été importé dans la base (donc protégé). */
    private boolean imported;

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

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public boolean isImported() {
        return imported;
    }

    public void setImported(boolean imported) {
        this.imported = imported;
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

    /**
     * Jeu de données correspondant au type (perikopa / aina / mofonaina),
     * ou null si le fichier n'est pas importable dans la base.
     */
    public String inferDataset() {
        if (dataset != null && !dataset.isEmpty()) return dataset;
        if (TYPE_PERIKOPA.equals(type)) return "perikopa";
        if (TYPE_AINA.equals(type) || TYPE_MOFONAINA.equals(type)) return "aina";
        return null;
    }

    /**
     * Compare deux versions numériques ("5.2" &lt; "5.10").
     *
     * @return négatif si left est plus ancienne, 0 si égales (ou inconnues).
     */
    public static int compareVersions(String left, String right) {
        if (left == null || left.isEmpty() || right == null || right.isEmpty()) return 0;
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int count = Math.max(a.length, b.length);
        for (int i = 0; i < count; i++) {
            int va = i < a.length ? parseVersionPart(a[i]) : 0;
            int vb = i < b.length ? parseVersionPart(b[i]) : 0;
            if (va != vb) return va < vb ? -1 : 1;
        }
        return 0;
    }

    private static int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Cette entrée est-elle plus récente que la version installée ?
     * Utilise versionCode s'il est connu, sinon le numéro de version.
     */
    public boolean isNewerThan(int installedCode, String installedName) {
        if (versionCode > 0 && installedCode > 0) return versionCode > installedCode;
        if (versionCode > 0) return true;
        if (versionName != null && !versionName.isEmpty()) {
            if (installedName == null || installedName.isEmpty()) return true;
            return compareVersions(versionName, installedName) > 0;
        }
        // Sans aucune version publiée : on fait confiance au serveur.
        return true;
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
