package com.fa.baiboly.data.playback;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.fa.baiboly.data.fihirana.FihiranaService;
import com.fa.baiboly.models.Playback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Détection automatique des playbacks dans un dossier choisi par
 * l'utilisateur (SAF), SANS copier les fichiers dans l'app : on enregistre
 * simplement l'URI du document (le dossier peut être partagé/transféré).
 *
 * Deux modes :
 * <ul>
 *   <li><b>dossier d'une catégorie</b> : tous les fichiers trouvés
 *       (sous-dossiers compris) appartiennent à cette catégorie ;</li>
 *   <li><b>dossier qui contient tout</b> : les sous-dossiers SONT les
 *       catégories, devinées d'après leur nom
 *       (\"Ffpm\" -> ffpm, \"Fihirana Fanampiny\" -> ff, ou n'importe quel
 *       autre nom qui ressemble à un sokajy de la base).</li>
 * </ul>
 *
 * Dans les deux cas, le chant est reconnu d'après le nom du fichier :
 * \"012 Jehovah ô deraina Ianao.mp3\" -> laharana 12, titre
 * \"Jehovah ô deraina Ianao\" ; \"ffpm 049 v2.mp3\" -> 49 + version 2.
 *
 * Ce qui n'est pas reconnu (pas de laharana, ou sokajy introuvable) est
 * mis de côté dans la table {@code detected_files} de {@link PlaybackStore}.
 *
 * // TODO : fenêtre dédiée pour réassigner les fichiers de
 * // TODO : detected_files à un chant (sokajy + laharana).
 */
public final class PlaybackScanner {

    /** Bilan d'un scan. */
    public static class Result {
        /** Fichiers audio vus. */
        public final int scanned;
        /** Fichiers attribués à un chant et enregistrés. */
        public final int registered;
        /** Fichiers non reconnus, gardés pour plus tard. */
        public final int pending;
        /** Fichiers non audio ignorés. */
        public final int ignored;

        Result(int scanned, int registered, int pending, int ignored) {
            this.scanned = scanned;
            this.registered = registered;
            this.pending = pending;
            this.ignored = ignored;
        }
    }

    /** Résultat livré sur le thread UI. */
    public interface Listener {
        void onResult(Result result);
    }

    /** Profondeur maximale de descente (dossier > catégorie > sous-dossiers). */
    private static final int MAX_DEPTH = 6;
    /** Garde-fou : ne pas descendre dans un dossier quasi illimité. */
    private static final int MAX_FILES = 4000;

    private static final Set<String> AUDIO_EXT = new HashSet<>(Arrays.asList(
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "oga", "opus",
            "amr", "awb", "wma", "mp2", "mpa", "mid", "midi", "3gp", "m4b"));

    /** \"v2\", \"version 3\", \"2\" (nom d'un sous-dossier = version). */
    private static final Pattern VERSION_FOLDER = Pattern.compile(
            "^(?:v|version)?[\\s._-]*(\\d{1,3})$",
            Pattern.CASE_INSENSITIVE);

    private final Context context;
    private final PlaybackStore store;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public PlaybackScanner(Context context) {
        this.context = context.getApplicationContext();
        this.store = new PlaybackStore(this.context);
    }

    /** Libère le thread de fond (à appeler dans onDestroy). */
    public void release() {
        executor.shutdownNow();
    }

    /**
     * Lance un scan en arrière-plan.
     *
     * @param treeUri       dossier choisi (tree Uri, permission persistante)
     * @param fixedCategory sokajy imposé (dossier d'une catégorie), null si
     *                      le sokajy doit être deviné (dossier « tout »)
     * @param rootMode      true = les sous-dossiers sont des catégories
     */
    public void scan(Uri treeUri, @Nullable String fixedCategory,
                     boolean rootMode, @Nullable Listener listener) {
        if (treeUri == null) {
            if (listener != null) {
                main.post(() -> listener.onResult(new Result(0, 0, 0, 0)));
            }
            return;
        }
        executor.execute(() -> {
            Result result = run(treeUri, fixedCategory, rootMode);
            if (listener != null) {
                main.post(() -> listener.onResult(result));
            }
        });
    }

    // =========================
    // SCAN
    // =========================

    private Result run(Uri treeUri, String fixedCategory, boolean rootMode) {
        List<String> known = loadKnownCategories(context);
        DocumentFile root = null;
        try {
            root = DocumentFile.fromTreeUri(context, treeUri);
        } catch (Exception ignored) {
            // root reste null
        }
        if (root == null || !root.exists()) return new Result(0, 0, 0, 0);

        String fixed = PlaybackStore.normalize(fixedCategory);
        // Le dossier choisi lui-même peut porter le nom du sokajy
        String rootCategory = guessCategory(name(root), known);

        // TODO : retirer aussi de la base les playbacks pointant vers des
        // TODO : fichiers qui ont disparu du dossier (non fait pour l'instant).
        Counters c = new Counters();
        walk(root, rootCategory, fixed, rootMode, known, 0, true, c);
        return new Result(c.scanned, c.registered, c.pending, c.ignored);
    }

    private static class Counters {
        int scanned;
        int registered;
        int pending;
        int ignored;
    }

    private void walk(DocumentFile dir, @Nullable String inheritedCategory,
                      String fixed, boolean rootMode, List<String> known,
                      int depth, boolean isScanRoot, Counters c) {
        if (depth > MAX_DEPTH || c.scanned >= MAX_FILES || !dir.exists()) return;

        // Sokajy porté par ce sous-dossier (hérité par ses enfants)
        String hereCategory = guessCategory(name(dir), known);
        if (hereCategory == null) hereCategory = inheritedCategory;

        DocumentFile[] children;
        try {
            children = dir.listFiles();
        } catch (Exception e) {
            return;
        }

        String dirName = name(dir);
        for (DocumentFile child : children) {
            if (c.scanned >= MAX_FILES) return;

            boolean isDir;
            try {
                isDir = child.isDirectory();
            } catch (Exception e) {
                continue;
            }

            if (isDir) {
                walk(child, hereCategory, fixed, rootMode, known,
                        depth + 1, false, c);
                continue;
            }

            String fileName = name(child);
            if (!isAudio(fileName)) {
                c.ignored++;
                continue;
            }

            c.scanned++;
            handleFile(child, fileName, dirName, hereCategory, fixed,
                    known, isScanRoot, c);
        }
    }

    private void handleFile(DocumentFile file, String fileName,
                            String parentName, @Nullable String folderCategory,
                            String fixed, List<String> known,
                            boolean isScanRoot, Counters c) {
        PlaybackManager.ParsedName parsed =
                PlaybackManager.parseFileName(fileName, known);

        // 1) le sokajy du dossier choisi, 2) celui du sous-dossier,
        // 3) celui écrit dans le nom du fichier
        String category;
        if (fixed != null && !fixed.isEmpty()) {
            category = fixed;
        } else if (folderCategory != null) {
            category = folderCategory;
        } else {
            category = parsed.category;
        }

        // Version : celle du fichier, sinon celle du sous-dossier (ex. \"v2/\")
        String label = parsed.label;
        if (label == null && !isScanRoot) {
            label = guessVersionLabel(parentName);
        }

        Uri uri = file.getUri();
        String docUri = uri != null ? uri.toString() : null;
        if (docUri == null) return;

        if (category == null || !parsed.hasNumber()) {
            // Pas reconnu : gardé de côté pour une réassignation manuelle
            // TODO : fenêtre « Hanovaana » listant ces fichiers.
            store.saveDetected(docUri, fileName, parentName, category,
                    parsed.number, parsed.title, label);
            c.pending++;
            return;
        }

        String title = parsed.title != null ? parsed.title
                : PlaybackManager.displayTitle(category, parsed.number);
        store.save(category, parsed.number, title, docUri,
                Playback.SOURCE_FOLDER, label, docUri);
        c.registered++;
    }

    // =========================
    // DEVINETTE DE SOKAJY
    // =========================

    /**
     * Devine le sokajy à partir d'un nom de dossier ou de fichier :
     *
     * <pre>
     * \"Ffpm\"                  -> ffpm
     * \"Fihirana Fanampiny\"    -> ff
     * \"ffanampiny\"            -> ff
     * \"Antema\"                -> antema
     * \"Maquettes\"             -> null (inconnu)
     * </pre>
     *
     * @param known sokajy de la base (peut être null/vide)
     */
    @Nullable
    public static String guessCategory(@Nullable String rawName,
                                       @Nullable List<String> known) {
        String key = PlaybackManager.normalizeToken(rawName);
        if (key.isEmpty()) return null;

        // 1) égalité stricte avec un sokajy connu
        String exact = matchKnown(key, known, true);
        if (exact != null) return exact;

        // 2) alias : tout ce qui finit par \"fanampiny\" = FF
        if (key.endsWith("fanampiny")) return "ff";

        // 3) commence par un sokajy connu (\"ffpmdemo\" -> ffpm)
        String starts = matchKnown(key, known, false);
        if (starts != null) return starts;

        // 4) contient un sokajy connu d'au moins 3 lettres
        if (known != null) {
            for (String c : known) {
                String nc = PlaybackManager.normalizeToken(c);
                if (nc.length() >= 3 && key.contains(nc)) {
                    return PlaybackStore.normalize(c);
                }
            }
        }
        return null;
    }

    @Nullable
    private static String matchKnown(String key, List<String> known,
                                     boolean exact) {
        if (known == null) return null;
        for (String c : known) {
            String nc = PlaybackManager.normalizeToken(c);
            if (nc.isEmpty()) continue;
            if (exact ? nc.equals(key)
                    : (nc.length() >= 2 && key.startsWith(nc))) {
                return PlaybackStore.normalize(c);
            }
        }
        return null;
    }

    /** \"v2\" / \"2\" (nom d'un sous-dossier) -> \"2\", sinon null. */
    @Nullable
    private static String guessVersionLabel(@Nullable String folderName) {
        if (folderName == null) return null;
        java.util.regex.Matcher m = VERSION_FOLDER.matcher(folderName.trim());
        if (!m.matches()) return null;
        try {
            return String.valueOf(Integer.parseInt(m.group(1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // =========================
    // HELPERS
    // =========================

    /**
     * Sokajy connus de l'app (lus dans la base des chants), avec les plus
     * courants garantis même si la lecture échoue.
     */
    public static List<String> loadKnownCategories(Context context) {
        List<String> list = new ArrayList<>();
        try {
            FihiranaService service = new FihiranaService(context);
            List<String> cats = service.getCategories();
            if (cats != null) {
                for (String c : cats) {
                    String n = PlaybackStore.normalize(c);
                    if (!n.isEmpty() && !list.contains(n)) list.add(n);
                }
            }
        } catch (Throwable ignored) {
            // Base indisponible : on garde la liste de secours ci-dessous
        }
        for (String fallback : new String[]{"ffpm", "ff", "antema",
                "tsanta", "safif"}) {
            if (!list.contains(fallback)) list.add(fallback);
        }
        return list;
    }

    @Nullable
    private static String name(DocumentFile file) {
        try {
            return file.getName();
        } catch (Exception e) {
            return null;
        }
    }

    /** Fichier audio ? (pas d'extension = on accepte, c'est rare). */
    private static boolean isAudio(String fileName) {
        if (fileName == null || fileName.isEmpty()) return false;
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot >= fileName.length() - 1) return true;
        String ext = fileName.substring(dot + 1)
                .toLowerCase(Locale.ROOT).trim();
        return AUDIO_EXT.contains(ext);
    }
}
