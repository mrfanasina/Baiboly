package com.fa.baiboly.data.download;

import android.content.Context;
import android.content.SharedPreferences;

import com.fa.baiboly.models.DownloadEntry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


/**
 * Client du catalogue de téléchargements.
 *
 * Deux fichiers, chacun son rôle (inchangés) :
 * - update.json : la mise à jour de l'APK (une seule version publiée) ;
 * - downloads.json : les autres fichiers à télécharger (lectures
 *   perikopa / Aina, et tout ce qu'on ajoute ensuite), avec leur lien.
 * Il n'y a plus de manifest.json à maintenir en parallèle.
 *
 * Format (voir docs/downloads.template.json) :
 * {
 *   "schema_version": 1,
 *   "base_url": "https://data.fjkm.mg/updates/",
 *   "categories": [
 *     { "title": "Application Baiboly",
 *       "items": [ { "type": "apk", "title": "Baiboly 5.3",
 *                    "url": "https://…/Baiboly-v5.3.apk",
 *                    "file_size": 39879213, "version": "5.3",
 *                    "version_code": 53 } ] },
 *     { "title": "Vakiteny sy Perikopa",
 *       "items": [ { "type": "perikopa", "dataset": "perikopa",
 *                    "year": 2028, "title": "Perikopa 2028",
 *                    "url": "perikopa/2028.json",
 *                    "file_size": 66622 } ] }
 *   ]
 * }
 *
 * - url absolue (https://…) : utilisée telle quelle ;
 * - url relative : résolue contre "base_url" (ou BASE_URL à défaut).
 *
 * Ajouter une année future = ajouter une entrée dans downloads.json.
 * L'application la voit sans mise à jour.
 *
 * Le téléchargement est REPRENABLE : on écrit dans un fichier ".part" et
 * on repart de la taille déjà reçue (Range).
 */
public class ServerManifestClient {

    // =========================================================
    // 🌐 BASE DES FICHIERS RELATIFS
    //    (les fichiers peuvent aussi avoir une URL absolue)
    // =========================================================
    public static final String DEFAULT_BASE_URL = "https://data.fjkm.mg/updates/";

    // =========================================================
    // 📦 MISE À JOUR DE L'APP (fichier dédié, format inchangé)
    // =========================================================
    public static final String APP_UPDATE_URL =
            "https://raw.githubusercontent.com/mrfanasina/baiboly-distribution/main/update.json";

    // =========================================================
    // 🗂️ CATALOGUE DES FICHIERS À TÉLÉCHARGER (lectures, etc.)
    // =========================================================
    public static final String DOWNLOADS_URL =
            "https://raw.githubusercontent.com/mrfanasina/baiboly-distribution/main/downloads.json";

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_BASE_URL = "download_server_url";

    private final Context context;

    public ServerManifestClient(Context context) {
        this.context = context.getApplicationContext();
    }

    // =========================
    // BASE URL (persistée)
    // =========================
    public String getBaseUrl() {
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL);
    }

    public void setBaseUrl(String url) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_BASE_URL, url).apply();
    }

    // =========================
    // 🗂️ CATALOGUE (downloads.json)
    // =========================
    /**
     * Lit downloads.json et renvoie tous les éléments activés, à plat,
     * avec leur catégorie et leur version.
     */
    public List<DownloadEntry> fetchDownloads() throws Exception {
        HttpURLConnection conn = open(new URL(DOWNLOADS_URL));
        try {
            JSONObject json = new JSONObject(readAll(conn));
            String base = firstNonEmpty(json.optString("base_url", ""), getBaseUrl());

            List<DownloadEntry> list = new ArrayList<>();
            JSONArray categories = json.optJSONArray("categories");
            if (categories == null) {
                // Variante tolérée : { "items": [ … ] } à plat.
                parseItems(json.optJSONArray("items"), "", base, list);
                return list;
            }

            for (int c = 0; c < categories.length(); c++) {
                JSONObject category = categories.optJSONObject(c);
                if (category == null) continue;
                String categoryTitle = category.optString("title", "");
                parseItems(category.optJSONArray("items"), categoryTitle, base, list);
            }
            return list;
        } finally {
            conn.disconnect();
        }
    }

    private void parseItems(JSONArray items, String categoryTitle, String base,
                            List<DownloadEntry> out) {
        if (items == null) return;
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null || !o.optBoolean("enabled", true)) continue;

            String url = resolveUrl(base,
                    firstNonEmpty(o.optString("url", ""), o.optString("apk_url", "")));
            if (url.isEmpty()) continue;

            String type = o.optString("type", DownloadEntry.TYPE_DATA);
            if ("apk".equalsIgnoreCase(type) || "application".equalsIgnoreCase(type)) {
                type = DownloadEntry.TYPE_APP;
            }

            DownloadEntry e = new DownloadEntry();
            e.setType(type);
            e.setTitle(firstNonEmpty(o.optString("title", ""), o.optString("id", "")));
            e.setSubtitle(o.optString("description", ""));
            e.setUrl(url);
            e.setSize(o.optLong("file_size", o.optLong("size", 0L)));
            e.setVersionName(emptyToNull(firstNonEmpty(
                    o.optString("version", ""), o.optString("version_name", ""))));
            e.setVersionCode(o.optInt("version_code", o.optInt("versionCode", 0)));
            e.setPublishedAt(o.optString("published_at", ""));
            e.setCategoryTitle(categoryTitle);

            String dataset = emptyToNull(o.optString("dataset", ""));
            if (dataset == null) {
                if (DownloadEntry.TYPE_PERIKOPA.equals(type)) dataset = "perikopa";
                else if (DownloadEntry.TYPE_AINA.equals(type)
                        || DownloadEntry.TYPE_MOFONAINA.equals(type)) dataset = "aina";
            }
            e.setDataset(dataset);
            e.setYear(o.optInt("year", 0));
            out.add(e);
        }
    }

    // =========================
    // 📦 MISE À JOUR DE L'APP (update.json)
    // =========================
    /**
     * Lit update.json et renvoie le paquet de mise à jour de l'app
     * (type "app"), ou null si aucune mise à jour n'est publiée.
     */
    public DownloadEntry fetchAppUpdate() throws Exception {
        HttpURLConnection conn = open(new URL(APP_UPDATE_URL));
        try {
            JSONObject json = new JSONObject(readAll(conn));
            String apkUrl = json.optString("apk_url", "");
            if (apkUrl.isEmpty()) return null;

            DownloadEntry e = new DownloadEntry();
            e.setType(DownloadEntry.TYPE_APP);
            e.setVersionCode(json.optInt("version_code", 0));
            String versionName = json.optString("version_name", "");
            e.setVersionName(versionName);
            e.setTitle(versionName.isEmpty() ? "Baiboly" : "Baiboly " + versionName);
            e.setUrl(apkUrl);
            e.setSize(json.optLong("file_size", 0L));

            JSONArray changelog = json.optJSONArray("changelog");
            if (changelog != null) {
                List<String> notes = new ArrayList<>();
                for (int i = 0; i < changelog.length(); i++) {
                    String line = changelog.optString(i, "").trim();
                    if (!line.isEmpty()) notes.add(line);
                }
                e.setChangelog(notes);
                if (!notes.isEmpty()) e.setSubtitle(notes.get(0));
            }
            e.setPublishedAt(json.optString("published_at", ""));
            return e;
        } finally {
            conn.disconnect();
        }
    }

    // =========================
    // ⬇️ TÉLÉCHARGEMENT
    // =========================

    /** Suivi de progression (appelé sur le thread de téléchargement). */
    public interface ProgressListener {
        void onProgress(long received, long total);
    }

    /** Signal consulté à chaque bloc : pause / annulation. */
    public interface CancelSignal {
        boolean isCancelled();
    }

    /** Téléchargement interrompu (pause ou annulation) : le .part est gardé. */
    public static class CancelledException extends Exception {
        public CancelledException() {
            super("Interrompu");
        }
    }

    /**
     * Télécharge l'entrée et écrit le fichier dans <app>/downloads/.
     * Reprend là où le fichier ".part" s'était arrêté.
     */
    public File download(DownloadEntry entry, ProgressListener listener,
                         CancelSignal signal) throws Exception {
        if (entry.absoluteUrl(getBaseUrl()) == null) {
            throw new Exception("URL source inconnue");
        }
        URL url = new URL(entry.absoluteUrl(getBaseUrl()));

        File dir = downloadsDir(context);
        File target = new File(dir, safeFileName(entry));
        File part = new File(dir, safeFileName(entry) + ".part");

        long existing = part.exists() ? part.length() : 0;

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("Accept-Encoding", "identity");
        if (existing > 0) {
            conn.setRequestProperty("Range", "bytes=" + existing + "-");
        }

        try {
            int code = conn.getResponseCode();
            if (code != 200 && code != 206) {
                throw new Exception("HTTP " + code + " : " + url);
            }

            boolean append = existing > 0 && code == 206;
            if (!append) {
                existing = 0;
                //noinspection ResultOfMethodCallIgnored
                part.delete();
            }

            long contentLength = conn.getContentLength();
            long total = contentLength > 0 ? existing + contentLength : -1;

            InputStream in = conn.getInputStream();
            OutputStream os = new FileOutputStream(part, append);
            byte[] buffer = new byte[8192];
            long received = existing;
            long lastNotify = 0;
            int len;
            try {
                while ((len = in.read(buffer)) > 0) {
                    os.write(buffer, 0, len);
                    received += len;
                    if (signal != null && signal.isCancelled()) {
                        throw new CancelledException();
                    }
                    long now = System.currentTimeMillis();
                    if (listener != null && now - lastNotify > 150) {
                        lastNotify = now;
                        listener.onProgress(received, total);
                    }
                }
                os.flush();
            } finally {
                try {
                    os.close();
                } catch (Exception ignored) {
                }
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }

            if (listener != null) listener.onProgress(received, total);

            // Terminé : le .part devient le fichier final.
            if (target.exists()) //noinspection ResultOfMethodCallIgnored
                target.delete();
            if (!part.renameTo(target)) {
                throw new Exception("Tsy afaka manoratra ny rakitra");
            }
            return target;
        } finally {
            conn.disconnect();
        }
    }

    public File download(DownloadEntry entry) throws Exception {
        return download(entry, null, null);
    }

    // =========================
    // 📁 DOSSIERS
    // =========================
    public static File downloadsDir(Context context) {
        File external = context.getExternalFilesDir(null);
        File dir = new File(external != null ? external : context.getFilesDir(), "downloads");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** Fichier final d'une entrée dans le dossier downloads. */
    public static File fileFor(Context context, DownloadEntry entry) {
        return new File(downloadsDir(context), safeFileName(entry));
    }

    /** Taille totale de tout ce qui a été téléchargé du serveur. */
    public static long downloadsSize(Context context) {
        File[] files = downloadsDir(context).listFiles();
        long size = 0;
        if (files != null) {
            for (File f : files) size += f.length();
        }
        return size;
    }

    // =========================
    // 🔧 OUTILS
    // =========================
    private static String resolveUrl(String base, String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (base == null || base.isEmpty()) return url;
        String b = base.endsWith("/") ? base : base + "/";
        return b + (url.startsWith("/") ? url.substring(1) : url);
    }

    private static String firstNonEmpty(String left, String right) {
        if (left != null && !left.isEmpty()) return left;
        return right != null ? right : "";
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    // =========================
    // 🔧 INFRA HTTP (TLS 1.2 etc.)
    // =========================
    private HttpURLConnection open(URL url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);

        int code = conn.getResponseCode();
        if (code != 200 && code != 204) {
            throw new Exception("HTTP " + code + " : " + url);
        }
        return conn;
    }

    private String readAll(HttpURLConnection conn) throws Exception {
        InputStream in = conn.getInputStream();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = in.read(buffer)) > 0) bos.write(buffer, 0, len);
        in.close();
        return bos.toString("UTF-8");
    }

    private static String safeFileName(DownloadEntry entry) {
        String raw = entry != null ? entry.getUrl() : null;
        if (raw == null || raw.isEmpty()) raw = entry != null ? entry.getTitle() : null;
        if (raw == null || raw.isEmpty()) raw = "download_" + System.currentTimeMillis();
        int lastSlash = raw.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < raw.length() - 1) {
            raw = raw.substring(lastSlash + 1);
        }
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
