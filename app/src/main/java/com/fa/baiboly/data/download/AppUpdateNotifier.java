package com.fa.baiboly.data.download;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;

import com.fa.baiboly.models.DownloadEntry;

/**
 * Vérifie update.json en arrière-plan et retient la version que l'utilisateur
 * a choisie de ne plus voir.
 *
 * Le point orange du menu et le pop-up de démarrage utilisent tous les deux
 * {@link #isDismissed(Context, DownloadEntry)} : quand la version publiée
 * change (versionCode/versionName différent), l'indication réapparaît.
 */
public final class AppUpdateNotifier {

    private static final String PREFS_NAME = "app_settings";
    /** Clé de la version ignorée : "code:12", "name:1.2.0" ou "url:…". */
    private static final String KEY_DISMISSED_VERSION = "update_dismissed_version";

    /** Callback appelé sur le thread principal (entry null = pas de mise à jour). */
    public interface Callback {
        void onResult(DownloadEntry update);
    }

    private AppUpdateNotifier() {
    }

    /** Interroge update.json hors du thread principal. */
    public static void check(Context context, Callback callback) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            DownloadEntry update = null;
            try {
                update = new ServerManifestClient(app).fetchAppUpdate();
            } catch (Exception ignored) {
            }

            if (update != null) {
                int code = update.getVersionCode();
                // Sans versionCode, on fait confiance au serveur (comme la
                // carte de l'écran Downloads).
                if (code > 0 && code <= installedVersionCode(app)) {
                    update = null;
                }
            }

            final DownloadEntry result = update;
            main.post(() -> callback.onResult(result));
        }, "app-update-check").start();
    }

    /** versionCode installé sur l'appareil, 0 si illisible. */
    public static int installedVersionCode(Context context) {
        try {
            PackageInfo pi = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    /** "v1.2.0" ou "v12", vide si inconnu. */
    public static String versionLabel(DownloadEntry entry) {
        if (entry == null) return "";
        String name = entry.getVersionName();
        if (name != null && !name.isEmpty()) return "v" + name;
        if (entry.getVersionCode() > 0) return "v" + entry.getVersionCode();
        return "";
    }

    /** Identité de la mise à jour, stable pour une version publiée. */
    public static String versionKey(DownloadEntry entry) {
        if (entry == null) return "";
        if (entry.getVersionCode() > 0) return "code:" + entry.getVersionCode();
        String name = entry.getVersionName();
        if (name != null && !name.isEmpty()) return "name:" + name;
        String url = entry.getUrl();
        return url == null || url.isEmpty() ? "" : "url:" + url;
    }

    public static boolean isDismissed(Context context, DownloadEntry entry) {
        String key = versionKey(entry);
        if (key.isEmpty()) return false;
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return key.equals(prefs.getString(KEY_DISMISSED_VERSION, null));
    }

    /** Ne plus afficher le pop-up ni le point pour cette version précise. */
    public static void dismissForVersion(Context context, DownloadEntry entry) {
        String key = versionKey(entry);
        if (key.isEmpty()) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DISMISSED_VERSION, key)
                .apply();
    }
}
