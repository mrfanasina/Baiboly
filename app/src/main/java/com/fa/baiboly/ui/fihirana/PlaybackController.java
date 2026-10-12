package com.fa.baiboly.ui.fihirana;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;

/**
 * Petit lecteur audio basé sur MediaPlayer, pour les playbacks (feo).
 * Préparation asynchrone, notifications d'état (lecture/pause),
 * de progression (toutes les 250 ms) et d'erreur.
 */
public class PlaybackController {

    public interface Listener {
        /** État lecture/pause changé. */
        void onPlayingChanged(boolean playing);

        /** Progression (ms), appelée régulièrement pendant la lecture. */
        void onProgress(int currentMs, int durationMs);

        /** Lecture arrivée à la fin. */
        void onCompleted();

        /** Erreur de lecture. */
        void onError(String message);
    }

    private static final long PROGRESS_INTERVAL_MS = 250;

    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MediaPlayer player;
    private String currentPath;

    public PlaybackController(Listener listener) {
        this.listener = listener;
    }

    /** Un fichier est chargé (lecture en cours ou en pause). */
    public boolean hasCurrent() {
        return currentPath != null;
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    /** Charge et lance la lecture d'un fichier audio (préparation async).
     *  Accepte un chemin absolu ou une URI "content://" (dossier SAF). */
    public void play(Context context, String path) {
        stopInternal();
        try {
            currentPath = path;
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            if (path != null && path.startsWith("content:")) {
                player.setDataSource(context, Uri.parse(path));
            } else {
                player.setDataSource(path);
            }
            player.setOnPreparedListener(mp -> {
                mp.start();
                listener.onProgress(0, Math.max(mp.getDuration(), 1));
                listener.onPlayingChanged(true);
                startProgressLoop();
            });
            player.setOnCompletionListener(mp -> {
                stopProgressLoop();
                listener.onCompleted();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                listener.onError("Nisy olana tamin'ny feo (" + what + ")");
                return true;
            });
            player.prepareAsync();
        } catch (IOException e) {
            currentPath = null;
            listener.onError("Tsy azo vakiana ny fichier : " + e.getMessage());
        }
    }

    /**
     * Pause / reprise. Si la fin est atteinte, la reprise repart
     * du début.
     */
    public void togglePlayPause() {
        if (player == null) return;
        try {
            if (player.isPlaying()) {
                player.pause();
                stopProgressLoop();
                listener.onPlayingChanged(false);
            } else {
                if (player.getDuration() > 0
                        && player.getCurrentPosition() >= player.getDuration()) {
                    player.seekTo(0);
                }
                player.start();
                listener.onPlayingChanged(true);
                startProgressLoop();
            }
        } catch (IllegalStateException ignored) {
            // Lecteur dans un état incohérent : on ignore
        }
    }

    public void seekTo(int positionMs) {
        if (player != null) {
            player.seekTo(positionMs);
        }
    }

    /** Arrête la lecture et libère le lecteur. */
    public void stop() {
        stopInternal();
        currentPath = null;
    }

    /** Libère toutes les ressources (appeler dans onDestroy). */
    public void release() {
        stopInternal();
        currentPath = null;
    }

    private void stopInternal() {
        stopProgressLoop();
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
            }
            player.release();
            player = null;
        }
    }

    private void startProgressLoop() {
        handler.removeCallbacks(progressRunnable);
        handler.postDelayed(progressRunnable, PROGRESS_INTERVAL_MS);
    }

    private void stopProgressLoop() {
        handler.removeCallbacks(progressRunnable);
    }

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (player == null) return;
            try {
                if (player.isPlaying()) {
                    listener.onProgress(player.getCurrentPosition(),
                            Math.max(player.getDuration(), 1));
                    handler.postDelayed(this, PROGRESS_INTERVAL_MS);
                }
            } catch (IllegalStateException ignored) {
            }
        }
    };
}
