package com.fa.baiboly.ui.fihirana;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.fa.baiboly.data.playback.PlaybackService;

/**
 * Pont entre les écrans et {@link PlaybackService} : lecture en
 * arrière-plan + notification média. Un seul service pour toute l'app.
 *
 * <p>Usage (activity / fragment) :</p>
 * <pre>
 *   private PlaybackServiceBridge bridge;
 *
 *   onCreate:  bridge = new PlaybackServiceBridge(this, listener); bridge.connect();
 *   onDestroy: bridge.disconnect();
 * </pre>
 *
 * Le service démarre dès {@link #play} (startForegroundService) et le
 * binding sert ensuite aux commandes. Si le service tourne déjà (l'utilisateur
 * est revenu sur un autre écran), le listener est reconnecté et l'état
 * actuel est joué par le mini-lecteur sans relancer le son.
 */
public class PlaybackServiceBridge {

    /** Événements à l'écran (thread UI, mêmes familiers que l'ancien
     *  {@link PlaybackController}). */
    public interface Listener {
        /** État lecture/pause changé. */
        void onPlayingChanged(boolean playing);

        /** Progression (ms), appelée régulièrement pendant la lecture. */
        void onProgress(int currentMs, int durationMs);

        /** Lecture arrivée à la fin. */
        void onCompleted();

        /** Erreur de lecture. */
        void onError(String message);

        /** La lecture a été stoppée (X, fichier illisible...). */
        void onStopped();
    }

    private final Context context;
    private final Listener screenListener;
    /** Callback optionnel quand le service devient lié (retour d'écran). */
    private final Runnable onConnected;

    private PlaybackService service;
    private boolean bound = false;
    /** Vrai si startForegroundService a été appelé pour le service. */
    private boolean started = false;

    /** Événement service -> écran (on tue le listener interne quand l'écran
     *  part). */
    private final PlaybackService.Listener serviceListener =
            new PlaybackService.Listener() {
                @Override
                public void onPlayingChanged(boolean playing) {
                    screenListener.onPlayingChanged(playing);
                }

                @Override
                public void onProgress(int currentMs, int durationMs) {
                    screenListener.onProgress(currentMs, durationMs);
                }

                @Override
                public void onCompleted() {
                    screenListener.onCompleted();
                }

                @Override
                public void onError(String message) {
                    screenListener.onError(message);
                }

                @Override
                public void onStopped() {
                    screenListener.onStopped();
                }
            };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((PlaybackService.LocalBinder) binder).getService();
            service.addListener(serviceListener);
            bound = true;
            if (onConnected != null) {
                onConnected.run();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;
        }
    };

    /**
     * @param context     contexte de l'écran (activity)
     * @param listener    événements de lecture
     * @param onConnected optionnel : exécuté au moment où le service est
     *                    lié (pour restaurer l'UI de l'écran depuis l'état
     *                    réel du service)
     */
    public PlaybackServiceBridge(Context context,
                                 Listener listener,
                                 Runnable onConnected) {
        this.context = context;
        this.screenListener = listener;
        this.onConnected = onConnected;
    }

    /** appeler dans onCreate (bind) */
    public void connect() {
        Intent intent = new Intent(context, PlaybackService.class);
        // bindService seul : le service démarre au premier play()
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    /** appeler dans onDestroy */
    public void disconnect() {
        if (bound) {
            context.unbindService(connection);
            bound = false;
        }
        service = null;
    }

    /** Lance la lecture (le service passe en arrière-plan / notification). */
    public void play(String path, String title, String songId) {
        Intent intent = new Intent(context, PlaybackService.class);
        if (!started) {
            context.startForegroundService(intent);
            started = true;
        }
        PlaybackService s = service;
        if (s != null) {
            s.play(path, title, songId);
        } else {
            // service pas encore lié : retente, le bind arrivera
            context.startService(intent);
        }
    }

    public void toggle() {
        PlaybackService s = service;
        if (s != null) s.togglePlayPause();
    }

    public void seek(int ms) {
        PlaybackService s = service;
        if (s != null) s.seekTo(ms);
    }

    /** Stoppe le son et retire la notification. */
    public void stop() {
        PlaybackService s = service;
        if (s != null) s.stopPlaying();
    }

    /** Le service joue-t-il actuellement ? */
    public boolean isPlaying() {
        PlaybackService s = service;
        return s != null && s.isPlaying();
    }

    /** État instantané du service (null si pas encore lié). */
    public PlaybackService.ServiceState peekState() {
        PlaybackService s = service;
        return s != null ? s.peekState() : null;
    }
}
