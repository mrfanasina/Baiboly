package com.fa.baiboly.data.transfer;

import android.content.Context;
import android.net.Uri;

import com.fa.baiboly.data.fandaharana.FandaharanaService;
import com.fa.baiboly.data.playback.PlaybackStore;
import com.fa.baiboly.models.Playback;
import com.fa.baiboly.models.Program;
import com.fa.baiboly.models.ProgramItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Prépare ce qui va partir (sender) et importe ce qui arrive (receiver) :
 *
 * - SENDER : sélection de programmes + playbacks → {@link TransferItem}s.
 *   Les playbacks venant d'un dossier SAF (source "folder") sont conservés
 *   sous forme d'URI content:// : le serveur les streamera à la demande,
 *   SANS copie préalable — ainsi on peut envoyer des centaines de playbacks
 *   importés depuis le téléphone, pas seulement ceux téléchargés.
 * - RECEIVER : JSON du programme → recréé dans la base via
 *   {@link FandaharanaService} ; fichier audio reçu → enregistré via
 *   {@link PlaybackStore} comme version locale (source "local").
 */
public final class TransferData {

    private TransferData() {
    }

    // =========================================================
    // SENDER
    // =========================================================

    /**
     * Combine programmes et playbacks choisis en liste d'items servables.
     * Résolution rapide uniquement (rien n'est copié ici) : les playbacks
     * introuvables sont signalés dans {@code skipped}, pas copiés.
     */
    public static java.util.List<TransferItem> buildSenderItems(
            Context context, java.util.List<Program> programs,
            java.util.List<Playback> playbacks,
            java.util.List<String> skipped) {

        java.util.List<TransferItem> items = new java.util.ArrayList<>();

        if (programs != null) {
            for (Program p : programs) {
                if (p == null) continue;
                TransferItem it = new TransferItem();
                it.type = TransferItem.TYPE_PROGRAM;
                it.title = p.getName();
                it.subtitle = p.getItemCount() + " item";
                it.program = p;
                it.size = 0;
                items.add(it);
            }
        }

        if (playbacks != null) {
            for (Playback pb : playbacks) {
                if (pb == null) continue;
                TransferItem it = resolvePlayback(context, pb);
                if (it == null) {
                    if (skipped != null) {
                        skipped.add(pb.getCategory() + " " + pb.getNumber());
                    }
                    continue;
                }
                items.add(it);
            }
        }
        return items;
    }

    /**
     * Résout un playback en item transférable : soit un fichier direct
     * lisible, soit une URI content:// (dossier SAF) streamée à la volée.
     * Renvoie null si le fichier n'est pas accessible.
     */
    private static TransferItem resolvePlayback(Context context, Playback pb) {
        TransferItem it = new TransferItem();
        it.type = TransferItem.TYPE_PLAYBACK;
        it.category = pb.getCategory();
        it.number = pb.getNumber();
        it.playTitle = pb.getTitle();
        it.label = pb.getLabel();

        String path = pb.getFilePath();
        String docUri = pb.getDocUri();

        // 1) URI content:// — priorité aux fichiers du dossier choisi (SAF)
        String uriText = (docUri != null && docUri.startsWith("content"))
                ? docUri
                : (path != null && path.startsWith("content") ? path : null);
        if (uriText != null) {
            try {
                Uri uri = Uri.parse(uriText);
                // Une SEULE ouverture : taille + preuve de lisibilité
                // (2 appels de moins par item : chargement bien plus rapide)
                android.os.ParcelFileDescriptor pfd;
                try {
                    pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                } catch (Exception e) {
                    return null;
                }
                if (pfd == null) return null;
                long len = pfd.getStatSize();
                try {
                    pfd.close();
                } catch (Exception ignored) {
                }

                it.contentUri = uri;
                it.size = len;
                // Extension déduite du MIME type (fallback sur le nom ensuite)
            } catch (Exception ignored) {
                return null;
            }
        } else if (path != null && !path.isEmpty() && !path.startsWith("content")) {
            // 2) Fichier direct (interne ou stockage lisible)
            File f = new File(path);
            if (!f.exists() || !f.canRead()) return null;
            it.file = f;
            it.size = f.length();
        } else {
            return null;
        }

        // Extension : du fichier, sinon du nom d'URI (content://)
        if (it.file != null) {
            it.ext = extensionOf(it.file.getName());
        } else {
            it.ext = extensionOf(uriLastSegment(uriText));
        }

        it.title = pb.getTitle() != null && !pb.getTitle().trim().isEmpty()
                ? pb.getTitle().trim()
                : pb.getCategory().toUpperCase() + " " + pb.getNumber();
        it.subtitle = (pb.getLabel() != null && !pb.getLabel().isEmpty()
                ? pb.getLabel() + " · " : "")
                + (it.size > 0 ? humanSize(it.size) : "fichier dossier");
        return it;
    }

    private static String uriLastSegment(String uriText) {
        try {
            String s = Uri.parse(uriText).getLastPathSegment();
            return s == null ? "" : s;
        } catch (Exception e) {
            return "";
        }
    }

    /** Extension déduite d'un type MIME audio (null si inconnu). */
    public static String extensionFromMime(String mime) {
        if (mime == null) return null;
        String m = mime.toLowerCase();
        if (m.contains("mpeg")) return "mp3";
        if (m.contains("mp4") || m.contains("m4a")) return "m4a";
        if (m.contains("wav")) return "wav";
        if (m.contains("ogg")) return "ogg";
        if (m.contains("flac")) return "flac";
        if (m.contains("aac")) return "aac";
        if (m.contains("opus")) return "opus";
        return null;
    }

    public static String extensionOf(String name) {
        if (name == null) return "mp3";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "mp3";
        return name.substring(dot + 1).toLowerCase();
    }

    public static String humanSize(long bytes) {
        if (bytes <= 0) return "0 kB";
        if (bytes < 1024 * 1024)
            return (bytes / 1024) + " kB";
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
    }

    // =========================================================
    // JSON PROGRAM
    // =========================================================

    public static JSONObject programToJson(Program p) throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", p.getName() == null ? "" : p.getName());
        o.put("created_at", p.getCreatedAt());
        JSONArray arr = new JSONArray();
        if (p.getItems() != null) {
            for (ProgramItem i : p.getItems()) {
                JSONObject io = new JSONObject();
                io.put("type", i.getType());
                io.put("reference", i.getReference() == null ? "" : i.getReference());
                io.putOpt("title", i.getTitle());
                io.putOpt("description", i.getDescription());
                io.put("favorite", i.isFavorite());
                io.put("position", i.getPosition());
                arr.put(io);
            }
        }
        o.put("items", arr);
        return o;
    }

    // =========================================================
    // RECEIVER : IMPORT
    // =========================================================

    /**
     * Recrée un programme reçu dans la base. Renvoie le nom créé,
     * ou lance une Exception si le JSON est invalide.
     */
    public static String importProgram(Context context, JSONObject json) throws Exception {
        FandaharanaService service = new FandaharanaService(context);

        String name = json.optString("name", "Fandaharana");
        long createdAt = json.optLong("created_at", System.currentTimeMillis());

        long newId = service.createProgram(name);
        JSONArray items = json.optJSONArray("items");
        if (items != null && newId > 0) {
            for (int k = 0; k < items.length(); k++) {
                JSONObject io = items.getJSONObject(k);
                long itemId = service.addItem(
                        newId,
                        io.optString("type", "other"),
                        io.optString("reference", ""),
                        io.optString("title", null));
                if (itemId > 0) {
                    service.updateItemDetails(itemId,
                            io.optString("title", null),
                            io.isNull("description") ? null : io.optString("description"),
                            io.optBoolean("favorite", false));
                }
            }
        }
        return name;
    }

    /**
     * Enregistre un fichier audio reçu comme nouvelle version du chant
     * (sokajy + laharana). Renvoie le titre affichable.
     */
    public static String importPlayback(Context context, File audio,
                                        String category, int number,
                                        String playTitle, String label) throws Exception {
        PlaybackStore store = new PlaybackStore(context);
        String ext = extensionOf(audio.getName());
        File dest = store.internalFileFor(category, number, ext);
        copy(new java.io.FileInputStream(audio), new FileOutputStream(dest));

        String version = label != null && !label.trim().isEmpty()
                ? label.trim() : "Voaray";

        store.save(category, number, playTitle, dest.getAbsolutePath(),
                Playback.SOURCE_LOCAL, version, null);

        return category.toUpperCase() + " " + number;
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);
        out.flush();
        in.close();
        out.close();
    }
}
