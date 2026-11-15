package com.fa.baiboly.data.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.fa.baiboly.BuildConfig;
import com.google.ai.client.generativeai.java.ChatFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service d'explication biblique via l'API Gemini, avec une chaine de
 * fallback automatique : si le modele courant est surcharge (503),
 * quota atteint (429) ou retire par Google (404), le message est
 * renvoye automatiquement sur le modele gratuit suivant de la liste.
 * La memoire des echanges est reinjectee lors d'une bascule pour que
 * la conversation garde son fil.
 */
public class BibleExplanationService {

    private static final String TAG = "BibleExplanationService";

    // =====================================================
    // CHAINE DE MODELES (fallback gratuit, ordre de qualite)
    // =====================================================
    // Le premier est le plus recent ; tous ces modeles Flash /
    // Flash-Lite sont servis gratuitement sur le niveau sans
    // facturation de l'API Gemini.
    private static final String[] MODELES_CANDIDATS = {
            "gemini-3.8-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash-lite"
    };

    /** Modele recommande par l'API dans un message 404 ("use models/xxx"). */
    private static final Pattern MODELE_RECOMMANDE =
            Pattern.compile("models/([a-zA-Z0-9.\\-]+)");

    /** Delai avant la tentative sur le modele suivant (ms). */
    private static final long DELAI_FALLBACK_MS = 1200;

    private GeminiClient geminiClient;
    private ChatFutures chatSession;
    private AiPreferencesService aiPrefs;
    private final Context appContext;

    /** Modele en cours d'utilisation (index dans MODELES_CANDIDATS). */
    private int indexModeleCourant = 0;

    // Memoire interne des echanges : permet de reinjecter l'historique
    // quand on bascule de modele (la nouvelle session SDK part a zero).
    private final List<String[]> transcript = new ArrayList<>();
    private String messageUtilisateurEnCours = null;

    public BibleExplanationService(Context context) {
        this.appContext = context.getApplicationContext();
        this.geminiClient = new GeminiClient(this.appContext, MODELES_CANDIDATS[0]);
        this.aiPrefs = new AiPreferencesService(this.appContext);
        this.chatSession = null;
    }

    /**
     * Demarre une nouvelle conversation a partir d'un verset (ou d'une
     * selection de versets). A appeler une seule fois, a l'ouverture de
     * l'activity de discussion.
     */
    public void demarrerConversation(String reference, String texteVerset, OnExplanationListener listener) {

        chatSession = geminiClient.startChat();
        transcript.clear();
        messageUtilisateurEnCours = null;

        String prompt = "Voici un passage biblique (" + reference + ") : \"" + texteVerset + "\". "
                + "Donne une explication de ce passage en suivant les consignes de "
                + "style, niveau, langue et longueur definies dans tes instructions."
                + aiPrefs.stylePrompt();

        envoyerMessage(prompt, listener);
    }

    /**
     * Demarre une conversation libre (pas liee a un verset precis).
     * Utilise quand on ouvre l'IA depuis l'accueil avec le theme du mois.
     */
    public void demarrerAvecQuestion(String question, OnExplanationListener listener) {

        chatSession = geminiClient.startChat();
        transcript.clear();
        messageUtilisateurEnCours = null;

        String prompt = "Tu es un assistant biblique expert. L'utilisateur te parle du theme du mois dans une application de Bible malgache. "
                + "Reponds de preference en malgache. Sois clair, utile et bienveillant.\n\n"
                + "Question : " + question;

        envoyerMessage(prompt, listener);
    }

    /**
     * Reprise d'une conversation restauree depuis l'historique :
     * nouvelle session SDK, reinjection de tout l'historique en un
     * unique message de contexte. Enregistre aussi les echanges dans
     * la memoire interne, pour qu'une bascule de modele garde le fil.
     */
    public void reprendreConversation(java.util.List<String[]> historique,
                                      OnExplanationListener ignoredListener) {

        chatSession = geminiClient.startChat();
        transcript.clear();
        messageUtilisateurEnCours = null;

        if (historique != null && !historique.isEmpty()) {
            transcript.addAll(historique);
        }

        if (historique == null || historique.isEmpty()) return;

        envoyerMessageSilencieux(construireBlocContexte(historique));
    }

    /**
     * Continue la conversation deja demarree (l'utilisateur pose une
     * question de suivi). L'historique est gere automatiquement par le SDK.
     */
    public void continuerConversation(String message, OnExplanationListener listener) {

        // Session jamais creee (chat ouvert directement, sans verset ni
        // question pre-demarree) : on en cree une au lieu d'echouer.
        if (chatSession == null) {
            chatSession = geminiClient.startChat();
        }

        envoyerMessage(message, listener);
    }

    public boolean hasConversationStarted() {
        return chatSession != null;
    }

    // =====================================================
    // ENVOI + FALLBACK AUTOMATIQUE DE MODELE
    // =====================================================

    /**
     * Envoi normal : enregistre le message pour la memoire interne,
     * puis delegue a l'envoi sur le modele courant.
     */
    private void envoyerMessage(String message, OnExplanationListener listener) {
        messageUtilisateurEnCours = message;
        envoyerSurModeleCourant(message, message, listener, 0);
    }

    /**
     * Envoi "silencieux" : message de contexte (reprise d'historique),
     * sa reponse ne pollue pas la memoire interne.
     */
    private void envoyerMessageSilencieux(String message) {
        try {
            Content content = contenuUtilisateur(message);
            chatSession.sendMessage(content);
        } catch (Exception e) {
            android.util.Log.w(TAG, "Contexte non envoye (tolere)", e);
        }
    }

    /**
     * Construit et envoie le message au modele courant ; en cas d'echec
     * recuperable, bascule sur le modele suivant et reessaie tout seul.
     *
     * @param messageBase     message d'origine (celui conserve en memoire)
     * @param messageAEnvoyer message reellement envoye (messageBase, ou
     *                        messageBase precede du bloc de memoire)
     * @param tentative       numero d'essai (0 = premier envoi)
     */
    private void envoyerSurModeleCourant(final String messageBase,
                                         final String messageAEnvoyer,
                                         final OnExplanationListener listener,
                                         final int tentative) {

        if (tentative >= MODELES_CANDIDATS.length + 1) {
            android.util.Log.e(TAG, "Tous les modeles de repli ont echoue");
            listener.onError("Il y avait une erreur");
            return;
        }

        try {
            Content content = contenuUtilisateur(messageAEnvoyer);
            ListenableFuture<GenerateContentResponse> responseFuture =
                    chatSession.sendMessage(content);

            Futures.addCallback(responseFuture, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse result) {
                    try {
                        String text = result.getText();
                        if (messageUtilisateurEnCours != null) {
                            transcript.add(new String[]{messageUtilisateurEnCours, text});
                            messageUtilisateurEnCours = null;
                        }
                        listener.onSuccess(text);
                    } catch (Exception e) {
                        android.util.Log.e(TAG, "Reponse illisible / vide du modele", e);
                        gererEchec(e, messageBase, listener, tentative);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    gererEchec(t, messageBase, listener, tentative);
                }
            }, Runnable::run);
            // NB : ce callback s'execute sur le thread qui complete le Future,
            // qui n'est generalement PAS le thread UI. C'est pour ca que
            // AiExplanationActivity fait un runOnUiThread(...) autour des mises
            // a jour de vue.
        } catch (Exception e) {
            gererEchec(e, messageBase, listener, tentative);
        }
    }

    /**
     * Analyse l'erreur : si elle est de type surcharge (503), quota (429)
     * ou modele retire (404 "no longer available"), bascule toute seule
     * sur le modele suivant (ou celui recommande par Google dans son
     * message). Sinon, remonte une erreur generique a l'interface (le
     * detail precis reste dans le log ci-dessus).
     */
    private void gererEchec(Throwable t, final String messageBase,
                            final OnExplanationListener listener, final int tentative) {

        String brut = t.getMessage() != null ? t.getMessage() : "";
        android.util.Log.e(TAG, "Erreur API precise (tentative " + (tentative + 1)
                + ", modele " + MODELES_CANDIDATS[
                        Math.min(indexModeleCourant, MODELES_CANDIDATS.length - 1)]
                + ")", t);

        String recommande = extraireModeleRecommande(brut);
        boolean modeleRetire = brut.contains("404")
                || brut.toLowerCase().contains("no longer available")
                || brut.contains("NOT_FOUND");
        boolean surcharge = brut.contains("503") || brut.contains("UNAVAILABLE")
                || brut.toLowerCase().contains("high demand")
                || brut.toLowerCase().contains("overload");
        boolean quota = brut.contains("429") || brut.contains("RESOURCE_EXHAUSTED");

        // 1. Google recommande explicitement un nouveau modele (404) :
        // on essaie d'abord ce nom-la.
        if (modeleRetire && recommande != null && !contientDeja(recommande)) {
            MODELES_CANDIDATS[indexModeleCourant] = recommande;
            android.util.Log.w(TAG, "Modele retire par Google -> bascule vers "
                    + recommande + " (recommande par l'API)");
            replanifierSurModele(messageBase, listener, tentative);
            return;
        }

        // 2. Surcharge / quota : passer au modele suivant de la chaine.
        if ((surcharge || quota) && indexModeleCourant + 1 < MODELES_CANDIDATS.length) {
            indexModeleCourant++;
            android.util.Log.w(TAG, "Surcharge/quota sur "
                    + MODELES_CANDIDATS[indexModeleCourant - 1] + " -> essai du modele "
                    + MODELES_CANDIDATS[indexModeleCourant]);
            replanifierSurModele(messageBase, listener, tentative);
            return;
        }

        listener.onError("Il y avait une erreur");
    }

    /** Apres un bref delai, reconstruit la session sur le modele suivant. */
    private void replanifierSurModele(final String messageBase,
                                      final OnExplanationListener listener,
                                      final int tentative) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    // Nouvelle session sur le modele suivant ; si la conversation
                    // avait deja demarre, la memoire des echanges est reinjectee
                    // en tete du message renvoye.
                    geminiClient = new GeminiClient(appContext,
                            MODELES_CANDIDATS[indexModeleCourant]);
                    chatSession = geminiClient.startChat();

                    String messageAEnvoyer;
                    if (transcript.isEmpty()) {
                        messageAEnvoyer = messageBase;
                    } else {
                        messageAEnvoyer = construireBlocContexte(derniersEchanges(6))
                                + "\n\n" + messageBase;
                    }

                    envoyerSurModeleCourant(messageBase, messageAEnvoyer, listener, tentative + 1);
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Bascule modele impossible", e);
                    listener.onError("Il y avait une erreur");
                }
            }
        }, DELAI_FALLBACK_MS);
    }

    /**
     * Nom du modele que Google demande d'utiliser, s'il est indique dans
     * le message d'erreur ("models/xxx").
     */
    private String extraireModeleRecommande(String brut) {
        Matcher m = MODELE_RECOMMANDE.matcher(brut);
        if (m.find()) return m.group(1);
        return null;
    }

    private boolean contientDeja(String modele) {
        for (String m : MODELES_CANDIDATS) {
            if (m.equals(modele)) return true;
        }
        return false;
    }

    /** Les N derniers echanges (question, reponse). */
    private List<String[]> derniersEchanges(int n) {
        int debut = Math.max(0, transcript.size() - n);
        return new ArrayList<>(transcript.subList(debut, transcript.size()));
    }

    /**
     * Bloc de contexte a reinjecter en tete d'un message : garde le fil de
     * la conversation apres une bascule de modele.
     */
    private String construireBlocContexte(List<String[]> echanges) {
        StringBuilder sb = new StringBuilder(
                "Reprise d'une conversation precedente (contexte a memoriser, "
                        + "reponds UNIQUEMENT a la derniere question ci-dessous) :\n");
        for (String[] paire : echanges) {
            sb.append("Utilisateur : ").append(paire[0]).append("\n")
              .append("Assistant : ").append(tronquer(paire[1])).append("\n\n");
        }
        return sb.toString();
    }

    /** Coupe une reponse trop longue (le contexte reste raisonnable). */
    private String tronquer(String s) {
        if (s == null) return "";
        return s.length() <= 600 ? s : s.substring(0, 600) + "...";
    }

    /** Prepare le Content utilisateur a partir du texte du message. */
    private Content contenuUtilisateur(String message) {
        Content.Builder contentBuilder = new Content.Builder();
        contentBuilder.setRole("user");
        contentBuilder.addText(message);
        return contentBuilder.build();
    }

    public interface OnExplanationListener {
        void onSuccess(String text);
        void onError(String errorLog);
    }
}
