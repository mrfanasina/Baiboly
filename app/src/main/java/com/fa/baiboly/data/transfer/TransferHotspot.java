package com.fa.baiboly.data.transfer;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.Random;
import java.util.concurrent.Executor;

/**
 * 📡 Création automatique du point d'accès (comme Xender) :
 *
 * - Android 13+ : tentative de configuration complète (SSID, bande
 *   2,4/5 GHz, ouvert ou WPA2) via {@code SoftApConfiguration} — appelée
 *   par réflexion car l'API n'est pas exposée partout ;
 * - Android 8–12 : startLocalOnlyHotspot() classique (réseau sécurisé
 *   aléatoire, bande choisie par le téléphone) ;
 * - Sinon, ou si le téléphone refuse : SECOURS MANUEL — l'utilisateur
 *   ouvre son hotspot via les réglages (où il choisit bande et sécurité)
 *   et saisit son SSID/mot de passe pour que le QR connecte l'autre
 *   téléphone automatiquement.
 */
public final class TransferHotspot {

    public static final int BAND_AUTO = 0;
    public static final int BAND_2_4GHZ = 1;
    public static final int BAND_5GHZ = 2;

    /** Préfixe des hotspots créés par l'app (le récepteur les scanne). */
    public static final String SSID_PREFIX = "BAIBOLY_SHARE_";

    /** Résultat du démarrage du point d'accès. */
    public interface Listener {
        /**
         * AP créé : ssid/pass (pass null = réseau ouvert).
         * {@code secured} = mode demandé par l'utilisateur — c'est lui qui
         * décide si le token du QR est obligatoire.
         */
        void onStarted(String ssid, @Nullable String pass, boolean secured);

        /** Impossible de créer l'AP automatiquement : secours manuel. */
        void onManualFallback(String reason);
    }

    /** Dernière réservation active (pour l'arrêter en quittant). */
    private static WifiManager.LocalOnlyHotspotReservation sReservation;

    /**
     * false = ce téléphone impose un mot de passe au point d'accès :
     * le switch "Misokatra" n'a alors plus aucun sens et l'UI le grise.
     */
    private static volatile boolean sOpenSupported = true;

    public static boolean isOpenSupported() {
        return sOpenSupported;
    }

    private TransferHotspot() {
    }

    /** Le téléphone peut-il faire du 5 GHz ? */
    public static boolean is5GHzSupported(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            return wm != null && wm.is5GHzBandSupported();
        } catch (Exception e) {
            return false;
        }
    }

    /** SSID aléatoire (utilisé aussi par le secours manuel). */
    public static String randomSsid() {
        return SSID_PREFIX + randomSuffix();
    }

    /** Un SSID est-il un hotspot Baiboly (ancien ou nouveau préfixe) ? */
    public static boolean isBaibolySsid(String ssid) {
        if (ssid == null || ssid.isEmpty()) return false;
        return ssid.startsWith(SSID_PREFIX) || ssid.startsWith("BAIBOLY-");
    }

    /** Mot de passe aléatoire (utilisé aussi par le secours manuel). */
    public static String randomPassword() {
        return randomPass();
    }

    /** Arrête le hotspot créé automatiquement. */
    public static void stop() {
        WifiManager.LocalOnlyHotspotReservation r = sReservation;
        sReservation = null;
        if (r != null) {
            try {
                r.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Crée le point d'accès. Résultat livré sur le thread UI.
     *
     * @param secured false = réseau OUVERT (pas de mot de passe), true = WPA2.
     * @param band    BAND_AUTO / BAND_2_4GHZ / BAND_5GHZ (5 GHz si supporté).
     */
    public static void start(Context context, boolean secured, int band,
                             Listener listener) {
        Context app = context.getApplicationContext();
        WifiManager wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);

        String ssid = randomSsid();
        String pass = secured ? randomPass() : null;

        if (wm == null) {
            manual("Module WiFi indisponible", listener);
            return;
        }

        // 1) Config complète (SSID/bande/ouverture exactement demandés),
        //    API cachée dispo uniquement depuis Android 13.
        if (Build.VERSION.SDK_INT >= 33
                && startConfigured(wm, ssid, pass, band, secured, listener)) {
            return;
        }

        // 2) AP local aléatoire du système (Android 8+) — il est
        //    SÉCURISÉ par défaut : on ne l'accepte que si un mot de passe
        //    est demandé (le callback le vérifie).
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                wm.startLocalOnlyHotspot(
                        getCallback(true, null, null, secured, listener),
                        new Handler(Looper.getMainLooper()));
                return;
            } catch (Exception e) {
                // → secours manuel ci-dessous
            }
        }

        manual(secured
                        ? "Le téléphone n'autorise pas le hotspot auto"
                        : "Hotspot ouvert non supporté par ce téléphone",
                listener);
    }

    // =========================================================
    // Configuration complète par réflexion (SSID/bande/ouverture)
    // =========================================================
    private static boolean startConfigured(WifiManager wm, String ssid,
                                           String pass, int band,
                                           boolean secured, Listener listener) {
        try {
            Class<?> cfgClass = Class.forName("android.net.wifi.SoftApConfiguration");
            Class<?> builderClass =
                    Class.forName("android.net.wifi.SoftApConfiguration$Builder");

            Object builder = builderClass.getDeclaredConstructor().newInstance();
            builderClass.getMethod("setSsid", String.class).invoke(builder, ssid);

            if (pass != null) {
                // setPassphrase(String, int securityType) depuis Android 13 ;
                // setPassphrase(String) si la 2e signature n'existe pas.
                boolean ok = false;
                try {
                    int wpa2 = cfgClass.getField("SECURITY_TYPE_WPA2_PSK").getInt(null);
                    builderClass.getMethod("setPassphrase", String.class, int.class)
                            .invoke(builder, pass, wpa2);
                    ok = true;
                } catch (NoSuchMethodException ignored) {
                }
                if (!ok) {
                    builderClass.getMethod("setPassphrase", String.class)
                            .invoke(builder, pass);
                }
            } else {
                // Réseau OUVERT : aucun mot de passe, jamais.
                int open = cfgClass.getField("SECURITY_TYPE_OPEN").getInt(null);
                builderClass.getMethod("setSecurityType", int.class)
                        .invoke(builder, open);
            }

            if (band == BAND_2_4GHZ) {
                builderClass.getMethod("setBand", int.class).invoke(builder,
                        cfgClass.getField("BAND_2GHZ").getInt(null));
            } else if (band == BAND_5GHZ && wm.is5GHzBandSupported()) {
                builderClass.getMethod("setBand", int.class).invoke(builder,
                        cfgClass.getField("BAND_5GHZ").getInt(null));
            }

            Object config = builderClass.getMethod("build").invoke(builder);

            // Signature réelle : startLocalOnlyHotspot(SoftApConfiguration,
            // Executor, LocalOnlyHotspotCallback) — l'ancienne signature
            // avec Handler n'existe pas : c'est pour ça que ça retombait
            // toujours sur le hotspot aléatoire (donc TOUJOURS un mdp).
            Executor direct = Runnable::run;
            Method start = WifiManager.class.getMethod("startLocalOnlyHotspot",
                    cfgClass, Executor.class,
                    WifiManager.LocalOnlyHotspotCallback.class);
            start.invoke(wm, config, direct,
                    getCallback(false, ssid, pass, secured, listener));
            return true;
        } catch (Throwable t) {
            // API non exposée ou champs absents : retry via legacy path
            return false;
        }
    }

    /**
     * Callback commun. {@code readReservationConfig} = legacy (8–12) où le
     * SSID/pass viennent de la réservation ; sinon valeurs passées en main.
     */
    private static WifiManager.LocalOnlyHotspotCallback getCallback(
            final boolean readReservationConfig, final String ssid,
            final String pass, final boolean secured,
            final Listener listener) {
        return new WifiManager.LocalOnlyHotspotCallback() {
            @Override
            public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                sReservation = reservation;
                String s = ssid;
                String p = pass;
                if (readReservationConfig) {
                    try {
                        WifiConfiguration wc = reservation.getWifiConfiguration();
                        if (wc != null && wc.SSID != null) {
                            s = trimQuotes(wc.SSID);
                            p = wc.preSharedKey != null && !wc.preSharedKey.isEmpty()
                                    ? trimQuotes(wc.preSharedKey) : null;
                        }
                    } catch (Exception ignored) {
                    }
                    // Mode OUVERT demandé mais le système a imposé un mot de
                    // passe : on REFUSE, l'utilisateur a dit "misokatra".
                    if (!secured && p != null) {
                        try {
                            reservation.close();
                        } catch (Exception ignored) {
                        }
                        sReservation = null;
                        // Capacité du téléphone : on grise le switch à vie.
                        sOpenSupported = false;
                        manual("Ce téléphone impose un mot de passe au hotspot",
                                listener);
                        return;
                    }
                }
                if (s == null) {
                    // SSID non lisible (Android récent) : l'QR ne pourrait pas
                    // connecter automatiquement → secours manuel.
                    manual("SSID du hotspot non lisible", listener);
                    return;
                }
                if (listener != null) {
                    listener.onStarted(s, p, secured);
                }
            }

            @Override
            public void onFailed(int reason) {
                manual("Échec du hotspot (code " + reason + ")", listener);
            }

            @Override
            public void onStopped() {
                sReservation = null;
            }
        };
    }

    // =========================================================
    // SECOURS : hotspot manuel via les réglages
    // =========================================================
    private static void manual(String reason, Listener listener) {
        if (listener != null) listener.onManualFallback(
                reason == null ? "inconnu" : reason);
    }

    private static String trimQuotes(String s) {
        if (s == null) return null;
        return s.replace("\"", "");
    }

    private static String randomSuffix() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(r.nextInt(10));
        }
        return sb.toString();
    }

    private static String randomPass() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        String alpha = "abcdefghjkmnpqrstuvwxyz23456789";
        for (int i = 0; i < 10; i++) {
            sb.append(alpha.charAt(r.nextInt(alpha.length())));
        }
        return sb.toString();
    }
}
