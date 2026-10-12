package com.fa.baiboly.ui.fihirana;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.playback.PlaybackManager;
import com.fa.baiboly.data.playback.PlaybackScanner;
import com.fa.baiboly.data.playback.PlaybackStore;
import com.fa.baiboly.data.playback.SafUtils;
import com.fa.baiboly.databinding.ActivityAddPlaybackBinding;
import com.fa.baiboly.models.Playback;
import com.fa.baiboly.models.Song;
import com.fa.baiboly.ui.fandaharana.SongPickerSheet;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.List;

/**
 * Gestion des playbacks (feo) d'un chant, calqué sur
 * {@link PlaybackVersionsSheet} mais dans une activité :
 *
 * - La liste des versions enregistrées (avec taille) s'affiche d'office
 *   dès qu'un chant est choisi, sans appuyer sur "Manampy" ;
 * - Le bloc "Azo alaina amin'ny serveur" fait le check automatique
 *   (dispo + taille affichée, erreur "Tsy misy connexion") et permet de
 *   télécharger avec progression en direct dans la ligne ;
 * - Le bouton "Manampy" = ajout depuis le téléphone UNIQUEMENT : il ouvre
 *   directement le sélecteur de fichier audio.
 *
 * Le numéro du chant et le label de version sont détectés automatiquement
 * dans le nom du fichier choisi (ex. "ffpm 049 v2.mp3" -> 49, "2").
 */
public class AddPlayBackActivity extends AppCompatActivity
        implements PlaybackManager.DownloadListener {

    private static final int REQ_PICK_AUDIO = 4242;
    private static final int REQ_PICK_FOLDER = 4243;
    /** Dossier qui contient tout (sous-dossiers = catégories). */
    private static final int REQ_PICK_FOLDER_ALL = 4244;

    private ActivityAddPlaybackBinding binding;
    private PlaybackManager playbackManager;
    private PlaybackStore store;
    /** Lecteur d'aperçu : écouter une version enregistrée depuis la liste. */
    private PlaybackController previewController;
    /** Id du playback en cours d'aperçu (-1 : aucun). */
    private long previewingId = -1;

    private String category = "";
    private int number = -1;
    private Uri audioUri;
    private String audioName;
    private boolean busy = false;
    /** Détection des playbacks dans les dossier choisis (SAF). */
    private PlaybackScanner scanner;
    /** Nombre de scans en cours (> 0 : on affiche "Mamantatra…"). */
    private int scansRunning = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Mode nuit + couleurs de base choisies (avant inflation)
        ColorManager.applyGlobalTheme(this);
        super.onCreate(savedInstanceState);
        binding = ActivityAddPlaybackBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        playbackManager = new PlaybackManager(this);
        store = new PlaybackStore(this);
        scanner = new PlaybackScanner(this);

        // Lecteur d'aperçu : joue une version depuis la liste
        previewController = new PlaybackController(new PlaybackController.Listener() {
            @Override
            public void onPlayingChanged(boolean playing) {
                refreshPreviewUi();
            }

            @Override
            public void onProgress(int currentMs, int durationMs) {
                // Pas de barre de progression dans l'écran de gestion
            }

            @Override
            public void onCompleted() {
                stopPreview();
            }

            @Override
            public void onError(String message) {
                toast(message);
                stopPreview();
            }
        });
        binding.btnStopPreview.setOnClickListener(v -> stopPreview());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // Hira préremplie si ouverte depuis SongDetailActivity
        String prefCategory = getIntent().getStringExtra("category");
        int prefNumber = getIntent().getIntExtra("number", -1);
        if (prefCategory != null && prefNumber > 0) {
            category = prefCategory.toLowerCase();
            number = prefNumber;
        }

        binding.cardSong.setOnClickListener(v -> {
            if (!busy) openSongPicker();
        });
        binding.cardFolder.setOnClickListener(v -> {
            if (!busy) openFolderPicker(REQ_PICK_FOLDER);
        });
        // Dossier qui contient TOUT : les sous-dossiers sont les catégories
        binding.cardFolderAll.setOnClickListener(v -> {
            if (!busy) openFolderPicker(REQ_PICK_FOLDER_ALL);
        });
        // Le bouton Ajouter = fichier local UNIQUEMENT : choix direct
        binding.btnAdd.setOnClickListener(v -> {
            if (!busy) pickFileFromPhone();
        });

        updateUi();
    }

    // =========================
    // HIRA (sokajy + laharana)
    // =========================

    private void openSongPicker() {
        // Même pad numérique que le fihirana
        new SongPickerSheet(this::setSongFromPicked)
                .show(getSupportFragmentManager(), "playback_song");
    }

    private void setSongFromPicked(Song song) {
        if (song == null) return;

        if (song.getCategory() != null && !song.getCategory().isEmpty()
                && song.getNumber() > 0) {
            category = song.getCategory().toLowerCase();
            number = song.getNumber();
        } else if (song.getId() != null && song.getId().contains("_")) {
            // Fallback : "ffpm_49" -> "ffpm" + 49
            String[] parts = song.getId().split("_");
            category = parts[0].toLowerCase();
            try {
                number = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        updateUi();
    }

    // =========================
    // AVY AMIN'NY FINDAY (bouton Ajouter)
    // =========================

    private void pickFileFromPhone() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(
                Intent.createChooser(intent, "Safidio fichier feo"), REQ_PICK_AUDIO);
    }

    @Override
    protected void onDestroy() {
        if (previewController != null) {
            previewController.release();
        }
        if (scanner != null) {
            scanner.release();
        }
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        playbackManager.addListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        playbackManager.removeListener(this);
    }

    @Override
    public void onDownloadStateChanged(PlaybackManager.DownloadState state) {
        if (isFinishing() || isDestroyed()) return;
        if (!hasSong() || !state.category.equals(PlaybackStore.normalize(category))
                || state.number != number) {
            return;
        }
        refreshVersions();
    }

    @Override
    public void onServerStateChanged(PlaybackManager.ServerInfo info) {
        if (isFinishing() || isDestroyed()) return;
        if (!hasSong() || !info.category.equals(PlaybackStore.normalize(category))
                || info.number != number) {
            return;
        }
        refreshVersions();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_PICK_AUDIO && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            audioUri = data.getData();
            audioName = queryDisplayName(audioUri);

            // Détection du numéro dans le nom du fichier (ex. "ffpm 049 v2.mp3")
            if (!hasSong()) {
                int detected = PlaybackManager.extractNumber(audioName);
                if (detected > 0) {
                    number = detected;
                    toast(getString(R.string.playback_version_detected,
                            String.valueOf(detected)));
                }
            }

            if (!hasSong()) {
                toast("Tsy hita ny laharana tao amin'ny anaran'ny fichier");
                audioUri = null;
                audioName = null;
                updateUi();
                return;
            }

            // Label de version proposé depuis le nom (ex. "... v2.mp3" -> "2")
            String detectedVersion = PlaybackManager.extractVersionLabel(audioName);
            askVersionLabel(detectedVersion);

        } else if ((requestCode == REQ_PICK_FOLDER
                || requestCode == REQ_PICK_FOLDER_ALL)
                && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            // Dossier choisi : permission persistante + détection des chants
            Uri treeUri = data.getData();
            SafUtils.takePersistablePermission(getContentResolver(), treeUri);

            boolean all = requestCode == REQ_PICK_FOLDER_ALL;
            if (all) {
                store.setRootFolderUri(treeUri);
            } else {
                store.setFolderUri(category.isEmpty() ? null : category, treeUri);
            }
            // Détection (sous-dossiers compris) : seul le résultat du chant
            // sélectionné sera affiché dans cet écran.
            startScan(treeUri, all ? null : category, all);
        }
    }

    // =========================
    // DETECTION DES FICHIERS DU DOSSIER
    // =========================

    /**
     * Explore le dossier (et ses sous-dossiers) en arrière-plan :
     * chaque fichier reconnu devient un playback du chant correspondant,
     * sans copie (on garde juste l'URI du fichier).
     *
     * Les fichiers non reconnus sont mis de côté dans la table
     * {@code detected_files}.
     * // TODO : fenêtre dédiée pour réassigner ces fichiers à un chant.
     */
    private void startScan(Uri treeUri, String fixedCategory, boolean rootMode) {
        scansRunning++;
        updateUi(); // affiche "Mamantatra ny feo…"
        scanner.scan(treeUri, fixedCategory, rootMode, result -> {
            if (isFinishing() || isDestroyed()) return;
            scansRunning = Math.max(0, scansRunning - 1);
            updateUi(); // la liste du chant sélectionné se rafraîchit
        });
    }

    /** Demande le label de la version (prérempli), puis enregistre. */
    private void askVersionLabel(String suggested) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.playback_version_hint);
        input.setText(suggested != null ? suggested : "");
        input.setSelection(input.getText().length());

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.playback_version_label_title)
                .setView(input)
                .setPositiveButton("Tehirizo", (d, w) -> {
                    saveLocalFile(input.getText().toString());
                })
                .setNegativeButton("Hanafoana", (d, w) -> {
                    audioUri = null;
                    audioName = null;
                })
                .show();
    }

    private void saveLocalFile(String label) {
        setBusy(true);
        playbackManager.addLocalFile(audioUri, category, number, label, result -> {
            setBusy(false);
            audioUri = null;
            audioName = null;
            if (result.success) {
                toast("Voatahiry ny playback (" + displayTitle() + ")");
                updateUi(); // la nouvelle version apparaît dans la liste
            } else {
                toast(result.error);
            }
        });
    }

    // =========================
    // DOSSIER PAR CATÉGORIE (SAF)
    // =========================

    private void openFolderPicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    // =========================
    // SUPPRESSION D'UNE VERSION
    // =========================

    private void confirmDelete(Playback playback) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.playback_delete)
                .setMessage(R.string.playback_delete_confirm)
                .setPositiveButton("Hamafa", (d, w) -> {
                    playbackManager.delete(playback, ok -> updateUi());
                })
                .setNegativeButton("Hanafoana", null)
                .show();
    }

    // =========================
    // UI
    // =========================

    private boolean hasSong() {
        return !category.isEmpty() && number > 0;
    }

    private String displayTitle() {
        return PlaybackManager.displayTitle(category, number);
    }

    /** Recharge la liste des versions + l'état des cartes. */
    private void updateUi() {
        binding.tvSongValue.setText(hasSong()
                ? displayTitle()
                : getString(R.string.playback_select_song));

        // Dossier choisi pour cette catégorie (SAF)
        Uri folder = store.getFolderUri(category);
        String folderName = prettyFolderName(folder);
        if (folderName != null) {
            binding.tvFolderValue.setText(folderName);
        } else {
            binding.tvFolderValue.setText(store.getRootFolderUri() != null
                    ? getString(R.string.playback_folder_default_root)
                    : getString(R.string.playback_folder_default));
        }
        binding.tvFolderHint.setText(scansRunning > 0
                ? getString(R.string.playback_scanning)
                : (hasSong()
                ? getString(R.string.playback_folder_hint_category,
                        displayTitle().split(" ")[0])
                : getString(R.string.playback_folder_hint_generic)));

        // Dossier qui contient tout (sous-dossiers = catégories)
        String rootName = prettyFolderName(store.getRootFolderUri());
        binding.tvFolderAllValue.setText(rootName != null
                ? rootName
                : getString(R.string.playback_folder_all_none));
        binding.tvFolderAllHint.setText(scansRunning > 0
                ? getString(R.string.playback_scanning)
                : getString(rootName != null
                ? R.string.playback_folder_all_selected
                : R.string.playback_folder_all_hint));

        refreshVersions();

        // Check serveur auto dès qu'un chant est connu (comme dans le sheet)
        if (hasSong()) {
            playbackManager.checkServer(category, number);
        }
    }

    /**
     * Liste des versions + bloc serveur, même rendu que dans le sheet :
     * item_playback_download pour chaque ligne.
     */
    private void refreshVersions() {
        LinearLayout list = binding.versionsList;
        list.removeAllViews();
        LinearLayout available = binding.availableContainer;
        available.removeAllViews();

        List<Playback> versions = hasSong()
                ? playbackManager.getAll(category, number)
                : new java.util.ArrayList<>();

        binding.versionsEmpty.setVisibility(
                versions.isEmpty() ? View.VISIBLE : View.GONE);
        binding.tvVersionsHint.setVisibility(
                versions.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvInstalledLabel.setVisibility(
                versions.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvAvailableLabel.setVisibility(
                hasSong() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = getLayoutInflater();

        for (final Playback p : versions) {
            View row = inflater.inflate(R.layout.item_playback_download, list, false);
            bindInstalledRow(row, p, versions.indexOf(p));
            list.addView(row);
        }

        if (hasSong()) {
            View serverRow = inflater.inflate(
                    R.layout.item_playback_download, available, false);
            bindServerRow(serverRow, versions);
            available.addView(serverRow);
        }

        refreshPreviewUi();
    }

    /** Ligne "Serveur" : check, download, progression, erreur réseau. */
    private void bindServerRow(View row, List<Playback> versions) {
        ImageView icon = row.findViewById(R.id.ivIcon);
        TextView label = row.findViewById(R.id.tvLabel);
        TextView sub = row.findViewById(R.id.tvSub);
        ProgressBar progress = row.findViewById(R.id.pbDownload);
        ImageButton action = row.findViewById(R.id.btnAction);
        ImageButton delete = row.findViewById(R.id.btnDelete);

        PlaybackManager.ServerInfo info =
                playbackManager.getServerInfo(category, number);
        PlaybackManager.DownloadState dl =
                playbackManager.getDownloadState(category, number);

        boolean hasServerCopy = hasServerCopy(versions);

        label.setText(getString(R.string.playback_add_from_server));
        delete.setVisibility(View.GONE);

        if (dl != null && dl.isRunning()) {
            // Téléchargement en cours : progression dans la ligne
            icon.setImageResource(R.drawable.ic_download);
            sub.setText(getString(R.string.playback_downloading));
            progress.setVisibility(View.VISIBLE);
            int pct = dl.percent();
            if (pct >= 0) {
                progress.setIndeterminate(false);
                progress.setProgress(pct);
                sub.setText(pct + " %");
            } else {
                progress.setIndeterminate(true);
            }
            action.setImageResource(R.drawable.ic_close);
            action.setOnClickListener(v ->
                    playbackManager.cancelDownload(category, number));
        } else if (info == null
                || info.status == PlaybackManager.ServerInfo.CHECKING) {
            // Check en cours (ou pas encore lancé)
            icon.setImageResource(R.drawable.ic_download);
            sub.setText(getString(R.string.playback_server_checking));
            progress.setVisibility(View.VISIBLE);
            progress.setIndeterminate(true);
            action.setImageResource(R.drawable.ic_refresh);
            action.setOnClickListener(v -> playbackManager.checkServer(category, number));
        } else if (info.status == PlaybackManager.ServerInfo.NO_INTERNET) {
            icon.setImageResource(R.drawable.ic_download);
            sub.setText(getString(R.string.playback_no_network));
            progress.setVisibility(View.GONE);
            action.setImageResource(R.drawable.ic_refresh);
            action.setOnClickListener(v -> playbackManager.checkServer(category, number));
        } else if (info.status == PlaybackManager.ServerInfo.UNAVAILABLE) {
            icon.setImageResource(R.drawable.ic_download);
            sub.setText(getString(R.string.playback_server_unavailable));
            progress.setVisibility(View.GONE);
            action.setImageResource(R.drawable.ic_refresh);
            action.setOnClickListener(v -> playbackManager.checkServer(category, number));
        } else {
            // Disponible sur le serveur (avec taille)
            icon.setImageResource(R.drawable.ic_download);
            String sizeText = info.sizeBytes > 0
                    ? PlaybackManager.formatSize(info.sizeBytes) : "";
            sub.setText(hasServerCopy
                    ? getString(R.string.playback_download_done,
                    PlaybackManager.formatSize(info.sizeBytes))
                    : (sizeText.isEmpty() ? getString(R.string.playback_download)
                    : getString(R.string.playback_download) + " · " + sizeText));
            progress.setVisibility(View.GONE);
            action.setImageResource(R.drawable.ic_download);
            action.setOnClickListener(v -> startServerDownload());
        }

        row.setOnClickListener(v -> {
            if (dl != null && dl.isRunning()) return;
            if (info != null && info.isAvailable()
                    && !hasServerCopy) {
                startServerDownload();
            }
        });
    }

    /**
     * Ligne d'une version enregistrée dans l'activité : taille + lecture
     * d'aperçu + poubelle, comme bindInstalledRow du sheet.
     */
    private void bindInstalledRow(View row, Playback playback, int index) {
        ImageView icon = row.findViewById(R.id.ivIcon);
        TextView label = row.findViewById(R.id.tvLabel);
        TextView sub = row.findViewById(R.id.tvSub);
        ProgressBar progress = row.findViewById(R.id.pbDownload);
        ImageButton action = row.findViewById(R.id.btnAction);
        ImageButton delete = row.findViewById(R.id.btnDelete);

        progress.setVisibility(View.GONE);
        icon.setImageResource(R.drawable.ic_audio);

        String labelText = playback.displayLabel(index);
        if (Playback.SOURCE_SERVER.equals(playback.getSource())) {
            labelText += "  •  " + getString(R.string.playback_add_from_server);
        }
        label.setText(labelText);

        long size = PlaybackManager.sizeOf(playback, this);
        StringBuilder subText = new StringBuilder(displayFileName(playback));
        if (size > 0) {
            subText.append(" · ").append(PlaybackManager.formatSize(size));
        }
        boolean fileOk = size >= 0
                || (playback.getFilePath() != null
                && playback.getFilePath().startsWith("content:"));
        if (!fileOk) {
            subText.append("  (fichier tsy hita)");
        }
        sub.setText(subText);

        action.setImageResource(fileOk ? R.drawable.ic_play : R.drawable.ic_download);
        if (fileOk) {
            action.setOnClickListener(v -> {
                if (previewingId == playback.getId()) {
                    stopPreview();
                } else {
                    startPreview(playback, index);
                }
            });
        } else {
            action.setOnClickListener(null);
            action.setClickable(false);
        }

        delete.setVisibility(View.VISIBLE);
        delete.setOnClickListener(v -> {
            if (previewingId == playback.getId()) stopPreview();
            confirmDelete(playback);
        });

        // Tags : id du playback + jouable, pour l'état lecture dans la liste
        row.setTag(R.id.btnPlayVersion, playback.getId());
        row.setTag(R.id.btnDelete, fileOk);
        row.setOnClickListener(v -> {
            if (fileOk) {
                if (previewingId == playback.getId()) {
                    stopPreview();
                } else {
                    startPreview(playback, index);
                }
            }
        });
    }

    /** Y a-t-il déjà une version venant du serveur dans la liste ? */
    private static boolean hasServerCopy(List<Playback> versions) {
        for (Playback p : versions) {
            if (Playback.SOURCE_SERVER.equals(p.getSource())) return true;
        }
        return false;
    }

    private void startServerDownload() {
        setBusy(true);
        playbackManager.downloadFromServer(category, number, null, result -> {
            setBusy(false);
            if (result.success) {
                toast("Voatahiry ny playback (" + displayTitle() + ")");
            } else {
                toast(result.error);
            }
            refreshVersions();
        });
    }

    // =========================
    // APERÇU (lecture d'une version depuis la liste)
    // =========================

    /** Lance l'écoute d'une version et affiche la bannière "Mitendry". */
    private void startPreview(Playback playback, int index) {
        previewingId = playback.getId();
        binding.tvNowPlaying.setText(getString(
                R.string.playback_now_playing,
                playback.displayLabel(index)));
        binding.cardNowPlaying.setVisibility(View.VISIBLE);
        previewController.play(this, playback.getFilePath());
    }

    /** Stoppe l'aperçu et masque la bannière. */
    private void stopPreview() {
        previewingId = -1;
        previewController.stop();
        binding.cardNowPlaying.setVisibility(View.GONE);
        refreshPreviewUi();
    }

    /** Reflète l'état lecture/pause sur les boutons de la liste. */
    private void refreshPreviewUi() {
        LinearLayout list = binding.versionsList;
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            ImageButton play = row.findViewById(R.id.btnAction);
            if (play == null) continue;
            // Le tag porte l'id du playback de la ligne (null = ligne serveur)
            Object tag = row.getTag(R.id.btnPlayVersion);
            if (!(tag instanceof Long)) continue;
            // Ligne non jouable : on ne touche pas à son icône
            if (!Boolean.TRUE.equals(row.getTag(R.id.btnDelete))) continue;
            long rowId = (Long) tag;
            boolean isThis = previewingId >= 0 && rowId == previewingId;
            play.setImageResource(isThis ? R.drawable.ic_pause : R.drawable.ic_play);
        }
    }

    /** Nom de fichier affichable (chemin interne ou URI SAF). */
    private String displayFileName(Playback p) {
        String path = p.getFilePath();
        if (path == null) return "?";
        if (path.startsWith("content:")) {
            try {
                Uri uri = Uri.parse(path);
                String name = queryDisplayName(uri);
                if (name != null) return name;
            } catch (Exception ignored) {
            }
            return path;
        }
        return new File(path).getName();
    }

    /** Nom lisible du dossier choisi (dernier segment du tree Uri). */
    private String prettyFolderName(Uri treeUri) {
        if (treeUri == null) return null;
        try {
            return SafUtils.folderDisplayName(getContentResolver(), treeUri);
        } catch (Exception e) {
            return treeUri.getLastPathSegment();
        }
    }

    /** Nom du fichier choisi (displayName du ContentResolver). */
    private String queryDisplayName(Uri uri) {
        Cursor c = getContentResolver().query(uri, null, null, null, null);
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

    private void setBusy(boolean busy) {
        this.busy = busy;
        binding.btnAdd.setEnabled(!busy);
        binding.btnAdd.setAlpha(busy ? 0.5f : 1f);
        binding.cardSong.setAlpha(busy ? 0.5f : 1f);
        binding.cardFolder.setAlpha(busy ? 0.5f : 1f);
        binding.cardFolderAll.setAlpha(busy ? 0.5f : 1f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
