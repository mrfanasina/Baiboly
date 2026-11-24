package com.fa.baiboly.ui.download;

import android.app.ActivityManager;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.Intent;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.download.DownloadHistoryStore;
import com.fa.baiboly.data.download.ServerManifestClient;
import com.fa.baiboly.databinding.ActivityDownloadCenterBinding;
import com.fa.baiboly.models.DownloadEntry;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Écran centré sur les téléchargements :
 *
 * - Voasintona farany
 * - Misy hisintona (perikopa, mofonaina…) lu depuis le serveur
 * - Fahatsiarovana ampiasaina (RAM + angona eo an-toerana)
 *
 * L'importation dans la base SQLite (perikopa_days…) se branchera
 * directement dans ServerManifestClient.download() plus tard.
 */
public class DownloadCenterActivity extends AppCompatActivity {

    private ActivityDownloadCenterBinding binding;
    private DownloadHistoryStore historyStore;
    private ServerManifestClient manifestClient;

    private List<DownloadEntry> recentList = new ArrayList<>();
    private List<DownloadEntry> availableList = new ArrayList<>();
    private final List<DownloadEntry> fallbackList = new ArrayList<>();

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

    /** Écoute du réseau : dès qu'une connexion est disponible, on revérifie update.json. */
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

        setupFallbackEntries();
        loadData();
        refreshMemoryInfo();

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

    /** Détecte le retour du réseau et recharge update.json automatiquement. */
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
                getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        int savedMode = prefs.getInt("theme_mode",
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);
        ColorManager.applyTheme(this);
    }

    // =========================================================
    // 📥 DONNÉES
    // =========================================================
    private void setupFallbackEntries() {
        // Sert si le serveur n'est pas encore joignable ou le manifest vide
        // — prépare déjà les paquets d'ANNÉES SUIVANTES (perikopa 2027,
        // lecture quotidienne, mofonaina archivée…) : dès que le lien
        // contient ces fichiers, le manifest du serveur les remplacera.
        int nextYear = java.util.Calendar.getInstance()
                .get(java.util.Calendar.YEAR) + 1;
        fallbackList.clear();
        fallbackList.add(new DownloadEntry(
                DownloadEntry.TYPE_PERIKOPA, "Perikopa " + nextYear,
                "Vakiteny isam-andro · taona manaraka",
                "perikopa/" + nextYear + ".json", 0L));
        fallbackList.add(new DownloadEntry(
                DownloadEntry.TYPE_MOFONAINA, "Mofonaina androany",
                "Mofo tsy miseho", null, 0L));
    }

    private void loadData() {
        if (loadingData) return;
        loadingData = true;

        // Historique récent
        recentList = historyStore.getRecent(20);
        updateRecentSection();

        // Catalogue downloads.json + manifest serveur + update.json
        binding.textServerState.setText("Miomana amin'ny serveur…");
        new Thread(() -> {
            // Catalogue officiel : catégories, versions, autres fichiers.
            List<DownloadEntry> catalog = new ArrayList<>();
            try {
                catalog = manifestClient.fetchDownloads();
            } catch (Exception ignored) {
            }

            List<DownloadEntry> fetched = new ArrayList<>();
            try {
                fetched = manifestClient.fetchManifest();
            } catch (Exception ignored) {
            }

            // Mise à jour publiée dans update.json (GitHub).
            DownloadEntry update = null;
            try {
                update = manifestClient.fetchAppUpdate();
            } catch (Exception ignored) {
            }

            // Sépare le paquet "app" (mise à jour) des données —il a
            // sa carte dédiée tout en haut de l'écran.
            List<DownloadEntry> data = new ArrayList<>();
            for (DownloadEntry e : fetched) {
                if (DownloadEntry.TYPE_APP.equals(e.getType())) {
                    if (update == null || e.getVersionCode() > update.getVersionCode()) {
                        update = e;
                    }
                } else if (catalog.isEmpty()) {
                    // Catalogue absent : on garde l'ancien manifest.
                    data.add(e);
                }
            }

            // downloads.json a la priorité (versions des autres apps).
            if (!catalog.isEmpty()) {
                data.addAll(0, catalog);
            }
            if (data.isEmpty()) data.addAll(fallbackList);

            final List<DownloadEntry> dataResult = data;
            final boolean fromServer = !catalog.isEmpty() || !fetched.isEmpty();
            final DownloadEntry updateResult = update;
            runOnUiThread(() -> {
                loadingData = false;
                appUpdate = updateResult;
                applyUpdateCard();
                availableList = withCategoryHeaders(dataResult);
                updateAvailableSection(fromServer);
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

    // =========================================================
    // ⬆️ MISE À JOUR DE L'APP (carte en haut)
    // =========================================================

    /**
     * Affiche la carte uniquement si le manifest propose une entrée
     * "app" dont versionCode est strictement postérieur à la version
     * installée. Si le lien n'a pas de versionCode, on Accepte quand
     * même ( carte visible ) — le serveur est déjà en train de décider.
     */
    private void applyUpdateCard() {
        if (appUpdate == null) {
            binding.cardUpdate.setVisibility(View.GONE);
            return;
        }
        if (appUpdate.getVersionCode() > 0
                && appUpdate.getVersionCode() <= installedVersionCode()) {
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

        // Version installée sur l'appareil.
        String current = installedVersionName();
        binding.textCurrentVersion.setText("Version ankehitriny : "
                + (current.isEmpty() ? "?" : "v" + current));

        // Changelog complet de update.json, affiché directement.
        StringBuilder desc = new StringBuilder();
        java.util.List<String> notes = appUpdate.getChangelog();
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

    /** versionCode (version name) Currently installed. */
    private int installedVersionCode() {
        try {
            android.content.pm.PackageInfo pi = getPackageManager()
                    .getPackageInfo(getPackageName(), 0);
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    /** versionName installé sur l'appareil ("5.3"), vide si illisible. */
    private String installedVersionName() {
        try {
            android.content.pm.PackageInfo pi = getPackageManager()
                    .getPackageInfo(getPackageName(), 0);
            return pi.versionName != null ? pi.versionName : "";
        } catch (Exception e) {
            return "";
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
        int cmp = compareVersions(version, current);
        if (cmp == 0) return "Actuel";
        return cmp < 0 ? "Ancienne" : "Vaovao";
    }

    /** Compare deux versions numériques ("5.2" < "5.10"). */
    private static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int count = Math.max(a.length, b.length);
        for (int i = 0; i < count; i++) {
            int va = i < a.length ? parseVersionPart(a[i]) : 0;
            int vb = i < b.length ? parseVersionPart(b[i]) : 0;
            if (va != vb) return va < vb ? -1 : 1;
        }
        return 0;
    }

    private static int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** Télécharge l'APK depuis le serveur puis lance l'installation. */
    private void installUpdate() {
        if (appUpdate == null) return;
        updateDownloading = true;
        binding.btnUpdateNow.setEnabled(false);
        binding.btnUpdateNow.setText("Mahandry…");
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
                    historyStore.add(entry, result.length(), System.currentTimeMillis());
                    openApkInstaller(result);
                    binding.cardUpdate.setVisibility(View.GONE);
                } else {
                    Toast.makeText(this,
                            "Tsy nety misintona : "
                                    + (msg != null ? msg : "tsy misy serveur"),
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
                if (out == null) throw new Exception("Tsy azo soratana ny rakitra");
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
                    historyStore.add(entry, result.length(), System.currentTimeMillis());
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

    /** Nom de fichier proposé pour l'APK (Baiboly-v5.3.apk). */
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

    private void updateRecentSection() {
        binding.chipRecent.setText(recentList.size() + " voasintona");
        if (recentList.isEmpty()) {
            binding.textRecentEmpty.setVisibility(View.VISIBLE);
            binding.recyclerRecent.setVisibility(View.GONE);
            return;
        }
        binding.textRecentEmpty.setVisibility(View.GONE);
        binding.recyclerRecent.setVisibility(View.VISIBLE);
        binding.recyclerRecent.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerRecent.setAdapter(new RecentAdapter());
    }

    private int countDownloads() {
        int count = 0;
        for (DownloadEntry entry : availableList) {
            if (!entry.isHeader()) count++;
        }
        return count;
    }

    private void updateAvailableSection(boolean fromServer) {
        int count = countDownloads();
        binding.chipAvailable.setText(count + " misy hisintona");
        binding.textAvailableEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        if (count == 0) {
            binding.textAvailableEmpty.setText("Tsy misy rakitra azo alaina amin'izao fotoana izao.");
        }
        if (fromServer) {
            binding.textServerState.setText("● Misy");
            binding.textServerState.setTextColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.success));
        } else {
            binding.textServerState.setText("● Tsy mbola misy serveur");
            binding.textServerState.setTextColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.orange));
        }
        binding.recyclerAvailable.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerAvailable.setAdapter(new AvailableAdapter());
    }

    // =========================================================
    // 📊 MÉMOIRE
    // =========================================================
    private void refreshMemoryInfo() {
        // RAM totale du téléphone vs RAM libre
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            am.getMemoryInfo(info);
            long total = info.totalMem;
            long free = info.availMem;
            long used = total - free;
            int percent = total > 0 ? (int) (used * 100 / total) : 0;

            binding.progressRam.setMax(100);
            binding.progressRam.setProgress(percent);
            binding.textRam.setText(
                    formatBytes(used) + " / " + formatBytes(total)
                            + "  (" + percent + " %)");
        }

        // Angona de l'app : base SQLite + fichiers téléchargés
        File dbDir = getDatabasePath("download_center.db").getParentFile();
        long dbSize = folderSize(dbDir);
        long downloads = ServerManifestClient.downloadsSize(this);
        binding.textStorage.setText(
                formatBytes(dbSize + downloads));
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
    // ⬇️ TÉLÉCHARGEMENT ( préparation de la future importation )
    // =========================================================
    private void downloadEntry(DownloadEntry entry, TextView button) {
        button.setEnabled(false);
        button.setText("Mahandry…");

        new Thread(() -> {
            File file = null;
            String error = null;
            try {
                file = manifestClient.download(entry);
            } catch (Exception e) {
                error = e.getMessage();
            }
            checkFileAndRecord(entry, file, error, button);
        }).start();
    }

    private void checkFileAndRecord(DownloadEntry entry, File file, String error, TextView button) {
        runOnUiThread(() -> {
            if (file != null) {
                historyStore.add(entry, file.length(), System.currentTimeMillis());
                Toast.makeText(this,
                        "Voasintona : " + file.getName(),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "Tsy nety : " + (error != null ? error
                                : "tsy misy serveur"),
                        Toast.LENGTH_SHORT).show();
            }
            button.setEnabled(true);
            button.setText("Sintomina");
            loadData();
        });
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

            // Version de l'APK + statut : Actuel / Ancienne / Vaovao.
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

            boolean done = historyStore.hasUrl(entry.getUrl());
            holder.btnDownload.setText(
                    done
                            ? "Sintomina indray"
                            : "Sintomina");
            holder.btnDownload.setOnClickListener(
                    v -> downloadEntry(entry, holder.btnDownload));
        }

        @Override
        public int getItemCount() {
            return availableList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView itemIcon;
            TextView textTitle, textVersion, textSubtitle, textMeta;
            com.google.android.material.button.MaterialButton btnDownload;

            VH(View view) {
                super(view);
                itemIcon = view.findViewById(R.id.itemIcon);
                textTitle = view.findViewById(R.id.textTitle);
                textVersion = view.findViewById(R.id.textVersion);
                textSubtitle = view.findViewById(R.id.textSubtitle);
                textMeta = view.findViewById(R.id.textMeta);
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

    // =========================================================
    // ADAPTER : VOASINTONA FARANY
    // =========================================================
    class RecentAdapter extends RecyclerView.Adapter<RecentAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_download_recent, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            DownloadEntry entry = recentList.get(position);
            holder.itemIcon.setImageResource(iconFor(entry.getType()));
            holder.textTitle.setText(entry.getTitle());
            holder.textSubtitle.setText(entry.getSubtitle() == null
                    ? "" : entry.getSubtitle());
        }

        @Override
        public int getItemCount() {
            return recentList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView itemIcon;
            TextView textTitle, textSubtitle;

            VH(View view) {
                super(view);
                itemIcon = view.findViewById(R.id.itemIcon);
                textTitle = view.findViewById(R.id.textTitle);
                textSubtitle = view.findViewById(R.id.textSubtitle);
            }
        }
    }

    private static int iconFor(String type) {
        if (DownloadEntry.TYPE_MOFONAINA.equals(type)) {
            return R.drawable.ic_bible;
        }
        if (DownloadEntry.TYPE_PERIKOPA.equals(type)) {
            return R.drawable.book_open;
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
