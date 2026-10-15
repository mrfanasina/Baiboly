package com.fa.baiboly.data.transfer;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

/**
 * Serveur HTTP WiFi local qui sert la sélection du sender :
 *
 * - GET /q?t=TOKEN           → manifest JSON de ce qui est dispo
 * - GET /q/item?t=TOKEN&i=N  → JSON du programme OU fichier audio
 *
 * Le QR du sender contient http://IP:PORT/q?t=TOKEN (+ les identifiants
 * WiFi avant l'URL). Deux modes :
 * - SÉCURISÉ (par défaut) : token obligatoire, fourni par le QR ;
 * - OUVERT : pas de mot de passe réseau et pas de QR nécessaire…
 *   le serveur répond sans token, il apparaît dans la découverte du
 *   récepteur et est accessible directement.
 */
public class TransferServer extends NanoHTTPD {

    public static final int SOCKET_READ_TIMEOUT = 60_000;

    private final String token;
    private final String deviceName;
    /** Sélection courante, permutée atomiquement (volatile ref). */
    private volatile List<TransferItem> items;
    private final Context context;
    /** true = QR/token obligatoire ; false = réseau ouvert, accès libre. */
    private final boolean requireToken;

    public TransferServer(int port, String token, String deviceName,
                          List<TransferItem> items, Context context,
                          boolean requireToken) {
        super(port);
        this.token = token;
        this.deviceName = deviceName;
        this.items = items;
        this.context = context.getApplicationContext();
        this.requireToken = requireToken;
    }

    /** Démarre en mode daemon : ne bloque pas, meurt avec l'app. */
    public void startDaemon() {
        try {
            start(SOCKET_READ_TIMEOUT, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Remplace la sélection servie (session déjà démarrée : l'expéditeur
     * peut continuer à cocher/décocher, le manifest reflète le choix).
     */
    public void setItems(List<TransferItem> newItems) {
        this.items = new ArrayList<>(newItems);
    }

    public boolean isSecured() {
        return requireToken;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Map<String, List<String>> params = session.getParameters();
        String uri = session.getUri();

        // Token obligatoire en mode sécurisé uniquement
        if (requireToken && !token.equals(first(params, "t"))) {
            return newFixedLengthResponse(
                    Status.FORBIDDEN, "text/plain", "SECURED");
        }

        if ("/q".equals(uri)) {
            return manifestResponse();
        }

        if ("/q/item".equals(uri)) {
            return itemResponse(first(params, "i"));
        }

        return newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Inconnu");
    }

    // =========================================================
    // 📄 MANIFEST : liste de ce qui est disponible
    // =========================================================
    private Response manifestResponse() {
        try {
            List<TransferItem> list = items;
            if (list == null) list = new ArrayList<>();
            JSONObject root = new JSONObject();
            root.put("device", deviceName);
            root.put("secured", requireToken);
            JSONArray arr = new JSONArray();
            for (int i = 0; i < list.size(); i++) {
                TransferItem it = list.get(i);
                JSONObject o = new JSONObject();
                o.put("i", i);
                o.put("type", it.type);
                o.put("title", it.title == null ? "" : it.title);
                o.putOpt("subtitle", it.subtitle);
                o.put("size", it.size);
                if (TransferItem.TYPE_PLAYBACK.equals(it.type)) {
                    o.put("category", it.category);
                    o.put("number", it.number);
                    if (it.ext != null) o.put("ext", it.ext);
                    o.putOpt("label", it.label);
                    o.putOpt("play_title", it.playTitle);
                }
                arr.put(o);
            }
            root.put("items", arr);
            return newFixedLengthResponse(
                    Status.OK, "application/json; charset=utf-8",
                    root.toString());
        } catch (Exception e) {
            return error(e);
        }
    }

    // =========================================================
    // 📥 UN ITEM : JSON (programme) ou STREAM (audio)
    // =========================================================
    private Response itemResponse(String indexParam) {
        int index;
        try {
            index = Integer.parseInt(indexParam);
        } catch (Exception e) {
            index = -1;
        }
        List<TransferItem> list = items;
        if (list == null || index < 0 || index >= list.size()) {
            return newFixedLengthResponse(
                    Status.NOT_FOUND, "text/plain", "Item inconnu");
        }

        TransferItem it = list.get(index);

        if (TransferItem.TYPE_PROGRAM.equals(it.type)) {
            try {
                JSONObject json = TransferData.programToJson(it.program);
                return newFixedLengthResponse(
                        Status.OK, "application/json; charset=utf-8",
                        json.toString());
            } catch (Exception e) {
                return error(e);
            }
        }

        // Playback : stream du fichier audio (local OU content:// SAF)
        try {
            InputStream in;
            long size;
            if (it.contentUri != null) {
                Uri uri = it.contentUri;
                in = context.getContentResolver().openInputStream(uri);
                if (in == null) return error(new Exception("Fichier dossier indisponible"));
                size = it.size;
                if (size <= 0) {                    try (android.os.ParcelFileDescriptor pfd =
                             context.getContentResolver().openFileDescriptor(uri, "r")) {
                        if (pfd != null) size = pfd.getStatSize();
                    } catch (Exception ignored) {
                    }
                }
            } else {
                File f = it.file;
                in = new FileInputStream(f);
                size = f.length();
            }

            String mime = it.contentType != null && !it.contentType.isEmpty()
                    && it.contentUri != null
                    ? it.contentType
                    : mimeType(it.ext);

            if (size > 0) {
                return newFixedLengthResponse(Status.OK, mime, in, size);
            }
            return newChunkedResponse(Status.OK, mime, in);
        } catch (Exception e) {
            return error(e);
        }
    }

    // =========================================================
    // 🔧 OUTILS
    // =========================================================
    private Response error(Exception e) {
        return newFixedLengthResponse(
                Status.INTERNAL_ERROR, "text/plain",
                "Erreur : " + e.getMessage());
    }

    private static String first(Map<String, List<String>> params, String key) {
        List<String> list = params.get(key);
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private static String mimeType(String ext) {
        if (ext == null) return "application/octet-stream";
        if ("mp3".equals(ext)) return "audio/mpeg";
        if ("m4a".equals(ext) || "mp4".equals(ext)) return "audio/mp4";
        if ("wav".equals(ext)) return "audio/wav";
        if ("ogg".equals(ext) || "oga".equals(ext)) return "audio/ogg";
        if ("flac".equals(ext)) return "audio/flac";
        return "application/octet-stream";
    }
}
