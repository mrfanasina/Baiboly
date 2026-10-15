package com.fa.baiboly.ui.transfer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.download.ServerManifestClient;
import com.fa.baiboly.data.fandaharana.FandaharanaService;
import com.fa.baiboly.data.playback.PlaybackStore;
import com.fa.baiboly.data.transfer.SenderAnnouncer;
import com.fa.baiboly.data.transfer.SenderDiscovery;
import com.fa.baiboly.data.transfer.TransferClient;
import com.fa.baiboly.data.transfer.TransferData;
import com.fa.baiboly.data.transfer.TransferHotspot;
import com.fa.baiboly.data.transfer.TransferItem;
import com.fa.baiboly.data.transfer.TransferServer;
import com.fa.baiboly.data.transfer.TransferUtils;
import com.fa.baiboly.data.transfer.TransferWifiConnect;
import com.fa.baiboly.databinding.ActivityTransferBinding;
import com.fa.baiboly.models.Playback;
import com.fa.baiboly.models.Program;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Transfert WiFi local de programmes (fandaharana) et playbacks :
 *
 * ✈️ ALEFA (send) : on démarre D'ABORD la session (hotspot auto +
 *    serveur + QR), la sélection n'est PAS requise ; on continue ensuite
 *    à cocher/décocher pendant que la session tourne — le serveur
 *    reflète le choix en direct, et la sélection d'avant reste en place.
 *    Le QR contient les identifiants WiFi + l'URL : le scan connecte
 *    l'autre téléphone automatiquement. Mode OUVERT possible (sans mot
 *    de passe ni QR).
 *
 * 📲 ANDRAY (receive) : liste auto des senders du réseau (🔓 direct,
 *    🔒 scan QR) ou scan du QR → sélection → téléchargement + import.
 *
 * TOUS les playbacks sont transférables : ceux importés depuis les
 * dossiers sont streamés directement (content://), sans copie.
 * La liste longue se filtre par sokajy (chips) et par recherche.
 */
public class TransferActivity extends AppCompatActivity {

    // =========================================================
    // ÉTATS
    // =========================================================
    private static final int PORT_MIN = 8619;
    private static final int PORT_MAX = 8631;

    private static final int REQ_CAMERA = 100;
    private static final int REQ_WIFI_PERMS = 101;
    private static final int REQ_DISCOVER_PERMS = 102;

    /** Préfixe des hotspots créés par l'app (scan auto côté récepteur). */
    private static final String HOTSPOT_PREFIX = "BAIBOLY-";

    private static final String PREFS_TRANSFER = "transfer_prefs";
    private static final String KEY_SELECTION = "selected_keys";
    private static final String CAT_ALL = "*";
    private static final String CAT_PROGRAM = "Fandaharana";

    private ActivityTransferBinding binding;
    private FandaharanaService fandaharanaService;
    private PlaybackStore playbackStore;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // MODE
    private boolean sendMode = true;

    // SEND — liste complète + sélection par clé stable
    private final List<TransferItem> sendItems = new ArrayList<>();
    private final List<TransferItem> filteredSendItems = new ArrayList<>();
    private final Set<String> sendChecked = new LinkedHashSet<>();
    private String filterCategory = CAT_ALL;
    private String searchQuery = "";
    private final Map<String, Integer> chipIdsByCat = new LinkedHashMap<>();
    private final Runnable searchRunnable = this::applyFilter;

    // SEND — session
    private TransferServer server;
    private SenderAnnouncer announcer;
    private String token;
    private int serverPort;
    private String qrUrlBase;

    // RECEIVE
    private TransferClient.Manifest receivedManifest;
    private String receivedQr;
    /** Hotspot Baiboly rejoint via le scan auto (pour nettoyage après). */
    private String joinedBaibolySsid;
    private final Set<Integer> receivedChecked = new HashSet<>();
    private final List<SenderDiscovery.Sender> sendersFound = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyGlobalTheme();

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = 120.0f;
        getWindow().setAttributes(params);

        super.onCreate(savedInstanceState);

        binding = ActivityTransferBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fandaharanaService = new FandaharanaService(this);
        playbackStore = new PlaybackStore(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.btnModeSend.setOnClickListener(v -> setMode(true));
        binding.btnModeReceive.setOnClickListener(v -> setMode(false));

        // Alefa : session avant même la sélection
        binding.btnShowQr.setOnClickListener(v -> startTransferSession());
        binding.btnStopServer.setOnClickListener(v -> stopServer());
        binding.btnToggleQr.setOnClickListener(v -> {
            boolean show = binding.qrImage.getVisibility() == View.GONE;
            binding.qrImage.setVisibility(show ? View.VISIBLE : View.GONE);
            binding.btnToggleQr.setText(show ? "Afeno ny QR" : "Asehoy ny QR");
        });

        // Filtrage
        binding.btnSelectAll.setOnClickListener(v -> {
            for (TransferItem it : filteredSendItems) sendChecked.add(it.key());
            notifySendChanged();
        });
        binding.btnClearAll.setOnClickListener(v -> {
            for (TransferItem it : filteredSendItems) sendChecked.remove(it.key());
            notifySendChanged();
        });
        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim().toLowerCase();
                uiHandler.removeCallbacks(searchRunnable);
                uiHandler.postDelayed(searchRunnable, 300);
            }
        });

        // 5 GHz seulement si le téléphone le supporte
        if (!TransferHotspot.is5GHzSupported(this)) {
            binding.chipBand5.setEnabled(false);
        }

        // Andray
        binding.btnScan.setOnClickListener(v -> launchScanner());
        binding.btnDownloadSelected.setOnClickListener(v -> downloadSelectedItems());
        binding.btnRefreshSenders.setOnClickListener(v -> refreshDiscovery());
        binding.btnJoinWifi.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));

        setMode(true);
    }

    private void applyGlobalTheme() {
        SharedPreferences prefs =
                getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        int savedMode = prefs.getInt("theme_mode",
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);
        ColorManager.applyTheme(this);
    }

    // =========================================================
    // 🔀 MODE UNIQUE ÉCRAN
    // =========================================================
    private void setMode(boolean isSend) {
        sendMode = isSend;
        binding.sendPanel.setVisibility(isSend ? View.VISIBLE : View.GONE);
        binding.receivePanel.setVisibility(isSend ? View.GONE : View.VISIBLE);
        binding.btnModeSend.setChecked(isSend);
        binding.btnModeReceive.setChecked(!isSend);

        if (isSend) {
            stopDiscovery();
            loadSendList();
        } else {
            receivedManifest = null;
            receivedChecked.clear();
            binding.textReceiveState.setText(
                    "Scan ny QR na tsindrio ny namana ao amin'ny lisitra");
            binding.recyclerReceive.setAdapter(null);
            binding.btnDownloadSelected.setVisibility(View.GONE);
            refreshDiscovery();
        }
    }

    @Override
    protected void onDestroy() {
        stopServer();
        stopDiscovery();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        saveSelection();
        super.onPause();
    }

    // =========================================================
    // ✈️ ALEFA : LISTE + FILTRES + SÉLECTION PERSISTANTE
    // =========================================================
    private void loadSendList() {
        binding.textStatus.setText("Maka ny lisitra…");
        new Thread(() -> {
            List<Program> programs = fandaharanaService.getAllPrograms();
            List<Playback> playbacks = playbackStore.getAllPlaybacks();

            List<String> skipped = new ArrayList<>();
            List<TransferItem> items = TransferData.buildSenderItems(
                    this, programs, playbacks, skipped);

            // La sélection précédente reste cochée (persistance par clé)
            Set<String> previous = loadSelection();
            if (previous != null) sendChecked.addAll(previous);
            // Impact des playbacks disparus : on ne garde que les clés existantes
            Set<String> known = new HashSet<>();
            for (TransferItem it : items) known.add(it.key());
            sendChecked.retainAll(known);

            final int skippedCount = skipped.size();
            runOnUiThread(() -> {
                sendItems.clear();
                sendItems.addAll(items);

                buildCategoryChips();
                applyFilter();

                if (skippedCount == 1) {
                    binding.textStatus.setText(
                            "⚠ 1 playback tsy azo alefa (fichier tsy misy)");
                } else if (skippedCount > 1) {
                    binding.textStatus.setText(skippedCount
                            + " playbacks tsy azo alefa");
                } else if (server != null) {
                    binding.textStatus.setText("");
                }
            });
        }).start();
    }

    /** Sokajy disponibles : "Fandaharana" + chaque catégorie de chant. */
    private void buildCategoryChips() {
        LinkedHashSet<String> cats = new LinkedHashSet<>();
        for (TransferItem it : sendItems) {
            String c = TransferItem.TYPE_PROGRAM.equals(it.type)
                    ? CAT_PROGRAM : it.category == null ? ""
                    : it.category.toLowerCase();
            if (!c.isEmpty()) cats.add(c);
        }

        binding.chipCats.removeAllViews();
        chipIdsByCat.clear();
        addCategoryChip(CAT_ALL, "REHETRA · " + sendItems.size());
        for (String c : cats) {
            addCategoryChip(c, upper(c) + " · " + countCategory(c));
        }
        Integer id = chipIdsByCat.containsKey(filterCategory)
                ? chipIdsByCat.get(filterCategory)
                : chipIdsByCat.get(CAT_ALL);
        if (id == null) {
            filterCategory = CAT_ALL;
            id = chipIdsByCat.get(CAT_ALL);
        }
        if (id != null) binding.chipCats.check(id);
        else binding.chipCats.clearCheck();
    }

    private int countCategory(String cat) {
        int n = 0;
        for (TransferItem it : sendItems) {
            if (catOf(it).equals(cat)) n++;
        }
        return n;
    }

    private String catOf(TransferItem it) {
        return TransferItem.TYPE_PROGRAM.equals(it.type)
                ? CAT_PROGRAM
                : (it.category == null ? "" : it.category.toLowerCase());
    }

    private String upper(String c) {
        return c == null ? "" : c.toUpperCase();
    }

    private void addCategoryChip(String category, String label) {
        Chip chip = (Chip) LayoutInflater.from(this)
                .inflate(R.layout.chip_cat, binding.chipCats, false);
        chip.setText(label);
        int id = View.generateViewId();
        chip.setId(id);
        chip.setChecked(category.equals(filterCategory));
        chip.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                filterCategory = category;
                applyFilter();
            }
        });
        binding.chipCats.addView(chip);
        chipIdsByCat.put(category, id);
    }

    /** Applique la recherche + la catégorie sur la liste complète. */
    private void applyFilter() {
        filteredSendItems.clear();
        for (TransferItem it : sendItems) {
            if (!filterCategory.equals(CAT_ALL)
                    && !catOf(it).equals(filterCategory)) continue;
            if (!searchQuery.isEmpty() && !matchesSearch(it)) continue;
            filteredSendItems.add(it);
        }
        if (binding.recyclerSend.getLayoutManager() == null) {
            binding.recyclerSend.setLayoutManager(new LinearLayoutManager(this));
        }
        if (binding.recyclerSend.getAdapter() == null) {
            binding.recyclerSend.setAdapter(new SendAdapter());
        } else {
            binding.recyclerSend.getAdapter().notifyDataSetChanged();
        }
        binding.textSendEmpty.setVisibility(
                filteredSendItems.isEmpty() ? View.VISIBLE : View.GONE);
        binding.recyclerSend.setVisibility(
                filteredSendItems.isEmpty() ? View.GONE : View.VISIBLE);
        updateSelectCount();
        updateServerSelection();
    }

    private boolean matchesSearch(TransferItem it) {
        String q = searchQuery;
        if (it.title != null && it.title.toLowerCase().contains(q)) return true;
        if (it.playTitle != null && it.playTitle.toLowerCase().contains(q)) return true;
        if (it.category != null && it.category.toLowerCase().contains(q)) return true;
        try {
            if (Integer.parseInt(q) == it.number) return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private void notifySendChanged() {
        if (binding.recyclerSend.getAdapter() != null) {
            binding.recyclerSend.getAdapter().notifyDataSetChanged();
        }
        updateSelectCount();
        updateServerSelection();
        saveSelection();
    }

    private void updateSelectCount() {
        binding.textSelectCount.setText(
                sendChecked.size() + " voasafidy"
                        + (server != null ? " · efa vonona ho alefa" : ""));
        binding.textSelectCount.requestLayout();
    }

    // =========================================================
    // ✈️ ALEFA : SESSION HOTSPOT + SERVEUR + QR (avant sélection)
    // =========================================================
    private void startTransferSession() {
        if (server != null) {
            focusQr();
            return;
        }

        // Permissions WiFi : fine location (< Android 13) + nearby (13+)
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT < 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                "android.permission.NEARBY_WIFI_DEVICES")
                != PackageManager.PERMISSION_GRANTED) {
            missing.add("android.permission.NEARBY_WIFI_DEVICES");
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    missing.toArray(new String[0]), REQ_WIFI_PERMS);
            return;
        }

        boolean secured = !binding.switchOpen.isChecked();
        int band = TransferHotspot.BAND_AUTO;
        if (binding.chipBand24.isChecked()) band = TransferHotspot.BAND_2_4GHZ;
        else if (binding.chipBand5.isChecked() && binding.chipBand5.isEnabled())
            band = TransferHotspot.BAND_5GHZ;

        binding.btnShowQr.setEnabled(false);
        binding.textStatus.setText("Mamorona hotspot…");

        TransferHotspot.start(this, secured, band, new TransferHotspot.Listener() {
            @Override
            public void onStarted(String ssid, String pass, boolean apSecured) {
                runOnUiThread(() -> beginServing(ssid, pass, apSecured));
            }

            @Override
            public void onManualFallback(String reason) {
                runOnUiThread(() -> fallbackDirect(reason));
            }
        });
    }

    /** Hotspot prêt (auto ou manuel) : serveur + annonce + QR. */
    private void beginServing(String ssid, String pass, boolean secured) {
        token = TransferUtils.newToken();
        boolean requireToken = secured;

        int chosenPort = -1;
        TransferServer started = null;
        for (int port = PORT_MIN; port <= PORT_MAX && started == null; port++) {
            try {
                TransferServer s = new TransferServer(
                        port, token, Build.MODEL,
                        currentSelection(), this, requireToken);
                s.startDaemon();
                started = s;
                chosenPort = port;
                qrUrlBase = "http://" + TransferUtils.localIpAddress() + ":" + port;
            } catch (Exception ignored) {
            }
        }
        if (started == null) {
            binding.btnShowQr.setEnabled(true);
            Toast.makeText(this, "Nisy olana amin'ny serveur", Toast.LENGTH_SHORT).show();
            return;
        }
        server = started;
        serverPort = chosenPort;

        if (announcer == null) announcer = new SenderAnnouncer();
        announcer.announce(serverPort, Build.MODEL, requireToken);

        String qr = TransferUtils.qrContent(
                qrUrlBase + "/q?t=" + token, ssid, pass);
        Bitmap bitmap = TransferUtils.qrBitmap(qr, 600);
        binding.qrImage.setImageBitmap(bitmap);
        binding.textServerInfo.setText(
                Build.MODEL + " · " + qrUrlBase
                        + (pass == null
                        ? "\n🔓 Misokatra : tsy misy teny miafina"
                        : "\n🔒 Misy teny miafina (" + ssid + ")")
                        + "\nEfa misokatra ny fampifandraisana : fidio izay ho alefa");
        binding.qrCard.setVisibility(View.VISIBLE);
        binding.qrImage.setVisibility(View.VISIBLE);
        binding.btnToggleQr.setText("Afeno ny QR");
        binding.btnShowQr.setVisibility(View.GONE);
        binding.btnShowQr.setEnabled(true);
        updateSelectCount();

        // Le QR en haut : proche du lecteur de l'autre téléphone
        focusQr();
        binding.textStatus.setText("");
    }

    /** Remonte la liste et centre le QR (session déjà affichée en haut). */
    private void focusQr() {
        if (binding.recyclerSend.getLayoutManager() != null) {
            binding.recyclerSend.smoothScrollToPosition(0);
        }
        binding.qrImage.setVisibility(View.VISIBLE);
        binding.btnToggleQr.setText("Afeno ny QR");
    }

    /** Sélection courante (items dont la clé est cochée). */
    private List<TransferItem> currentSelection() {
        List<TransferItem> list = new ArrayList<>();
        for (TransferItem it : sendItems) {
            if (sendChecked.contains(it.key())) list.add(it);
        }
        return list;
    }

    /** Sélection modifiée pendant la session → serveur mis à jour. */
    private void updateServerSelection() {
        if (server != null) {
            server.setItems(currentSelection());
        }
    }

    /**
     * Direct : si le hotspot auto est refusé par le téléphone, on démarre
     * QUAND MÊME la session sur le réseau WiFi courant — serveur + QR
     * (URL seule, pas d'identifiants WiFi connus) + découverte. Les deux
     * téléphones doivent être sur le même WiFi ; le récepteur a sinon
     * son bouton "Réglages WiFi". Aucun modal, aucune mise en attente.
     */
    private void fallbackDirect(String reason) {
        binding.btnShowQr.setEnabled(true);
        binding.textStatus.setText(
                "Hotspot tsy mety (" + reason
                        + ") : amin'ny WiFi iray ihany ianareo");
        beginServing(null, null, !binding.switchOpen.isChecked());
    }

    private void stopServer() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception ignored) {
            }
            server = null;
        }
        if (announcer != null) {
            announcer.stop();
        }
        TransferHotspot.stop();
        binding.btnShowQr.setVisibility(View.VISIBLE);
        binding.qrCard.setVisibility(View.GONE);
        updateSelectCount();
    }

    // =========================================================
    // SÉLECTION PERSISTANTE
    // =========================================================
    private void saveSelection() {
        SharedPreferences prefs =
                getSharedPreferences(PREFS_TRANSFER, Context.MODE_PRIVATE);
        prefs.edit().putStringSet(KEY_SELECTION, new HashSet<>(sendChecked)).apply();
    }

    private Set<String> loadSelection() {
        SharedPreferences prefs =
                getSharedPreferences(PREFS_TRANSFER, Context.MODE_PRIVATE);
        return prefs.getStringSet(KEY_SELECTION, null);
    }

    // =========================================================
    // 📲 ANDRAY : DÉCOVERTE AUTO DES SENDERS
    //  1) découverte réseau (UDP + TCP) de ce qui est déjà joignable ;
    //  2) si rien : scan WiFi des hotspots "BAIBOLY-…" ouverts voisins
    //     → connexion auto → nouvelle découverte → la liste s'affiche
    //     SANS scanner de QR. Les 🔒 demandent le QR (token secret).
    // =========================================================
    private void refreshDiscovery() {
        stopDiscovery();
        binding.progressSenders.setVisibility(View.VISIBLE);
        binding.textSendersEmpty.setVisibility(View.GONE);
        binding.recyclerSenders.setAdapter(null);
        binding.textSendersTitle.setText("🔍 Mikaroka ny mpandefa…");

        SenderDiscovery.discover(this, senders -> {
            if (!senders.isEmpty()) {
                presentSenders(senders);
                return;
            }
            // Rien sur le réseau courant : tenter de rejoindre un hotspot
            // "BAIBOLY-*" ouvert à proximité (nécessite la permission).
            if (!canScoutNetworks()) {
                presentSenders(senders);
                requestScoutPerms();
                return;
            }
            runOnUiThread(() -> binding.textSendersTitle
                    .setText("📶 Mikaroka ny WiFi Baiboly manodidina…"));
            scoutAndJoin(() ->
                    SenderDiscovery.discover(this, this::presentSenders));
        });
    }

    /** Affiche (ou vide) la liste des senders trouvés. */
    private void presentSenders(List<SenderDiscovery.Sender> senders) {
        sendersFound.clear();
        sendersFound.addAll(senders);
        boolean empty = sendersFound.isEmpty();
        binding.progressSenders.setVisibility(View.GONE);
        binding.textSendersTitle.setText(empty
                ? "🔍 Tsy misy mpandefa hita"
                : "🔍 Mpandefa " + sendersFound.size() + " hita");
        binding.textSendersEmpty.setText(empty
                ? "Tsy maintsy ao amin'ny 'Alefa' ny namana ary amin'ny WiFi iray ihany ianareo."
                : "Tsy misy mpandefa hita : scan-eo ny QR.");
        binding.textSendersEmpty.setVisibility(
                empty ? View.VISIBLE : View.GONE);
        binding.recyclerSenders.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerSenders.setAdapter(new SendersAdapter());
    }

    /** Permission de lire les réseaux WiFi voisins (scan). */
    private boolean canScoutNetworks() {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(this,
                    "android.permission.NEARBY_WIFI_DEVICES")
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Demande discrète des permissions de scan si absentes. */
    private void requestScoutPerms() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            missing.add("android.permission.NEARBY_WIFI_DEVICES");
        } else {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        ActivityCompat.requestPermissions(this,
                missing.toArray(new String[0]), REQ_DISCOVER_PERMS);
    }

    /**
     * Scan les SSID "BAIBOLY-*" alentour (hotspots ouverts de l'app) et
     * se connecte au premier trouvé. {
     * Covenant appelle done().
     */
    private void scoutAndJoin(Runnable done) {
        new Thread(() -> {
            try {
                android.net.wifi.WifiManager wm =
                        (android.net.wifi.WifiManager)
                                getApplicationContext()
                                        .getSystemService(Context.WIFI_SERVICE);
                if (wm == null || !wm.isWifiEnabled()) {
                    runOnUiThread(done);
                    return;
                }
                try {
                    wm.startScan();
                } catch (Exception ignored) {
                }

                String candidate = null;
                long deadline = System.currentTimeMillis() + 8000;
                while (candidate == null
                        && System.currentTimeMillis() < deadline) {
                    // Demande de scan périodique (le premier peut être vide)
                    try {
                        //noinspection BusyWait
                        Thread.sleep(800);
                    } catch (InterruptedException e) {
                        runOnUiThread(done);
                        return;
                    }
                    try {
                        List<android.net.wifi.ScanResult> results =
                                wm.getScanResults();
                        for (android.net.wifi.ScanResult r : results) {
                            if (r.SSID != null
                                    && r.SSID.startsWith(HOTSPOT_PREFIX)) {
                                candidate = r.SSID;
                                break;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    if (candidate == null) {
                        try {
                            wm.startScan();
                        } catch (Exception ignored) {
                        }
                    }
                }

                String ssid = candidate;
                if (ssid == null || currentSsidEquals(ssid)) {
                    runOnUiThread(done);
                    return;
                }

                runOnUiThread(() -> binding.textSendersTitle.setText(
                        "📶 Mifandray amin'ny " + ssid + " …"));
                // Réseau ouvert de l'app : pas de mot de passe
                TransferWifiConnect.connect(this, ssid, null, wcResult -> {
                    if (wcResult != null && wcResult.connected) {
                        joinedBaibolySsid = ssid;
                    }
                    runOnUiThread(done);
                });
            } catch (Exception e) {
                runOnUiThread(done);
            }
        }, "baiboly-scout").start();
    }

    private void stopDiscovery() {
        SenderDiscovery.stop();
        binding.progressSenders.setVisibility(View.GONE);
    }

    // =========================================================
    // 📲 ANDRAY : SCAN QR → CONNECT WIFI → LISTE
    // =========================================================
    private void launchScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setCaptureActivity(PortraitCaptureActivity.class);
        integrator.setPrompt("Tondroy ny QR amin'ny findraman'ny namana");
        integrator.setBeepEnabled(false);
        integrator.initiateScan();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchScanner();
        } else if (requestCode == REQ_WIFI_PERMS) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startTransferSession();
            } else {
                binding.btnShowQr.setEnabled(true);
                Toast.makeText(this,
                        "Lalana (permission) WiFi ilaina ho an'ny hotspot",
                        Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_DISCOVER_PERMS) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                refreshDiscovery();
            } else {
                Toast.makeText(this,
                        "Lalana WiFi : tsy afaka mifandray ho azy amin'ny WiFi Baiboly",
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Tsy misy fahatalanjona amin'ny kamera",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(
                requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            handleScannedQr(result.getContents());
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * QR scanné : si les identifiants WiFi y sont → connexion auto au
     * réseau de transfert, puis récupération du manifest. Sinon direct.
     */
    private void handleScannedQr(String qrContent) {
        String[] parsed = TransferUtils.parseQr(qrContent);
        if (parsed != null && parsed[2] != null
                && TransferClient.parseTransferUrl(qrContent) != null) {
            String ssid = parsed[0];
            String wifiPass = parsed[1];
            String url = parsed[2];

            if (ssid != null && !ssid.isEmpty() && !currentSsidEquals(ssid)) {
                binding.textReceiveState.setText(
                        "📶 Mifandray amin'ny WiFi " + ssid + "…");
                TransferWifiConnect.connect(this, ssid, wifiPass, wcResult -> {
                    fetchReceivedManifest(qrContent, url, wcResult.connected);
                });
                return;
            }
            fetchReceivedManifest(qrContent, url, true);
        } else {
            Toast.makeText(this, "QR tsy mety (URL hafo)",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** SSID actuel déjà bon ? (évite une reconnexion inutile) */
    private boolean currentSsidEquals(String ssid) {
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return false;
            android.net.wifi.WifiInfo info = wm.getConnectionInfo();
            return info != null && info.getSSID() != null
                    && info.getSSID().replace("\"", "")
                    .equalsIgnoreCase(ssid);
        } catch (Exception e) {
            return false;
        }
    }

    private void fetchReceivedManifest(String qrContent, String url,
                                       boolean connectedOk) {
        if (!connectedOk) {
            binding.textReceiveState.setText(
                    "📶 Tsy nety ny WiFi : sokafy ny fikirana WiFi, mifidiana ny "
                            + "WiFi'n'ny namana, dia andramo indray");
            return;
        }

        String manifestUrl = TransferClient.manifestUrl(url);
        if (manifestUrl == null) {
            Toast.makeText(this, "QR tsy mety (URL fohy loatra)",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        receivedQr = qrContent;
        binding.textReceiveState.setText("Mikaroka… " + manifestUrl);

        new Thread(() -> {
            TransferClient.Manifest manifest = null;
            Exception error = null;
            for (int attempt = 0; attempt < 3 && manifest == null; attempt++) {
                try {
                    manifest = TransferClient.fetchManifest(manifestUrl);
                } catch (Exception e) {
                    error = e;
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {
                        return;
                    }
                }
            }
            TransferClient.Manifest m = manifest;
            Exception e = error;
            runOnUiThread(() -> {
                if (m != null) {
                    showReceivedManifest(m);
                } else {
                    binding.textReceiveState.setText(
                            "Tsy neken'ny namana : "
                                    + (e == null ? "tsy fantatra" : e.getMessage()));
                }
            });
        }).start();
    }

    private void showReceivedManifest(TransferClient.Manifest manifest) {
        receivedManifest = manifest;
        receivedChecked.clear();
        for (int i = 0; i < manifest.items.size(); i++) {
            receivedChecked.add(i);
        }
        binding.textReceiveState.setText(
                "Misy " + manifest.items.size() + " avy amin'ny "
                        + manifest.deviceName
                        + (manifest.secured ? " 🔒" : " 🔓"));
        binding.recyclerReceive.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerReceive.setAdapter(new ReceiveAdapter());
        binding.btnDownloadSelected.setVisibility(
                manifest.items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // =========================================================
    // 📲 ANDRAY : TÉLÉCHARGER + IMPORTER
    // =========================================================
    private void downloadSelectedItems() {
        if (receivedManifest == null) return;

        binding.btnDownloadSelected.setEnabled(false);
        binding.textReceiveState.setText("Mihakatra…");

        new Thread(() -> {
            int ok = 0;
            List<String> fails = new ArrayList<>();
            StringBuilder progress = new StringBuilder();

            File tempDir = ServerManifestClient.downloadsDir(this);
            String[] parsedUrl = TransferClient.parseTransferUrl(receivedQr);
            String base = parsedUrl[0];
            String tokenValue = parsedUrl[1];
            String tokenParam = tokenValue == null ? "" : "t=" + tokenValue + "&";

            for (Integer idxObj : receivedChecked) {
                int idx = idxObj;
                if (idx < 0 || idx >= receivedManifest.items.size()) continue;
                TransferClient.ReceivedItem it = receivedManifest.items.get(idx);
                String label = it.title == null || it.title.isEmpty()
                        ? it.type : it.title;

                try {
                    File downloaded = TransferClient.downloadItem(
                            base + tokenParam + "i=" + idx,
                            label, tempDir);

                    if (it.isProgram()) {
                        JSONObject json = new JSONObject(readAsText(downloaded));
                        String imported = TransferData.importProgram(this, json);
                        //noinspection ResultOfMethodCallIgnored
                        downloaded.delete();
                        ok++;
                        progress.append("✓ ").append(imported).append("\n");
                    } else {
                        String ext = (it.ext != null && !it.ext.isEmpty())
                                ? it.ext : "mp3";
                        File renamed = new File(downloaded.getParentFile(),
                                downloaded.getName().replaceAll("\\.mp3$", "." + ext));
                        if (!renamed.equals(downloaded)) {
                            //noinspection ResultOfMethodCallIgnored
                            downloaded.renameTo(renamed);
                            downloaded = renamed;
                        }
                        String imported = TransferData.importPlayback(
                                this, downloaded, it.category, it.number,
                                it.playTitle, it.label);
                        //noinspection ResultOfMethodCallIgnored
                        downloaded.delete();
                        ok++;
                        progress.append("✓ ").append(imported).append("\n");
                    }
                } catch (Exception ex) {
                    fails.add(label);
                    progress.append("✗ ").append(label).append("\n");
                }

                String status = "… " + (ok + fails.size()) + "/"
                        + receivedManifest.items.size();
                runOnUiThread(() -> binding.textReceiveState.setText(status));
            }

            String finalProgress = progress.toString();
            final int finalOk = ok;
            runOnUiThread(() -> {
                binding.btnDownloadSelected.setEnabled(true);
                binding.textReceiveState.setText(
                        (finalOk > 0 ? "Voaray : " + finalProgress : "")
                                + (fails.isEmpty() ? "Voaray daholo ✓"
                                : "Nisy " + fails.size() + " tsy nety")
                                + "\n📶 Miverina amin'ny WiFi mahazatra…");
                Toast.makeText(this,
                        finalOk + " voaray, " + fails.size() + " tsy nety",
                        Toast.LENGTH_SHORT).show();
                // Le transfert est fini : on libère le réseau de transfert
                cleanupTransferNetwork();
            });
        }).start();
    }

    /**
     * Transfert terminé : on retire la suggestion du réseau Baiboly
     * (créée par le scan QR). Android repart automatiquement sur le WiFi
     * d'origine — le récepteur n'est déconnecté que le temps de l'envoi.
     */
    private void cleanupTransferNetwork() {
        if (android.os.Build.VERSION.SDK_INT < 29) return;
        // Identifiants du réseau de transfert : du QR scanné, ou du scan
        // auto des hotspots BAIBOLY-\* (réseau ouvert).
        String ssid = null;
        String pass = null;
        try {
            String[] parsed = receivedQr == null
                    ? null : TransferUtils.parseQr(receivedQr);
            if (parsed != null && parsed[0] != null) {
                ssid = parsed[0];
                pass = parsed[1];
            }
        } catch (Exception ignored) {
        }
        if (ssid == null && joinedBaibolySsid != null) {
            ssid = joinedBaibolySsid;
            pass = null;
        }
        if (ssid == null) return;

        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                // Retire UNIQUEMENT la suggestion de l'app (jamais les
                // réseaux connus de l'utilisateur) : le WiFi d'origine revient.
                android.net.wifi.WifiNetworkSuggestion.Builder b =
                        new android.net.wifi.WifiNetworkSuggestion.Builder()
                                .setSsid(ssid);
                if (pass != null && !pass.isEmpty()) {
                    b.setWpa2Passphrase(pass);
                }
                wm.removeNetworkSuggestions(
                        java.util.Collections.singletonList(b.build()));
            }
        } catch (Exception ignored) {
        }
    }

    private static String readAsText(File f) throws Exception {
        java.io.FileInputStream fis = new java.io.FileInputStream(f);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = fis.read(buffer)) > 0) bos.write(buffer, 0, len);
        fis.close();
        return bos.toString("UTF-8");
    }

    // =========================================================
    // ADAPTERS (send / receive / senders)
    // =========================================================
    class SendAdapter extends RecyclerView.Adapter<SendAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transfer_select, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            TransferItem it = filteredSendItems.get(position);
            boolean isProgram = TransferItem.TYPE_PROGRAM.equals(it.type);
            String key = it.key();

            holder.itemIcon.setImageResource(isProgram
                    ? R.drawable.ic_dashboard_black_24dp
                    : R.drawable.ic_music_note);
            holder.textTitle.setText(it.title);
            holder.textSubtitle.setText(isProgram
                    ? it.subtitle
                    : it.category.toUpperCase() + " " + it.number + " · " + it.subtitle);
            holder.checkItem.setOnCheckedChangeListener(null);
            holder.checkItem.setChecked(sendChecked.contains(key));
            holder.checkItem.setOnCheckedChangeListener((b, c) -> {
                if (c) sendChecked.add(key);
                else sendChecked.remove(key);
                updateSelectCount();
                updateServerSelection();
                saveSelection();
            });
        }

        @Override
        public int getItemCount() {
            return filteredSendItems.size();
        }

        class VH extends RecyclerView.ViewHolder {
            CheckBox checkItem;
            ImageView itemIcon;
            TextView textTitle, textSubtitle;

            VH(View view) {
                super(view);
                checkItem = view.findViewById(R.id.checkItem);
                itemIcon = view.findViewById(R.id.itemIcon);
                textTitle = view.findViewById(R.id.textTitle);
                textSubtitle = view.findViewById(R.id.textSubtitle);
            }
        }
    }

    class ReceiveAdapter extends RecyclerView.Adapter<ReceiveAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transfer_select, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            if (receivedManifest == null
                    || position >= receivedManifest.items.size()) return;
            TransferClient.ReceivedItem it = receivedManifest.items.get(position);
            boolean isProgram = it.isProgram();

            holder.itemIcon.setImageResource(isProgram
                    ? R.drawable.ic_dashboard_black_24dp
                    : R.drawable.ic_music_note);
            holder.textTitle.setText(it.title);
            holder.textSubtitle.setText(isProgram
                    ? "Fandaharana"
                    : TransferData.humanSize(it.size) + " · "
                    + it.category.toUpperCase() + " " + it.number);
            holder.checkItem.setOnCheckedChangeListener(null);
            holder.checkItem.setChecked(receivedChecked.contains(position));
            holder.checkItem.setOnCheckedChangeListener((b, c) -> {
                if (c) receivedChecked.add(position);
                else receivedChecked.remove(position);
            });
        }

        @Override
        public int getItemCount() {
            return receivedManifest == null ? 0 : receivedManifest.items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            CheckBox checkItem;
            ImageView itemIcon;
            TextView textTitle, textSubtitle;

            VH(View view) {
                super(view);
                checkItem = view.findViewById(R.id.checkItem);
                itemIcon = view.findViewById(R.id.itemIcon);
                textTitle = view.findViewById(R.id.textTitle);
                textSubtitle = view.findViewById(R.id.textSubtitle);
            }
        }
    }

    class SendersAdapter extends RecyclerView.Adapter<SendersAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_sender, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            if (position >= sendersFound.size()) return;
            SenderDiscovery.Sender s = sendersFound.get(position);

            holder.textSenderLock.setText(s.secured ? "🔒" : "🔓");
            holder.textSenderName.setText(s.device);
            holder.textSenderSub.setText(s.secured
                    ? "Misy teny miafina : mila scan-ena ny QR"
                    : "Misokatra : azo tsindriana mivantana");

            holder.itemView.setOnClickListener(v -> {
                if (s.secured) {
                    binding.textReceiveState.setText(
                            "🔒 " + s.device + " : scan-eo ny QR'n'ilay namana");
                    launchScanner();
                } else {
                    connectToOpenSender(s);
                }
            });
        }

        @Override
        public int getItemCount() {
            return sendersFound.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView textSenderLock, textSenderName, textSenderSub;

            VH(View view) {
                super(view);
                textSenderLock = view.findViewById(R.id.textSenderLock);
                textSenderName = view.findViewById(R.id.textSenderName);
                textSenderSub = view.findViewById(R.id.textSenderSub);
            }
        }
    }

    /** Sender ouvert : connexion directement, sans QR ni token. */
    private void connectToOpenSender(SenderDiscovery.Sender s) {
        binding.textReceiveState.setText("Mifandray amin'i " + s.device + "…");
        handleScannedQr(s.url() + "?");
    }
}
