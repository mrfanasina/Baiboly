package com.fa.baiboly.data.playback;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Gestion des playbacks.
 *
 * Structure des fichiers :
 *
 * Catégories normales :
 * https://www.fifohazana-fpma.org/images/{category}/{number}.mp3
 *
 * Exemple :
 * https://www.fifohazana-fpma.org/images/ffpm/049.mp3
 *
 * Catégorie spéciale "ff" :
 * https://www.fifohazana-fpma.org/images/ffanampiny/{number}.mp3
 *
 * Exemple :
 * https://www.fifohazana-fpma.org/images/ffanampiny/49.mp3
 */
public final class PlaybackApi {

    private static final String TAG = "PlaybackApi";

    public static final String BASE_URL =
            "https://www.fifohazana-fpma.org";

    public static final int CONNECT_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS = 30_000;

    private PlaybackApi() {
    }

    /**
     * Construit directement l'URL du fichier MP3.
     *
     * Règles :
     *
     * category = "ff"
     * -> dossier "ffanampiny"
     * -> numéro sur 2 chiffres
     *
     * Exemple :
     * getPlaybackUrl("ff", 49)
     * -> https://www.fifohazana-fpma.org/images/ffanampiny/49.mp3
     *
     * Pour les autres catégories :
     *
     * getPlaybackUrl("ffpm", 49)
     * -> https://www.fifohazana-fpma.org/images/ffpm/049.mp3
     */
    public static String getPlaybackUrl(String category, int number) {

        String folder;
        String laharana;

        if ("ff".equalsIgnoreCase(category) || "antema".equalsIgnoreCase(category)) {

            // et seulement 2 chiffres.
            if ("ff".equalsIgnoreCase(category)){
                // FF utilise le dossier ffanampiny
                folder = "ffanampiny";
            } else {
                folder = "antema";
            }
            laharana = String.format("%02d", number);

        } else {

            // Les autres catégories utilisent leur nom
            // et 3 chiffres.
            folder = category;
            laharana = String.format("%03d", number);
        }

        // URLEncoder.encode(String, Charset) n'existe qu'à partir d'API 33 :
        // sur les appareils plus anciens, elle lève NoSuchMethodError.
        // On utilise la surcharge historique (String, String), API 1+.
        String encodedFolder;
        try {
            encodedFolder = URLEncoder.encode(folder, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 est toujours présent sur Android : jamais atteint
            encodedFolder = folder;
        }

        return BASE_URL + "/images/"
                + encodedFolder
                + "/" + laharana + ".mp3";
    }

    /** En-tête Content-Length renvoyé par le serveur (-1 si inconnu). */
    public static long remoteLength(String fileUrl) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(fileUrl);

            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("HEAD");
            conn.setRequestProperty("Accept", "audio/mpeg");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return -1;
            }

            String len = conn.getHeaderField("Content-Length");
            return len == null ? -1 : Long.parseLong(len.trim());

        } catch (Exception e) {
            return -1;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Télécharge le fichier audio distant vers destFile.
     *
     * Appel bloquant :
     * à exécuter hors du thread UI.
     *
     * @return nombre d'octets téléchargés
     */
    public static long download(String fileUrl, File destFile)
            throws IOException {
        try (OutputStream out = new java.io.FileOutputStream(destFile)) {
            return download(fileUrl, out, destFile.getName());
        }
    }

    /**
     * Télécharge le fichier audio distant vers un flux de sortie (document
     * SAF). Écrit d'abord dans un document temporaire ".tmp" dans le même
     * dossier, puis supprime le .tmp et renomme le document final en place
     * (via delete + create renommé par l'appelant avec le flux déjà ouvert,
     * cf. PlaybackManager).
     *
     * En pratique : écrit dans {@code out} avec reprise sur le document
     * temporaire {@code tmpName} si un téléchargement précédent a échoué.
     *
     * Appel bloquant :
     * à exécuter hors du thread UI.
     *
     * @return nombre d'octets téléchargés
     */
    public static long download(String fileUrl, OutputStream out, String tmpName)
            throws IOException {

        HttpURLConnection conn = null;

        try {
            URL url = new URL(fileUrl);

            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "audio/mpeg");

            int code = conn.getResponseCode();

            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException(
                        "HTTP " + code + " : " + fileUrl
                );
            }

            long total = 0;

            try (InputStream in = conn.getInputStream()) {

                byte[] buffer = new byte[8192];

                int n;

                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    total += n;
                }

                out.flush();
            }

            Log.d(
                    TAG,
                    "downloaded : "
                            + tmpName
                            + " (" + total + " octets)"
            );

            return total;

        } catch (IOException e) {

            Log.e(TAG, "Tsy nandeha", e);

            throw e;

        } finally {

            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
