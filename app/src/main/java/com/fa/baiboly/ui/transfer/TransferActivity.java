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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
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
 * Transfert WiFi local de programmes (fandaharana) et playbacks — REFONTE :
 *
 * 🧭 UN SEUL BOUTON FLOTTANT : ouvre la fenêtre de choix
 *    Handefa (QR direct) / Handray (recevoir).
 *
 * ✈️ HANDEFA : démarre DIRECTEMENT le partage de connexion + le QR dans
 *    une FENÊTRE DÉDIÉE (dialog_transfer_qr) :
 *    • bande 2,4 GHz par défaut, changeable en 5 GHz (QR violet) TANT QUE
 *      personne n'a scanné ;
 *    • dès qu'un appareil se connecte : bande + ouverture VERROUILLÉES,
 *      la session continue (d'autres peuvent encore rejoindre) ;
 *    • la liste reste accessible : sélection + groupement par sokajy,
 *      le serveur reflète le choix en direct.
 *
 * 📲 HANDRAY : liste auto des senders (🔓 direct, 🔒 scan QR) ou scan du
 *    QR → connexion AUTOMATIQUE et fiable au WiFi du partage
 *    (WifiNetworkSpecifier, Android 10+) → téléchargement + import.
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

    /** 🎨 Couleurs du QR : 2,4 GHz = encre foncée, 5 GHz = encre violette. */
    private static final int QR_INK_2_4GHZ = 0xFF263238;
    private static final int QR_INK_5GHZ = 0xFF7B1FA2;

    /** Préfixe des hotspots créés par l'app : {@link TransferHotspot#SSID_PREFIX}.
     *  Les deux formes ("BAIBOLY_SHARE_xxx" et l'ancien "BAIBOLY-xxx")
     *  sont reconnues par {@link TransferHotspot#isBaibolySsid}. */
    private static boolean isBaibolyNetwork(SenderDiscovery.Sender s) {
        return s != null && s.isWifiNetwork()
                && TransferHotspot.isBaibolySsid(s.ssid);
    }

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

    // 🧭 REFONTE : réglages verrouillables de la session
    /** Bande du hotspot : 2,4 GHz par défaut, changeable avant le scan. */
    private int currentBand = TransferHotspot.BAND_2_4GHZ;
    /** false = hotspot WPA2 (défaut), true = réseau ouvert. */
    private boolean sessionOpen;
    /** true dès qu'un appareil s'est connecté : les réglages se verrouillent. */
    private boolean sessionLocked;
    /** true pendant la recréation du hotspot (changement de bande/ouverture). */
    private boolean sessionRestarting;
    /** true pendant la création du hotspot (empêche un double démarrage). */
    private boolean sessionStarting;
    private String sessionSsid;
    private String sessionPass;
    private Bitmap currentQr;

    // 🤳 Fenêtre dédiée du QR
    private AlertDialog qrDialog;
    private View dialogRoot;
    private ImageView dialogQr;
    private Chip dialogChip24;
    private Chip dialogChip5;
    private SwitchMaterial dialogSwitchOpen;
    private TextView dialogState;
    private TextView dialogBandLock;
    private TextView dialogQrPassword;
    private TextView dialogQrInfo;

    // RECEIVE
    private TransferClient.Manifest receivedManifest;
    private String receivedQr;
    /** Hotspot Baiboly rejoint via le scan auto (pour nettoyage après). */
    private String joinedBaibolySsid;

    // =========================================================
    // 📊 FILE D'ATTENTE (envoi ET réception)
    // =========================================================
    /** Une ligne de la file : titre + statut + progression. */
    public static class ProgressEntry {
        static final int WAITING = 0;
        static final int RUNNING = 1;
        static final int DONE = 2;
        static final int FAILED = 3;
        static final int CANCELLED = 4;

        public final String title;
        public final String subtitle;
        public int status = WAITING;
        public int progress;
        /** Côté réception : index de l'item dans le manifest. */
        public int index = -1;
        /** Côté émission : clé stable de l'item. */
        public String key;

        ProgressEntry(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle == null ? "" : subtitle;
        }
    }

    /** File côté émission (ce qui part) / côté réception (ce qui arrive). */
    private final List<ProgressEntry> sendQueue = new ArrayList<>();
    private final List<ProgressEntry> receiveQueue = new ArrayList<>();
    /** Index de téléchargements annulés par l'utilisateur. */
    private final Set<Integer> receiveCancelled = new HashSet<>();
    private final List<SenderDiscovery.Sender> sendersFound = new ArrayList<>();

    // Découverte en 2 temps : senders du réseau courant + réseaux Baiboly scannés
    private int discoveryGeneration;
    private int discoveryStepsLeft;
    private List<SenderDiscovery.Sender> pendingSenders;
    private List<SenderDiscovery.Sender> pendingNetworks;

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

        // 🧭 UN SEUL BOUTON FLOTTANT : fenêtre de choix Handefa / Handray
        binding.fabNav.setOnClickListener(v -> showNavChooser());

        // 🔴 Session : revoir le QR / arrêter
        binding.btnViewQr.setOnClickListener(v -> showQrDialog());
        binding.btnStopServer.setOnClickListener(v -> stopServer());

        // Sélection rapide
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

        // Andray
        binding.btnScan.setOnClickListener(v -> launchScanner());
        binding.btnRefreshSenders.setOnClickListener(v -> refreshDiscovery());
        binding.btnJoinWifi.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));

        // 🧭 NAVIGATION BASSE (même principe que l'accueil)
        binding.navTransfer.setOnItemSelectedListener(item -> {
            if (navInternal) return true;
            int id = item.getItemId();
            if (id == R.id.nav_send) selectNav(NAV_SEND);
            else if (id == R.id.nav_progress) selectNav(NAV_PROGRESS);
            else if (id == R.id.nav_receive) selectNav(NAV_RECEIVE);
            return true;
        });

        selectNav(NAV_SEND);
    }

    // =========================================================
    // 🧭 NAVIGATION : HANDEFA / EN COURS / HANDRAY
    // =========================================================
    private static final int NAV_SEND = 0;
    private static final int NAV_PROGRESS = 1;
    private static final int NAV_RECEIVE = 2;

    private int navIndex = NAV_SEND;
    /** Empêche la boucle selectNav ⇄ setSelectedItemId. */
    private boolean navInternal;

    private void selectNav(int index) {
        boolean enteringReceive = index == NAV_RECEIVE && navIndex != NAV_RECEIVE;
        boolean leavingReceive = navIndex == NAV_RECEIVE && index != NAV_RECEIVE;

        navIndex = index;
        // "En cours" garde le côté courant ; seuls les 2 bouts changent le sens.
        if (index == NAV_SEND) sendMode = true;
        else if (index == NAV_RECEIVE) sendMode = false;

        binding.sendPanel.setVisibility(index == NAV_SEND ? View.VISIBLE : View.GONE);
        binding.progressPanel.setVisibility(index == NAV_PROGRESS ? View.VISIBLE : View.GONE);
        binding.receivePanel.setVisibility(index == NAV_RECEIVE ? View.VISIBLE : View.GONE);

        int menuId = index == NAV_SEND ? R.id.nav_send
                : index == NAV_PROGRESS ? R.id.nav_progress : R.id.nav_receive;
        if (binding.navTransfer.getSelectedItemId() != menuId) {
            navInternal = true;
            binding.navTransfer.setSelectedItemId(menuId);
            navInternal = false;
        }

        if (leavingReceive) stopDiscovery();

        if (index == NAV_SEND && sendItems.isEmpty()) loadSendList();

        if (enteringReceive) {
            receivedManifest = null;
            binding.textReceiveState.setText(
                    getString(R.string.transfer_receive_initial_state));
            refreshDiscovery();
        }

        if (index == NAV_PROGRESS) renderProgress();
    }

    // =========================================================
    // 🧭 UN SEUL BOUTON FLOTTANT → fenêtre de choix Handefa / Handray
    // =========================================================
    private void showNavChooser() {
        if (qrDialog != null && qrDialog.isShowing()) return;

        boolean sessionRunning = server != null;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.transfer_chooser_title))
                .setItems(new CharSequence[]{
                        getString(sessionRunning
                                ? R.string.transfer_chooser_send_resume
                                : R.string.transfer_chooser_send),
                        getString(R.string.transfer_chooser_receive)
                }, (d, which) -> {
                    if (which == 0) {
                        if (server != null) showQrDialog();
                        else startTransferSession();
                    } else {
                        selectNav(NAV_RECEIVE);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void applyGlobalTheme() {
        SharedPreferences prefs =
                getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        int savedMode = prefs.getInt("theme_mode",
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);
        ColorManager.applyTheme(this);
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
    // ✈️ ALEFA : LISTE ÉPURÉE (sélection + groupement, rien d'autre)
    // =========================================================
    private void loadSendList() {
        binding.textStatus.setText(getString(R.string.transfer_loading_list));
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
                    binding.textStatus.setText(getString(R.string.transfer_skipped_one));
                } else if (skippedCount > 1) {
                    binding.textStatus.setText(getString(R.string.transfer_skipped_multiple, skippedCount));
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
        addCategoryChip(CAT_ALL, getString(R.string.transfer_cat_all, sendItems.size()));
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
        String offered = server != null ? getString(R.string.transfer_offered_suffix) : "";
        binding.textSelectCount.setText(getString(R.string.transfer_select_count_fmt, sendChecked.size(), offered));
        binding.textSelectCount.requestLayout();
    }

    /** Sélection courante (items dont la clé est cochée). */
    private List<TransferItem> currentSelection() {
        List<TransferItem> list = new ArrayList<>();
        for (TransferItem it : sendItems) {
            if (sendChecked.contains(it.key())) list.add(it);
        }
        return list;
    }

    /** Sélection modifiée pendant la session → serveur + file mis à jour. */
    private void updateServerSelection() {
        if (server != null) {
            server.setItems(currentSelection());
            syncSendQueue();
        }
    }

    /**
     * La file d'émission suit la sélection : les éléments retirés disparaissent,
     * les statuts déjà atteints sont conservés.
     */
    private void syncSendQueue() {
        List<TransferItem> selection = currentSelection();
        List<ProgressEntry> next = new ArrayList<>();
        for (TransferItem it : selection) {
            ProgressEntry found = null;
            for (ProgressEntry e : sendQueue) {
                if (it.key().equals(e.key)) {
                    found = e;
                    break;
                }
            }
            if (found == null) {
                found = new ProgressEntry(it.title, it.subtitle);
                found.key = it.key();
            }
            next.add(found);
        }
        sendQueue.clear();
        sendQueue.addAll(next);
        renderProgress();
    }

    /** Un item a réellement été servi (ou a échoué) : la file le montre. */
    private void onItemServed(int index, boolean ok) {
        String key = server == null ? null : server.itemKeyAt(index);
        if (key == null) return;
        for (ProgressEntry e : sendQueue) {
            if (key.equals(e.key)) {
                if (e.status != ProgressEntry.CANCELLED) {
                    e.status = ok ? ProgressEntry.DONE : ProgressEntry.FAILED;
                    if (ok) e.progress = 100;
                }
                break;
            }
        }
        renderProgress();
    }

    // =========================================================
    // 🤳 FENÊTRE DÉDIÉE DU QR (Handefa)
    // =========================================================

    /**
     * ✈️ HANDEFA : démarre DIRECTEMENT le partage + QR.
     * La bande est l'état courant (2,4 GHz par défaut) et le mode ouvert
     * l'état courant du switch.
     */
    private void startTransferSession() {
        if (server != null) {
            showQrDialog();
            return;
        }
        if (sessionStarting) return;

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

        if (currentBand == TransferHotspot.BAND_5GHZ
                && !TransferHotspot.is5GHzSupported(this)) {
            currentBand = TransferHotspot.BAND_2_4GHZ;
        }
        boolean secured = !sessionOpen;

        sessionStarting = true;
        binding.textStatus.setText(getString(R.string.transfer_creating_hotspot));

        TransferHotspot.start(this, secured, currentBand, new TransferHotspot.Listener() {
            @Override
            public void onStarted(String ssid, String pass, boolean apSecured) {
                runOnUiThread(() -> beginServing(ssid, pass, apSecured));
            }

            @Override
            public void onManualFallback(String reason) {
                runOnUiThread(() -> {
                    sessionStarting = false;
                    // Si le téléphone vient d'imposer un mot de passe au
                    // point d'accès, le switch "Misokatra" devient faux.
                    applyOpenSwitchAvailability();
                    fallbackDirect(reason);
                });
            }
        });
    }

    /** Changement du réseau de transfert (bande ou ouverture) pendant la session. */
    private void restartSession() {
        if (sessionLocked) return;
        sessionRestarting = true;
        stopServer();
        binding.textStatus.setText(getString(R.string.transfer_rebuilding));
        if (dialogState != null) dialogState.setText(R.string.transfer_rebuilding);
        startTransferSession();
    }

    /** Hotspot prêt (auto ou manuel) : serveur + annonce + QR. */
    private void beginServing(String ssid, String pass, boolean secured) {
        sessionSsid = ssid;
        sessionPass = pass;
        sessionOpen = !secured;

        token = TransferUtils.newToken();
        final boolean requireToken = secured;
        final List<TransferItem> initial = currentSelection();

        new Thread(() -> {
            // ⚠️ L'interface du hotspot (ap*/swlan*) n'a pas encore son
            // adresse au moment du callback : sans attente on construisait
            // "http://null:8619" dans le QR. On relit jusqu'à ce que l'IP
            // arrive, puis on monte le serveur.
            String ip = TransferUtils.localIpAddress(TransferActivity.this, 8000);

            TransferServer started = null;
            int chosenPort = -1;
            String base = null;
            if (ip != null) {
                for (int port = PORT_MIN; port <= PORT_MAX && started == null; port++) {
                    try {
                        TransferServer s = new TransferServer(
                                port, token, Build.MODEL,
                                initial, TransferActivity.this, requireToken);
                        s.startDaemon();
                        started = s;
                        chosenPort = port;
                        base = "http://" + ip + ":" + port;
                    } catch (Exception ignored) {
                    }
                }
            }
            final TransferServer srv = started;
            final int boundPort = chosenPort;
            final String urlBase = base;
            runOnUiThread(() -> onSessionReady(srv, boundPort, urlBase,
                    ssid, pass, requireToken));
        }, "transfer-session").start();
    }

    /** Serveur démarré (ou échec) : construction du QR + fenêtre dédiée. */
    private void onSessionReady(TransferServer started, int port, String urlBase,
                                String ssid, String pass, boolean requireToken) {
        if (started == null || urlBase == null) {
            TransferHotspot.stop();
            sessionStarting = false;
            sessionRestarting = false;
            String msg = getString(urlBase == null
                    ? R.string.transfer_url_error
                    : R.string.transfer_server_error);
            binding.textStatus.setText(msg);
            if (dialogState != null) dialogState.setText(msg);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            return;
        }

        server = started;
        serverPort = port;
        qrUrlBase = urlBase;

        if (announcer == null) announcer = new SenderAnnouncer();
        announcer.announce(serverPort, Build.MODEL, requireToken);

        String qr = TransferUtils.qrContent(
                urlBase + "/q?t=" + token, ssid, pass);
        currentQr = TransferUtils.qrBitmap(qr, 600, qrInkColor());
        if (currentQr == null) {
            sessionStarting = false;
            stopServer();
            binding.textStatus.setText(getString(R.string.transfer_server_error));
            Toast.makeText(this, getString(R.string.transfer_server_error),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // La file d'émission : chaque élément servi passe en "Vita"
        started.setServeListener((index, ok) ->
                runOnUiThread(() -> onItemServed(index, ok)));
        // 🔒 Premier appareil connecté → réglages verrouillés
        started.setClientListener(count ->
                runOnUiThread(() -> onFirstClient(count)));
        syncSendQueue();

        String openText = pass == null
                ? getString(R.string.transfer_info_open)
                : getString(R.string.transfer_info_secured, ssid);
        binding.textServerInfo.setText(
                getString(R.string.transfer_server_info_fmt,
                        Build.MODEL, urlBase, openText,
                        getString(R.string.transfer_info_list_ready)));
        binding.sessionCard.setVisibility(View.VISIBLE);

        binding.textStatus.setText("");
        sessionStarting = false;
        sessionRestarting = false;

        updateSelectCount();
        showQrDialog();
        selectNav(NAV_PROGRESS);
    }

    /**
     * 🔐 Premier appareil connecté à la session : la bande 2,4/5 GHz et
     * l'ouverture se verrouillent (on ne peut plus détruire/reconstruire
     * le réseau) — d'autres personnes peuvent toujours rejoindre.
     */
    private void onFirstClient(int count) {
        if (count <= 0 || sessionLocked) return;
        sessionLocked = true;
        if (dialogBandLock != null) {
            dialogBandLock.setText(R.string.transfer_band_locked);
            dialogBandLock.setTextColor(
                    ContextCompat.getColor(this, R.color.green));
        }
        if (dialogChip24 != null) dialogChip24.setEnabled(false);
        if (dialogChip5 != null) dialogChip5.setEnabled(false);
        if (dialogSwitchOpen != null) dialogSwitchOpen.setEnabled(false);
    }

    /** 🎨 Couleur du QR : 5 GHz = violet, 2,4 GHz = encre foncée. */
    private int qrInkColor() {
        if (currentBand == TransferHotspot.BAND_5GHZ
                && TransferHotspot.is5GHzSupported(this)) {
            return QR_INK_5GHZ;
        }
        return QR_INK_2_4GHZ;
    }

    /**
     * Ouvre (ou met à jour) la fenêtre dédiée : QR coloré, bande 2,4/5,
     * switch "Misokatra", infos SSID/mot de passe/URL.
     */
    private void showQrDialog() {
        if (dialogRoot == null) {
            dialogRoot = LayoutInflater.from(this)
                    .inflate(R.layout.dialog_transfer_qr, null);
            dialogQr = dialogRoot.findViewById(R.id.qrDialogImage);
            dialogChip24 = dialogRoot.findViewById(R.id.chipBand24);
            dialogChip5 = dialogRoot.findViewById(R.id.chipBand5);
            dialogSwitchOpen = dialogRoot.findViewById(R.id.switchOpen);
            dialogState = dialogRoot.findViewById(R.id.textQrDialogState);
            dialogBandLock = dialogRoot.findViewById(R.id.textBandLock);
            dialogQrPassword = dialogRoot.findViewById(R.id.textQrPassword);
            dialogQrInfo = dialogRoot.findViewById(R.id.textQrInfo);

            boolean fiveOk = TransferHotspot.is5GHzSupported(this);
            dialogChip5.setEnabled(fiveOk);

            attachDialogListeners();

            qrDialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.transfer_qr_title))
                    .setView(dialogRoot)
                    .setPositiveButton(getString(R.string.transfer_close), null)
                    .setNegativeButton(getString(R.string.transfer_stop_session),
                            (d, w) -> stopServer())
                    .create();
        }
        refreshQrDialogContent();
        qrDialog.show();
    }

    /** Remplit la fenêtre QR avec l'état courant de la session. */
    private void refreshQrDialogContent() {
        if (dialogRoot == null) return;

        dialogChip24.setOnCheckedChangeListener(null);
        dialogChip5.setOnCheckedChangeListener(null);
        dialogChip24.setChecked(
                currentBand != TransferHotspot.BAND_5GHZ);
        dialogChip5.setChecked(
                currentBand == TransferHotspot.BAND_5GHZ
                        && TransferHotspot.is5GHzSupported(this));
        dialogSwitchOpen.setOnCheckedChangeListener(null);
        dialogSwitchOpen.setChecked(sessionOpen);

        if (currentQr != null) {
            dialogQr.setImageBitmap(currentQr);
            dialogQr.setVisibility(View.VISIBLE);
            dialogState.setText("");
        } else {
            dialogState.setText(
                    server == null
                            ? getString(R.string.transfer_creating_hotspot)
                            : getString(R.string.transfer_server_error));
        }

        if (sessionLocked) {
            dialogChip24.setEnabled(false);
            dialogChip5.setEnabled(false);
            dialogSwitchOpen.setEnabled(false);
        } else {
            dialogChip24.setEnabled(true);
            dialogChip5.setEnabled(TransferHotspot.is5GHzSupported(this));
            dialogSwitchOpen.setEnabled(TransferHotspot.isOpenSupported());
        }
        dialogBandLock.setText(sessionLocked
                ? R.string.transfer_band_locked
                : R.string.transfer_band_changeable);

        // 🔑 Identifiants du réseau, à taper à la main si besoin
        String ssid = sessionSsid;
        if (server == null) {
            dialogQrPassword.setText("");
            dialogQrInfo.setText("");
        } else if (ssid == null) {
            dialogQrPassword.setText(R.string.transfer_qr_no_hotspot);
        } else if (sessionPass == null || sessionPass.isEmpty()) {
            dialogQrPassword.setText(
                    getString(R.string.transfer_qr_wifi_open, ssid));
        } else {
            dialogQrPassword.setText(
                    getString(R.string.transfer_qr_wifi_pass, ssid, sessionPass));
        }
        if (server != null) {
            String openText = sessionPass == null
                    ? getString(R.string.transfer_info_open)
                    : getString(R.string.transfer_info_secured, ssid);
            dialogQrInfo.setText(
                    getString(R.string.transfer_server_info_fmt,
                            Build.MODEL, qrUrlBase, openText, ""));
        }

        attachDialogListeners();
    }

    /** Rattache les listeners de la fenêtre (après setChecked massif). */
    private void attachDialogListeners() {
        dialogChip24.setOnCheckedChangeListener((b, checked) -> {
            if (checked && currentBand != TransferHotspot.BAND_2_4GHZ) {
                currentBand = TransferHotspot.BAND_2_4GHZ;
                if (server != null && !sessionLocked) restartSession();
                else refreshQrDialogContent();
            }
        });
        dialogChip5.setOnCheckedChangeListener((b, checked) -> {
            if (checked && currentBand != TransferHotspot.BAND_5GHZ
                    && TransferHotspot.is5GHzSupported(this)) {
                currentBand = TransferHotspot.BAND_5GHZ;
                if (server != null && !sessionLocked) restartSession();
                else refreshQrDialogContent();
            }
        });
        dialogSwitchOpen.setOnCheckedChangeListener((b, checked) -> {
            sessionOpen = checked;
            if (server != null && !sessionLocked) restartSession();
            else refreshQrDialogContent();
        });
    }

    /**
     * Ce téléphone impose-t-il un mot de passe au point d'accès ?
     * Auquel cas le switch "Misokatra" est grisé et décoché : il ne
     * pourrait de toute façon pas être honoré.
     */
    private void applyOpenSwitchAvailability() {
        if (TransferHotspot.isOpenSupported()) return;
        if (dialogSwitchOpen != null && dialogSwitchOpen.isEnabled()) {
            dialogSwitchOpen.setOnCheckedChangeListener(null);
            dialogSwitchOpen.setChecked(false);
            sessionOpen = false;
            dialogSwitchOpen.setEnabled(false);
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
        String msg = getString(R.string.transfer_fallback_direct, reason);
        binding.textStatus.setText(msg);
        if (dialogState != null) dialogState.setText(msg);
        sessionRestarting = false;
        beginServing(null, null, !sessionOpen);
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
        sessionLocked = false;
        currentQr = null;
        binding.sessionCard.setVisibility(View.GONE);
        updateSelectCount();
        if (!sessionRestarting && qrDialog != null) {
            qrDialog.dismiss();
        }
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
    //  2) EN PARALLÈLE : scan des réseaux WiFi "BAIBOLY_SHARE_*" voisins,
    //     même si on n'est connecté à AUCUN réseau — ils apparaissent
    //     dans la liste et un clic suffit :
    //       • réseau ouvert → connexion auto puis liste des senders ;
    //       • réseau protégé → scan du QR (il fournit le mot de passe)
    //         puis connexion WiFi automatique.
    // =========================================================
    private void refreshDiscovery() {
        stopDiscovery();
        binding.progressSenders.setVisibility(View.VISIBLE);
        binding.textSendersEmpty.setVisibility(View.GONE);
        binding.recyclerSenders.setAdapter(null);
        binding.textSendersTitle.setText(getString(R.string.transfer_searching_senders));

        final int gen = ++discoveryGeneration;
        boolean canScan = canScoutNetworks();
        if (!canScan) requestScoutPerms();

        pendingSenders = null;
        pendingNetworks = null;
        discoveryStepsLeft = canScan ? 2 : 1;

        SenderDiscovery.discover(this, senders -> {
            if (gen != discoveryGeneration) return;
            pendingSenders = senders;
            discoveryStepDone(gen);
        });

        if (canScan) {
            runOnUiThread(() -> {
                if (gen == discoveryGeneration) binding.textSendersTitle
                        .setText(getString(R.string.transfer_scouting_wifi));
            });
            SenderDiscovery.scanWifiNetworks(this, 7000, networks -> {
                if (gen != discoveryGeneration) return;
                pendingNetworks = networks;
                discoveryStepDone(gen);
            });
        }
    }

    /** Les 2 moitiés de la découverte arrivent → affichage progressif. */
    private void discoveryStepDone(int gen) {
        if (gen != discoveryGeneration) return;
        discoveryStepsLeft--;
        List<SenderDiscovery.Sender> merged = new ArrayList<>();
        if (pendingSenders != null) merged.addAll(pendingSenders);
        if (pendingNetworks != null) merged.addAll(pendingNetworks);
        // On n'affiche pas un écran vide alors que d'autres résultats
        // sont encore en cours — sauf si c'est tout ce qu'on aura.
        if (discoveryStepsLeft > 0 && merged.isEmpty()) return;
        presentSenders(merged);
    }

    /** Affiche (ou vide) la liste des senders trouvés. */
    private void presentSenders(List<SenderDiscovery.Sender> senders) {
        sendersFound.clear();
        sendersFound.addAll(senders);
        boolean empty = sendersFound.isEmpty();
        binding.progressSenders.setVisibility(View.GONE);
        binding.textSendersTitle.setText(empty
                ? getString(R.string.transfer_no_senders_found)
                : getString(R.string.transfer_senders_found_count, sendersFound.size()));
        binding.textSendersEmpty.setText(empty
                ? getString(R.string.transfer_senders_empty_help)
                : getString(R.string.transfer_senders_empty_scan));
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
     * Réseau Baiboly ouvert repéré dans la liste : connexion directe,
     * sans mot de passe ni QR, puis découverte des senders de ce réseau.
     */
    private void connectToWifiNetwork(SenderDiscovery.Sender s) {
        final String ssid = s.ssid;
        if (ssid == null) return;

        if (currentSsidEquals(ssid)) {
            joinedBaibolySsid = ssid;
            discoverOnCurrentNetwork();
            return;
        }

        binding.textReceiveState.setText(
                getString(R.string.transfer_connecting_to_ssid, ssid));
        TransferWifiConnect.connect(this, ssid, null, wcResult -> {
            if (wcResult != null && wcResult.connected) {
                joinedBaibolySsid = ssid;
                discoverOnCurrentNetwork();
            } else {
                binding.textReceiveState.setText(
                        getString(R.string.transfer_wifi_connect_failed));
            }
        });
    }

    /**
     * Déjà sur le bon réseau : on ne fait que chercher les senders de ce
     * réseau (rapide, sans relancer le scan WiFi).
     */
    private void discoverOnCurrentNetwork() {
        stopDiscovery();
        binding.progressSenders.setVisibility(View.VISIBLE);
        binding.textSendersEmpty.setVisibility(View.GONE);
        binding.recyclerSenders.setAdapter(null);
        binding.textSendersTitle.setText(getString(R.string.transfer_searching_senders));

        final int gen = ++discoveryGeneration;
        pendingSenders = null;
        pendingNetworks = null;
        discoveryStepsLeft = 1;
        SenderDiscovery.discover(this, senders -> {
            if (gen != discoveryGeneration) return;
            pendingSenders = senders;
            discoveryStepDone(gen);
        });
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
        integrator.setPrompt(getString(R.string.transfer_scan_prompt));
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
                Toast.makeText(this,
                        getString(R.string.transfer_perm_wifi_hotspot),
                        Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_DISCOVER_PERMS) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                refreshDiscovery();
            } else {
                Toast.makeText(this,
                        getString(R.string.transfer_perm_wifi_disabled),
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, getString(R.string.transfer_perm_camera_denied),
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
     * QR scanné : connexion AUTOMATIQUE au WiFi du QR (identifiants dedans),
     * puis récupération automatique du manifest — même si Android n'a pas
     * encore confirmé la connexion, on tente le serveur de toute façon
     * (le réseau se rattare souvent 1–2 s plus tard).
     */
    private void handleScannedQr(String qrContent) {
        String[] parsed = TransferUtils.parseQr(qrContent);
        if (parsed == null || parsed[2] == null
                || TransferClient.parseTransferUrl(qrContent) == null) {
            Toast.makeText(this, getString(R.string.transfer_invalid_qr),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final String ssid = parsed[0];
        final String wifiPass = parsed[1];
        final String url = parsed[2];

        // Déjà sur le bon réseau (ou QR sans bloc WiFi) : rien à connecter.
        if (ssid == null || ssid.isEmpty() || currentSsidEquals(ssid)) {
            if (ssid != null && !ssid.isEmpty()) joinedBaibolySsid = ssid;
            fetchReceivedManifest(qrContent, url, true);
            return;
        }

        binding.textReceiveState.setText(
                getString(R.string.transfer_connecting_wifi_ssid, ssid));
        TransferWifiConnect.connect(this, ssid, wifiPass, wcResult -> {
            boolean ok = wcResult != null && wcResult.connected;
            if (ok) joinedBaibolySsid = ssid;
            fetchReceivedManifest(qrContent, url, ok);
        });
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
        String manifestUrl = TransferClient.manifestUrl(url);
        if (manifestUrl == null) {
            Toast.makeText(this, getString(R.string.transfer_invalid_qr),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        receivedQr = qrContent;
        binding.textReceiveState.setText(
                getString(R.string.transfer_fetching_manifest, manifestUrl));

        new Thread(() -> {
            TransferClient.Manifest manifest = null;
            Exception error = null;
            // On réessaie même si la connexion WiFi n'est pas encore
            // confirmée : le réseau arrive souvent juste après.
            for (int attempt = 0; attempt < 4 && manifest == null; attempt++) {
                try {
                    manifest = TransferClient.fetchManifest(manifestUrl);
                } catch (Exception e) {
                    error = e;
                    try {
                        Thread.sleep(1200);
                    } catch (InterruptedException ignored) {
                        return;
                    }
                }
            }
            final TransferClient.Manifest m = manifest;
            final Exception e = error;
            runOnUiThread(() -> {
                if (m != null) {
                    showReceivedManifest(m);
                } else if (!connectedOk) {
                    // Jamais connecté au réseau du QR → vrai problème WiFi
                    binding.textReceiveState.setText(
                            getString(R.string.transfer_wifi_connect_failed));
                } else {
                    binding.textReceiveState.setText(
                            getString(R.string.transfer_connection_refused,
                                    (e == null ? getString(R.string.transfer_unknown_error) : e.getMessage())));
                }
            });
        }).start();
    }

    /**
     * Manifest reçu → PLUS DE BOUTON : tout est mis dans la file et
     * téléchargé automatiquement. L'écran bascule sur "En cours" pour
     * montrer arriver chaque élément (avec annulation possible).
     */
    private void showReceivedManifest(TransferClient.Manifest manifest) {
        receivedManifest = manifest;
        receiveQueue.clear();
        receiveCancelled.clear();

        for (int i = 0; i < manifest.items.size(); i++) {
            TransferClient.ReceivedItem it = manifest.items.get(i);
            String label = it.title == null || it.title.isEmpty()
                    ? it.type : it.title;
            String sub = it.isProgram()
                    ? getString(R.string.transfer_program_type_label)
                    : TransferData.humanSize(it.size) + " · "
                    + it.category.toUpperCase() + " " + it.number;
            ProgressEntry entry = new ProgressEntry(label, sub);
            entry.index = i;
            receiveQueue.add(entry);
        }

        binding.textReceiveState.setText(
                getString(R.string.transfer_received_manifest_summary,
                        manifest.items.size(), manifest.deviceName,
                        manifest.secured ? getString(R.string.transfer_secured_tag)
                                : getString(R.string.transfer_open_tag)));

        sendMode = false;
        selectNav(NAV_PROGRESS);
        binding.textReceiveState.setText(getString(R.string.transfer_auto_receiving));
        renderProgress();
        startReceiveQueue();
    }

    // =========================================================
    // 📲 ANDRAY : RÉCEPTION AUTOMATIQUE + IMPORT
    // =========================================================
    private void startReceiveQueue() {
        if (receivedManifest == null || receivedQr == null) return;

        final TransferClient.Manifest manifest = receivedManifest;
        final String qr = receivedQr;

        new Thread(() -> {
            String[] parsedUrl = TransferClient.parseTransferUrl(qr);
            if (parsedUrl == null) {
                runOnUiThread(this::onReceiveQueueFinished);
                return;
            }
            String base = parsedUrl[0];
            String tokenValue = parsedUrl[1];
            String tokenParam = tokenValue == null ? "" : "t=" + tokenValue + "&";
            File tempDir = ServerManifestClient.downloadsDir(TransferActivity.this);

            for (int i = 0; i < receiveQueue.size(); i++) {
                final ProgressEntry entry = receiveQueue.get(i);
                final int idx = entry.index;
                if (idx < 0 || idx >= manifest.items.size()) continue;

                if (entry.status != ProgressEntry.WAITING
                        || receiveCancelled.contains(idx)) {
                    continue;
                }
                markEntry(entry, ProgressEntry.RUNNING, 0);

                TransferClient.ReceivedItem it = manifest.items.get(idx);
                String label = it.title == null || it.title.isEmpty()
                        ? it.type : it.title;
                File downloaded = null;
                try {
                    downloaded = TransferClient.downloadItem(
                            base + tokenParam + "i=" + idx,
                            label, tempDir,
                            (received, size) -> {
                                if (receiveCancelled.contains(idx)) return false;
                                int pct = size > 0
                                        ? (int) (received * 100 / size) : -1;
                                if (pct >= 0) markEntry(entry, ProgressEntry.RUNNING, pct);
                                return true;
                            });

                    if (it.isProgram()) {
                        JSONObject json = new JSONObject(readAsText(downloaded));
                        TransferData.importProgram(this, json);
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
                        TransferData.importPlayback(
                                this, renamed, it.category, it.number,
                                it.playTitle, it.label);
                        downloaded = renamed;
                    }
                    markEntry(entry, ProgressEntry.DONE, 100);
                } catch (TransferClient.CancelledException ce) {
                    markEntry(entry, ProgressEntry.CANCELLED, 0);
                } catch (Exception ex) {
                    markEntry(entry, ProgressEntry.FAILED, 0);
                } finally {
                    if (downloaded != null) {
                        //noinspection ResultOfMethodCallIgnored
                        downloaded.delete();
                    }
                }
            }

            runOnUiThread(this::onReceiveQueueFinished);
        }, "transfer-receive").start();
    }

    /** Passe une entrée de la file à un statut (sur le thread UI). */
    private void markEntry(ProgressEntry entry, int status, int progress) {
        runOnUiThread(() -> {
            entry.status = status;
            entry.progress = progress;
            renderProgress();
        });
    }

    /** Réception terminée : bilan + libération du réseau de transfert. */
    private void onReceiveQueueFinished() {
        int ok = 0;
        int failed = 0;
        for (ProgressEntry e : receiveQueue) {
            if (e.status == ProgressEntry.DONE) ok++;
            else if (e.status == ProgressEntry.FAILED) failed++;
        }
        binding.textReceiveState.setText(ok + "/" + receiveQueue.size()
                + " " + getString(R.string.transfer_returning_home_wifi));
        Toast.makeText(this,
                getString(R.string.transfer_summary_toast, ok, failed),
                Toast.LENGTH_SHORT).show();
        // Le transfert est fini : on libère le réseau de transfert
        cleanupTransferNetwork();
    }

    /**
     * Transfert terminé : on retire la suggestion du réseau Baiboly
     * (créée par le scan QR) et on libère le réseau spécifié — Android
     * repart automatiquement sur le WiFi d'origine — le récepteur n'est
     * déconnecté que le temps de l'envoi.
     */
    private void cleanupTransferNetwork() {
        TransferWifiConnect.release(getApplicationContext());
        if (android.os.Build.VERSION.SDK_INT < 29) return;
        // Identifiants du réseau de transfert : du QR scanné, ou du scan
        // auto des hotspots BAIBOLY-* (réseau ouvert).
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

    // =========================================================
    // 📊 LA FILE D'ATTENTE (envoi ET réception)
    // =========================================================
    /** La file du côté courant : ce qui part, ou ce qui arrive. */
    private List<ProgressEntry> activeQueue() {
        return sendMode ? sendQueue : receiveQueue;
    }

    /** Résumé + liste de la file. */
    private void renderProgress() {
        List<ProgressEntry> queue = activeQueue();
        if (binding.recyclerProgress.getLayoutManager() == null) {
            binding.recyclerProgress.setLayoutManager(new LinearLayoutManager(this));
        }
        if (binding.recyclerProgress.getAdapter() == null) {
            binding.recyclerProgress.setAdapter(new ProgressAdapter());
        } else {
            binding.recyclerProgress.getAdapter().notifyDataSetChanged();
        }
        // La liste a tout le poids restant : si la file est vide on la masque,
        // sinon le message "vide" serait écrasé au bas de l'écran.
        boolean empty = queue.isEmpty();
        binding.recyclerProgress.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.textProgressEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.textProgressTitle.setText(progressSummary(queue));
    }

    private String progressSummary(List<ProgressEntry> queue) {
        int done = 0;
        int running = 0;
        int waiting = 0;
        for (ProgressEntry e : queue) {
            if (e.status == ProgressEntry.DONE) done++;
            else if (e.status == ProgressEntry.RUNNING) running++;
            else if (e.status == ProgressEntry.WAITING) waiting++;
        }
        return getString(R.string.transfer_progress_title) + "  ·  "
                + getString(R.string.transfer_queue_summary, done, running, waiting);
    }

    /**
     * Annulation d'une ligne : côté réception on coupe le téléchargement,
     * côté émission on retire l'élément de la sélection (donc du manifest).
     */
    private void cancelEntry(int position) {
        List<ProgressEntry> queue = activeQueue();
        if (position < 0 || position >= queue.size()) return;
        ProgressEntry entry = queue.get(position);
        if (entry.status == ProgressEntry.DONE
                || entry.status == ProgressEntry.FAILED
                || entry.status == ProgressEntry.CANCELLED) return;

        entry.status = ProgressEntry.CANCELLED;
        if (sendMode) {
            if (entry.key != null) {
                sendChecked.remove(entry.key);
                updateServerSelection();
                saveSelection();
            }
        } else {
            receiveCancelled.add(entry.index);
        }
        renderProgress();
        Toast.makeText(this, getString(R.string.transfer_removed_from_queue),
                Toast.LENGTH_SHORT).show();
    }

    class ProgressAdapter extends RecyclerView.Adapter<ProgressAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transfer_progress, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            List<ProgressEntry> queue = activeQueue();
            if (position >= queue.size()) return;
            final int pos = position;
            ProgressEntry entry = queue.get(position);

            holder.textTitle.setText(entry.title);
            holder.textSub.setText(entry.subtitle);

            int dot;
            int label;
            int color;
            switch (entry.status) {
                case ProgressEntry.RUNNING:
                    dot = R.drawable.circle_violet;
                    label = R.string.transfer_status_running;
                    color = ContextCompat.getColor(TransferActivity.this,
                            R.color.teal_700);
                    holder.bar.setVisibility(View.VISIBLE);
                    holder.bar.setProgressCompat(Math.max(entry.progress, 0), true);
                    break;
                case ProgressEntry.DONE:
                    dot = R.drawable.circle_green;
                    label = R.string.transfer_status_done;
                    color = ContextCompat.getColor(TransferActivity.this,
                            R.color.green);
                    holder.bar.setVisibility(View.GONE);
                    break;
                case ProgressEntry.FAILED:
                    dot = R.drawable.circle_red;
                    label = R.string.transfer_status_failed;
                    color = ContextCompat.getColor(TransferActivity.this,
                            R.color.teal_200);
                    holder.bar.setVisibility(View.GONE);
                    break;
                case ProgressEntry.CANCELLED:
                    dot = R.drawable.circle_dot;
                    label = R.string.transfer_status_cancelled;
                    color = ContextCompat.getColor(TransferActivity.this,
                            R.color.title_gray);
                    holder.bar.setVisibility(View.GONE);
                    break;
                default:
                    dot = R.drawable.circle_yellow;
                    label = R.string.transfer_status_waiting;
                    color = ContextCompat.getColor(TransferActivity.this,
                            R.color.title_gray);
                    holder.bar.setVisibility(View.GONE);
                    break;
            }
            holder.statusDot.setBackgroundResource(dot);
            holder.textStatus.setText(label);
            holder.textStatus.setTextColor(color);

            boolean cancellable = entry.status == ProgressEntry.WAITING
                    || entry.status == ProgressEntry.RUNNING;
            holder.btnCancel.setVisibility(cancellable ? View.VISIBLE : View.INVISIBLE);
            holder.btnCancel.setOnClickListener(v -> cancelEntry(pos));
        }

        @Override
        public int getItemCount() {
            return activeQueue().size();
        }

        class VH extends RecyclerView.ViewHolder {
            View statusDot;
            TextView textTitle, textSub, textStatus;
            com.google.android.material.progressindicator.LinearProgressIndicator bar;
            ImageButton btnCancel;

            VH(View view) {
                super(view);
                statusDot = view.findViewById(R.id.statusDot);
                textTitle = view.findViewById(R.id.textProgressTitle);
                textSub = view.findViewById(R.id.textProgressSub);
                textStatus = view.findViewById(R.id.textProgressStatus);
                bar = view.findViewById(R.id.barProgress);
                btnCancel = view.findViewById(R.id.btnCancelProgress);
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
            final SenderDiscovery.Sender s = sendersFound.get(position);

            holder.textSenderLock.setText("");
            holder.textSenderName.setText(s.isWifiNetwork()
                    ? "📶 " + s.device : s.device);
            holder.textSenderSub.setText(s.secured
                    ? getString(R.string.transfer_sender_secured_sub)
                    : getString(R.string.transfer_sender_open_sub));

            holder.itemView.setOnClickListener(v -> {
                if (isBaibolyNetwork(s)) {
                    // Réseau WiFi Baiboly repéré par scan — même si on
                    // n'était connecté à AUCUN réseau.
                    if (s.secured) {
                        // Protégé : le QR fournit le mot de passe, il
                        // connecte le WiFi automatiquement.
                        binding.textReceiveState.setText(
                                getString(R.string.transfer_wifi_need_qr, s.ssid));
                        launchScanner();
                    } else {
                        // Ouvert : on se connecte directement.
                        connectToWifiNetwork(s);
                    }
                } else if (s.isWifiNetwork()) {
                    // Réseau non reconnu : rien à faire.
                } else if (s.secured) {
                    binding.textReceiveState.setText(
                            getString(R.string.transfer_sender_scan_prompt, s.device));
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
        String url = s.url();
        if (url == null) return;
        binding.textReceiveState.setText(getString(R.string.transfer_connecting_to_device, s.device));
        handleScannedQr(url + "?");
    }
}
