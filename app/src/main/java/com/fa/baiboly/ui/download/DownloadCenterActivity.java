package com.fa.baiboly.ui.download;

import android.app.ActivityManager;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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
        binding.btnUpdateApp.setOnClickListener(v -> {
            if (appUpdate != null && !updateDownloading) installUpdate();
        });
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
        // Historique récent
        recentList = historyStore.getRecent(20);
        updateRecentSection();

        // Manifest du serveur (async léger)
        binding.textServerState.setText("Miomana amin'ny serveur…");
        new Thread(() -> {
            List<DownloadEntry> fetched = new ArrayList<>();
            try {
                fetched = manifestClient.fetchManifest();
            } catch (Exception ignored) {
            }

            // Sépare le paquet "app" (mise à jour) des données —il a
            // sa carte dédiée tout en haut de l'écran.
            DownloadEntry update = null;
            List<DownloadEntry> data = new ArrayList<>();
            for (DownloadEntry e : fetched) {
                if (DownloadEntry.TYPE_APP.equals(e.getType())) {
                    if (update == null || e.getVersionCode() > update.getVersionCode()) {
                        update = e;
                    }
                } else {
                    data.add(e);
                }
            }
            if (data.isEmpty()) data.addAll(fallbackList);

            final List<DownloadEntry> dataResult = data;
            final boolean fromServer = !fetched.isEmpty();
            final DownloadEntry updateResult = update;
            runOnUiThread(() -> {
                appUpdate = updateResult;
                applyUpdateCard();
                availableList = dataResult;
                updateAvailableSection(fromServer);
            });
        }).start();
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
        binding.textUpdateDesc.setText(
                "Fandaharana vaovao azo misintona avy amin'ny serveur. "
                        + "Rehefa vita ny fisintona dia atao avy hatrany ny fametrahana.");
        binding.btnUpdateApp.setEnabled(!updateDownloading);
        binding.btnUpdateApp.setText("Misintona");
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

    /** Télécharge l'APK depuis le serveur puis lance l'installation. */
    private void installUpdate() {
        if (appUpdate == null) return;
        updateDownloading = true;
        binding.btnUpdateApp.setEnabled(false);
        binding.btnUpdateApp.setText("Mahandry…");

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

    private void updateAvailableSection(boolean fromServer) {
        binding.chipAvailable.setText(availableList.size() + " misy hisintona");
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
    class AvailableAdapter extends RecyclerView.Adapter<AvailableAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_download_available, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            DownloadEntry entry = availableList.get(position);

            holder.itemIcon.setImageResource(iconFor(entry.getType()));
            holder.textTitle.setText(entry.getTitle());
            holder.textSubtitle.setText(entry.getSubtitle() == null
                    ? "" : entry.getSubtitle());

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
            TextView textTitle, textSubtitle;
            com.google.android.material.button.MaterialButton btnDownload;

            VH(View view) {
                super(view);
                itemIcon = view.findViewById(R.id.itemIcon);
                textTitle = view.findViewById(R.id.textTitle);
                textSubtitle = view.findViewById(R.id.textSubtitle);
                btnDownload = view.findViewById(R.id.btnDownload);
            }
        }
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
        return R.drawable.book_bookmark_svgrepo_com;
    }

    // =========================================================
    // 🚀 OUVERTURE DEPUIS N'IMPORTE QUELLE PARTIE DE L'APP
    // =========================================================
    public static void open(Context context) {
        context.startActivity(new Intent(context, DownloadCenterActivity.class));
    }
}
