package com.fa.baiboly.data.transfer;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 🔍 Découverte des senders sur le réseau local (côté récepteur) :
 *
 * 1. Annonce UDP (rapide) : "BAIBOLY_PING" en broadcast → les senders
 *    d'ici répondent avec infos (port, nom, 🔒/🔓).
 * 2. Secours TCP : scan des ports de {@link TransferActivity} plage
 *    hook :PORT_MIN..PORT_MAX sur le sous-réseau, puis GET /q sans token
 *    : 200 = ouvert, 403 = sécurisé (QR nécessaire).
 *
 * Résultat : liste dédoublonnée d'appareils qui partagent, avec leur
 * état sécurisé — on peut accéder directement aux ouverts.
 */
public final class SenderDiscovery {

    /** Un sender trouvé sur le réseau. */
    public static class Sender {
        public final String ip;
        public final int port;
        public String device;
        public boolean secured;

        public Sender(String ip, int port, String device, boolean secured) {
            this.ip = ip;
            this.port = port;
            this.device = device;
            this.secured = secured;
        }

        public String url() {
            return "http://" + ip + ":" + port + "/q";
        }
    }

    public interface Listener {
        void onResult(List<Sender> senders);
    }

    /** Plage de ports du serveur de transfert (mêmes valeurs que l'activité). */
    private static final int PORT_RANGE_MIN = 8619;
    private static final int PORT_RANGE_MAX = 8631;

    private static final long UDP_WAIT_MS = 3500;
    private static final int TCP_THREADS = 80;
    private static final int TCP_TIMEOUT_MS = 180;

    private static volatile boolean sRunning;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private SenderDiscovery() {
        // découverte statique
    }

    public static void stop() {
        sRunning = false;
    }

    /** Lance la découverte (UDP + secours TCP), résultat sur le thread UI. */
    public static void discover(Context context, Listener listener) {
        final Context app = context.getApplicationContext();
        sRunning = true;
        new Thread(() -> {
            List<Sender> result;
            try {
                result = run(app);
            } catch (Exception e) {
                result = new ArrayList<>();
            }
            final List<Sender> finalList = result;
            if (sRunning && listener != null) {
                MAIN.post(() -> listener.onResult(finalList));
            }
        }, "sender-discovery").start();
    }

    private static List<Sender> run(Context app) throws Exception {
        Map<String, Sender> found = new LinkedHashMap<>();

        List<String> subnets = localSubnets();
        if (subnets.isEmpty()) return new ArrayList<>();

        // ---------- 1) ANNONCE UDP ----------
        udpRound(subnets, found);

        // ---------- 2) SECOURS : SCAN TCP s'il n'y a rien ----------
        if (found.isEmpty() && sRunning) {
            tcpRound(subnets, found);
        }

        return dedupe(found);
    }

    // =========================================================
    // UDP : broadcast + collecte des réponses
    // =========================================================
    private static void udpRound(List<String> subnets, Map<String, Sender> found) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(300);

            byte[] ping = SenderAnnouncer.PING.getBytes("UTF-8");
            List<InetAddress> targets = new ArrayList<>();
            for (String subnet : subnets) {
                try {
                    targets.add(InetAddress.getByName(subnet + ".255"));
                } catch (Exception ignored) {
                }
            }
            for (InetAddress t : targets) {
                try {
                    socket.send(new DatagramPacket(ping, ping.length, t,
                            SenderAnnouncer.PORT));
                } catch (Exception ignored) {
                }
            }

            long deadline = System.currentTimeMillis() + UDP_WAIT_MS;
            byte[] buffer = new byte[512];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (sRunning && System.currentTimeMillis() < deadline) {
                try {
                    socket.receive(packet);
                    String body = new String(packet.getData(),
                            packet.getOffset(), packet.getLength()).trim();
                    JSONObject json = new JSONObject(body);
                    String ip = packet.getAddress().getHostAddress();
                    int port = json.optInt("port", -1);
                    if (port <= 0) continue;
                    String key = ip + ":" + port;
                    if (!found.containsKey(key)) {
                        found.put(key, new Sender(ip, port,
                                json.optString("device", "Téléphone"),
                                json.optBoolean("secured", true)));
                    }
                } catch (java.net.SocketTimeoutException ste) {
                    // continue d'écouter jusqu'au deadline
                } catch (Exception e) {
                    break; // réponse invalide : on s'arrête
                }
                packet.setLength(buffer.length);
            }
        } catch (Exception ignored) {
        } finally {
            if (socket != null) socket.close();
        }
    }

    // =========================================================
    // TCP : scan du sous-réseau sur la plage de ports
    // =========================================================
    private static void tcpRound(List<String> subnets, Map<String, Sender> found) {
        ExecutorService pool = Executors.newFixedThreadPool(TCP_THREADS);
        AtomicInteger pending = new AtomicInteger(0);

        for (final String subnet : subnets) {
            for (int host = 1; host <= 254; host++) {
                final String ip = subnet + "." + host;
                for (int port = PORT_RANGE_MIN; port <= PORT_RANGE_MAX; port++) {
                    final int p = port;
                    pending.incrementAndGet();
                    pool.execute(() -> {
                        if (!sRunning) return;
                        try {
                            // Ping TCP rapide : y a-t-il un serveur ici ?
                            try (Socket socket = new Socket()) {
                                socket.connect(new InetSocketAddress(ip, p),
                                        TCP_TIMEOUT_MS);
                            } catch (Exception e) {
                                return; // rien sur ce port
                            }

                            int status = httpStatus(ip, p, "/q", null);
                            boolean open = status == 200;
                            boolean secured = status == 403;
                            if (!open && !secured) return;

                            String device = "Baiboly";
                            if (open) {
                                device = fetchDevice(ip, p);
                            }
                            final String key = ip + ":" + p;
                            synchronized (found) {
                                if (!found.containsKey(key)) {
                                    found.put(key, new Sender(ip, p,
                                            device, secured));
                                }
                            }
                        } finally {
                            pending.decrementAndGet();
                        }
                    });
                }
            }
        }
        // Attente bornée du scan (25 entrées max… garde-fou simple)
        try {
            long deadline = System.currentTimeMillis() + 10_000;
            while (pending.get() > 0 && System.currentTimeMillis() < deadline) {
                //noinspection BusyWait
                Thread.sleep(120);
            }
        } catch (Exception ignored) {
        }
        pool.shutdownNow();
    }

    /** Code HTTP de GET /q (avec ou sans token) : 200, 403, -1. */
    private static int httpStatus(String ip, int port, String path, String token) {
        HttpURLConnection conn = null;
        try {
            String url = "http://" + ip + ":" + port + path
                    + (token == null ? "" : "?t=" + token);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(400);
            conn.setReadTimeout(1200);
            return conn.getResponseCode();
        } catch (Exception e) {
            return -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Nom de l'appareil sender (champ "device" du manifest). */
    private static String fetchDevice(String ip, int port) {
        InputStream in = null;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("http://" + ip + ":" + port
                    + "/q").openConnection();
            conn.setConnectTimeout(600);
            conn.setReadTimeout(1500);
            if (conn.getResponseCode() != 200) return "Baiboly";
            in = conn.getInputStream();
            byte[] buffer = new byte[512];
            int len = in.read(buffer);
            if (len <= 0) return "Baiboly";
            JSONObject json = new JSONObject(new String(buffer, 0, len, "UTF-8"));
            return json.optString("device", "Baiboly");
        } catch (Exception e) {
            return "Baiboly";
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
            if (conn != null) conn.disconnect();
        }
    }


    /** Sous-réseaux IPv4 locaux (max 2), format "x.y.z" (sans le 4e bloc). */
    private static List<String> localSubnets() {
        List<String> subnets = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements() && subnets.size() < 2) {
                NetworkInterface nif = interfaces.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                Enumeration<InetAddress> addresses = nif.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.isLoopbackAddress()) continue;
                    String host = addr.getHostAddress();
                    if (host == null || !host.contains(".")
                            || (!addr.isSiteLocalAddress()
                            && !host.startsWith("192.168."))) {
                        continue;
                    }
                    String subnet = host.substring(0, host.lastIndexOf('.'));
                    if (!subnets.contains(subnet)) {
                        subnets.add(subnet);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return subnets;
    }

    private static List<Sender> dedupe(Map<String, Sender> found) {
        List<Sender> list = new ArrayList<>(found.values());
        return list;
    }
}
