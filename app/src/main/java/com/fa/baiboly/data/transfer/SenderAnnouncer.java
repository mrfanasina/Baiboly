package com.fa.baiboly.data.transfer;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * 📢 Annonceur UDP du sender : écoute les "BAIBOLY_PING" du récepteur
 * sur le réseau local et répond avec ses infos (port HTTP, nom, sécurisé
 * ou non). C'est ce qui permet au récepteur de voir les senders sans QR,
 * comme Xender.
 */
public final class SenderAnnouncer {

    public static final int PORT = 24685;
    public static final String PING = "BAIBOLY_PING";

    private volatile boolean running;
    private volatile DatagramSocket socket;
    private Thread thread;

    private int httpPort;
    private String deviceName;
    private boolean secured;

    /** Démarre (ou redémarre) l'annonceur avec les infos du serveur. */
    public synchronized void announce(int httpPort, String deviceName,
                                      boolean secured) {
        stop();
        this.httpPort = httpPort;
        this.deviceName = deviceName;
        this.secured = secured;
        running = true;
        thread = new Thread(this::loop, "transfer-announcer");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        DatagramSocket s = socket;
        socket = null;
        if (s != null && !s.isClosed()) {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
        thread = null;
    }

    private void loop() {
        DatagramSocket local = null;
        try {
            local = new DatagramSocket(PORT);
            local.setSoTimeout(0);
            socket = local;
            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (running && local != null && !local.isClosed()) {
                try {
                    local.receive(packet);
                } catch (Exception e) {
                    break; // socket fermé
                }
                String msg = new String(packet.getData(),
                        packet.getOffset(), packet.getLength()).trim();
                if (PING.equals(msg)) {
                    JSONObject reply = new JSONObject();
                    try {
                        reply.put("port", httpPort);
                        reply.put("device", deviceName);
                        reply.put("secured", secured);
                        byte[] data = reply.toString().getBytes("UTF-8");
                        InetAddress to = packet.getAddress();
                        int port = packet.getPort();
                        //noinspection ExplicitArrayFloor
                        local.send(new DatagramPacket(data, data.length, to, port));
                    } catch (Exception ignored) {
                    }
                }
                packet.setLength(buffer.length);
            }
        } catch (Exception ignored) {
            // Port UDP occupé (autre app) : le scan TCP reste le secours
        } finally {
            if (local != null && !local.isClosed()) {
                try {
                    local.close();
                } catch (Exception ignored) {
                }
            }
            if (socket == local) socket = null;
        }
    }
}
