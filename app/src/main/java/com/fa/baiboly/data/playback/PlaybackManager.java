package com.fa.baiboly.data.playback;

import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;

import java.net.HttpURLConnection;
import java.net.URL;
import android.os.Looper;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.fa.baiboly.models.Playback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Point d'entrée unique pour les playbacks (feo) :
 *
 * - {@link #get(String, int)} / {@link #getAll(String, int)} : playbacks
 *   d'un chant (catégorie + numéro), plusieurs versions possibles ;
 * - {@link #addLocalFile(Uri, String, int, String, Callback)} : ajout depuis
 *   un fichier audio choisi sur le téléphone ;
 * - {@link #downloadFromServer(String, int, String, Callback)} : téléchargement
 *   du fichier API (catégorie + numéro) avec progression visible ;
 * - {@link #prefetch(String, int)} : appelé à l'entrée dans un chant, lance
 *   d'office le téléchargement en arrière-plan (aucun popup pendant l'entrée,
 *   la progression s'affiche dans l'UI et le lecteur devient dispo direct).
 *
 * Les fichiers sont enregistrés de préférence dans le dossier choisi par
 * l'utilisateur pour la catégorie (SAF, cf. {@link PlaybackStore}), sinon
 * dans le stockage interne de l'app. Les opérations lourdes tournent sur
 * un thread de fond ; les callbacks reviennent toujours sur le thread UI.
 *
 * Progression : ajouter un {@link DownloadListener} (écrans ouverts) avec
 * {@link #addListener(Listener)} ; chaque changement d'état est posté au
 * thread UI, avec throttle pendant le transfert.
 */
public class PlaybackManager {

    /** Callback sur le thread UI. */
    public interface Callback<T> {
        void onResult(T result);
    }

    /** Résultat d'un enregistrement (local ou téléchargé). */
    public static class SaveResult {
        public final boolean success;
        public final Playback playback; // null si échec
        public final String error;      // message si échec

        private SaveResult(boolean success, Playback playback, String error) {
            this.success = success;
            this.playback = playback;
            this.error = error;
        }

        static SaveResult ok(Playback playback) {
            return new SaveResult(true, playback, null);
        }

        static SaveResult fail(String error) {
            return new SaveResult(false, null, error);
        }
    }

    // =========================
    // ÉTAT DE TÉLÉCHARGEMENT
    // =========================

    /** État d'un téléchargement pour un chant (sokajy + laharana). */
    public static class DownloadState {
        public static final int RUNNING = 0;
        public static final int DONE = 1;
        public static final int FAILED = 2;
        public static final int CANCELLED = 3;

        public final String category;
        public final int number;
        /** RUNNING, DONE, FAILED ou CANCELLED. */
        public final int status;
        /** Octets déjà reçus (RUNNING) ou téléchargés (DONE). */
        public final long downloadedBytes;
        /** Taille totale annoncée par le serveur, -1 si inconnue. */
        public final long totalBytes;

        DownloadState(String category, int number, int status,
                      long downloadedBytes, long totalBytes) {
            this.category = category;
            this.number = number;
            this.status = status;
            this.downloadedBytes = downloadedBytes;
            this.totalBytes = totalBytes;
        }

        /** Progression 0..100, -1 si la taille totale est inconnue. */
        public int percent() {
            if (totalBytes <= 0 || downloadedBytes <= 0) return -1;
            int pct = (int) (downloadedBytes * 100 / totalBytes);
            return Math.max(0, Math.min(100, pct));
        }

        public boolean isRunning() {
            return status == RUNNING;
        }
    }

    /**
     * Notifié sur le thread UI à chaque changement d'état d'un
     * téléchargement (démarrage, progression throttlée, fin, échec,
     * annulation), et à chaque vérification de disponibilité serveur.
     */
    public interface DownloadListener {
        void onDownloadStateChanged(DownloadState state);

        /** Le résultat du check serveur a changé (dispo, taille, réseau). */
        default void onServerStateChanged(ServerInfo info) {
        }
    }

    // =========================
    // DISPONIBILITÉ SERVEUR
    // =========================

    /** Résultat du check serveur pour un chant. */
    public static class ServerInfo {
        public static final int CHECKING = 0;
        public static final int AVAILABLE = 1;
        public static final int UNAVAILABLE = 2;
        public static final int NO_INTERNET = 3;

        public final String category;
        public final int number;
        /** CHECKING, AVAILABLE, UNAVAILABLE ou NO_INTERNET. */
        public final int status;
        /** Taille annoncée par le serveur (octets), -1 si inconnue. */
        public final long sizeBytes;

        ServerInfo(String category, int number, int status, long sizeBytes) {
            this.category = category;
            this.number = number;
            this.status = status;
            this.sizeBytes = sizeBytes;
        }

        public boolean isAvailable() {
            return status == AVAILABLE;
        }
    }

    /** Dernier état de vérification serveur par chant. */
    private final Map<String, ServerInfo> serverInfos = new HashMap<>();
    /** Dernier chant vérifié : re-vérifié dès qu'Internet revient. */
    private volatile String lastCheckedKey;
    /** Callback réseau enregistré ? (une seule fois par instance) */
    private volatile boolean netCallbackRegistered = false;

    private final List<DownloadListener> listeners = new ArrayList<>();
    /**
     * Dernier état connu de chaque téléchargement, par clé
     * "catégorie:number" (cf. {@link #key(String, int)}).
     */
    private final Map<String, DownloadState> states = new HashMap<>();
    /** Chants dont le téléchargement doit s'interrompre. */
    private final java.util.Set<String> cancelRequests = new java.util.HashSet<>();

    private final Context context;
    private final PlaybackStore store;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public PlaybackManager(Context context) {
        this.context = context.getApplicationContext();
        this.store = new PlaybackStore(this.context);
    }

    // =========================
    // LISTENERS DE PROGRESSION
    // =========================

    /** Ajoute un listener de progression (écrans ouverts). */
    public synchronized void addListener(DownloadListener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    /** Retire un listener précédemment ajouté. */
    public synchronized void removeListener(DownloadListener l) {
        listeners.remove(l);
    }

    /** Dernier état connu d'un téléchargement (null si jamais lancé). */
    @Nullable
    public synchronized DownloadState getDownloadState(String category, int number) {
        return states.get(key(category, number));
    }

    /** Y a-t-il un téléchargement en cours pour ce chant ? */
    public synchronized boolean isDownloading(String category, int number) {
        DownloadState s = states.get(key(category, number));
        return s != null && s.isRunning();
    }

    /** Clé de map pour un chant. */
    private static String key(String category, int number) {
        return PlaybackStore.normalize(category) + ":" + number;
    }

    /** Pose l'état puis notifie les listeners (thread UI). */
    private void setStateAndNotify(DownloadState state) {
        synchronized (this) {
            states.put(key(state.category, state.number), state);
        }
        List<DownloadListener> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(listeners);
        }
        for (DownloadListener l : snapshot) {
            main.post(() -> l.onDownloadStateChanged(state));
        }
    }

    // =========================
    // ANNULATION
    // =========================

    /**
     * Demande l'annulation du téléchargement d'un chant. Le flag est
     * consommé par la boucle de lecture (sécurisé, plusieurs appels sans
     * effet après la fin).
     */
    public void cancelDownload(String category, int number) {
        synchronized (this) {
            cancelRequests.add(key(category, number));
        }
    }

    private boolean consumeCancel(String category, int number) {
        synchronized (this) {
            return cancelRequests.remove(key(category, number));
        }
    }

    // =========================
    // CHECK SERVEUR (dispo / taille / réseau)
    // =========================

    /** Dernier check connu pour un chant (null si jamais vérifié). */
    @Nullable
    public synchronized ServerInfo getServerInfo(String category, int number) {
        return serverInfos.get(key(category, number));
    }

    /**
     * Vérifie si le serveur a le fichier du chant (HEAD) et sa taille.
     * Appelé après construction de l'UI ; le résultat est posté aux
     * listeners (thread UI). Se rejoue automatiquement dès qu'Internet
     * revient si le dernier check a échoué faute de réseau.
     */
    public void checkServer(String category, int number) {
        String cat = PlaybackStore.normalize(category);
        if (cat.isEmpty() || number <= 0) return;

        ServerInfo prev = getServerInfo(cat, number);
        if (prev != null && prev.status == ServerInfo.CHECKING) return;

        lastCheckedKey = key(cat, number);
        registerNetworkCallback();

        ServerInfo checking =
                new ServerInfo(cat, number, ServerInfo.CHECKING, -1);
        synchronized (this) {
            serverInfos.put(key(cat, number), checking);
        }
        notifyServer(checking);

        executor.execute(() -> {
            ServerInfo result;
            if (!isNetworkAvailable()) {
                result = new ServerInfo(cat, number, ServerInfo.NO_INTERNET, -1);
            } else {
                long size = PlaybackApi.remoteLength(
                        PlaybackApi.getPlaybackUrl(cat, number));
                result = size > 0
                        ? new ServerInfo(cat, number, ServerInfo.AVAILABLE, size)
                        : new ServerInfo(cat, number, ServerInfo.UNAVAILABLE, -1);
            }
            synchronized (this) {
                serverInfos.put(key(cat, number), result);
            }
            notifyServer(result);
        });
    }

    /** Re-check quand Internet revient (dernier chant consulté). */
    private void registerNetworkCallback() {
        if (netCallbackRegistered) return;
        synchronized (this) {
            if (netCallbackRegistered) return;
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                netCallbackRegistered = true;
                return;
            }
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            try {
                cm.registerNetworkCallback(request,
                        new ConnectivityManager.NetworkCallback() {
                            @Override
                            public void onAvailable(Network network) {
                                String k = lastCheckedKey;
                                if (k == null) return;
                                String[] parts = k.split(":");
                                if (parts.length != 2) return;
                                try {
                                    checkServer(parts[0],
                                            Integer.parseInt(parts[1]));
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        });
                netCallbackRegistered = true;
            } catch (Exception e) {
                // Pas grave : le check manuel reste disponible
            }
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network active = cm.getActiveNetwork();
        if (active == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void notifyServer(final ServerInfo info) {
        List<DownloadListener> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(listeners);
        }
        main.post(() -> {
            for (DownloadListener l : snapshot) {
                l.onServerStateChanged(info);
            }
        });
    }

    // =========================
    // LECTURE
    // =========================

    /** Toutes les versions d'un chant (la plus récente en premier). */
    public List<Playback> getAll(String category, int number) {
        return store.getAll(category, number);
    }

    // =========================
    // TÉLÉCHARGEMENT
    // =========================

    /**
     * Appelé à l'entrée dans un chant : si aucune version locale n'existe,
     * lance d'office la récupération de la version serveur en arrière-plan.
     * Silence en cas d'absence de connexion ; le listener d'UI affiche
     * l'échec (bouton récupérable).
     */
    public void prefetch(String category, int number) {
        if (category == null || number <= 0) return;
        if (isDownloading(category, number)) return;

        String cat = PlaybackStore.normalize(category);
        if (cat.isEmpty()) return;

        // Une version locale existe déjà : rien à télécharger.
        if (!store.getAll(cat, number).isEmpty()) return;

        // Ne relance pas automatiquement après un échec récent : évite de
        // boussiller la data à chaque swipe si le serveur est par terre.
        DownloadState prev = getDownloadState(cat, number);
        if (prev != null && prev.status == DownloadState.FAILED) return;

        executor.execute(() -> {
            try {
                startDownload(cat, number);
            } catch (CancelException cancelled) {
                setStateAndNotify(new DownloadState(
                        cat, number, DownloadState.CANCELLED, 0, -1));
            } catch (Exception e) {
                setServerResult(cat, number,
                        isNetworkAvailable() ? ServerInfo.UNAVAILABLE
                                : ServerInfo.NO_INTERNET);
                setStateAndNotify(new DownloadState(
                        cat, number, DownloadState.FAILED, 0, -1));
            }
        });
    }

    /**
     * Télécharge la version serveur d'un chant avec progression.
     *
     * Si un téléchargement est déjà en cours pour ce chant, ne redémarre
     * pas : le callback n'est pas appelé (l'UI suit le listener d'état).
     */
    public void downloadFromServer(String category, int number,
                                   @Nullable String label,
                                   Callback<SaveResult> callback) {
        String cat = PlaybackStore.normalize(category);

        if (isDownloading(cat, number)) {
            if (callback != null) {
                main.post(() ->
                        callback.onResult(SaveResult.fail(
                                "Efa misy download mandeha ho an'io hira io")));
            }
            return;
        }

        runSafely(callback, () -> {
            DocumentFile safDoc = null;
            try {
                long total = startDownload(cat, number);

                Playback playback;
                Uri folder = resolveSaveFolder(cat);
                if (folder != null) {
                    String name = store.fileNameFor(cat, number, "mp3");
                    safDoc = createUniqueDocument(folder, name);
                }
                if (safDoc != null) {
                    try (OutputStream os = context.getContentResolver()
                            .openOutputStream(safDoc.getUri())) {
                        downloadTo(PlaybackApi.getPlaybackUrl(cat, number),
                                os, total, cat, number);
                    }
                    String uri = safDoc.getUri().toString();
                    playback = store.save(cat, number,
                            displayTitle(cat, number),
                            uri, Playback.SOURCE_SERVER, label, uri);
                } else {
                    File dest = store.internalFileFor(cat, number, "mp3");
                    downloadTo(PlaybackApi.getPlaybackUrl(cat, number),
                            new FileOutputStream(dest), total, cat, number);
                    playback = store.save(cat, number,
                            displayTitle(cat, number),
                            dest.getAbsolutePath(), Playback.SOURCE_SERVER,
                            label, null);
                }

                setStateAndNotify(new DownloadState(cat, number,
                        DownloadState.DONE, total, total));
                post(callback, SaveResult.ok(playback));

            } catch (CancelException cancelled) {
                deletePartial(safDoc);
                setStateAndNotify(new DownloadState(cat, number,
                        DownloadState.CANCELLED, 0, -1));
                post(callback, SaveResult.fail("Voajanona ny téléchargement"));
            } catch (Exception e) {
                deletePartial(safDoc);
                setServerResult(cat, number,
                        isNetworkAvailable() ? ServerInfo.UNAVAILABLE
                                : ServerInfo.NO_INTERNET);
                setStateAndNotify(new DownloadState(cat, number,
                        DownloadState.FAILED, 0, -1));
                post(callback, SaveResult.fail(
                        "Nisy olana : "
                                + (e.getMessage() != null
                                ? e.getMessage()
                                : e.toString())));
            }
        });
    }

    /** Pose un résultat serveur + notifie (thread UI). */
    private void setServerResult(String category, int number, int status) {
        setServerResult(category, number, status, -1);
    }

    private void setServerResult(String category, int number, int status,
                                 long sizeBytes) {
        ServerInfo info = new ServerInfo(category, number, status, sizeBytes);
        synchronized (this) {
            serverInfos.put(key(category, number), info);
        }
        notifyServer(info);
    }

    /**
     * Phase 1 d'un téléchargement : annonce du serveur et passage à
     * l'état RUNNING ( octets 0 / total ).
     *
     * @return taille totale annoncée (peut être -1 : inconnue)
     */
    private long startDownload(String category, int number)
            throws IOException {
        String cat = PlaybackStore.normalize(category);
        String url = PlaybackApi.getPlaybackUrl(cat, number);

        // Interroge le serveur (HEAD) : une simple connexion ratée lance
        // FAILED, mais pas d'exception jetée au visage de l'utilisateur.
        long total = PlaybackApi.remoteLength(url);
        if (total <= 0) {
            throw new IOException("HTTP : tsy azo alahina ny serveur");
        }

        setStateAndNotify(new DownloadState(cat, number,
                DownloadState.RUNNING, 0, total));
        setServerResult(cat, number, ServerInfo.AVAILABLE, total);
        return total;
    }

    // =========================
    // BOUCLE DE TÉLÉCHARGEMENT
    // =========================

    /** Exception interne pour interrompre proprement un téléchargement. */
    private static class CancelException extends IOException {
    }

    /**
     * Boucle de téléchargement bas niveau : notifie la progression (throttle
     * ~400 ms) et teste le flag d'annulation à chaque bloc lu. Le flux
     * {@code out} est fermé ici.
     */
    private void downloadTo(String url, OutputStream out, long totalBytes,
                            String category, int number) throws IOException {
        long downloaded = 0;
        long lastNotify = 0;
        HttpURLConnection conn = null;
        try {
            java.net.URL u = new java.net.URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(PlaybackApi.CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(PlaybackApi.READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "audio/mpeg");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code + " : " + url);
            }

            try (InputStream in = conn.getInputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (consumeCancel(category, number)) {
                        throw new CancelException();
                    }
                    out.write(buffer, 0, n);
                    downloaded += n;

                    // Throttle notifications (<= 400 ms)
                    long now = System.currentTimeMillis();
                    if (now - lastNotify >= 400) {
                        lastNotify = now;
                        setStateAndNotify(new DownloadState(
                                category, number, DownloadState.RUNNING,
                                downloaded, totalBytes));
                    }
                }
                out.flush();
            }
        } finally {
            if (conn != null) conn.disconnect();
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    // =========================
    // ÉCRITURE (LOCAL)
    // =========================

    /**
     * Ajoute un playback depuis un fichier audio choisi sur le téléphone
     * (Uri). Copié de préférence dans le dossier utilisateur de la
     * catégorie (SAF), sinon dans le stockage interne.
     */
    public void addLocalFile(Uri audioUri, String category, int number,
                             @Nullable String label, Callback<SaveResult> callback) {
        runSafely(callback, () -> {
            DocumentFile safDoc = null;
            try {
                String fileName = queryDisplayName(audioUri);
                String ext = extensionOf(fileName);
                String labelClean = PlaybackStore.cleanLabel(label);
                String docUri = null;
                String filePath;
                // Titre trouvé dans le nom ("012 Jehovah ô deraina Ianao.mp3")
                String parsedTitle =
                        parseFileName(fileName, knownCategories()).title;

                Uri folder = resolveSaveFolder(category);
                if (folder != null) {
                    String name = store.fileNameFor(category, number, ext);
                    safDoc = createUniqueDocument(folder, name);
                }
                if (safDoc != null) {
                    copy(audioUri, context.getContentResolver()
                            .openOutputStream(safDoc.getUri()));
                    filePath = safDoc.getUri().toString();
                    docUri = filePath;
                } else {
                    filePath = internalCopy(audioUri, category, number, ext);
                }

                Playback playback = store.save(category, number,
                        parsedTitle != null ? parsedTitle
                                : displayTitle(category, number),
                        filePath, Playback.SOURCE_LOCAL, labelClean, docUri);

                // Une version locale existe maintenant : l'état serveur
                // (échec ou succès du pré-téléchargement) n'a plus de sens.
                synchronized (this) {
                    states.remove(key(category, number));
                }

                post(callback, SaveResult.ok(playback));
            } catch (Exception e) {
                deletePartial(safDoc);
                post(callback, SaveResult.fail(
                        "Tsy voatahiry : "
                                + (e.getMessage() != null ? e.getMessage() : e.toString())));
            }
        });
    }

    /**
     * Filet de sécurité : exécute la tâche et garantit que le callback est
     * TOUJOURS posté (même sur Error type NoSuchMethodError, qui n'est pas
     * attrapé par catch (Exception) et laissait sinon l'écran en attente).
     */
    private void runSafely(Callback<SaveResult> callback, Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                if (callback != null) {
                    main.post(() ->
                            callback.onResult(SaveResult.fail("Nisy olana : " + t)));
                }
            }
        });
    }

    /**
     * Variante générique pour les callbacks non-SaveResult (delete...).
     */
    private void runSafelyGeneric(Callback<Boolean> callback, Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                post(callback, false);
            }
        });
    }

    /** Supprime un playback (fichier + ligne DB), callback UI. */
    public void delete(Playback playback, Callback<Boolean> callback) {
        long id = playback != null ? playback.getId() : -1;
        runSafelyGeneric(callback, () -> {
            boolean ok = id >= 0 && store.delete(id);
            post(callback, ok);
        });
    }

    /** Suppression synchrone (à n'utiliser que sur le thread de fond). */
    public boolean deleteSync(Playback playback) {
        return playback != null && store.delete(playback.getId());
    }

    // =========================
    // HELPERS
    // =========================

    /** Supprime un document SAF partiel (échec de copie/téléchargement). */
    private void deletePartial(@Nullable DocumentFile doc) {
        if (doc != null) {
            try {
                //noinspection ResultOfMethodCallIgnored
                doc.delete();
            } catch (Exception ignored) {
            }
        }
    }

    /** Copie dans le stockage interne, retourne le chemin absolu. */
    private String internalCopy(Uri from, String category, int number, String ext)
            throws IOException {
        File dest = store.internalFileFor(category, number, ext);
        copy(from, new FileOutputStream(dest));
        return dest.getAbsolutePath();
    }

    /**
     * Crée (ou réutilise) un document dans le dossier SAF avec le nom voulu.
     * Si le nom existe déjà, un suffixe " (2)", " (3)"... est ajouté :
     * les versions existantes ne sont jamais écrasées.
     */
    @Nullable
    private DocumentFile createUniqueDocument(Uri folderUri, String baseName) {
        try {
            DocumentFile folder = DocumentFile.fromTreeUri(context, folderUri);
            if (folder == null || !folder.exists() || !folder.canWrite()) {
                return null;
            }

            int dot = baseName.lastIndexOf('.');
            String stem = dot > 0 ? baseName.substring(0, dot) : baseName;
            String ext = dot > 0 ? baseName.substring(dot) : "";
            String candidate = baseName;

            // Reprise d'un .tmp précédent éventuel
            DocumentFile existing = folder.findFile(candidate + ".tmp");
            if (existing != null && existing.exists()) {
                //noinspection ResultOfMethodCallIgnored
                existing.delete();
            }

            // Le nom existe déjà -> suffixe numérique
            if (folder.findFile(candidate) != null) {
                int i = 2;
                while (folder.findFile(stem + " (" + i + ")" + ext) != null) {
                    i++;
                }
                candidate = stem + " (" + i + ")" + ext;
            }

            DocumentFile dest = folder.createFile("audio/mpeg", candidate);
            // createFile peut ajouter/rénommer l'extension : repartir du réel
            return dest != null && dest.canWrite() ? dest : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Titre affichable du chant (ex. "FFPM 49"). */
    public static String displayTitle(String category, int number) {
        return (category == null ? "" : category.toUpperCase()) + " " + number;
    }

    // =========================
    // DOSSIER DE DESTINATION
    // =========================

    /** Cache des sokajy connus (base de données de l'app). */
    private volatile List<String> knownCategories;

    /**
     * Dossier où ÉCRIRE les fichiers d'une catégorie :
     *
     * 1. le dossier choisi POUR cette catégorie (s'il y en a un) ;
     * 2. sinon le dossier « qui contient tout » + un sous-dossier nommé
     *    d'après la catégorie (créé au besoin, ex. {@code <tout>/ffpm/}),
     *    en réutilisant si possible un sous-dossier existant qui
     *    correspond ("Ffpm" pour "ffpm", "Fihirana Fanampiny" pour "ff") ;
     * 3. sinon null = stockage interne de l'app.
     */
    @Nullable
    private Uri resolveSaveFolder(String category) {
        String cat = PlaybackStore.normalize(category);
        Uri folder = cat.isEmpty() ? null : store.getFolderUri(cat);
        if (folder != null) return folder;

        Uri root = store.getRootFolderUri();
        if (root == null || cat.isEmpty()) return null;

        try {
            DocumentFile rootDoc = DocumentFile.fromTreeUri(context, root);
            if (rootDoc == null || !rootDoc.exists()) return null;

            List<String> known = knownCategories();
            DocumentFile sub = null;
            for (DocumentFile kid : rootDoc.listFiles()) {
                if (!kid.isDirectory()) continue;
                String name = kid.getName();
                if (name == null) continue;
                if (name.equalsIgnoreCase(cat)
                        || cat.equalsIgnoreCase(PlaybackScanner.guessCategory(name, known))) {
                    sub = kid;
                    break;
                }
            }
            if (sub == null) {
                sub = rootDoc.createFile("vnd.android.document/directory", cat);
            }
            if (sub != null && sub.exists() && sub.canWrite()) return sub.getUri();
        } catch (Exception ignored) {
            // Dossier inaccessible : on retombe sur le stockage interne
        }
        return null;
    }

    /** Sokajy connus de l'app (une seule lecture de la base). */
    private List<String> knownCategories() {
        List<String> known = knownCategories;
        if (known == null) {
            known = PlaybackScanner.loadKnownCategories(context);
            knownCategories = known;
        }
        return known;
    }

    /** Taille d'un fichier de playback (octets), -1 si introuvable. */
    public static long sizeOf(@Nullable Playback playback, Context context) {
        if (playback == null || playback.getFilePath() == null) return -1;
        String path = playback.getFilePath();
        try {
            if (path.startsWith("content:")) {
                Cursor c = context.getContentResolver().query(
                        Uri.parse(path), null, null, null, null);
                if (c != null) {
                    try {
                        int idx = c.getColumnIndex(OpenableColumns.SIZE);
                        if (idx >= 0 && c.moveToFirst()) {
                            return c.getLong(idx);
                        }
                    } finally {
                        c.close();
                    }
                }
                return -1;
            }
            File f = new File(path);
            return f.exists() ? f.length() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Taille lisible (ex. "1,4 Mo", "820 ko"). */
    public static String formatSize(long bytes) {
        if (bytes < 0) return "?";
        if (bytes >= 1_000_000) {
            return String.format(java.util.Locale.FRANCE, "%.1f Mo",
                    bytes / 1_000_000.0);
        }
        if (bytes >= 1_000) {
            return String.format(java.util.Locale.FRANCE, "%.0f ko",
                    bytes / 1_000.0);
        }
        return bytes + " o";
    }

    /**
     * Détecte un numéro de chant dans un nom de fichier audio.
     * Cherche le DERNIER nombre dans le nom (hors extension), en sautant
     * les "versions" (v2, version 3...) pour ne pas confondre version et
     * numéro : "ffpm 049 v2.mp3" -> 49, "FFPM 171.mp3" -> 171,
     * "hira 12 v3.mp3" -> 12. Retourne -1 si aucun numéro.
     */
    public static int extractNumber(String fileName) {
        if (fileName == null) return -1;

        // Retire l'extension
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+")
                .matcher(stem);

        int lastNumber = -1;
        int lastEnd = -1;
        int lastNumberBeforeVersion = -1;

        while (m.find()) {
            int value = Integer.parseInt(m.group());
            // Le motif qui précède ce nombre (pour repérer "v", "version")
            String before = stem.substring(
                    m.start() > 1 ? m.start() - 2 : 0, m.start()).toLowerCase();
            boolean isVersionNumber = before.endsWith("v")
                    || before.endsWith("version");

            if (!isVersionNumber) {
                lastNumberBeforeVersion = value;
                lastEnd = m.end();
            }
            lastNumber = value;
        }

        // S'il y a un numéro de version en dernier (ex. "hira 12 v2"),
        // le numéro du chant est celui d'avant.
        if (lastEnd >= 0 && lastNumber != lastNumberBeforeVersion) {
            return lastNumberBeforeVersion;
        }
        return lastNumber;
    }

    /**
     * Détecte un label de version dans un nom de fichier : "... v2.mp3"
     * -> "2", "... version 3.mp3" -> "3", "... (2).mp3" -> "2".
     * Retourne null si aucune version détectée.
     */
    public static String extractVersionLabel(String fileName) {
        if (fileName == null) return null;

        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;

        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?:^|[^a-z0-9])(?:v|version)[\\s_.-]*([0-9]{1,3})(?![0-9])",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(stem);
        if (m.find()) {
            return String.valueOf(Integer.parseInt(m.group(1)));
        }

        // "... (2)" : copie ou version
        java.util.regex.Matcher copy = java.util.regex.Pattern.compile(
                "\\((\\d{1,3})\\)\\s*$").matcher(stem);
        if (copy.find()) {
            return copy.group(1);
        }
        return null;
    }

    // =========================
    // DECOMPOSITION D'UN NOM DE FICHIER
    // =========================

    /**
     * Ce qu'on a pu deviner dans le nom d'un fichier audio :
     * laharana, titre (le texte à côté du laharana), version, sokajy.
     */
    public static class ParsedName {
        /** Laharana du chant (-1 si aucun trouvé). */
        public final int number;
        /** Titre trouvé à côté du laharana, null si vide. */
        public final String title;
        /** Label de version ("v2" -> "2"), null si aucun. */
        public final String label;
        /** Sokajy trouvé dans le nom ("ffpm", "ff"…), null sinon. */
        public final String category;

        ParsedName(int number, String title, String label, String category) {
            this.number = number;
            this.title = title;
            this.label = label;
            this.category = category;
        }

        public boolean hasNumber() {
            return number > 0;
        }
    }

    /** Nombre en tête ("012 Jehovah..." -> 12). */
    private static final java.util.regex.Pattern LEADING_NUMBER =
            java.util.regex.Pattern.compile("^\\s*(\\d{1,6})\\b\\s*[-_.:)\\]]*\\s*(.*)$");

    /**
     * Préfixe non numérique puis nombre ("ffpm 049 v2" -> "ffpm" + 49,
     * "Fihirana Fanampiny - 12 ..." -> "ff" + 12).
     */
    private static final java.util.regex.Pattern PREFIXED_NUMBER =
            java.util.regex.Pattern.compile(
                    "^\\s*([^\\d]{1,30}?)\\s*[-_.:\\]]*\\s*(\\d{1,6})\\b\\s*[-_.:)\\]]*\\s*(.*)$");

    /**
     * Décompose un nom de fichier de chant :
     *
     * <pre>
     * "012 Jehovah ô deraina Ianao.mp3" -> 12 + "Jehovah ô deraina Ianao"
     * "ffpm 049 v2.mp3"                 -> 49 + version "2" + sokajy "ffpm"
     * "ff_12 - Titre.mp3"               -> 12 + "Titre" + sokajy "ff"
     * "12.mp3"                          -> 12
     * "Jehovah 2020.mp3"                -> -1 (pas de laharana fiable)
     * </pre>
     *
     * Le préfixe n'est accepté que s'il correspond à un sokajy connu
     * (sinon un titre comme "Jehovah 2020" serait pris pour un laharana).
     * {@code knownCategories} peut être null/vide.
     */
    public static ParsedName parseFileName(String fileName,
                                           List<String> knownCategories) {
        if (fileName == null) return new ParsedName(-1, null, null, null);

        String stem = stripExtension(fileName);
        if (stem.trim().isEmpty()) return new ParsedName(-1, null, null, null);

        String label = extractVersionLabel(stem);

        // 1) Le laharana est en tête : "012 Jehovah ô deraina Ianao"
        java.util.regex.Matcher m = LEADING_NUMBER.matcher(stem);
        if (m.matches()) {
            int n = toNumber(m.group(1));
            if (n > 0) {
                return new ParsedName(n, cleanTitle(m.group(2)), label, null);
            }
        }

        // 2) Un sokajy connu puis le laharana : "ffpm 049 ..."
        java.util.regex.Matcher m2 = PREFIXED_NUMBER.matcher(stem);
        if (m2.matches()) {
            String prefix = normalizeToken(m2.group(1));
            if (!prefix.isEmpty()) {
                int n = toNumber(m2.group(2));
                String known = PlaybackScanner.guessCategory(prefix, knownCategories);
                if (n > 0 && (known != null || GENERIC_PREFIXES.contains(prefix))) {
                    return new ParsedName(n, cleanTitle(m2.group(3)), label, known);
                }
            }
        }

        return new ParsedName(-1, null, label, null);
    }

    /** Mots courants qui peuvent précéder un laharana (pas un sokajy). */
    private static final java.util.Set<String> GENERIC_PREFIXES =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "hira", "feo", "playback", "chant", "song"));

    /** "012" -> 12, valeur hors bornes -> -1. */
    private static int toNumber(String digits) {
        try {
            int n = Integer.parseInt(digits);
            return (n > 0 && n <= 9999) ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** "ffpm_049" / "ffpm 049 v2.mp3" -> "ffpm_049" / "ffpm 049 v2". */
    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot > 0 && dot < fileName.length() - 1) {
            String ext = fileName.substring(dot + 1).trim();
            if (ext.length() <= 5 && !ext.contains(" ")) {
                return fileName.substring(0, dot);
            }
        }
        return fileName;
    }

    /**
     * Nettoie le titre retrouvé à côté du laharana : retire les séparateurs
     * et le suffixe de version ("... v2" / "... (2)").
     */
    @Nullable
    private static String cleanTitle(String raw) {
        if (raw == null) return null;
        String t = raw.replaceAll("^\\s*[-_.:)\\]]+\\s*", "").trim();
        // "v2" / "version 3" / "(2)" pris comme tout le titre -> vide
        t = t.replaceAll("(?i)^(?:v|version)[\\s._-]*\\d{1,3}$", "").trim();
        // Suffixe de version : "... v2", "... version 3", "... (2)"
        t = t.replaceAll("\\s+(?:v|version)\\s*\\d{1,3}\\s*$", "")
                .replaceAll("\\s*\\(\\d{1,3}\\)\\s*$", "")
                .trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Clé de comparaison d'un nom de dossier/sokajy : minuscules, sans
     * accent ni caractère spécial ("Fihirana Fanampiny" ->
     * "fihiranafanampiny", "Ffpm" -> "ffpm").
     */
    public static String normalizeToken(String raw) {
        if (raw == null) return "";
        String s = java.text.Normalizer.normalize(raw,
                java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Nom du fichier choisi (displayName du ContentResolver). */
    private String queryDisplayName(Uri uri) {
        Cursor c = context.getContentResolver().query(uri, null, null, null, null);
        if (c != null) {
            try {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && c.moveToFirst() && c.getString(idx) != null) {
                    return c.getString(idx);
                }
            } finally {
                c.close();
            }
        }
        return null;
    }

    /** Extension d'après un nom de fichier (fallback "mp3"). */
    private static String extensionOf(String name) {
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && dot < name.length() - 1) {
                String ext = name.substring(dot + 1).toLowerCase();
                if (ext.matches("[a-z0-9]{1,5}")) return ext;
            }
        }
        return "mp3";
    }

    /** Copie le contenu d'une Uri (stockage du téléphone) vers un flux. */
    private void copy(Uri from, OutputStream out) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(from)) {
            if (in == null) {
                throw new IOException("Fichier tsy azo sokafana");
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** Passe le résultat au thread UI. */
    private <T> void post(Callback<T> callback, T result) {
        if (callback != null) {
            main.post(() -> callback.onResult(result));
        }
    }
}
