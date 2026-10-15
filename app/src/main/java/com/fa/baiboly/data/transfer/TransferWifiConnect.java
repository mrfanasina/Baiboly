package com.fa.baiboly.data.transfer;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;

import androidx.annotation.Nullable;

import java.util.Collections;

/**
 * 📶 Côté récepteur : connexion AUTOMATIQUE au réseau WiFi de transfert
 * à partir des identifiants lus dans le QR (format WIFI:T:…;S:…;P:…;;).
 *
 * - Android 10+ : suggestions réseau {@link WifiNetworkSuggestion}
 *   (le système connecte), puis attente de la connexion.
 * - Android 6–9 : API legacy addNetwork/enableNetwork.
 *
 * Si la connexion ne se fait pas, l'activité propose le secours manuel.
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

    private static final int CHECK_INTERVAL_MS = 900;
    private static final int MAX_WAIT_MS = 22_000;

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
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> {
                        if (listener != null) listener.onResult(result);
                    });
        }, "wifi-connect").start();
    }

    private static Result run(Context app, String ssid, String pass) {
        Result result = new Result();
        result.ssid = ssid;

        WifiManager wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return result;

        // ---------- Demande de connexion ----------
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
    // Android 10+ : suggestions réseau
    // =========================================================
    private static boolean suggestNetwork(WifiManager wm, String ssid,
                                          String pass) {
        try {
            WifiNetworkSuggestion.Builder b = new WifiNetworkSuggestion.Builder()
                    .setSsid(ssid);
            if (pass != null) {
                b.setWpa2Passphrase(pass);
            }
            int status = wm.addNetworkSuggestions(Collections.singletonList(b.build()));
            return status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS;
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
