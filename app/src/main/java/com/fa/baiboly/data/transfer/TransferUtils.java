package com.fa.baiboly.data.transfer;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.wifi.WifiManager;

import android.content.Context;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;


/**
 * Outils du transfert WiFi :
 * - IP locale de l'appareil (réseau WiFi, hotspot, y compris l'IP du
 *   hotspot automatique créé par l'app) ;
 * - génération du QR code (zxing core, sans app externe) ;
 * - bloc "WIFI:T:...;S:...;P:...;;" (standar Android) pour que le scan
 *   connecte automatiquement au réseau de transfert.
 */
public final class TransferUtils {

    private TransferUtils() {
    }

    // =========================================================
    // 🌐 IP LOCALE
    // =========================================================

    /**
     * Première IPv4 "site-local" (192.168.x.x / 10.x.x.x) du téléphone,
     * WiFi ou hotspot. Null si aucun réseau local trouvé.
     */
    public static String localIpAddress() {
        String fallback = null;
        try {
            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;

                String name = nif.getName() == null ? "" : nif.getName().toLowerCase();

                Enumeration<InetAddress> addresses = nif.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.isLoopbackAddress()) continue;
                    String host = addr.getHostAddress();
                    if (host == null || !host.contains(".")) continue;

                    // Priorité à l'interface du hotspot (ap*, swlan*, ...)
                    if (name.startsWith("ap") || name.startsWith("swlan")
                            || name.contains("swlan")) {
                        return host;
                    }
                    if (addr.isSiteLocalAddress() || host.startsWith("192.168.")) {
                        if (fallback == null) fallback = host;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    // =========================================================
    // ⬛ GÉNÉRATION QR
    // =========================================================

    /** Bitmap QR code du contenu donné (taille carrée en px). */
    public static Bitmap qrBitmap(String content, int sizePx) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(
                    content, BarcodeFormat.QR_CODE, sizePx, sizePx);

            Bitmap bitmap = Bitmap.createBitmap(
                    sizePx, sizePx, Bitmap.Config.RGB_565);
            for (int x = 0; x < sizePx; x++) {
                for (int y = 0; y < sizePx; y++) {
                    bitmap.setPixel(x, y,
                            matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (WriterException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Token aléatoire court (protection des échanges). */
    public static String newToken() {
        String alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 8; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    // =========================================================
    // 📶 BLOC WIFI DU QR (format standard Android)
    // =========================================================

    /**
     * Contenu complet du QR : identifiants WiFi (connecte automatiquement)
     * suivis de l'URL de transfert. Pass null = réseau ouvert (nopass).
     */
    public static String qrContent(String transferUrl, String ssid,
                                   String pass) {
        StringBuilder sb = new StringBuilder();
        if (ssid != null && !ssid.isEmpty()) {
            sb.append("WIFI:T:").append(pass == null ? "nopass" : "WPA")
                    .append(";S:").append(escapeWifi(ssid)).append(';');
            if (pass != null) {
                sb.append("P:").append(escapeWifi(pass)).append(';');
            }
            sb.append(";;");
        }
        return sb.append(transferUrl).toString();
    }

    /** Échappe les caractères spéciaux du format WIFI: ( \ ; , : "). */
    private static String escapeWifi(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Sépare les identifiants WiFi (optionnels) de l'URL de transfert dans
     * un contenu de QR scanné. Renvoie {ssid, pass ou null, url} — ssid
     * null s'il n'y a pas de bloc WIFI dans le QR.
     */
    public static String[] parseQr(String content) {
        if (content == null) return null;
        String text = content.trim();

        String ssid = null;
        String pass = null;
        String url = text;

        if (text.startsWith("WIFI:")) {
            int sep = text.indexOf(";;", 5);
            if (sep < 0) return null;
            String wifiBlock = text.substring(5, sep);
            url = text.substring(sep + 2).trim();

            String[] parts = wifiBlock.split("(?<!\\\\);");
            for (String part : parts) {
                if (part.startsWith("S:")) ssid = unescapeWifi(part.substring(2));
                else if (part.startsWith("P:")) pass = unescapeWifi(part.substring(2));
            }
        }

        if (url.isEmpty()) return null;
        return new String[]{ssid, pass, url};
    }

    private static String unescapeWifi(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                i++;
            }
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }
}
