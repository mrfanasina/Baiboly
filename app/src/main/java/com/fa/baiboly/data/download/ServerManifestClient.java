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
 * Client du serveur de données. PRÉPARÉ pour la suite :
 *
 * 1) fetchManifest() : lit BASE_URL + "manifest.json" qui décrit
 *    ce qui est téléchargeable (perikopa par année, mofonaina…)
 *
 * 2) download() : télécharge un fichier et le range dans
 *    <app>/downloads/. L'IMPORTATION DANS LA BASE BASE
 *    (perikopa_days / perikopa_readings / perikopa_months) sera
 *    branchée plus tard — c'est le point d'entrée unique.
 *
 * Format attendu du manifest.json (urls relatives à BASE_URL) :
 * {
 *   "items": [
 *     { "type": "perikopa",   "title": "Perikopa 2026",
 *       "url": "perikopa/2026.json",   "size": 243000 },
 *     { "type": "mofonaina",  "title": "Mofonaina 14 Oktobra",
 *       "url": "mofonaina/2026-10-14.json", "size": 12000 }
 *   ]
 * }
 */
public class ServerManifestClient {

    // =========================================================
    // 🌐 URL DU SERVEUR — à remplacer quand les fichiers perikopa
    //    et mofonaina seront mis en ligne. Modifiable depuis le
    //    code via setBaseUrl().
    // =========================================================
    public static final String DEFAULT_BASE_URL = "https://data.fjkm.mg/updates/";

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
    // 📄 MANIFEST
    // =========================
    public List<DownloadEntry> fetchManifest() throws Exception {
        String manifestUrl = getBaseUrl();
        if (!manifestUrl.endsWith("/")) manifestUrl += "/";
        manifestUrl += "manifest.json";

        HttpURLConnection conn = open(new URL(manifestUrl));
        try {
            String body = readAll(conn);
            JSONObject json = new JSONObject(body);
            JSONArray items = json.optJSONArray("items");

            List<DownloadEntry> list = new ArrayList<>();
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject o = items.getJSONObject(i);
                    DownloadEntry e = new DownloadEntry();
                    e.setType(o.optString("type", DownloadEntry.TYPE_DATA));
                    e.setTitle(o.optString("title", ""));
                    e.setUrl(o.optString("url", ""));
                    e.setSize(o.optLong("size", 0L));
                    list.add(e);
                }
            }
            return list;
        } finally {
            conn.disconnect();
        }
    }

    // =========================
    // ⬇️ TÉLÉCHARGEMENT
    // =========================
    /**
     * Télécharge l'entrée et écrit le fichier dans
     * &lt;app&gt;/downloads/. Retourne le fichier écrit.
     * (Plus tard : importer le contenu dans la base ici.)
     */
    public File download(DownloadEntry entry) throws Exception {
        URL url = new URL(entry.absoluteUrl(getBaseUrl()));
        HttpURLConnection conn = open(url);

        try {
            File dir = downloadsDir(context);
            File out = new File(dir, safeFileName(entry));

            InputStream in = conn.getInputStream();
            OutputStream os = new FileOutputStream(out);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) > 0) {
                os.write(buffer, 0, len);
            }
            os.flush();
            os.close();
            in.close();

            return out;

        } finally {
            conn.disconnect();
        }
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

    private String safeFileName(DownloadEntry entry) {
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
