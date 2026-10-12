package com.fa.baiboly.ui.fihirana;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fa.baiboly.R;
import com.fa.baiboly.data.playback.PlaybackManager;
import com.fa.baiboly.data.playback.PlaybackStore;
import com.fa.baiboly.models.Playback;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;
import java.util.List;

/**
 * BottomSheet "Feo" : deux blocs clairs pour le chant courant.
 *
 * - "Efa voatahiry" : versions installées, avec taille, tap = lecture
 *   directe, poubelle = suppression IMMÉDIATE de la version ;
 * - "Azo alaina amin'ny serveur" : ce que le serveur propose (check
 *   automatique après construction de l'UI, taille affichée, bouton
 *   download -> progression en direct dans la ligne, sans popup).
 *
 * Le check rejoue automatiquement dès qu'Internet revient, et le bouton
 * "havaozina" relance le check à la demande.
 */
public class PlaybackVersionsSheet extends BottomSheetDialogFragment
        implements PlaybackManager.DownloadListener {

    /** Notifié quand une version est supprimée ou ajoutée. */
    public interface OnVersionsChanged {
        void onVersionsChanged();
    }

    /** Notifié quand l'utilisateur veut jouer une version précise. */
    public interface OnPlayRequested {
        void onPlayRequested(Playback playback);
    }

    private static final String ARG_CATEGORY = "category";
    private static final String ARG_NUMBER = "number";

    private PlaybackManager manager;
    private LinearLayout installedContainer;
    private LinearLayout availableContainer;
    private TextView tvInstalledLabel;
    private TextView tvAvailableLabel;

    private String category;
    private int number;

    private OnVersionsChanged changedListener;
    private OnPlayRequested playListener;

    public static PlaybackVersionsSheet newInstance(String category, int number) {
        PlaybackVersionsSheet sheet = new PlaybackVersionsSheet();
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY, category);
        args.putInt(ARG_NUMBER, number);
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnVersionsChanged(OnVersionsChanged l) {
        this.changedListener = l;
    }

    public void setOnPlayRequested(OnPlayRequested l) {
        this.playListener = l;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_playback_versions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        category = args != null ? args.getString(ARG_CATEGORY, "") : "";
        number = args != null ? args.getInt(ARG_NUMBER, -1) : -1;

        manager = new PlaybackManager(requireContext().getApplicationContext());

        installedContainer = view.findViewById(R.id.installedContainer);
        availableContainer = view.findViewById(R.id.availableContainer);
        tvInstalledLabel = view.findViewById(R.id.tvInstalledLabel);
        tvAvailableLabel = view.findViewById(R.id.tvAvailableLabel);
        TextView tvTitle = view.findViewById(R.id.tvSheetTitle);
        tvTitle.setText(PlaybackManager.displayTitle(category, number));

        view.findViewById(R.id.btnClose).setOnClickListener(v -> dismiss());
        // Actualiser : rejoue le check serveur à la demande
        view.findViewById(R.id.btnRefresh).setOnClickListener(v -> {
            manager.checkServer(category, number);
            rebuildRows();
        });

        rebuildRows();

        // Appel check APRÈS construction de l'UI : le statut (dispo /
        // pas internet) s'affiche en direct dès que le serveur répond.
        manager.checkServer(category, number);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (manager != null) {
            manager.addListener(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (manager != null) {
            manager.removeListener(this);
        }
    }

    // =========================
    // CONSTRUCTION DES BLOCS
    // =========================

    private void rebuildRows() {
        if (installedContainer == null || getContext() == null) return;
        installedContainer.removeAllViews();
        availableContainer.removeAllViews();

        List<Playback> versions = manager.getAll(category, number);
        LayoutInflater inflater = getLayoutInflater();

        // ---- Bloc INSTALLÉS ----
        tvInstalledLabel.setVisibility(
                versions.isEmpty() ? View.GONE : View.VISIBLE);
        for (int i = 0; i < versions.size(); i++) {
            Playback p = versions.get(i);
            View row = inflater.inflate(
                    R.layout.item_playback_download, installedContainer, false);
            bindInstalledRow(row, p, i);
            installedContainer.addView(row);
        }

        // ---- Bloc DISPONIBLE (serveur) ----
        View serverRow = inflater.inflate(
                R.layout.item_playback_download, availableContainer, false);
        bindServerRow(serverRow, versions);
        availableContainer.addView(serverRow);
        tvAvailableLabel.setVisibility(View.VISIBLE);
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
                manager.getServerInfo(category, number);
        PlaybackManager.DownloadState dl =
                manager.getDownloadState(category, number);

        boolean hasServerCopy = false;
        for (Playback p : versions) {
            if (Playback.SOURCE_SERVER.equals(p.getSource())) {
                hasServerCopy = true;
                break;
            }
        }

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
                    manager.cancelDownload(category, number));
        } else if (info == null || info.status == PlaybackManager.ServerInfo.CHECKING) {
            // Check en cours (ou pas encore lancé)
            icon.setImageResource(R.drawable.ic_download);
            sub.setText(getString(R.string.playback_server_checking));
            progress.setVisibility(View.VISIBLE);
            progress.setIndeterminate(true);
            action.setImageResource(R.drawable.ic_refresh);
            action.setOnClickListener(v -> manager.checkServer(category, number));
        } else if (info.status == PlaybackManager.ServerInfo.NO_INTERNET) {
            icon.setImageResource(R.drawable.ic_download);
            sub.setText(getString(R.string.playback_no_network));
            progress.setVisibility(View.GONE);
            action.setImageResource(R.drawable.ic_refresh);
            action.setOnClickListener(v -> manager.checkServer(category, number));
        } else if (info.status == PlaybackManager.ServerInfo.UNAVAILABLE) {
            icon.setImageResource(R.drawable.ic_download);
            sub.setText(getString(R.string.playback_server_unavailable));
            progress.setVisibility(View.GONE);
            action.setImageResource(R.drawable.ic_refresh);
            action.setOnClickListener(v -> manager.checkServer(category, number));
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
            Playback server = firstServerVersion();
            if (server != null && playListener != null) {
                playListener.onPlayRequested(server);
            } else if (info != null && info.isAvailable()) {
                startServerDownload();
            }
        });
    }

    /** Ligne d'une version installée : taille + play + suppression. */
    private void bindInstalledRow(View row, Playback playback, int index) {
        ImageView icon = row.findViewById(R.id.ivIcon);
        TextView label = row.findViewById(R.id.tvLabel);
        TextView sub = row.findViewById(R.id.tvSub);
        ProgressBar progress = row.findViewById(R.id.pbDownload);
        ImageButton action = row.findViewById(R.id.btnAction);
        ImageButton delete = row.findViewById(R.id.btnDelete);

        progress.setVisibility(View.GONE);
        icon.setImageResource(R.drawable.ic_audio);

        label.setText(playback.displayLabel(index));

        long size = PlaybackManager.sizeOf(playback, requireContext());
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
                if (playListener != null) {
                    playListener.onPlayRequested(playback);
                }
            });
        } else {
            action.setOnClickListener(null);
            action.setClickable(false);
        }

        // Poubelle : suppression immédiate de CETTE version, les autres
        // restent en place.
        delete.setVisibility(View.VISIBLE);
        delete.setOnClickListener(v -> {
            manager.delete(playback, ok -> {
                if (ok) {
                    rebuildRows();
                    if (changedListener != null) changedListener.onVersionsChanged();
                } else {
                    Toast.makeText(requireContext(),
                            R.string.playback_download_failed,
                            Toast.LENGTH_SHORT).show();
                }
            });
        });

        row.setOnClickListener(v -> {
            if (fileOk && playListener != null) {
                playListener.onPlayRequested(playback);
            }
        });
    }

    private void startServerDownload() {
        manager.downloadFromServer(category, number, null, result -> {
            if (result.success && playListener != null) {
                playListener.onPlayRequested(result.playback);
                if (changedListener != null) changedListener.onVersionsChanged();
            } else if (!result.success && getContext() != null) {
                Toast.makeText(requireContext(), result.error,
                        Toast.LENGTH_SHORT).show();
            }
            rebuildRows();
        });
    }

    @Nullable
    private Playback firstServerVersion() {
        List<Playback> versions = manager.getAll(category, number);
        for (Playback p : versions) {
            if (Playback.SOURCE_SERVER.equals(p.getSource())) return p;
        }
        return null;
    }

    /** Nom de fichier affichable (chemin interne ou URI SAF). */
    private String displayFileName(Playback p) {
        String path = p.getFilePath();
        if (path == null) return "?";
        if (path.startsWith("content:")) {
            String last = Uri.parse(path).getLastPathSegment();
            return last != null ? last : path;
        }
        return new File(path).getName();
    }

    // =========================
    // LISTENERS
    // =========================

    @Override
    public void onDownloadStateChanged(PlaybackManager.DownloadState state) {
        if (!isAdded()) return;
        if (!state.category.equals(PlaybackStore.normalize(category))
                || state.number != number) {
            return;
        }
        rebuildRows();
        if (state.status == PlaybackManager.DownloadState.DONE
                && changedListener != null) {
            changedListener.onVersionsChanged();
        }
    }

    @Override
    public void onServerStateChanged(PlaybackManager.ServerInfo info) {
        if (!isAdded()) return;
        if (!info.category.equals(PlaybackStore.normalize(category))
                || info.number != number) {
            return;
        }
        rebuildRows();
    }
}
