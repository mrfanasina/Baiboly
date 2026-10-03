package com.fa.baiboly.data.ai;

import android.content.Context;

import com.google.ai.client.generativeai.java.ChatFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

public class BibleExplanationService {

    private final GeminiClient geminiClient;
    private final AiPreferencesService aiPrefs;

    // Session de chat en cours : garde l'historique côté SDK, donc pas besoin
    // de renvoyer manuellement les échanges précédents à chaque message.
    private ChatFutures chatSession;

    public BibleExplanationService(Context context) {
        this.geminiClient = new GeminiClient(context);
        this.aiPrefs = new AiPreferencesService(context);
    }

    /**
     * Démarre une nouvelle conversation à partir d'un verset (ou d'une
     * sélection de versets). À appeler une seule fois, à l'ouverture de
     * l'activity de discussion.
     */
    public void demarrerConversation(String reference, String texteVerset, OnExplanationListener listener) {

        chatSession = geminiClient.startChat();

        String prompt = "Voici un passage biblique (" + reference + ") : \"" + texteVerset + "\". "
                + "Donne une explication de ce passage en suivant les consignes de "
                + "style, niveau, langue et longueur définies dans tes instructions."
                + aiPrefs.stylePrompt();

        envoyerMessage(prompt, listener);
    }

    /**
     * Démarre une conversation libre (pas liée à un verset précis).
     * Utilisé quand on ouvre l'IA depuis l'accueil avec le thème du mois.
     */
    public void demarrerAvecQuestion(String question, OnExplanationListener listener) {

        chatSession = geminiClient.startChat();

        String prompt = "Tu es un assistant biblique expert. L'utilisateur te parle du thème du mois dans une application de Bible malgache. "
                + "Réponds de préférence en malgache. Sois clair, utile et bienveillant.\n\n"
                + "Question : " + question;

        envoyerMessage(prompt, listener);
    }

    /**
     * Continue la conversation déjà démarrée (l'utilisateur pose une
     * question de suivi). L'historique est géré automatiquement par le SDK.
     */
    public void continuerConversation(String message, OnExplanationListener listener) {

        if (chatSession == null) {
            listener.onError("Aucune conversation en cours. Réouvrez l'écran d'explication.");
            return;
        }

        envoyerMessage(message, listener);
    }

    private void envoyerMessage(String message, OnExplanationListener listener) {

        Content.Builder contentBuilder = new Content.Builder();
        contentBuilder.setRole("user");
        contentBuilder.addText(message);
        Content content = contentBuilder.build();
        ListenableFuture<GenerateContentResponse> responseFuture =
                chatSession.sendMessage(content);

        Futures.addCallback(responseFuture, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                listener.onSuccess(result.getText());
            }

            @Override
            public void onFailure(Throwable t) {
                listener.onError(t.getMessage() != null ? t.getMessage() : "Erreur inconnue");
            }
        }, Runnable::run);
        // NB : ce callback s'exécute sur le thread qui complète le Future,
        // qui n'est généralement PAS le thread UI. C'est pour ça que
        // AiExplanationActivity fait un runOnUiThread(...) autour des mises
        // à jour de vue.
    }

    public boolean hasConversationStarted() {
        return chatSession != null;
    }

    public interface OnExplanationListener {
        void onSuccess(String text);
        void onError(String errorLog);
    }
}