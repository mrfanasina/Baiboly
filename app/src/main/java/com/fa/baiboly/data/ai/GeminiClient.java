package com.fa.baiboly.data.ai;

import android.content.Context;

import com.fa.baiboly.BuildConfig;
import com.fa.baiboly.data.perikopa.PerikopaService;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.ChatFutures;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerationConfig;
import com.google.ai.client.generativeai.type.RequestOptions;
import com.google.ai.client.generativeai.type.Tool;
import com.google.ai.client.generativeai.type.ToolConfig;

import java.util.Collections;
import java.util.List;

public class GeminiClient {

    // La clé API est lue depuis BuildConfig, générée à partir de local.properties
    // (GEMINI_API_KEY=xxxxx dans local.properties — jamais commitée sur git).
    private static final String API_KEY = BuildConfig.GEMINI_API_KEY;

    private final GenerativeModelFutures model;
    private PerikopaService service;
    private final AiPreferencesService aiPrefs;

    public GeminiClient(Context context) {
        this.service = new PerikopaService(context);
        this.aiPrefs = new AiPreferencesService(context);

        // 1. Définir la restriction stricte (System Instruction)
        Content systemInstruction = new Content.Builder()
                .addText("Vous êtes un assistant spécialisé exclusivement dans l'étude de la "
                        + "Bible et l'histoire du christianisme. Vous devez refuser poliment "
                        + "d'aborder tout autre sujet.\n\n"
                        + "Vous êtes de sensibilité protestante EGLISE REFORMEE DE MADAGASCAR.\n"
                        + "EGLISE DE JESUS-CHRIST DE MADAGASCAR FJKM (FIANGONAN'I JESOA KRISTY ETO MADAGASIKARA). Ne le mentionner pas "
                        + "dans vos réponses.\n\n"
                        + "Par défaut, vous expliquez les passages bibliques de manière claire, sans présenter les différentes interprétations des autres traditions chrétiennes.\n\n"
                        + "Vous ne présentez les points de vue catholique, orthodoxe, évangélique ou d'autres traditions que si l'utilisateur le demande explicitement, ou si la question porte directement sur une comparaison entre doctrines.\n\n"
                        + "N'indiquez jamais spontanément qu'il existe \"plusieurs points de vue\", sauf si cela est indispensable pour répondre correctement à la question.\n\n"
                        + "Voici le thème mensuel de ce mois : " + service.getTodayLohahevitra() + " — Si aucun rapport du tout ne force pas avec. Tu peux l'utiliser comme titre ou theme principale si ça a un rapport "
                        + "Vous devez utiliser la version MG1865 de la Bible malgache si possible.\n\n"
                        + "=== Préférences de l'utilisateur (Fikirakiràna) ===\n"
                        + aiPrefs.fullPrompt())
                .build();

        // 2. Configurer les paramètres
        // NB : setTemperature() n'existe pas dans toutes les versions du SDK,
        // on garde donc la config par défaut pour l'instant. Voir la note en
        // bas de fichier si vous voulez la réactiver plus tard.
        GenerationConfig config = new GenerationConfig.Builder().build();

        // 3. Initialiser le modèle de base
        // NB : en 0.9.0, l'ordre exact du constructeur est :
        // (modelName, apiKey, generationConfig, safetySettings, requestOptions, tools, toolConfig, systemInstruction)
        // Il faut donc fournir tous les paramètres intermédiaires pour atteindre systemInstruction.
        GenerativeModel baseModel = new GenerativeModel(
                "gemini-3.1-flash-lite",
                API_KEY,
                config,
                Collections.emptyList(),
                new RequestOptions(),
                (List<Tool>) null,
                (ToolConfig) null,
                systemInstruction
        );

        // 4. Convertir en version "Futures" compatible à 100% avec Java
        this.model = GenerativeModelFutures.from(baseModel);
    }

    public GenerativeModelFutures getModel() {
        return this.model;
    }

    /**
     * Démarre une session de chat qui garde l'historique des échanges
     * (nécessaire pour pouvoir continuer la discussion après l'explication
     * initiale d'un verset).
     */
    public ChatFutures startChat() {
        return this.model.startChat();
    }
}

/*
 * NOTE SÉCURITÉ :
 * La clé API n'est plus codée en dur : elle est lue depuis local.properties
 * (GEMINI_API_KEY=xxxxx), qui n'est jamais commité sur git, puis injectée
 * dans BuildConfig par app/build.gradle.kts.
 *
 * NB : même avec ce mécanisme, la clé reste présente dans l'APK final et
 * peut être extraite par décompilation. Pour une protection réelle, faire
 * passer les appels par un petit backend à vous, pour ne jamais exposer
 * la clé côté client.
 */
