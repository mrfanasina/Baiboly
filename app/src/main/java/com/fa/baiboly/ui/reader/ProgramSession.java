package com.fa.baiboly.ui.reader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.fa.baiboly.R;
import com.fa.baiboly.ui.fanekena.FanekenaActivity;
import com.fa.baiboly.ui.fihirana.SongDetailActivity;
import com.fa.baiboly.ui.verses.VersesActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Session de lecture d'un programme (Fandaharana) : une liste ordonnée
 * d'items lisibles (vakiteny, hira, fanekena) que les lecteurs existants
 * parcourent dans l'ordre.
 *
 * Chaque lecteur reçoit la session complète via l'extra {@link #EXTRA_SESSION}
 * et son point de départ via {@link #EXTRA_SESSION_INDEX}. Il affiche les
 * items consécutifs de son type, puis une page "Manaraka" : quand l'utilisateur
 * la feuillète, le lecteur de l'item suivant (autre type -> autre activity)
 * démarre avec une animation de slide.
 */
public final class ProgramSession {

    public static final String EXTRA_SESSION = "programSession";
    public static final String EXTRA_SESSION_INDEX = "programSessionIndex";
    /** Page de départ dans la portion (pour revenir au milieu d'un run). */
    public static final String EXTRA_SESSION_PAGE = "programSessionPage";

    /** Un item lisible du programme (type + référence + titre affiché). */
    public static class Item {
        public final String type;
        public final String reference;
        public final String title;

        public Item(String type, String reference, String title) {
            this.type = type;
            this.reference = reference;
            this.title = title;
        }
    }

    private ProgramSession() {}

    // =========================================================
    // (DE)SÉRIALISATION JSON (org.json, inclus dans Android)
    // =========================================================

    /** Sérialise la session en JSON compact pour un extra d'Intent. */
    public static String encode(List<Item> items) {
        JSONArray arr = new JSONArray();
        if (items != null) {
            for (Item it : items) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("t", it.type != null ? it.type : "verse");
                    o.put("r", it.reference != null ? it.reference : "");
                    o.put("l", it.title != null ? it.title : "");
                    arr.put(o);
                } catch (JSONException ignored) {
                    // Item sauté en cas de souci : la session reste lisible
                }
            }
        }
        return arr.toString();
    }

    /** Désérialise la session (jamais null, liste vide en cas d'erreur). */
    public static List<Item> decode(String json) {
        List<Item> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Item(
                        o.optString("t", "verse"),
                        o.optString("r", ""),
                        o.optString("l", "")));
            }
        } catch (JSONException ignored) {
            // Session corrompue : on rend une liste vide
        }
        return out;
    }

    // =========================================================
    // NAVIGATION DANS LA SESSION
    // =========================================================

    /** Même type de contenu (null-safe). */
    private static boolean sameType(Item a, Item b) {
        return a.type == null ? b.type == null : a.type.equals(b.type);
    }

    /**
     * Items consécutifs de même type à partir de {@code index} : la portion
     * de session que le lecteur courant peut afficher sans changer d'activity.
     */
    public static List<Item> sameTypeRun(List<Item> session, int index) {
        List<Item> run = new ArrayList<>();
        if (session == null || index < 0 || index >= session.size()) return run;
        for (int i = index; i < session.size(); i++) {
            if (!sameType(session.get(index), session.get(i))) break;
            run.add(session.get(i));
        }
        return run;
    }

    /**
     * Début de la portion (run) de même type qui contient l'item à
     * {@code index} : utile pour ancrer un lecteur au milieu d'un run.
     */
    public static int runStart(List<Item> session, int index) {
        if (session == null || index < 0 || index >= session.size()) {
            return Math.max(index, 0);
        }
        int i = index;
        while (i > 0 && sameType(session.get(i - 1), session.get(index))) i--;
        return i;
    }

    /**
     * Ouvre le lecteur adapté au type de la portion qui commence à
     * {@code index}, animé, en arrivant sur la page 0 de la portion.
     */
    public static void openReader(Context context, String sessionJson, int index) {
        startReader(context, sessionJson, index, 0, true);
    }

    /**
     * Comme {@link #openReader(Context, String, int)} mais en arrivant
     * directement sur {@code page} dans la portion (swipe arrière, ancrage
     * sur un item précis).
     */
    public static void openReader(Context context, String sessionJson,
                                  int index, int page) {
        startReader(context, sessionJson, index, page, true);
    }

    private static void startReader(Context context, String sessionJson,
                                    int index, int page, boolean forward) {
        List<Item> session = decode(sessionJson);
        if (session.isEmpty()) return;

        if (index < 0) index = 0;
        if (index >= session.size()) index = session.size() - 1;

        String type = session.get(index).type;
        Class<?> target;
        if ("song".equals(type)) {
            target = SongDetailActivity.class;
        } else if ("fanekena".equals(type)) {
            target = FanekenaActivity.class;
        } else {
            target = VersesActivity.class;
        }

        Intent intent = new Intent(context, target);
        intent.putExtra(EXTRA_SESSION, sessionJson);
        intent.putExtra(EXTRA_SESSION_INDEX, index);
        intent.putExtra(EXTRA_SESSION_PAGE, Math.max(page, 0));
        context.startActivity(intent);

        // Glissement horizontal, dans le sens du swipe : avant = entrée par
        // la droite, arrière = entrée par la gauche. Opaque -> aucun flash,
        // même en enchaînement rapide entre lecteurs de types différents.
        if (context instanceof Activity) {
            ((Activity) context).overridePendingTransition(
                    forward ? R.anim.slide_in_right : R.anim.slide_in_left,
                    forward ? R.anim.slide_out_left : R.anim.slide_out_right);
        }
    }

    /**
     * Ouvre directement une lecture isolée (type + référence), avec la même
     * transition horizontale que les lecteurs. Utilisé par les suggestions
     * d'historique des pages "Manaraka" / "Teo aloha".
     */
    public static void openSingle(Context context, String reference, String type) {
        if (context == null || reference == null || reference.isEmpty()) return;

        Intent intent;
        if ("song".equals(type)) {
            intent = new Intent(context, SongDetailActivity.class);
            intent.putExtra("songId", reference);
            intent.putExtra("title", "");
        } else if ("fanekena".equals(type)) {
            intent = new Intent(context, FanekenaActivity.class);
            intent.putExtra("code", reference);
        } else {
            intent = new Intent(context, VersesActivity.class);
            intent.putExtra("reading", reference);
        }
        context.startActivity(intent);

        // Même transition horizontale que le reste de la session
        if (context instanceof Activity) {
            ((Activity) context).overridePendingTransition(
                    R.anim.slide_in_right, R.anim.slide_out_left);
        }
    }

    /**
     * Avance vers l'item suivant : démarre son lecteur (animé) puis ferme
     * l'activity courante. Appelé quand l'utilisateur feuillète la page
     * "Manaraka".
     */
    public static void finishAndAdvance(AppCompatActivity current,
                                        String sessionJson,
                                        int nextRunStart) {
        startReader(current, sessionJson, nextRunStart, 0, true);
        current.finish();
    }

    /**
     * Recule vers l'item précédent : redémarre son lecteur (animé vers la
     * gauche) puis ferme l'activity courante. Appelé quand l'utilisateur
     * feuillète la page "Teo aloha".
     */
    public static void finishAndGoBack(AppCompatActivity current,
                                       String sessionJson,
                                       int prevRunStart,
                                       int pageInRun) {
        startReader(current, sessionJson, prevRunStart, pageInRun, false);
        current.finish();
    }
}
