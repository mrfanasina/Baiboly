package com.fa.baiboly.ui.ai;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.ai.BibleExplanationService;
import com.fa.baiboly.data.ai.ChatHistoryDbHelper;
import com.fa.baiboly.ui.settings.AiSettingsActivity;
import com.fa.baiboly.data.parser.BibleRefParser;
import com.fa.baiboly.models.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class AiExplanationActivity extends AppCompatActivity {

    public static final String EXTRA_REFERENCE = "extra_reference";
    public static final String EXTRA_VERSE_TEXT = "extra_verse_text";

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_TEXT_SIZE = "text_size";
    private static final float DEFAULT_TEXT_SIZE = 18f;
    public static final String EXTRA_PREFILL_QUESTION = "extra_prefill_question";

    // Contexte livre/chapitre pour résoudre "and" / "andininy" dans le
    // chat : extrait de la référence de départ via le parser centralisé
    // BibleRefParser (une seule table de livres, un seul comportement).
    private BibleExplanationService explanationService;
    private ChatAdapter adapter;
    private RecyclerView recyclerChat;
    private EditText etMessage;
    private ImageButton btnSend;
    private TextView tvDisclaimer;
    private TextView chipNewMessage;

    private boolean isUserAtBottom = true;

    // Démarrage explicite (verset / question) : empêche la restauration
    // d'historique de se déclencher et sert de contexte toute la vie de l'écran.
    @Nullable private String reference;
    @Nullable private String prefillQuestion;

    // =====================================================
    // HISTORIQUE + LIMITE DE CONTINUATION
    // =====================================================

    /** Persistance des discussions (SQLite ai_chat.db). */
    private ChatHistoryDbHelper chatHistory;

    /** Tiroir d'historique (burger à droite). */
    private androidx.drawerlayout.widget.DrawerLayout drawerHistory;

    private HistoryDrawerAdapter historyDrawerAdapter;

    /** Conversation en cours (-1 = aucune, sera créée au 1er échange). */
    private long currentConversationId = -1;

    /** Limite d'échanges user/IA par conversation. */
    public static final int MAX_EXCHANGES_PER_CHAT = 10;

    /** Compteur d'échanges (paire user+IA) dans la conversation en cours. */
    private int exchangeCount = 0;

    public static void start(Context context, String reference, String verseText) {
        Intent intent = new Intent(context, AiExplanationActivity.class);
        intent.putExtra(EXTRA_REFERENCE, reference);
        intent.putExtra(EXTRA_VERSE_TEXT, verseText);
        context.startActivity(intent);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Mode nuit + couleurs de base choisies (avant inflation)
        ColorManager.applyGlobalTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_explanation);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Champs (et non variables locales) : la restauration d'historique
        // doit savoir si l'écran a été ouvert avec un démarrage explicite.
        this.reference = getIntent().getStringExtra(EXTRA_REFERENCE);
        this.prefillQuestion = getIntent().getStringExtra(EXTRA_PREFILL_QUESTION);
        String verseText = getIntent().getStringExtra(EXTRA_VERSE_TEXT);

        // Toolbar : retour (←) à gauche, burger (☰) à droite (menu item).
        // Le burger ouvre le tiroir d'historique.
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Fanazavana AI");
        }

        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerChat = findViewById(R.id.recyclerChat);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        tvDisclaimer = findViewById(R.id.tvDisclaimer);
        chipNewMessage = findViewById(R.id.chipNewMessage);

        setupHistoryDrawer();

        // Livre/chapitre de contexte pour résoudre "and" / "andininy" dans
        // les messages du chat — extraits de la référence de départ, si
        // elle existe (voir REFERENCE_PATTERN). Restent null si l'activité
        // est ouverte sans passage précis (chat "vierge") : les marqueurs
        // "and"/"andininy" ne seront alors simplement pas détectés.
        String[] context = parseBookAndChapter(reference);

        adapter = new ChatAdapter(
                getSupportFragmentManager(),
                context != null ? context[0] : null,
                context != null ? context[1] : null
        );
        adapter.setOnNotRelevantListener(this::envoyerQuestionPlusCiblee);
        adapter.setOnOpenAiSettingsListener(() ->
                startActivity(new Intent(this, AiSettingsActivity.class)));
        recyclerChat.setLayoutManager(new LinearLayoutManager(this));
        recyclerChat.setAdapter(adapter);

        setupScrollBehavior();

        applyTextSizeFromSettings();

        explanationService = new BibleExplanationService(this);

        chatHistory = new ChatHistoryDbHelper(this);

        setupSendButton();

        // Restaurer la conversation la plus récente (historique persistant) :
        // elle continue là où elle s'était arrêtée.
        restoreLatestConversationIfNeeded();

        if (reference != null && verseText != null && !verseText.trim().isEmpty()) {
            demarrerAvecReference(reference, verseText);
        } else if (prefillQuestion != null && !prefillQuestion.trim().isEmpty()) {
            // Start a fresh chat session then send the prefill question
            final String question = prefillQuestion.trim();
            explanationService.demarrerAvecQuestion(question,
                    new BibleExplanationService.OnExplanationListener() {
                        @Override
                        public void onSuccess(String text) {
                            runOnUiThread(() -> {
                                adapter.addMessage(new ChatMessage(ChatMessage.TYPE_AI, text));

                                // Historique : le démarrage pré-rempli est un échange à part entière
                                exchangeCount++;
                                persistExchange(question, text);
                                refreshHistoryDrawer();
                                if (!canContinueConversation()) {
                                    showConversationLimitReached();
                                } else {
                                    setInputEnabled(true);
                                }
                                onNewAiMessageAdded();
                            });
                        }

                        @Override
                        public void onError(String errorLog) {
                            runOnUiThread(() -> {
                                adapter.addMessage(new ChatMessage(ChatMessage.TYPE_AI,
                                        "Nisy olana: " + errorLog));
                                setInputEnabled(true);
                                onNewAiMessageAdded();
                            });
                        }
                    });
            adapter.addMessage(new ChatMessage(ChatMessage.TYPE_USER, prefillQuestion.trim()));
            scrollToBottomForced();
            adapter.addMessage(new ChatMessage(ChatMessage.TYPE_LOADING, ""));
            scrollToBottomForced();
            setInputEnabled(false);
        } else {
            afficherMessageBienvenue();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyTextSizeFromSettings();
    }

    // =====================================================
    // HISTORIQUE : restauration + sauvegarde
    // =====================================================

    /**
     * Rouvre la conversation la plus récente (messages + compteur).
     * Appelé à l'ouverture SI aucun verset/question n'a été passé :
     * un écran ouvert délibérément avec une référence démarre une
     * conversation NEUVE sur ce verset.
     */
    private void restoreLatestConversationIfNeeded() {

        if (reference != null || prefillQuestion != null) {
            return; // démarrage explicite -> conversation neuve
        }

        long latest = chatHistory.getLatestConversationId();
        if (latest < 0) return;

        List<ChatHistoryDbHelper.StoredMessage> stored =
                chatHistory.getMessages(latest);
        if (stored.isEmpty()) return;

        currentConversationId = latest;

        int userMsgs = 0;
        java.util.List<String[]> pairs = new ArrayList<>();

        String pendingUser = null;

        for (ChatHistoryDbHelper.StoredMessage m : stored) {
            if (m.type == ChatMessage.TYPE_USER) {
                userMsgs++;
                pendingUser = m.text;
            } else if (m.type == ChatMessage.TYPE_AI) {
                pairs.add(new String[]{
                        pendingUser != null ? pendingUser : "",
                        m.text});
                pendingUser = null;
            }

            adapter.addMessage(new ChatMessage(
                    m.type == 0 ? ChatMessage.TYPE_USER : ChatMessage.TYPE_AI,
                    m.text));
        }

        // Le nombre d'échanges persisté re-conditionne la limite.
        exchangeCount = userMsgs;

        // Réinjecte l'historique dans la session SDK (mémoire du modèle).
        explanationService.reprendreConversation(pairs,
                new BibleExplanationService.OnExplanationListener() {
                    @Override
                    public void onSuccess(String text) {
                        // contexte absorbé
                    }

                    @Override
                    public void onError(String errorLog) {
                        // sans mémoire, la conversation repart à zéro côté
                        // modèle mais la vue reste utilisable
                    }
                });

        recyclerChat.post(() ->
                recyclerChat.scrollToPosition(adapter.getItemCount() - 1));
    }

    /**
     * Persiste un échange complet (question + réponse) une fois les deux
     * disponibles. Appelé unique au onSuccess de l'IA.
     */
    private void persistExchange(String userQuestion, String aiAnswer) {

        if (chatHistory == null) return;

        try {
            // Titre : référence biblique si la question en contient une
            // ("Jao 3:16", "Sal 96:7-10"...), sinon le début de la question,
            // nettoyé (majuscule + coupe au premier saut de ligne).
            String titre = buildConversationTitle(userQuestion);

            currentConversationId = chatHistory.appendExchange(
                    currentConversationId,
                    userQuestion,
                    aiAnswer,
                    titre);
        } catch (Exception e) {
            // L'historique ne doit jamais casser le chat
        }
    }

    /**
     * Titre pertinent de conversation : priorité à la référence biblique
     * détectée (via BibleRefParser — zéro faux positif), sinon la question
     * recadrée. Fallback neutre si la question est vide/illisible.
     */
    private String buildConversationTitle(String userQuestion) {

        if (userQuestion == null) return "Resaka";

        String q = userQuestion.trim();

        // Le bouton "Hazavao" affiche "Hazavao Jao 3:16" -> titre = référence seule.
        if (q.startsWith("Hazavao ") && q.length() <= 60) {
            q = q.substring("Hazavao ".length()).trim();
        }

        // 1) Référence biblique complète dans la question ?
        List<BibleRefParser.DetectedRef> refs =
                BibleRefParser.detectReferences(q);
        if (!refs.isEmpty()) {
            return refs.get(0).reference;
        }

        // 2) Question lisible : première phrase, recadrée.
        q = q.replaceFirst("^Hazavao\\s+", "")
                .replaceFirst("^[\"«]", "")
                .trim();
        int cut = Math.min(q.length(), 45);
        q = q.substring(0, cut);
        int pointCut = q.indexOf("\n");
        if (pointCut > 5) q = q.substring(0, pointCut);

        if (q.length() > 0) {
            q = Character.toUpperCase(q.charAt(0)) + q.substring(1);
            return q + (userQuestion.length() > 45 ? "…" : "");
        }

        return "Resaka";
    }

    /**
     * Crée une nouvelle conversation : vide la vue, reset le compteur,
     * repart d'une session IA neuve.
     */
    private void startNewConversation() {

        currentConversationId = -1;
        exchangeCount = 0;

        adapter.clearMessages();

        explanationService = new BibleExplanationService(this);

        afficherMessageBienvenue();
        setInputEnabled(true);
    }

    // =====================================================
    // LIMITE DE CONTINUATION
    // =====================================================

    /** true si l'utilisateur peut encore poser une question de suivi. */
    private boolean canContinueConversation() {
        return exchangeCount < MAX_EXCHANGES_PER_CHAT;
    }

    /**
     * Affiche le message de fin de conversation (limite atteinte) :
     * bulle IA + saisie désactivée + menu "Resaka vaovao".
     */
    private void showConversationLimitReached() {

        adapter.removeLoadingIfPresent();
        adapter.addMessage(new ChatMessage(ChatMessage.TYPE_AI,
                "⛔ Voafetra ny fifanakalozan-draharaha (" + MAX_EXCHANGES_PER_CHAT
                        + " fanontaniana farafahabetsahana). Sokafy ny resaka vaovao"
                        + " mba hanohizana."));
        onNewAiMessageAdded();
        setInputEnabled(false);
        etMessage.setHint("Voafetra ny fifanakalozana — sokafy vaovao");
    }

    // =====================================================
    // MENU : historique + nouvelle discussion + paramètres IA
    // =====================================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_ai_chat, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        if (item.getItemId() == R.id.action_ai_open_drawer) {
            // Burger (☰) à droite : ouvre le tiroir d'historique.
            drawerHistory.openDrawer(androidx.core.view.GravityCompat.END);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {

        // Tiroir ouvert -> le back le ferme au lieu de quitter l'écran.
        if (drawerHistory != null
                && drawerHistory.isDrawerOpen(androidx.core.view.GravityCompat.END)) {
            drawerHistory.closeDrawer(androidx.core.view.GravityCompat.END);
            return;
        }
        super.onBackPressed();
    }

    // =====================================================
    // HISTORIQUE : tiroir de droite (burger)
    // =====================================================

    /**
     * Branche le tiroir : liste des conversations (tap = rouvrir,
     * poubelle = supprimer), bouton "+ Resaka vaovao" et réglages IA.
     */
    private void setupHistoryDrawer() {

        drawerHistory = findViewById(R.id.drawerHistory);

        View drawerContent = findViewById(R.id.historyDrawerContent);

        android.view.ViewParent parent = drawerContent != null
                ? drawerContent.getParent() : null;

        // NB : <include> est dans le NavigationView -> les ids sont
        // trouvés directement par findViewById depuis l'activity (pas de
        // besoin de drawerContent.findViewById, mais on garde les deux
        // pour la robustesse).
        androidx.recyclerview.widget.RecyclerView recyclerHistory =
                findViewById(R.id.recyclerHistoryDrawer);
        if (recyclerHistory == null && drawerContent != null) {
            recyclerHistory = drawerContent.findViewById(R.id.recyclerHistoryDrawer);
        }

        View btnNewChat = findViewById(R.id.btnDrawerNewChat);
        if (btnNewChat == null && drawerContent != null) {
            btnNewChat = drawerContent.findViewById(R.id.btnDrawerNewChat);
        }

        View btnSettings = findViewById(R.id.btnDrawerSettings);
        if (btnSettings == null && drawerContent != null) {
            btnSettings = drawerContent.findViewById(R.id.btnDrawerSettings);
        }

        historyDrawerAdapter = new HistoryDrawerAdapter(
                conversationId -> {
                    // Tap = rouvrir + fermer le tiroir
                    drawerHistory.closeDrawer(androidx.core.view.GravityCompat.END);
                    openConversation(conversationId);
                },
                entry -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Fafao io resaka io?")
                        .setMessage(entry.title)
                        .setPositiveButton("Fafao", (d, w) -> {
                            chatHistory.deleteConversation(entry.id);
                            refreshHistoryDrawer();
                            // Si on supprimait la conversation affichée,
                            // on repart sur une discussion neuve.
                            if (entry.id == currentConversationId) {
                                startNewConversation();
                            }
                        })
                        .setNegativeButton("TSIA", null)
                        .show()
        );

        if (recyclerHistory != null) {
            recyclerHistory.setLayoutManager(
                    new androidx.recyclerview.widget.LinearLayoutManager(this));
            recyclerHistory.setAdapter(historyDrawerAdapter);
        }

        if (btnNewChat != null) {
            btnNewChat.setOnClickListener(v -> {
                drawerHistory.closeDrawer(androidx.core.view.GravityCompat.END);
                startNewConversation();
            });
        }

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                drawerHistory.closeDrawer(androidx.core.view.GravityCompat.END);
                startActivity(new Intent(this, AiSettingsActivity.class));
            });
        }

        refreshHistoryDrawer();
    }

    /** Recharge la liste du tiroir depuis la base. */
    private void refreshHistoryDrawer() {
        if (historyDrawerAdapter != null && chatHistory != null) {
            historyDrawerAdapter.submit(chatHistory.listConversations());
        }
    }
    private void openConversation(long conversationId) {

        List<ChatHistoryDbHelper.StoredMessage> stored =
                chatHistory.getMessages(conversationId);
        if (stored.isEmpty()) return;

        adapter.clearMessages();
        currentConversationId = conversationId;

        int userMsgs = 0;
        java.util.List<String[]> pairs = new ArrayList<>();
        String pendingUser = null;

        for (ChatHistoryDbHelper.StoredMessage m : stored) {
            if (m.type == ChatMessage.TYPE_USER) {
                userMsgs++;
                pendingUser = m.text;
            } else if (m.type == ChatMessage.TYPE_AI) {
                pairs.add(new String[]{
                        pendingUser != null ? pendingUser : "",
                        m.text});
                pendingUser = null;
            }
            adapter.addMessage(new ChatMessage(
                    m.type == 0 ? ChatMessage.TYPE_USER : ChatMessage.TYPE_AI,
                    m.text));
        }

        exchangeCount = userMsgs;

        explanationService = new BibleExplanationService(this);
        explanationService.reprendreConversation(pairs,
                new BibleExplanationService.OnExplanationListener() {
                    @Override
                    public void onSuccess(String text) { /* contexte absorbé */ }
                    @Override
                    public void onError(String errorLog) { /* toléré */ }
                });

        recyclerChat.post(() ->
                recyclerChat.scrollToPosition(adapter.getItemCount() - 1));

        if (!canContinueConversation()) {
            setInputEnabled(false);
            etMessage.setHint("Voafetra ny fifanakalozana — sokafy vaovao");
        }
    }

    // =====================================================
    // EXTRACTION LIVRE/CHAPITRE DE LA RÉFÉRENCE DE DÉPART
    // =====================================================

    /**
     * Extrait {livre, chapitre} d'une référence via BibleRefParser.
     * Retourne null si le format n'est pas reconnu (ex. référence absente
     * ou mal formée) — cas géré proprement en amont.
     */
    @Nullable
    private String[] parseBookAndChapter(@Nullable String reference) {

        if (reference == null || reference.trim().isEmpty()) {
            return null;
        }

        BibleRefParser.ParsedRef ref =
                BibleRefParser.parse(reference.trim());

        if (ref == null || !ref.hasNumbers) {
            return null;
        }

        return new String[]{
                ref.bookName.trim(),
                String.valueOf(ref.startChapter)
        };
    }

    // =====================================================
    // TAILLE DE TEXTE
    // =====================================================

    private void applyTextSizeFromSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        float textSize = prefs.getFloat(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE);
        adapter.setTextSize(textSize);
    }

    // =====================================================
    // SCROLL : disclaimer flottant en fondu + pastille "nouveau message"
    // =====================================================

    private void setupScrollBehavior() {

        recyclerChat.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                boolean atBottom = !rv.canScrollVertically(1);
                if (atBottom != isUserAtBottom) {
                    isUserAtBottom = atBottom;
                    showDisclaimer(atBottom);
                    if (atBottom) {
                        showNewMessageChip(false);
                    }
                }
            }
        });

        chipNewMessage.setOnClickListener(v -> {
            showNewMessageChip(false);
            scrollToBottomForced();
        });
    }

    private void showDisclaimer(boolean show) {
        tvDisclaimer.animate()
                .alpha(show ? 1f : 0f)
                .setDuration(200)
                .start();
    }

    private void showNewMessageChip(boolean show) {
        chipNewMessage.animate()
                .alpha(show ? 1f : 0f)
                .setDuration(200)
                .start();
    }

    // =====================================================
    // DÉMARRAGE DE LA CONVERSATION
    // =====================================================

    private void demarrerAvecReference(String reference, String verseText) {

        String promptAffiche = "Hazavao " + reference;

        adapter.addMessage(new ChatMessage(ChatMessage.TYPE_USER, promptAffiche));
        scrollToBottomForced();

        adapter.addMessage(new ChatMessage(ChatMessage.TYPE_LOADING, ""));
        scrollToBottomForced();

        setInputEnabled(false);

        final String shownRef = reference;

        explanationService.demarrerConversation(reference, verseText,
                new BibleExplanationService.OnExplanationListener() {

                    @Override
                    public void onSuccess(String text) {
                        runOnUiThread(() -> {
                            adapter.removeLoadingIfPresent();
                            adapter.addMessage(new ChatMessage(ChatMessage.TYPE_AI, text));

                            // Historique : le 1er échange de la conversation
                            exchangeCount++;
                            persistExchange(shownRef, text);
                            refreshHistoryDrawer();
                            if (!canContinueConversation()) {
                                showConversationLimitReached();
                            } else {
                                setInputEnabled(true);
                            }
                            onNewAiMessageAdded();
                        });
                    }

                    @Override
                    public void onError(String errorLog) {
                        runOnUiThread(() -> {
                            adapter.removeLoadingIfPresent();
                            adapter.addMessage(new ChatMessage(ChatMessage.TYPE_AI,
                                    "Nisy olana teo amin'ny Fanazavana"));
                            setInputEnabled(true);
                            Toast.makeText(AiExplanationActivity.this, "Tsy nandeha ny IA", Toast.LENGTH_SHORT).show();
                            onNewAiMessageAdded();
                        });
                    }
                });
    }

    private void afficherMessageBienvenue() {
        adapter.addMessage(new ChatMessage(ChatMessage.TYPE_AI,
                "👋 Miarahaba! Manontania momba ny Baiboly, andininy iray, na lohahevitra ara-pinoana — hazavaiko araka izay fantatro."));
    }

    // =====================================================
    // ENVOI DE MESSAGE UTILISATEUR
    // =====================================================

    private void setupSendButton() {

        btnSend.setEnabled(false);
        btnSend.setAlpha(0.4f);

        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasText = s.toString().trim().length() > 0;
                btnSend.setEnabled(hasText);
                btnSend.setAlpha(hasText ? 1f : 0.4f);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                tryToSendMessage();
                return true;
            }
            return false;
        });

        btnSend.setOnClickListener(v -> tryToSendMessage());
    }

    private void tryToSendMessage() {
        String message = etMessage.getText().toString().trim();
        if (message.isEmpty()) return;

        etMessage.setText("");
        envoyerMessageUtilisateur(message);
    }

    /**
     * Chip "Tsy dia mifandraika loatra?" : repose la question en demandant
     * une réponse resserrée sur la référence et le sujet exact.
     */
    private void envoyerQuestionPlusCiblee(String question) {
        String message = question + "\n(Azy ihany no hazavao, aza miala amin'ny lohahevitra : "
                + "valiny fohy, mifantatra amin'ny andininy sy ny teny nontaniana.)";
        envoyerMessageUtilisateur(message);
    }

    private void envoyerMessageUtilisateur(String message) {

        // LIMITE DE CONTINUATION : au-delà de MAX_EXCHANGES_PER_CHAT échanges
        // dans la conversation en cours, on stoppe et on propose une
        // nouvelle discussion.
        if (!canContinueConversation()) {
            showConversationLimitReached();
            return;
        }

        adapter.addMessage(new ChatMessage(ChatMessage.TYPE_USER, message));
        adapter.addMessage(new ChatMessage(ChatMessage.TYPE_LOADING, ""));
        scrollToBottomForced();

        setInputEnabled(false);

        final String question = message;

        explanationService.continuerConversation(message,
                new BibleExplanationService.OnExplanationListener() {

                    @Override
                    public void onSuccess(String text) {
                        runOnUiThread(() -> {
                            adapter.removeLoadingIfPresent();
                            adapter.addMessage(new ChatMessage(ChatMessage.TYPE_AI, text));

                            // Historique : sauvegarde de l'échange complet
                            exchangeCount++;
                            persistExchange(question, text);
                            refreshHistoryDrawer();

                            if (canContinueConversation()) {
                                setInputEnabled(true);
                            } else {
                                showConversationLimitReached();
                            }
                            onNewAiMessageAdded();
                        });
                    }

                    @Override
                    public void onError(String errorLog) {
                        runOnUiThread(() -> {
                            adapter.removeLoadingIfPresent();
                            adapter.addMessage(new ChatMessage(ChatMessage.TYPE_AI, "Nisy olana: " + errorLog));
                            setInputEnabled(true);
                            onNewAiMessageAdded();
                        });
                    }
                });
    }

    // =====================================================
    // UTILITAIRES UI
    // =====================================================

    private void setInputEnabled(boolean enabled) {
        etMessage.setEnabled(enabled);
        boolean hasText = etMessage.getText().toString().trim().length() > 0;
        btnSend.setEnabled(enabled && hasText);
        btnSend.setAlpha(btnSend.isEnabled() ? 1f : 0.4f);
        etMessage.setHint(enabled ? "Manontany ..." : "Miandry valiny...");
    }

    private void scrollToBottomForced() {
        isUserAtBottom = true;
        showDisclaimer(true);
        showNewMessageChip(false);
        recyclerChat.post(() -> {
            if (adapter.getItemCount() > 0) {
                recyclerChat.smoothScrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }

    private void onNewAiMessageAdded() {
        if (!isUserAtBottom) {
            showNewMessageChip(true);
            return;
        }
        int position = adapter.getItemCount() - 1;
        recyclerChat.post(() -> scrollToRevealTop(position));
    }

    private void scrollToRevealTop(int position) {
        RecyclerView.LayoutManager lm = recyclerChat.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) return;

        LinearSmoothScroller smoothScroller = new LinearSmoothScroller(recyclerChat.getContext()) {
            @Override
            protected int getVerticalSnapPreference() {
                return LinearSmoothScroller.SNAP_TO_START;
            }
        };
        smoothScroller.setTargetPosition(position);
        lm.startSmoothScroll(smoothScroller);
    }
    /** Ouvre le chat IA avec une question libre (venant p.ex. de la recherche),
     *  envoyée automatiquement dès l'ouverture, comme si l'utilisateur l'avait tapée. */
    public static void startWithQuestion(Context context, String question) {
        Intent intent = new Intent(context, AiExplanationActivity.class);
        intent.putExtra(EXTRA_PREFILL_QUESTION, question);
        context.startActivity(intent);
    }
}