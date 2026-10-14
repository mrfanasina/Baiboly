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
     * Reprise d'une conversation restaurée depuis l'historique :
     * nouvelle session SDK, puis réinjection de tout l'historique en un
     * unique message "contexte" (le SDK ne permet pas d'insérer des
     * échanges dans un chat existant). Le résultat du message de contexte
     * est jeté : la session est prête pour la vraie question suivante.
     */
    public void reprendreConversation(java.util.List<String[]> historique,
                                      OnExplanationListener ignoredListener) {

        chatSession = geminiClient.startChat();

        if (historique == null || historique.isEmpty()) return;

        StringBuilder ctx = new StringBuilder(
                "Reprise d'une conversation précédente (contexte à mémoriser, "
                        + "réponds juste \"ok\") :\n");

        for (String[] pair : historique) {
            // pair[0] = question user, pair[1] = réponse IA
            ctx.append("Utilisateur : ").append(pair[0]).append("\n")
               .append("Assistant : ").append(pair[1]).append("\n\n");
        }

        envoyerMessage(ctx.toString(), new OnExplanationListener() {
            @Override
            public void onSuccess(String text) {
                // Contexte absorbé : on ignore la réponse
            }

            @Override
            public void onError(String errorLog) {
                // Sans contexte injecté, la session marche quand même :
                // la suite partira simplement sans mémoire du passé
            }
        });
    }

    /**
     * Continue la conversation déjà démarrée (l'utilisateur pose une
     * question de suivi). L'historique est géré automatiquement par le SDK.
     */
    public void continuerConversation(String message, OnExplanationListener listener) {

        // Session jamais créée (chat ouvert directement, sans verset ni
        // question pré-démarrée) : on en crée une au lieu d'échouer —
        // l'utilisateur ne doit JAMAIS voir "Aucune conversation en cours".
        if (chatSession == null) {
            chatSession = geminiClient.startChat();
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