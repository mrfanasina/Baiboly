package com.fa.baiboly.data.playback;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.fa.baiboly.R;
import com.fa.baiboly.ui.fihirana.SongDetailActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lecteur audio (feo) de toute l'app, tourne en arrière-plan :
 *
 * <ul>
 *   <li>le son CONTINUE quand l'utilisateur quitte
 *       {@link SongDetailActivity} ;</li>
 *   <li>une notification média (play/pause + stop, tappable pour
 *       rouvrir le chant) reste dans la barre de statut.</li>
 * </ul>
 *
 * Les écrans (SongDetailActivity, MainActivity) se bindent dessus et
 * s'abonnent via {@link Listener} pour rafraîchir mini-lecteur et
 * boutons. Toutes les commandes (lancer, pause, seek, stop) passent
 * par ce service : un seul MediaPlayer pour l'app.
 */
public class PlaybackService extends Service {

    public static final String ACTION_TOGGLE =
            "com.fa.baiboly.playback.TOGGLE";
    public static final String ACTION_STOP =
            "com.fa.baiboly.playback.STOP";

    private static final String CHANNEL_ID = "feo_playback";
    private static final int NOTIFICATION_ID = 42;
    private static final int REQ_TOGGLE = 10;
    private static final int REQ_STOP = 11;
    private static final long PROGRESS_INTERVAL_MS = 250;

    /** Événements envoyés aux écrans abonnés (thread UI). */
    public interface Listener {
        /** Lecture / pause changée. */
        void onPlayingChanged(boolean playing);

        /** Progression de la lecture (ms), régulièrement. */
        void onProgress(int currentMs, int durationMs);

        /** La lecture est arrivée à la fin. */
        void onCompleted();

        /** Erreur de lecture (message affichable). */
        void onError(String message);

        /** La lecture a été stoppée (X ou fichier illisible). */
        void onStopped();
    }

    /** Image instantanée de l'état (pour restaurer l'UI d'un écran). */
    public static class ServiceState {
        /** Un fichier est chargé (lecture, pause ou prêt). */
        public boolean hasCurrent;
        public boolean playing;
        /** Titre affiché (ex. "FFPM 49"). */
        public String title;
        /** Id du chant joué (ex. "ffpm_49"). */
        public String songId;
        public int currentMs;
        public int durationMs;
    }

    public class LocalBinder extends Binder {
        public PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    private final IBinder binder = new LocalBinder();
    private final ServiceState state = new ServiceState();
    private final List<Listener> listeners = new ArrayList<>();

    private MediaPlayer player;
    private MediaSessionCompat session;
    private Handler main;
    private String currentPath;
    private boolean foregroundShown;

    // =========================================================
    // CYCLE DE VIE
    // =========================================================

    @Override
    public void onCreate() {
        super.onCreate();
        main = new Handler(Looper.getMainLooper());
        createChannel();
        ensureSession();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /** Reçoit les taps de la notification (toggle / stop). */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_TOGGLE.equals(action)) {
                togglePlayPause();
            } else if (ACTION_STOP.equals(action)) {
                stopPlaying();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        if (session != null) {
            session.setActive(false);
            session.release();
            session = null;
        }
        super.onDestroy();
    }

    // =========================================================
    // COMMANDES DES ÉCRANS
    // =========================================================

    /**
     * Charge et joue un fichier (chemin absolu ou URI "content://").
     * Si c'est déjà le fichier courant : simple pause / reprise.
     */
    public void play(String path, String title, String songId) {
        ensureSession();
        state.hasCurrent = true;
        state.title = title;
        state.songId = songId;
        updateMetadata();

        if (player != null && path != null && path.equals(currentPath)) {
            resume();
            return;
        }

        releasePlayer();
        currentPath = path;
        showForeground(); // notification visible dès le lancement

        try {
            player = new MediaPlayer();
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            if (path != null && path.startsWith("content:")) {
                player.setDataSource(this, android.net.Uri.parse(path));
            } else {
                player.setDataSource(path);
            }
            player.setOnPreparedListener(mp -> {
                mp.start();
                state.playing = true;
                state.currentMs = 0;
                state.durationMs = Math.max(mp.getDuration(), 1);
                publishPlayingChanged();
                pushSessionState();
                showForeground();
                startProgressLoop();
            });
            player.setOnCompletionListener(mp -> {
                state.playing = false;
                publishPlayingChanged();
                publishCompleted();
                stopEverything(); // son fini : notification retirée
            });
            player.setOnErrorListener((mp, what, extra) -> {
                state.playing = false;
                publishPlayingChanged();
                publishError("Nisy olana tamin'ny feo (" + what + ")");
                stopEverything();
                return true;
            });
            player.prepareAsync();
        } catch (IOException | RuntimeException e) {
            state.hasCurrent = false;
            publishError(
                    "Nisy olana tamin'ny fichier : " + e.getMessage());
            stopEverything();
        }
    }

    /** Pause / reprise (repart au début si la fin était atteinte). */
    public void togglePlayPause() {
        if (player == null) return;
        if (state.playing) {
            pause();
        } else {
            resume();
        }
    }

    public void seekTo(int positionMs) {
        if (player != null) {
            player.seekTo(Math.max(0, positionMs));
            pushSessionState();
        }
    }

    /** Stop complet : son coupé, notification retirée, listeners notifiés. */
    public void stopPlaying() {
        currentPath = null;
        stopEverything();
    }

    /** État instantané (copie, utilisable direct sur le thread UI). */
    public ServiceState peekState() {
        ServiceState s = new ServiceState();
        synchronized (state) {
            s.hasCurrent = state.hasCurrent;
            s.playing = state.playing;
            s.title = state.title;
            s.songId = state.songId;
            s.durationMs = state.durationMs;
        }
        try {
            if (player != null) {
                s.currentMs = player.getCurrentPosition();
                s.durationMs = Math.max(player.getDuration(), 1);
            }
        } catch (IllegalStateException ignored) {
        }
        return s;
    }

    public boolean isPlaying() {
        return state.playing;
    }

    // =========================================================
    // ABONNEMENTS DES ÉCRANS
    // =========================================================

    public synchronized void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
            l.onPlayingChanged(state.playing);
        }
    }

    public synchronized void removeListener(Listener l) {
        listeners.remove(l);
    }

    // =========================================================
    // PAUSE / REPRISE / STOP (interne)
    // =========================================================

    private void pause() {
        if (player == null || !state.playing) return;
        player.pause();
        state.playing = false;
        publishPlayingChanged();
        pushSessionState();
        stopProgressLoop();
        // En pause la notification redevient escamotable (icône play)
        refreshForeground();
    }

    private void resume() {
        if (player == null) return;
        try {
            if (state.durationMs > 0
                    && player.getCurrentPosition() >= state.durationMs - 250) {
                player.seekTo(0);
            }
            player.start();
            state.playing = true;
            publishPlayingChanged();
            pushSessionState();
            showForeground();
            startProgressLoop();
        } catch (IllegalStateException ignored) {
        }
    }

    private void stopEverything() {
        releasePlayer();
        boolean wasCurrent = state.hasCurrent;
        state.hasCurrent = false;
        state.playing = false;
        state.currentMs = 0;
        state.durationMs = 0;
        currentPath = null;
        pushSessionState();
        if (wasCurrent) {
            publishStopped();
        }
        removeNotificationAndForeground();
        stopSelf();
    }

    private void releasePlayer() {
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

    // =========================================================
    // MEDIA SESSION (boutons casque / montre)
    // =========================================================

    private void ensureSession() {
        if (session != null) return;
        session = new MediaSessionCompat(this, "BaibolyFeo");
        session.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                resume();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onStop() {
                stopPlaying();
            }
        });
        session.setActive(true);
    }

    private void updateMetadata() {
        if (session == null) return;
        session.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                        state.title != null ? state.title : "Feo")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,
                        getString(R.string.app_name))
                .build());
    }

    private void pushSessionState() {
        if (session == null) return;
        PlaybackStateCompat.Builder sb =
                new PlaybackStateCompat.Builder()
                        .setActions(
                                PlaybackStateCompat.ACTION_PLAY
                                        | PlaybackStateCompat.ACTION_PAUSE
                                        | PlaybackStateCompat.ACTION_STOP
                                        | PlaybackStateCompat.ACTION_SEEK_TO)
                        .setState(state.playing
                                        ? PlaybackStateCompat.STATE_PLAYING
                                        : PlaybackStateCompat.STATE_PAUSED,
                                state.currentMs, 1.0f);
        session.setPlaybackState(sb.build());
    }

    // =========================================================
    // NOTIFICATION (barre de statut)
    // =========================================================

    private void showForeground() {
        Notification notif = buildNotification(state.playing);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notif);
        }
        foregroundShown = true;
    }

    /** Rafraîchit la notification (icône / texte) sans recréer le service. */
    private void refreshForeground() {
        if (!foregroundShown) return;
        NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(state.playing));
        }
    }

    private Notification buildNotification(boolean playing) {
        Intent openApp = new Intent(this, SongDetailActivity.class);
        if (state.songId != null) {
            openApp.putExtra("songId", state.songId);
            openApp.putExtra("title", state.title);
        }
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent toggleIntent = PendingIntent.getService(
                this, REQ_TOGGLE,
                new Intent(this, PlaybackService.class)
                        .setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopIntent = PendingIntent.getService(
                this, REQ_STOP,
                new Intent(this, PlaybackService.class)
                        .setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE);

        String title = state.title != null ? state.title : "Feo";

        // Style média : la notification est pilotée par la MediaSession
        // (aperçu dans les notifications, casque, montre). Les actions
        // ajoutées restent utiles aux appareils sans rendu du style média.
        androidx.media.app.NotificationCompat.MediaStyle style =
                new androidx.media.app.NotificationCompat.MediaStyle();
        if (session != null) {
            style.setMediaSession(session.getSessionToken());
        }
        style.setShowActionsInCompactView(0); // play/pause seul en compact

        NotificationCompat.Builder b =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_music_note)
                        .setContentTitle(title)
                        .setContentText(
                                getString(R.string.playback_now_playing_tap))
                        .setContentIntent(contentIntent)
                        .setOnlyAlertOnce(true)
                        .setOngoing(playing)
                        .setVisibility(
                                NotificationCompat.VISIBILITY_PUBLIC)
                        .addAction(playing
                                        ? R.drawable.ic_pause
                                        : R.drawable.ic_play,
                                playing ? "Ajanona" : "Alefa",
                                toggleIntent)
                        .addAction(R.drawable.ic_close,
                                "Hanafoana", stopIntent);

        if (session != null) {
            b.setStyle(style);
        }
        return b.build();
    }

    private void removeNotificationAndForeground() {
        if (foregroundShown) {
            stopForeground(true);
            foregroundShown = false;
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Feo (playback)",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(
                    "Feo mandefa eo ambonin'ny app");
            channel.setShowBadge(false);
            NotificationManager nm = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    // =========================================================
    // PROGRESSION (boucle 250 ms)
    // =========================================================

    private void startProgressLoop() {
        main.removeCallbacks(progressRunnable);
        main.postDelayed(progressRunnable, PROGRESS_INTERVAL_MS);
    }

    private void stopProgressLoop() {
        if (main != null) {
            main.removeCallbacks(progressRunnable);
        }
    }

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (player == null || !state.playing) return;
            try {
                state.currentMs = player.getCurrentPosition();
                state.durationMs = Math.max(player.getDuration(), 1);
                publishProgress();
                pushSessionState();
                main.postDelayed(this, PROGRESS_INTERVAL_MS);
            } catch (IllegalStateException ignored) {
            }
        }
    };

    // =========================================================
    // PUBLICATION AUX ÉCRANS (thread UI)
    // =========================================================

    private void publishPlayingChanged() {
        final boolean playing = state.playing;
        synchronized (listeners) {
            for (Listener l : new ArrayList<>(listeners)) {
                main.post(() -> l.onPlayingChanged(playing));
            }
        }
    }

    private void publishProgress() {
        final int cur = state.currentMs;
        final int dur = state.durationMs;
        synchronized (listeners) {
            for (Listener l : new ArrayList<>(listeners)) {
                main.post(() -> l.onProgress(cur, dur));
            }
        }
    }

    private void publishCompleted() {
        synchronized (listeners) {
            for (Listener l : new ArrayList<>(listeners)) {
                main.post(l::onCompleted);
            }
        }
    }

    private void publishError(final String message) {
        synchronized (listeners) {
            for (Listener l : new ArrayList<>(listeners)) {
                main.post(() -> l.onError(message));
            }
        }
    }

    private void publishStopped() {
        synchronized (listeners) {
            for (Listener l : new ArrayList<>(listeners)) {
                main.post(l::onStopped);
            }
        }
    }
}
