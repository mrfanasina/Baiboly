package com.fa.baiboly.data.transfer;

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
 * Client du récepteur (receive) :
 *
 * - fetchManifest() : lit http://HOST:PORT/q?t=TOKEN (URL complète
 *   lue du QR code) et renvoie la liste de ce que le sender propose
 * - downloadItem() : télécharge un item (JSON ou audio) vers un fichier
 *
 * La connexion est automatique : tout est dans l'URL du QR
 * (host, port, token) — rien à taper, même réseau WiFi seulement.
 */
public final class TransferClient {

    private TransferClient() {
    }

    // =========================================================
    // 📄 MANIFEST
    // =========================================================
    public static class ReceivedItem {
        public int index;
        public String type;      // program | playback
        public String title;
        public String subtitle;
        public long size;
        // playback :
        public String category;
        public int number;
        public String ext;
        public String label;
        public String playTitle;

        public boolean isProgram() {
            return TransferItem.TYPE_PROGRAM.equals(type);
        }
    }

    public static class Manifest {
        public String deviceName;
        /** true = le sender exige son token (QR) ; false = ouvert. */
        public boolean secured = true;
        public final List<ReceivedItem> items = new ArrayList<>();
    }

    /** Lit le manifest du sender. manifestUrl = URL du QR (avec réseau ouvert, sans token). */
    public static Manifest fetchManifest(String manifestUrl) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(manifestUrl).openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(8000);
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            String body = readAll(conn);
            JSONObject root = new JSONObject(body);

            Manifest manifest = new Manifest();
            manifest.deviceName = root.optString("device", "Téléphone");
            manifest.secured = root.optBoolean("secured", true);
            org.json.JSONArray arr = root.optJSONArray("items");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    ReceivedItem it = new ReceivedItem();
                    it.index = o.optInt("i", i);
                    it.type = o.optString("type", "");
                    it.title = o.optString("title", "");
                    it.subtitle = o.optString("subtitle", "");
                    it.size = o.optLong("size", 0);
                    it.category = o.optString("category", "");
                    it.number = o.optInt("number", 0);
                    it.ext = o.optString("ext", "mp3");
                    it.label = o.optString("label", null);
                    it.playTitle = o.optString("play_title", null);
                    manifest.items.add(it);
                }
            }
            return manifest;
        } finally {
            conn.disconnect();
        }
    }

    // =========================================================
    // ⬇️ TÉLÉCHARGEMENT D'UN ITEM
    // =========================================================
    /**
     * Télécharge un item et écrit un fichier dans destDir.
     * itemUrl = base de l'appel item : http://ip:port/q?t=TOKEN&i=N
     */
    public static File downloadItem(String itemUrl, String displayName, File destDir)
            throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(itemUrl).openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(15000);

        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);

            // json → .json, sinon .mp3 (ext affinée via le manifest côté récepteur)
            String ct = conn.getContentType() == null ? "" : conn.getContentType();
            String ext = ct.contains("json") ? "json" : "mp3";
            String safe = displayName == null ? "transfer_" + System.currentTimeMillis()
                    : displayName.replaceAll("[^a-zA-Z0-9-_ ]", "").trim();
            File out = new File(destDir, safe + "." + ext);

            InputStream in = conn.getInputStream();
            OutputStream os = new FileOutputStream(out);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) os.write(buffer, 0, len);
            os.flush();
            os.close();
            in.close();
            return out;
        } finally {
            conn.disconnect();
        }
    }

    // =========================================================
    // 🔧 OUTILS RÉSEAU
    // =========================================================
    /**
     * Analyse le contenu d'un QR (avec ou sans bloc "WIFI:...") :
     * renvoie {base "http://ip:port/q?", token (null si ouvert)}.
     * Token null/absent = réseau ouvert, le serveur n'exige pas de token.
     */
    public static String[] parseTransferUrl(String qrContent) {
        try {
            String[] parsed = TransferUtils.parseQr(qrContent);
            if (parsed == null) return null;
            String url = parsed[2];

            android.net.Uri uri = android.net.Uri.parse(url);
            String scheme = uri.getScheme();
            if (!"http".equals(scheme) && !"https".equals(scheme)) return null;

            String token = uri.getQueryParameter("t");
            if (token != null && token.isEmpty()) token = null;
            String path = uri.getPath() == null ? "/q" : uri.getPath();
            if (!"/q".equals(path)) return null;

            String base = scheme + "://" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + "/q?";
            return new String[]{base, token};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Analyse l'URL du QR (compat ancien format) : renvoie
     * {base "http://ip:port/q?", token}, sinon null.
     */
    public static String[] parseUrl(String qrContent) {
        return parseTransferUrl(qrContent);
    }

    /** URL complète du manifest pour un sender scanné. */
    public static String manifestUrl(String qrContent) {
        String[] parsed = parseTransferUrl(qrContent);
        if (parsed == null) return null;
        return parsed[0] + (parsed[1] == null ? "" : "t=" + parsed[1]);
    }

    /** Base d'un item pour téléchargement (…/q? + token appelé baseUrl). */
    public static String itemUrl(String qrContent) {
        String[] parsed = parseUrl(qrContent);
        if (parsed == null) return null;
        return parsed[0] + "t=" + parsed[1];
    }

    private static String readAll(HttpURLConnection conn) throws Exception {
        InputStream in = conn.getInputStream();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = in.read(buffer)) > 0) bos.write(buffer, 0, len);
        in.close();
        return bos.toString("UTF-8");
    }
}
