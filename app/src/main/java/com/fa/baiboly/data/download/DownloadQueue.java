package com.fa.baiboly.data.download;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.fa.baiboly.models.DownloadEntry;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * File de téléchargement de l'écran Downloads.
 *
 * - plusieurs téléchargements à la fois ({@link #MAX_CONCURRENT}) ;
 * - pause / reprise (le fichier .part est conservé, la reprise utilise Range) ;
 * - annulation (supprime le .part) ;
 * - l'état est persisté : après redémarrage, les téléchargements en cours
 *   réapparaissent en pause, prêts à être repris.
 *
 * Un seul exemplaire par processus ({@link #get}).
 */
public final class DownloadQueue {

    public static final int STATE_QUEUED = 0;
    public static final int STATE_RUNNING = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_DONE = 3;
    public static final int STATE_FAILED = 4;
    public static final int STATE_CANCELLED = 5;

    /** Deux fichiers en parallèle : rapide sans saturer la connexion. */
    public static final int MAX_CONCURRENT = 2;

    /** Une ligne de la file. */
    public static final class Task {
        public final String url;
        public final DownloadEntry entry;
        public int state = STATE_QUEUED;
        public long bytes;
        public long total = -1;
        public String error;

        Task(DownloadEntry entry) {
            this.entry = entry;
            this.url = entry.getUrl() == null ? "" : entry.getUrl();
            this.total = entry.getSize() > 0 ? entry.getSize() : -1;
        }

        public int progress() {
            if (state == STATE_DONE) return 100;
            if (total <= 0) return -1;
            return (int) Math.max(0, Math.min(100, bytes * 100 / total));
        }

        public boolean isActive() {
            return state == STATE_QUEUED || state == STATE_RUNNING;
        }
    }

    /** Prévient l'écran (toujours sur le thread principal). */
    public interface Listener {
        void onQueueChanged();
    }

    /** Signal d'interruption d'un téléchargement en cours. */
    private static final class Signal implements ServerManifestClient.CancelSignal {
        volatile boolean cancelled;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static DownloadQueue instance;

    public static synchronized DownloadQueue get(Context context) {
        if (instance == null) {
            instance = new DownloadQueue(context.getApplicationContext());
        }
        return instance;
    }

    private final Context context;
    private final DownloadHistoryStore store;
    private final ServerManifestClient client;
    private final List<Task> tasks = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Signal> signals = new HashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(MAX_CONCURRENT);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable notifyRunnable = this::notifyListeners;
    private boolean restored;

    private DownloadQueue(Context context) {
        this.context = context;
        this.store = new DownloadHistoryStore(context);
        this.client = new ServerManifestClient(context);
    }

    // =========================================================
    // ÉCOUTE
    // =========================================================
    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Listener l : listeners) {
            try {
                l.onQueueChanged();
            } catch (Exception ignored) {
            }
        }
    }

    /** Regroupe les notifications (150 ms) pour ne pas saturer l'écran. */
    private void postNotify() {
        main.removeCallbacks(notifyRunnable);
        main.postDelayed(notifyRunnable, 120);
    }

    // =========================================================
    // LECTURE
    // =========================================================
    public synchronized List<Task> getTasks() {
        ensureRestored();
        return new ArrayList<>(tasks);
    }

    /** Lignes visibles dans « En cours » (y compris pause et échec). */
    public synchronized List<Task> getActiveTasks() {
        ensureRestored();
        List<Task> list = new ArrayList<>();
        for (Task t : tasks) {
            if (t.isActive() || t.state == STATE_PAUSED
                    || t.state == STATE_FAILED) list.add(t);
        }
        return list;
    }

    public synchronized boolean hasActive() {
        ensureRestored();
        for (Task t : tasks) {
            if (t.isActive()) return true;
        }
        return false;
    }

    public synchronized int activeCount() {
        return getActiveTasks().size();
    }

    private synchronized Task find(String url) {
        if (url == null) return null;
        for (Task t : tasks) {
            if (url.equals(t.url)) return t;
        }
        return null;
    }

    // =========================================================
    // ACTIONS
    // =========================================================

    /** Ajoute des entrées à la file (ignore celles déjà terminées). */
    public void enqueue(List<DownloadEntry> entries) {
        enqueue(entries, false);
    }

    /**
     * @param force true = « Maka indray » : retélécharge même si le contenu
     *              est déjà présent sur l'appareil.
     */
    public void enqueue(List<DownloadEntry> entries, boolean force) {
        if (entries == null || entries.isEmpty()) return;
        ensureRestored();
        List<Task> added = new ArrayList<>();
        synchronized (this) {
            for (DownloadEntry entry : entries) {
                if (entry == null || entry.isHeader()) continue;
                String url = entry.getUrl();
                if (url == null || url.isEmpty()) continue;
                if (!force && store.isDownloaded(entry)) continue;

                Task existing = find(url);
                if (existing != null) {
                    if (existing.state == STATE_PAUSED
                            || existing.state == STATE_FAILED
                            || existing.state == STATE_CANCELLED
                            || (force && existing.state == STATE_DONE)) {
                        existing.state = STATE_QUEUED;
                        existing.error = null;
                        existing.bytes = 0;
                        added.add(existing);
                    }
                    continue;
                }
                Task task = new Task(entry);
                tasks.add(task);
                added.add(task);
            }
            persist();
        }
        if (!added.isEmpty()) {
            postNotify();
            schedule();
        }
    }

    public void pause(String url) {
        synchronized (this) {
            Task t = find(url);
            if (t == null) return;
            if (t.state == STATE_RUNNING) {
                Signal s = signals.get(url);
                if (s != null) s.cancelled = true;
                t.state = STATE_PAUSED;
            } else if (t.state == STATE_QUEUED) {
                t.state = STATE_PAUSED;
            } else {
                return;
            }
            persist();
        }
        postNotify();
        schedule();
    }

    public void resume(String url) {
        synchronized (this) {
            Task t = find(url);
            if (t == null) return;
            if (t.state == STATE_PAUSED || t.state == STATE_FAILED
                    || t.state == STATE_CANCELLED) {
                t.state = STATE_QUEUED;
                t.error = null;
            }
            persist();
        }
        postNotify();
        schedule();
    }

    public void cancel(String url) {
        synchronized (this) {
            Task t = find(url);
            if (t == null) return;
            Signal s = signals.get(url);
            if (s != null) s.cancelled = true;
            t.state = STATE_CANCELLED;
            t.bytes = 0;
            t.total = -1;
            deletePart(t.entry);
            persist();
        }
        postNotify();
        schedule();
    }

    /** Retire une ligne terminée / annulée de la liste. */
    public void remove(String url) {
        synchronized (this) {
            Task t = find(url);
            if (t == null) return;
            if (t.isActive()) return;
            tasks.remove(t);
            persist();
        }
        postNotify();
    }

    /** Vide la file des lignes terminées, échouées et annulées. */
    public void clearFinished() {
        synchronized (this) {
            for (int i = tasks.size() - 1; i >= 0; i--) {
                Task t = tasks.get(i);
                if (!t.isActive() && t.state != STATE_PAUSED) tasks.remove(i);
            }
            persist();
        }
        postNotify();
    }

    public void resumeAllPaused() {
        synchronized (this) {
            for (Task t : tasks) {
                if (t.state == STATE_PAUSED) t.state = STATE_QUEUED;
            }
            persist();
        }
        postNotify();
        schedule();
    }

    // =========================================================
    // MOTEUR
    // =========================================================
    private void schedule() {
        List<Task> toStart = new ArrayList<>();
        synchronized (this) {
            int running = 0;
            for (Task t : tasks) {
                if (t.state == STATE_RUNNING) running++;
            }
            for (Task t : tasks) {
                if (running >= MAX_CONCURRENT) break;
                if (t.state != STATE_QUEUED) continue;
                t.state = STATE_RUNNING;
                running++;
                toStart.add(t);
            }
            if (!toStart.isEmpty()) persist();
        }
        for (Task t : toStart) {
            pool.execute(() -> run(t));
        }
        if (!toStart.isEmpty()) postNotify();
    }

    private void run(Task task) {
        Signal signal = new Signal();
        synchronized (this) {
            signals.put(task.url, signal);
        }
        try {
            File file = client.download(task.entry,
                    (received, total) -> {
                        task.bytes = received;
                        task.total = total;
                        postNotify();
                    },
                    signal);

            store.record(task.entry, file);

            // Données perikopa / aina / mofonaina : on importe dans la base.
            // Tout JSON est tenté : le type est déduit du contenu si besoin.
            String dataset = task.entry.inferDataset();
            boolean imported = false;
            if (file.getName().toLowerCase().endsWith(".json")) {
                DataImportService.ImportResult result =
                        new DataImportService(context).importFile(file, dataset);
                imported = result.success;
                if (imported) {
                    store.markImported(task.url, dataset);
                    // Le contenu vit maintenant dans la base : le JSON
                    // téléchargé ne sert plus à rien.
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }

            synchronized (this) {
                task.state = STATE_DONE;
                task.bytes = task.total > 0 ? task.total : task.bytes;
                if (imported) task.entry.setImported(true);
                task.entry.setLocalPath(imported ? null : file.getAbsolutePath());
                signals.remove(task.url);
                persist();
            }
        } catch (ServerManifestClient.CancelledException e) {
            synchronized (this) {
                if (task.state != STATE_CANCELLED) task.state = STATE_PAUSED;
                signals.remove(task.url);
                persist();
            }
        } catch (Exception e) {
            synchronized (this) {
                task.state = STATE_FAILED;
                task.error = e.getMessage();
                signals.remove(task.url);
                persist();
            }
        }
        postNotify();
        schedule();
    }

    private void deletePart(DownloadEntry entry) {
        try {
            File part = new File(ServerManifestClient.downloadsDir(context),
                    ServerManifestClient.fileFor(context, entry).getName() + ".part");
            //noinspection ResultOfMethodCallIgnored
            part.delete();
        } catch (Exception ignored) {
        }
    }

    // =========================================================
    // PERSISTANCE
    // =========================================================
    private void persist() {
        List<DownloadHistoryStore.QueueRow> rows = new ArrayList<>();
        for (Task t : tasks) {
            // Inutile de restaurer les lignes terminées / annulées.
            if (t.state == STATE_DONE || t.state == STATE_CANCELLED) continue;
            DownloadHistoryStore.QueueRow r = new DownloadHistoryStore.QueueRow();
            r.url = t.url;
            r.state = t.state;
            r.bytes = t.bytes;
            r.total = t.total;
            rows.add(r);
        }
        try {
            store.saveQueue(rows);
        } catch (Exception ignored) {
        }
    }

    /** Recharge la file après un redémarrage : en cours → en pause. */
    private void ensureRestored() {
        if (restored) return;
        restored = true;
        try {
            List<DownloadHistoryStore.QueueRow> rows = store.loadQueue();
            for (DownloadHistoryStore.QueueRow row : rows) {
                if (row.url == null || row.url.isEmpty()) continue;
                DownloadEntry entry = store.findByUrl(row.url);
                if (entry == null) continue;
                Task t = new Task(entry);
                t.bytes = row.bytes;
                if (row.total > 0) t.total = row.total;
                t.state = row.state == STATE_DONE ? STATE_DONE
                        : row.state == STATE_RUNNING ? STATE_PAUSED
                        : row.state;
                tasks.add(t);
            }
        } catch (Exception ignored) {
        }
    }
}
