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
import com.fa.baiboly.ui.settings.AiSettingsActivity;
import com.fa.baiboly.data.parser.BibleRefParser;
import com.fa.baiboly.models.ChatMessage;

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

        String reference = getIntent().getStringExtra(EXTRA_REFERENCE);
        String verseText = getIntent().getStringExtra(EXTRA_VERSE_TEXT);
        String prefillQuestion = getIntent().getStringExtra(EXTRA_PREFILL_QUESTION);
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

        setupSendButton();

        if (reference != null && verseText != null && !verseText.trim().isEmpty()) {
            demarrerAvecReference(reference, verseText);
        } else if (prefillQuestion != null && !prefillQuestion.trim().isEmpty()) {
            // Start a fresh chat session then send the prefill question
            explanationService.demarrerAvecQuestion(prefillQuestion.trim(),
                    new BibleExplanationService.OnExplanationListener() {
                        @Override
                        public void onSuccess(String text) {
                            runOnUiThread(() -> {
                                adapter.addMessage(new ChatMessage(ChatMessage.TYPE_AI, text));
                                setInputEnabled(true);
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
    // MENU : bouton paramètres IA
    // =====================================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_ai_chat, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_ai_settings) {
            startActivity(new Intent(this, AiSettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
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

        explanationService.demarrerConversation(reference, verseText,
                new BibleExplanationService.OnExplanationListener() {

                    @Override
                    public void onSuccess(String text) {
                        runOnUiThread(() -> {
                            adapter.removeLoadingIfPresent();
                            adapter.addMessage(new ChatMessage(ChatMessage.TYPE_AI, text));
                            setInputEnabled(true);
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

        adapter.addMessage(new ChatMessage(ChatMessage.TYPE_USER, message));
        adapter.addMessage(new ChatMessage(ChatMessage.TYPE_LOADING, ""));
        scrollToBottomForced();

        setInputEnabled(false);

        explanationService.continuerConversation(message,
                new BibleExplanationService.OnExplanationListener() {

                    @Override
                    public void onSuccess(String text) {
                        runOnUiThread(() -> {
                            adapter.removeLoadingIfPresent();
                            adapter.addMessage(new ChatMessage(ChatMessage.TYPE_AI, text));
                            setInputEnabled(true);
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