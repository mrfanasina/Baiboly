package com.fa.baiboly.ui.download;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.download.DownloadHistoryStore;
import com.fa.baiboly.data.download.DownloadQueue;
import com.fa.baiboly.data.download.ServerManifestClient;
import com.fa.baiboly.data.playback.PlaybackStore;
import com.fa.baiboly.databinding.ActivityDownloadCenterBinding;
import com.fa.baiboly.models.DownloadEntry;
import com.fa.baiboly.models.Playback;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Écran centré sur les téléchargements :
 *
 * - Mandeha / miandry : file de téléchargement (plusieurs à la fois,
 *   pause / reprise / annulation) ;
 * - Voasintonà : ce qui est déjà téléchargé, supprimable (sauf contenu
 *   importé dans la base, qui reste pour toujours) ;
 * - Feo voasintona : historique des playbacks téléchargés ;
 * - Misy hisintona : catalogue du serveur, sélection multiple et
 *   « Alaina manontolo ».
 *
 * Hors ligne : rien n'est supprimé. Dès que le serveur répond de nouveau,
 * un téléchargement qui n'y existe plus est retiré (les contenus importés
 * dans la base sont protégés).
 */
public class DownloadCenterActivity extends AppCompatActivity
        implements DownloadQueue.Listener {

    private static final String PREFS = "app_settings";
    private static final String KEY_PENDING_APK = "pending_apk_path";
    private static final String KEY_PENDING_APK_VERSION = "pending_apk_version";

    private ActivityDownloadCenterBinding binding;
    private DownloadHistoryStore historyStore;
    private ServerManifestClient manifestClient;
    private PlaybackStore playbackStore;
    private DownloadQueue queue;

    /** Téléchargements terminés (base locale). */
    private List<DownloadEntry> downloadedList = new ArrayList<>();
    /** Playbacks venus du serveur (historique des feo téléchargés). */
    private List<Playback> playbackList = new ArrayList<>();
    /** Lignes affichées : en-têtes de catégorie + éléments du serveur. */
    private List<DownloadEntry> availableList = new ArrayList<>();
    private final Set<String> selectedUrls = new LinkedHashSet<>();

    /** Paquet de mise à jour de l'app proposé par le manifest (ou null). */
    private DownloadEntry appUpdate;
    private boolean updateDownloading;
    /** Entrée dont l'APK est en cours d'enregistrement vers le dossier choisi. */
    private DownloadEntry pendingSaveEntry;

    /** Sélecteur de dossier : « Alaina » enregistre l'APK où l'utilisateur veut. */
    private final ActivityResultLauncher<String> saveApkLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument(
                            "application/vnd.android.package-archive"),
                    uri -> {
                        if (uri != null) saveApkTo(uri);
                    });

    /** Écoute du réseau : dès qu'une connexion est disponible, on revérifie. */
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean networkCallbackRegistered;
    /** Évite deux chargements simultanés (création + retour du réseau). */
    private boolean loadingData;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyGlobalTheme();

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = 120.0f;
        getWindow().setAttributes(params);

        super.onCreate(savedInstanceState);

        binding = ActivityDownloadCenterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        historyStore = new DownloadHistoryStore(this);
        manifestClient = new ServerManifestClient(this);
        playbackStore = new PlaybackStore(this);
        queue = DownloadQueue.get(this);
        queue.addListener(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // Pull to refresh : recharge le manifest du serveur
        binding.swipeRefresh.setColorSchemeColors(
                androidx.core.content.ContextCompat.getColor(this, R.color.success));
        binding.swipeRefresh.setOnRefreshListener(() -> {
            loadData();
            binding.swipeRefresh.setRefreshing(false);
        });

        // File : vider les lignes terminées
        binding.btnClearFinished.setOnClickListener(v -> queue.clearFinished());

        // Sélection multiple
        binding.btnSelectAll.setOnClickListener(v -> toggleSelectAll());
        binding.btnDownloadAll.setOnClickListener(v -> downloadSelectedOrAll());

        cleanInstalledApk();
        loadData();
        refreshMemoryInfo();
        renderQueue();

        // Mise à jour de l'app : carte en haut de l'écran
        binding.btnUpdateNow.setOnClickListener(v -> {
            if (appUpdate != null && !updateDownloading) installUpdate();
        });
        binding.btnDownloadApk.setOnClickListener(v -> {
            if (appUpdate != null && !updateDownloading) downloadApkToChosenFolder();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerNetworkWatcher();
    }

    @Override
    protected void onStop() {
        unregisterNetworkWatcher();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        queue.removeListener(this);
        super.onDestroy();
    }

    /** Détecte le retour du réseau et recharge automatiquement le serveur. */
    private void registerNetworkWatcher() {
        if (networkCallbackRegistered) return;
        connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    if (!isFinishing()) loadData();
                });
            }
        };

        try {
            connectivityManager.registerNetworkCallback(
                    new NetworkRequest.Builder()
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .build(),
                    networkCallback);
            networkCallbackRegistered = true;
        } catch (Exception ignored) {
        }
    }

    private void unregisterNetworkWatcher() {
        if (!networkCallbackRegistered || connectivityManager == null) return;
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Exception ignored) {
        }
        networkCallbackRegistered = false;
    }

    private void applyGlobalTheme() {
        SharedPreferences prefs =
                getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int savedMode = prefs.getInt("theme_mode",
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);
        ColorManager.applyTheme(this);
    }

    // =========================================================
    // 📥 DONNÉES
    // =========================================================
    private void loadData() {
        if (loadingData) return;
        loadingData = true;

        // Historique local
        downloadedList = historyStore.getAll();
        playbackList = serverPlaybacks();
        renderDownloaded();
        renderPlayback();
        renderQueue();

        new Thread(() -> {
            // Catalogue unique : downloads.json contient l'APK de l'app ET
            // les lectures (perikopa, Aina…), avec le lien de chaque fichier.
            List<DownloadEntry> catalog = new ArrayList<>();
            boolean reachable = false;
            try {
                catalog = manifestClient.fetchDownloads();
                reachable = true;
            } catch (Exception ignored) {
            }

            // Mise à jour de l'APK : update.json (fichier dédié, inchangé).
            DownloadEntry update = null;
            try {
                update = manifestClient.fetchAppUpdate();
            } catch (Exception ignored) {
            }

            final boolean serverOk = reachable;
            final List<DownloadEntry> dataResult = catalog;
            final DownloadEntry updateResult = update;
            runOnUiThread(() -> {
                loadingData = false;
                appUpdate = updateResult;
                applyUpdateCard();

                if (serverOk) {
                    // Le serveur répond : on retire ce qui n'y existe plus
                    // (les contenus importés dans la base sont protégés).
                    Set<String> serverUrls = new HashSet<>();
                    for (DownloadEntry e : dataResult) {
                        if (e.getUrl() != null) serverUrls.add(e.getUrl());
                    }
                    int removed = historyStore.reconcile(serverUrls);
                    if (removed > 0) {
                        downloadedList = historyStore.getAll();
                        renderDownloaded();
                    }
                }

                availableList = withCategoryHeaders(dataResult);
                pruneSelection();
                updateAvailableSection(serverOk);
                renderQueue();
            });
        }).start();
    }

    /**
     * Transforme la liste en lignes affichables : un en-tête par catégorie
     * (downloads.json) suivi de ses éléments.
     */
    private List<DownloadEntry> withCategoryHeaders(List<DownloadEntry> entries) {
        List<DownloadEntry> rows = new ArrayList<>();
        String currentCategory = null;
        for (DownloadEntry entry : entries) {
            String category = entry.getCategoryTitle();
            if (category != null && !category.isEmpty()
                    && !category.equals(currentCategory)) {
                rows.add(DownloadEntry.header(category));
                currentCategory = category;
            }
            rows.add(entry);
        }
        return rows;
    }

    private List<Playback> serverPlaybacks() {
        List<Playback> result = new ArrayList<>();
        for (Playback p : playbackStore.getAllPlaybacks()) {
            if (Playback.SOURCE_SERVER.equals(p.getSource())) result.add(p);
        }
        return result;
    }

    // =========================================================
    // ⬆️ MISE À JOUR DE L'APP (carte en haut)
    // =========================================================

    private void applyUpdateCard() {
        if (appUpdate == null) {
            binding.cardUpdate.setVisibility(View.GONE);
            return;
        }
        if (!appUpdate.isNewerThan(installedVersionCode(), installedVersionName())) {
            binding.cardUpdate.setVisibility(View.GONE);
            return;
        }
        binding.cardUpdate.setVisibility(View.VISIBLE);
        if (appUpdate.getVersionName() != null
                && !appUpdate.getVersionName().isEmpty()) {
            binding.textUpdateVersion.setText("v" + appUpdate.getVersionName());
        } else if (appUpdate.getVersionCode() > 0) {
            binding.textUpdateVersion.setText("v" + appUpdate.getVersionCode());
        } else {
            binding.textUpdateVersion.setText("");
        }

        String current = installedVersionName();
        binding.textCurrentVersion.setText("Version : "
                + (current.isEmpty() ? "?" : "v" + current));

        StringBuilder desc = new StringBuilder();
        List<String> notes = appUpdate.getChangelog();
        if (notes != null && !notes.isEmpty()) {
            for (String note : notes) {
                if (desc.length() > 0) desc.append('\n');
                desc.append("• ").append(note);
            }
            binding.textUpdateDesc.setText(desc.toString());
            binding.textUpdateDesc.setVisibility(View.VISIBLE);
        } else {
            binding.textUpdateDesc.setVisibility(View.GONE);
        }

        if (appUpdate.getSize() > 0) {
            binding.textUpdateSize.setVisibility(View.VISIBLE);
            binding.textUpdateSize.setText("Habeny : " + formatBytes(appUpdate.getSize()));
        } else {
            binding.textUpdateSize.setVisibility(View.GONE);
        }
        binding.btnUpdateNow.setEnabled(!updateDownloading);
        binding.btnUpdateNow.setText("Havaozina");
        binding.btnDownloadApk.setEnabled(!updateDownloading);
        binding.btnDownloadApk.setText("Alaina");
    }

    private int installedVersionCode() {
        try {
            android.content.pm.PackageInfo pi = getPackageManager()
                    .getPackageInfo(getPackageName(), 0);
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private String installedVersionName() {
        try {
            android.content.pm.PackageInfo pi = getPackageManager()
                    .getPackageInfo(getPackageName(), 0);
            return pi.versionName != null ? pi.versionName : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Télécharge l'APK depuis le serveur puis lance l'installation. */
    private void installUpdate() {
        if (appUpdate == null) return;
        updateDownloading = true;
        binding.btnUpdateNow.setEnabled(false);
        binding.btnUpdateNow.setText("Miandry…");
        binding.btnDownloadApk.setEnabled(false);

        final DownloadEntry entry = appUpdate;
        new Thread(() -> {
            File apk = null;
            String error = null;
            try {
                apk = manifestClient.download(entry);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final File result = apk;
            final String msg = error;
            runOnUiThread(() -> {
                updateDownloading = false;
                if (result != null && result.length() > 0) {
                    historyStore.record(entry, result);
                    // L'APK sera supprimé dès que la nouvelle version est
                    // réellement installée (voir cleanInstalledApk()).
                    rememberApkForCleanup(result, entry.getVersionCode());
                    openApkInstaller(result);
                    binding.cardUpdate.setVisibility(View.GONE);
                } else {
                    Toast.makeText(this,
                            "Tsy mety nalaina : "
                                    + (msg != null ? msg : "olana avy aty am-potony"),
                            Toast.LENGTH_LONG).show();
                }
                applyUpdateCard();
                loadData();
            });
        }, "update-download").start();
    }

    /** « Alaina » : choisir le dossier puis y enregistrer l'APK. */
    private void downloadApkToChosenFolder() {
        pendingSaveEntry = appUpdate;
        saveApkLauncher.launch(suggestedApkName(appUpdate));
    }

    /** Copie l'APK téléchargé vers l'emplacement choisi par l'utilisateur. */
    private void saveApkTo(Uri uri) {
        final DownloadEntry entry = pendingSaveEntry != null ? pendingSaveEntry : appUpdate;
        if (entry == null) return;

        updateDownloading = true;
        binding.btnUpdateNow.setEnabled(false);
        binding.btnDownloadApk.setEnabled(false);
        binding.btnDownloadApk.setText("Mahandry…");

        new Thread(() -> {
            File apk = null;
            String error = null;
            try {
                apk = manifestClient.download(entry);
                java.io.InputStream in = new java.io.FileInputStream(apk);
                java.io.OutputStream out = getContentResolver().openOutputStream(uri);
                if (out == null) throw new Exception("Tsy afaka alaina");
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);
                out.flush();
                out.close();
                in.close();
            } catch (Exception e) {
                error = e.getMessage();
            }
            final File result = apk;
            final String msg = error;
            runOnUiThread(() -> {
                updateDownloading = false;
                if (result != null && msg == null) {
                    historyStore.record(entry, result);
                    Toast.makeText(this,
                            "Voatahiry ny APK : " + suggestedApkName(entry),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this,
                            "Tsy nety : " + (msg != null ? msg : "tsy misy serveur"),
                            Toast.LENGTH_LONG).show();
                }
                applyUpdateCard();
                loadData();
            });
        }, "update-save").start();
    }

    private static String suggestedApkName(DownloadEntry entry) {
        if (entry == null) return "Baiboly.apk";
        String url = entry.getUrl();
        if (url != null) {
            int slash = url.lastIndexOf('/');
            if (slash >= 0 && slash < url.length() - 1) {
                String name = url.substring(slash + 1);
                if (name.toLowerCase(Locale.US).endsWith(".apk")) return name;
            }
        }
        String version = entry.getVersionName();
        return version != null && !version.isEmpty()
                ? "Baiboly-v" + version + ".apk"
                : "Baiboly.apk";
    }

    /** Passe la main au système pour installer l'APK téléchargé. */
    private void openApkInstaller(File apk) {
        try {
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", apk);
            Intent install = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri,
                            "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(install);
        } catch (Exception e) {
            Toast.makeText(this,
                    "Tsy azo apetraka : " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================
    // 🧹 APK : suppression après installation
    // =========================================================
    private void rememberApkForCleanup(File apk, int versionCode) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_PENDING_APK, apk.getAbsolutePath())
                .putInt(KEY_PENDING_APK_VERSION, versionCode)
                .apply();
    }

    /**
     * Au démarrage : si la version installée a atteint (ou dépassé) celle
     * de l'APK téléchargé, l'APK n'a plus d'utilité → on le supprime.
     */
    private void cleanInstalledApk() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String path = prefs.getString(KEY_PENDING_APK, null);
        if (path == null || path.isEmpty()) return;

        int expected = prefs.getInt(KEY_PENDING_APK_VERSION, 0);
        if (expected > 0 && installedVersionCode() < expected) return;

        File apk = new File(path);
        if (apk.exists()) //noinspection ResultOfMethodCallIgnored
            apk.delete();

        prefs.edit().remove(KEY_PENDING_APK)
                .remove(KEY_PENDING_APK_VERSION).apply();
    }

    // =========================================================
    // ⬇️ SÉLECTION & TÉLÉCHARGEMENT GROUPÉ
    // =========================================================
    private List<DownloadEntry> selectableEntries() {
        List<DownloadEntry> list = new ArrayList<>();
        for (DownloadEntry e : availableList) {
            if (e.isHeader() || e.getUrl() == null || e.getUrl().isEmpty()) continue;
            // Les APK se téléchargent un par un (carte de mise à jour) :
            // « Alaina manontolo » ne concerne que les fichiers de données.
            if (DownloadEntry.TYPE_APP.equals(e.getType())) continue;
            list.add(e);
        }
        return list;
    }

    private void toggleSelectAll() {
        List<DownloadEntry> selectable = selectableEntries();
        boolean allSelected = !selectable.isEmpty();
        for (DownloadEntry e : selectable) {
            if (!selectedUrls.contains(e.getUrl())) {
                allSelected = false;
                break;
            }
        }
        if (allSelected) {
            selectedUrls.clear();
        } else {
            for (DownloadEntry e : selectable) selectedUrls.add(e.getUrl());
        }
        renderAvailable();
    }

    /** « Alaina manontolo » : télécharge la sélection, ou tout si rien n'est coché. */
    private void downloadSelectedOrAll() {
        List<DownloadEntry> selectable = selectableEntries();
        List<DownloadEntry> toDownload = new ArrayList<>();
        for (DownloadEntry e : selectable) {
            if (selectedUrls.isEmpty() || selectedUrls.contains(e.getUrl())) {
                toDownload.add(e);
            }
        }
        if (toDownload.isEmpty()) {
            Toast.makeText(this, "Tsy misy rakitra azo alaina",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        int already = 0;
        for (DownloadEntry e : toDownload) {
            if (historyStore.isDownloaded(e)) already++;
        }
        if (already == toDownload.size()) {
            Toast.makeText(this, "Efa ao daholo ireo rakitra ireo",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        queue.enqueue(toDownload);
        Toast.makeText(this,
                (toDownload.size() - already)
                        + " rakitra napetraka ao amin'ny fila",
                Toast.LENGTH_SHORT).show();
        renderQueue();
    }

    private void pruneSelection() {
        Set<String> known = new HashSet<>();
        for (DownloadEntry e : selectableEntries()) known.add(e.getUrl());
        selectedUrls.retainAll(known);
    }

    private void toggleSelection(DownloadEntry entry) {
        if (entry.getUrl() == null) return;
        if (!selectedUrls.remove(entry.getUrl())) selectedUrls.add(entry.getUrl());
        renderAvailable();
    }

    // =========================================================
    // ⏳ FILE D'ATTENTE
    // =========================================================
    @Override
    public void onQueueChanged() {
        runOnUiThread(() -> {
            renderQueue();
            renderAvailable();
            refreshMemoryInfo();
        });
    }

    private void renderQueue() {
        List<DownloadQueue.Task> tasks = queue.getActiveTasks();
        boolean empty = tasks.isEmpty();
        binding.textQueueEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerQueue.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.chipQueue.setText(queue.activeCount() + " mandeha");

        boolean hasFailed = false;
        for (DownloadQueue.Task task : tasks) {
            if (task.state == DownloadQueue.STATE_FAILED) hasFailed = true;
        }
        binding.btnClearFinished.setVisibility(hasFailed ? View.VISIBLE : View.GONE);

        if (binding.recyclerQueue.getLayoutManager() == null) {
            binding.recyclerQueue.setLayoutManager(new LinearLayoutManager(this));
        }
        if (binding.recyclerQueue.getAdapter() == null) {
            binding.recyclerQueue.setAdapter(new QueueAdapter());
        } else {
            binding.recyclerQueue.getAdapter().notifyDataSetChanged();
        }
    }

    // =========================================================
    // ✅ VOASINTONÀ
    // =========================================================
    private void renderDownloaded() {
        binding.chipRecent.setText(downloadedList.size() + " voasintona");
        binding.chipDownloaded.setText(downloadedList.size() + " rakitra");
        boolean empty = downloadedList.isEmpty();
        binding.textDownloadedEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerDownloaded.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) return;

        binding.recyclerDownloaded.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerDownloaded.setAdapter(new DownloadedAdapter());
    }

    private void renderPlayback() {
        binding.chipPlayback.setText(playbackList.size() + " feo");
        boolean empty = playbackList.isEmpty();
        binding.textPlaybackEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerPlayback.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) return;

        binding.recyclerPlayback.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPlayback.setAdapter(new PlaybackAdapter());
    }

    // =========================================================
    // ⬇️ MISY HISINTONA
    // =========================================================
    private int countDownloads() {
        int count = 0;
        for (DownloadEntry entry : availableList) {
            if (!entry.isHeader()) count++;
        }
        return count;
    }

    private void renderAvailable() {
        if (binding.recyclerAvailable.getLayoutManager() == null) {
            binding.recyclerAvailable.setLayoutManager(new LinearLayoutManager(this));
        }
        if (binding.recyclerAvailable.getAdapter() == null) {
            binding.recyclerAvailable.setAdapter(new AvailableAdapter());
        } else {
            binding.recyclerAvailable.getAdapter().notifyDataSetChanged();
        }
        int total = selectableEntries().size();
        binding.textSelectedCount.setText(selectedUrls.size() + " / " + total + " voasafidy");
        binding.btnDownloadAll.setEnabled(total > 0);
    }

    private void updateAvailableSection(boolean fromServer) {
        int count = countDownloads();
        binding.chipAvailable.setText(count + " misy hisintona");
        binding.textAvailableEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        if (count == 0) {
            binding.textAvailableEmpty.setText(fromServer
                    ? "Tsy misy rakitra azo alaina amin'izao fotoana izao."
                    : "Tsy misy réseau : ny voasintona dia tehirizina.");
        }
        if (fromServer) {
            binding.textServerState.setText("Misy");
            binding.textServerState.setTextColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.success));
        } else {
            // Hors ligne : on ne supprime RIEN, on le dit simplement.
            binding.textServerState.setText("Tsy misy réseau");
            binding.textServerState.setTextColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.orange));
        }
        renderAvailable();
    }

    // =========================================================
    // MÉMOIRE
    // =========================================================
    private void refreshMemoryInfo() {
        File dbDir = getDatabasePath("download_center.db").getParentFile();
        long dbSize = folderSize(dbDir);
        long downloads = ServerManifestClient.downloadsSize(this);
        binding.textStorage.setText(formatBytes(dbSize + downloads));
    }

    private long folderSize(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                size += f.isDirectory() ? folderSize(f) : f.length();
            }
        }
        return size;
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f kB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // =========================================================
    // ADAPTER : FILE D'ATTENTE
    // =========================================================
    class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_download_progress, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            List<DownloadQueue.Task> tasks = queue.getActiveTasks();
            if (position >= tasks.size()) return;
            DownloadQueue.Task task = tasks.get(position);

            holder.itemIcon.setImageResource(iconFor(task.entry.getType()));
            holder.textTitle.setText(task.entry.getTitle());

            String state;
            switch (task.state) {
                case DownloadQueue.STATE_RUNNING:
                    state = task.progress() >= 0
                            ? task.progress() + " %"
                            : "Mandeha…";
                    holder.btnPauseResume.setText("Ajanona");
                    holder.bar.setIndeterminate(task.total <= 0);
                    if (task.total > 0) {
                        holder.bar.setProgressCompat(task.progress(), true);
                    }
                    break;
                case DownloadQueue.STATE_QUEUED:
                    state = "Miandry…";
                    holder.btnPauseResume.setText("Ajanona");
                    holder.bar.setIndeterminate(true);
                    break;
                case DownloadQueue.STATE_PAUSED:
                    state = "Najanona";
                    holder.btnPauseResume.setText("Tohizo");
                    holder.bar.setIndeterminate(false);
                    holder.bar.setProgressCompat(
                            Math.max(0, task.progress()), false);
                    break;
                case DownloadQueue.STATE_FAILED:
                    state = "Tsy nety" + (task.error == null ? "" : " : " + task.error);
                    holder.btnPauseResume.setText("Haverina");
                    holder.bar.setIndeterminate(false);
                    holder.bar.setProgressCompat(
                            Math.max(0, task.progress()), false);
                    break;
                case DownloadQueue.STATE_CANCELLED:
                    state = "Nofoanana";
                    holder.btnPauseResume.setText("Haverina");
                    holder.bar.setIndeterminate(false);
                    break;
                default:
                    state = "Vita";
                    holder.btnPauseResume.setText("Ajanona");
                    holder.bar.setIndeterminate(false);
                    break;
            }

            String size = task.total > 0
                    ? formatBytes(task.bytes) + " / " + formatBytes(task.total)
                    : formatBytes(task.bytes);
            holder.textProgressSub.setText(state + " · " + size);
            holder.textPercent.setText(
                    task.state == DownloadQueue.STATE_RUNNING && task.total > 0
                            ? task.progress() + " %" : "");

            holder.btnPauseResume.setOnClickListener(v -> {
                if (task.state == DownloadQueue.STATE_RUNNING
                        || task.state == DownloadQueue.STATE_QUEUED) {
                    queue.pause(task.url);
                } else {
                    queue.resume(task.url);
                }
            });
            holder.btnCancelProgress.setOnClickListener(v -> {
                queue.cancel(task.url);
                queue.remove(task.url);
            });
        }

        @Override
        public int getItemCount() {
            return queue.getActiveTasks().size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView itemIcon;
            TextView textTitle, textProgressSub, textPercent;
            com.google.android.material.button.MaterialButton btnPauseResume,
                    btnCancelProgress;
            com.google.android.material.progressindicator.LinearProgressIndicator bar;

            VH(View view) {
                super(view);
                itemIcon = view.findViewById(R.id.itemIcon);
                textTitle = view.findViewById(R.id.textTitle);
                textProgressSub = view.findViewById(R.id.textProgressSub);
                textPercent = view.findViewById(R.id.textPercent);
                btnPauseResume = view.findViewById(R.id.btnPauseResume);
                btnCancelProgress = view.findViewById(R.id.btnCancelProgress);
                bar = view.findViewById(R.id.barProgress);
            }
        }
    }

    // =========================================================
    // ADAPTER : VOASINTONÀ
    // =========================================================
    class DownloadedAdapter extends RecyclerView.Adapter<DownloadedAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_download_recent, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            if (position >= downloadedList.size()) return;
            DownloadEntry entry = downloadedList.get(position);

            holder.itemIcon.setImageResource(iconFor(entry.getType()));
            holder.textTitle.setText(entry.getTitle());
            holder.textSubtitle.setText(entry.getSubtitle() == null
                    ? "" : entry.getSubtitle());

            if (entry.isImported()) {
                // Importé dans la base : conservé pour toujours.
                holder.textStatus.setText("Ao amin'ny base");
                holder.textStatus.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                                DownloadCenterActivity.this, R.color.success));
                holder.btnDelete.setVisibility(View.GONE);
            } else {
                holder.textStatus.setText(entry.getSize() > 0
                        ? formatBytes(entry.getSize()) : "Vita");
                holder.textStatus.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                                DownloadCenterActivity.this, R.color.secondary_text));
                holder.btnDelete.setVisibility(View.VISIBLE);
                holder.btnDelete.setOnClickListener(v -> confirmDelete(entry));
            }
        }

        @Override
        public int getItemCount() {
            return downloadedList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView itemIcon;
            TextView textTitle, textSubtitle, textStatus;
            com.google.android.material.button.MaterialButton btnDelete;

            VH(View view) {
                super(view);
                itemIcon = view.findViewById(R.id.itemIcon);
                textTitle = view.findViewById(R.id.textTitle);
                textSubtitle = view.findViewById(R.id.textSubtitle);
                textStatus = view.findViewById(R.id.textStatus);
                btnDelete = view.findViewById(R.id.btnDelete);
            }
        }
    }

    private void confirmDelete(DownloadEntry entry) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Hamafa ?")
                .setMessage(entry.getTitle() + "\n\nHo voafafa ny rakitra voasintona.")
                .setPositiveButton("Hamafa", (d, w) -> {
                    if (historyStore.deleteByUrl(entry.getUrl(), true)) {
                        downloadedList = historyStore.getAll();
                        renderDownloaded();
                        refreshMemoryInfo();
                        Toast.makeText(this, "Voafafa", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Tsy voafafa", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Tsia", null)
                .show();
    }

    // =========================================================
    // ADAPTER : FEO VOASINTONA
    // =========================================================
    class PlaybackAdapter extends RecyclerView.Adapter<PlaybackAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_download_recent, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            if (position >= playbackList.size()) return;
            Playback pb = playbackList.get(position);

            holder.itemIcon.setImageResource(R.drawable.ic_music_note);
            holder.textTitle.setText(pb.getTitle() != null && !pb.getTitle().isEmpty()
                    ? pb.getTitle()
                    : pb.getCategory().toUpperCase(Locale.US) + " " + pb.getNumber());
            holder.textSubtitle.setText(pb.getCategory().toUpperCase(Locale.US)
                    + " " + pb.getNumber() + " · "
                    + (pb.getLabel() == null ? "Serveur" : pb.getLabel()));
            holder.textStatus.setText("Feo");
            holder.textStatus.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                            DownloadCenterActivity.this, R.color.secondary_text));
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> {
                if (playbackStore.delete(pb.getId())) {
                    playbackList = serverPlaybacks();
                    renderPlayback();
                    refreshMemoryInfo();
                    Toast.makeText(DownloadCenterActivity.this,
                            "Voafafa ny feo", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return playbackList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView itemIcon;
            TextView textTitle, textSubtitle, textStatus;
            com.google.android.material.button.MaterialButton btnDelete;

            VH(View view) {
                super(view);
                itemIcon = view.findViewById(R.id.itemIcon);
                textTitle = view.findViewById(R.id.textTitle);
                textSubtitle = view.findViewById(R.id.textSubtitle);
                textStatus = view.findViewById(R.id.textStatus);
                btnDelete = view.findViewById(R.id.btnDelete);
            }
        }
    }

    // =========================================================
    // ADAPTER : MISY HISINTONA
    // =========================================================
    class AvailableAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return availableList.get(position).isHeader() ? 0 : 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 0) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_download_category_header, parent, false);
                return new HeaderVH(view);
            }
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_download_available, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder baseHolder, int position) {
            DownloadEntry entry = availableList.get(position);

            if (baseHolder instanceof HeaderVH) {
                ((HeaderVH) baseHolder).textCategory.setText(entry.getTitle());
                return;
            }

            VH holder = (VH) baseHolder;
            holder.itemIcon.setImageResource(iconFor(entry.getType()));
            holder.textTitle.setText(entry.getTitle());

            String label = "";
            String version = entry.getVersionName();
            boolean isApk = DownloadEntry.TYPE_APP.equals(entry.getType())
                    || "apk".equals(entry.getType());
            if (version != null && !version.isEmpty()) {
                label = "v" + version;
                if (isApk) {
                    String status = versionStatus(version);
                    if (!status.isEmpty()) label += " · " + status;
                }
            } else if (entry.getVersionCode() > 0) {
                label = "v" + entry.getVersionCode();
            }
            holder.textVersion.setText(label);
            holder.textVersion.setVisibility(
                    label.isEmpty() ? View.GONE : View.VISIBLE);

            String description = entry.getSubtitle();
            holder.textSubtitle.setText(description == null ? "" : description);
            holder.textSubtitle.setVisibility(
                    description == null || description.isEmpty() ? View.GONE : View.VISIBLE);

            String meta = buildMeta(entry);
            holder.textMeta.setText(meta);
            holder.textMeta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);

            String url = entry.getUrl();
            boolean selectable = !DownloadEntry.TYPE_APP.equals(entry.getType());
            holder.checkItem.setVisibility(selectable ? View.VISIBLE : View.GONE);
            boolean imported = historyStore.isImported(url);
            boolean done = historyStore.isDownloaded(entry);
            holder.btnDownload.setText(imported ? "Havaozina"
                    : done ? "Maka indray" : "Alaina");
            holder.btnDownload.setOnClickListener(v -> {
                List<DownloadEntry> one = new ArrayList<>();
                one.add(entry);
                queue.enqueue(one, true);
                renderQueue();
            });

            if (imported) {
                holder.textState.setText("Ao amin'ny base · tsy voafafa");
                holder.textState.setTextColor(
                        ContextCompat.getColor(DownloadCenterActivity.this,
                                R.color.success));
                holder.textState.setVisibility(View.VISIBLE);
            } else if (done) {
                holder.textState.setText("Efa voasintona");
                holder.textState.setTextColor(
                        ContextCompat.getColor(DownloadCenterActivity.this,
                                R.color.secondary_text));
                holder.textState.setVisibility(View.VISIBLE);
            } else {
                holder.textState.setVisibility(View.GONE);
            }

            boolean selected = url != null && selectedUrls.contains(url);
            MaterialCardView card = (MaterialCardView) holder.itemView;
            card.setStrokeWidth(selected ? 2 : 1);
            card.setStrokeColor(selected
                    ? MaterialColors.getColor(card,
                    androidx.appcompat.R.attr.colorPrimary)
                    : ContextCompat.getColor(DownloadCenterActivity.this,
                    R.color.card_stroke));

            holder.checkItem.setOnCheckedChangeListener(null);
            holder.checkItem.setChecked(selected);
            holder.checkItem.setOnCheckedChangeListener((b, checked) -> {
                if (url == null) return;
                if (checked) selectedUrls.add(url);
                else selectedUrls.remove(url);
                binding.textSelectedCount.setText(
                        selectedUrls.size() + " / " + selectableEntries().size()
                                + " voasafidy");
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
            });
            holder.itemView.setOnClickListener(
                    selectable ? v -> toggleSelection(entry) : null);
        }

        @Override
        public int getItemCount() {
            return availableList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView itemIcon;
            TextView textTitle, textVersion, textSubtitle, textMeta, textState;
            CheckBox checkItem;
            com.google.android.material.button.MaterialButton btnDownload;

            VH(View view) {
                super(view);
                itemIcon = view.findViewById(R.id.itemIcon);
                textTitle = view.findViewById(R.id.textTitle);
                textVersion = view.findViewById(R.id.textVersion);
                textSubtitle = view.findViewById(R.id.textSubtitle);
                textMeta = view.findViewById(R.id.textMeta);
                textState = view.findViewById(R.id.textState);
                checkItem = view.findViewById(R.id.checkItem);
                btnDownload = view.findViewById(R.id.btnDownload);
            }
        }

        class HeaderVH extends RecyclerView.ViewHolder {
            TextView textCategory;

            HeaderVH(View view) {
                super(view);
                textCategory = view.findViewById(R.id.textCategory);
            }
        }
    }

    /**
     * « Actuel » si c'est la version installée, « Ancienne » si elle est
     * plus ancienne, « Vaovao » si elle est plus récente.
     */
    private String versionStatus(String version) {
        if (version == null || version.isEmpty()) return "";
        String current = installedVersionName();
        if (current.isEmpty()) return "";
        int cmp = DownloadEntry.compareVersions(version, current);
        if (cmp == 0) return "Mitovy";
        return cmp < 0 ? "Taloha" : "Vaovao";
    }

    /** "38.0 MB · 24 Nov 2026" (morceaux disponibles seulement). */
    private static String buildMeta(DownloadEntry entry) {
        StringBuilder meta = new StringBuilder();
        if (entry.getSize() > 0) meta.append(formatBytes(entry.getSize()));
        if (entry.getPublishedAt() != null && !entry.getPublishedAt().isEmpty()) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(entry.getPublishedAt());
        }
        return meta.toString();
    }

    private static int iconFor(String type) {
        if (DownloadEntry.TYPE_MOFONAINA.equals(type)) {
            return R.drawable.ic_bible;
        }
        if (DownloadEntry.TYPE_PERIKOPA.equals(type)) {
            return R.drawable.book_open;
        }
        if (DownloadEntry.TYPE_AINA.equals(type)) {
            return R.drawable.ic_bible;
        }
        if (DownloadEntry.TYPE_APP.equals(type) || "apk".equals(type)) {
            return R.drawable.ic_download;
        }
        return R.drawable.book_bookmark_svgrepo_com;
    }

    // =========================================================
    // 🚀 OUVERTURE DEPUIS N'IMPORTE QUELLE PARTIE DE L'APP
    // =========================================================
    public static void open(Context context) {
        context.startActivity(new Intent(context, DownloadCenterActivity.class));
    }
}
