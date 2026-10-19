package com.fa.baiboly.data.transfer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 📶 Côté récepteur : connexion AUTOMATIQUE au réseau WiFi de transfert
 * à partir des identifiants lus dans le QR (format WIFI:T:…;S:…;P:…;;).
 *
 * - Android 10+ : {@link WifiNetworkSpecifier} via ConnectivityManager —
 *   c'est LUI qui connecte VRAIMENT le téléphone au SSID/mot de passe du
 *   QR (l'ancienne approche « suggestion réseau » se contentait de
 *   PROPONER le réseau, sans jamais se connecter — d'où « le scan ne
 *   connecte pas au WiFi du partage »). Le réseau obtenu est local-only :
 *   on binde le processus dessus pour que le téléchargement l'utilise.
 * - Android 6–9 : API legacy addNetwork/enableNetwork.
 *
 * Si la connexion ne se fait pas, l'activité propose le secours manuel
 * (bouton « Fikirana WiFi »).
 */
public final class TransferWifiConnect {

    /** Résultat de la tentative de connexion. */
    public static class Result {
        public boolean connected;
        /** true = SSID détecté connecté ; false = connexion incertaine. */
        public boolean confirmed;
        public String ssid;
    }

    public interface Listener {
        void onResult(Result result);
    }

    private static final int CHECK_INTERVAL_MS = 700;
    private static final int MAX_WAIT_MS = 15_000;
    /** Délai du réseau spécifié (le système montre parfois une demande). */
    private static final int SPECIFIER_TIMEOUT_MS = 20_000;

    /** Callback du réseau spécifié : garder pour le libérer à la fin. */
    private static volatile ConnectivityManager.NetworkCallback sSpecifierCallback;

    private TransferWifiConnect() {
    }

    /**
     * Connecte le téléphone au SSID donné (pass null = réseau ouvert),
     * puis vérifie la connexion. Résultat livré sur le thread UI.
     */
    public static void connect(Context context, String ssid,
                               @Nullable String pass, Listener listener) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            Result result = run(app, ssid, pass);
            new Handler(Looper.getMainLooper())
                    .post(() -> {
                        if (listener != null) listener.onResult(result);
                    });
        }, "wifi-connect").start();
    }

    private static Result run(Context app, String ssid, String pass) {
        Result result = new Result();
        result.ssid = ssid;

        // ---------- Android 10+ : la voie fiable d'abord ----------
        if (Build.VERSION.SDK_INT >= 29 && specifierConnect(app, ssid, pass)) {
            result.connected = true;
            result.confirmed = true;
            return result;
        }

        WifiManager wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return result;

        // ---------- Demande de connexion (secours) ----------
        boolean asked;
        if (Build.VERSION.SDK_INT >= 29) {
            asked = suggestNetwork(wm, ssid, pass);
        } else {
            asked = legacyConnect(wm, ssid, pass);
        }
        if (!asked) return result;

        // ---------- Attente de la connexion ----------
        long deadline = System.currentTimeMillis() + MAX_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                // Thread.sleep ok : thread de fond
                Thread.sleep(CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                return result;
            }
            String current = currentSsid(wm);
            if (current != null && current.equalsIgnoreCase(ssid)) {
                result.connected = true;
                result.confirmed = true;
                return result;
            }
        }
        // Laissons le récepteur réessayer le manifest : réponse incertaine
        result.connected = false;
        result.confirmed = false;
        return result;
    }

    // =========================================================
    // Android 10+ : connexion RÉELLE via WifiNetworkSpecifier
    // =========================================================

    /**
     * Demande explicitement le SSID + mot de passe du QR. Le système
     * affiche (si besoin) une demande de connexion, puis délivre un
     * {@link Network} local-only qu'on binde au processus : toutes les
     * connexions HTTP suivantes (manifest + téléchargements) passent par
     * le hotspot de transfert.
     *
     * @return true si le réseau est disponible et bindé.
     */
    private static boolean specifierConnect(Context app, String ssid,
                                            String pass) {
        ConnectivityManager cm = app.getSystemService(ConnectivityManager.class);
        if (cm == null) return false;

        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] ok = {false};
        try {
            WifiNetworkSpecifier.Builder sb = new WifiNetworkSpecifier.Builder()
                    .setSsid(ssid);
            if (pass != null && !pass.isEmpty()) {
                sb.setWpa2Passphrase(pass);
            }
            // Réseau local-only : il n'a PAS de vrai internet, on le retire
            // de la demande sinon le système ne matchera jamais le hotspot.
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(sb.build())
                    .build();

            ConnectivityManager.NetworkCallback callback =
                    new ConnectivityManager.NetworkCallback() {
                        @Override
                        public void onAvailable(Network network) {
                            // Tout le processus parle au hotspot
                            try {
                                cm.bindProcessToNetwork(network);
                            } catch (Exception ignored) {
                            }
                            ok[0] = true;
                            latch.countDown();
                        }

                        @Override
                        public void onUnavailable() {
                            latch.countDown();
                        }

                        @Override
                        public void onLost(Network network) {
                            // Perte du réseau de transfert : on ne fait
                            // rien ici, l'écran "En cours" montre l'état.
                        }
                    };

            sSpecifierCallback = callback;
            cm.requestNetwork(request, callback, SPECIFIER_TIMEOUT_MS);
        } catch (Exception e) {
            sSpecifierCallback = null;
            return false;
        }

        try {
            //noinspection ResultOfMethodCallIgnored
            latch.await(SPECIFIER_TIMEOUT_MS + 2_000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
        return ok[0];
    }

    /** Libère le réseau de transfert : le WiFi d'origine peut revenir. */
    public static void release(Context app) {
        ConnectivityManager.NetworkCallback cb = sSpecifierCallback;
        sSpecifierCallback = null;
        if (cb != null) {
            try {
                ConnectivityManager cm =
                        app.getSystemService(ConnectivityManager.class);
                if (cm != null) cm.unregisterNetworkCallback(cb);
            } catch (Exception ignored) {
            }
        }
        try {
            ConnectivityManager cm = app.getSystemService(ConnectivityManager.class);
            if (cm != null) cm.bindProcessToNetwork(null);
        } catch (Exception ignored) {
        }
    }

    // =========================================================
    // Secours : réseau suggéré (Android 10+)
    // =========================================================
    private static boolean suggestNetwork(WifiManager wm, String ssid,
                                          String pass) {
        try {
            WifiNetworkSuggestion.Builder b = new WifiNetworkSuggestion.Builder()
                    .setSsid(ssid);
            if (pass != null && !pass.isEmpty()) {
                b.setWpa2Passphrase(pass);
            }
            // Android 11+ : le réseau est explicitement éligible à l'autojoin
            // → le téléphone se connecte tout seul au WiFi du transfert,
            // sans qu'on ait besoin d'ouvrir les réglages.
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    b.setIsInitialAutojoinEnabled(true);
                } catch (Throwable ignored) {
                }
            }
            int status = wm.addNetworkSuggestions(
                    Collections.singletonList(b.build()));
            if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                return false;
            }
            // Un scan rend le nouveau réseau visible tout de suite.
            try {
                wm.startScan();
            } catch (Exception ignored) {
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================
    // Android 6–9 : API legacy
    // =========================================================
    @SuppressWarnings("deprecation")
    private static boolean legacyConnect(WifiManager wm, String ssid,
                                         String pass) {
        try {
            WifiConfiguration wc = new WifiConfiguration();
            wc.SSID = "\"" + ssid + "\"";
            if (pass == null) {
                wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            } else {
                wc.preSharedKey = "\"" + pass + "\"";
            }
            int id = wm.addNetwork(wc);
            if (id < 0) return false;
            wm.disconnect();
            boolean ok = wm.enableNetwork(id, true);
            wm.reconnect();
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    /** SSID du réseau actuel (null si inconnu ou sans permission location). */
    @Nullable
    private static String currentSsid(WifiManager wm) {
        try {
            android.net.wifi.WifiInfo info = wm.getConnectionInfo();
            if (info == null) return null;
            if (info.getNetworkId() == -1) return null;
            String ssid = info.getSSID();
            if (ssid == null) return null;
            ssid = ssid.replace("\"", "");
            if (ssid.contains("unknown ssid")) return null;
            return ssid;
        } catch (Exception e) {
            return null;
        }
    }
}
