package com.fa.baiboly.ui.reader;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.fa.baiboly.R;
import com.fa.baiboly.data.history.HistoryService;
import com.fa.baiboly.models.History;
import com.fa.baiboly.ui.fandaharana.ProgramContentTypes;
import com.fa.baiboly.ui.fanekena.CodeAdapter;
import com.fa.baiboly.ui.fihirana.SongDetailActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Page "voisine" d'un lecteur en mode session de programme : annonce
 * l'item précédent ("Teo aloha") ou suivant ("Manaraka") du programme.
 * Un swipe ou un tap dessus fait passer à cet item (l'activity hôte change
 * si le type change, avec animation de slide dans le bon sens).
 *
 * En bas de la page, un bandeau de suggestions reprend les dernières
 * lectures consultées (historique), hors type de la page voisine : un tap
 * ouvre directement la lecture (même transition que les lecteurs).
 */
public class ReaderNeighborPageFragment extends Fragment {

    /** Communication page -> activity hôte. */
    public interface Host {
        /** Tap sur la page "Manaraka". */
        void onReaderNextTapped();

        /** Tap sur la page "Teo aloha". */
        void onReaderPrevTapped();

        /** Services partagés (une seule instance DB pour toute l'activity). */
        HistoryService getHistoryService();
    }

    private static final String ARG_TITLE = "neighbor_title";
    private static final String ARG_TYPE = "neighbor_type";
    private static final String ARG_BACK = "neighbor_back";

    /** Nombre max de puces affichées (un type distinct par puce). */
    private static final int MAX_SUGGESTIONS = 3;

    public static ReaderNeighborPageFragment newInstance(String title,
                                                         String type,
                                                         boolean back) {
        ReaderNeighborPageFragment fragment = new ReaderNeighborPageFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_TYPE, type);
        args.putBoolean(ARG_BACK, back);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reader_neighbor_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        String type = args != null ? args.getString(ARG_TYPE) : null;
        String title = args != null ? args.getString(ARG_TITLE) : null;
        boolean back = args != null && args.getBoolean(ARG_BACK);

        TextView label = view.findViewById(R.id.neighborLabel);
        TextView typeLabel = view.findViewById(R.id.nextTypeLabel);
        TextView titleView = view.findViewById(R.id.nextTitle);
        View iconBg = view.findViewById(R.id.nextIconBg);
        ImageView icon = view.findViewById(R.id.nextIcon);
        ImageView arrow = view.findViewById(R.id.nextArrow);

        // Page de transition (pas de contenu scrollable) : le bouton
        // flottant "Feo" de l'activité est masqué ici.
        if (getActivity() instanceof SongDetailActivity) {
            ((SongDetailActivity) getActivity()).onPageAtBottom(false);
        }

        label.setText(back ? "Teo aloha" : "Manaraka");
        // Flèche : vers la gauche en arrière, vers la droite en avant
        arrow.setRotation(back ? 0f : 180f);

        String typeText = ProgramContentTypes.labelOf(type);
        typeLabel.setText(typeText);
        titleView.setText(title != null && !title.isEmpty() ? title : typeText);

        // Cercle coloré du type voisin (même style que la liste du programme)
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        try {
            circle.setColor(Color.parseColor(ProgramContentTypes.colorOf(type)));
        } catch (Exception e) {
            circle.setColor(Color.parseColor(ProgramContentTypes.COLOR_VERSE));
        }
        iconBg.setBackground(circle);
        icon.setImageResource(ProgramContentTypes.iconOf(type));

        view.findViewById(R.id.nextPageRoot).setOnClickListener(v -> {
            if (getActivity() instanceof Host) {
                if (back) {
                    ((Host) getActivity()).onReaderPrevTapped();
                } else {
                    ((Host) getActivity()).onReaderNextTapped();
                }
            }
        });

        setupHistoryBar(view, type);
    }

    // =========================================================
    // BANDEAU DE SUGGESTIONS (historique)
    // =========================================================

    /**
     * Affiche les dernières lectures consultées (une puce par type lu
     * récemment, hors type de la page voisine). La requête est minuscule
     * (LIMIT 5) et la page se crée hors écran -> aucun impact sur le
     * scroll/swipe visible.
     */
    private void setupHistoryBar(View root, String neighborType) {
        LinearLayout bar = root.findViewById(R.id.historyBar);
        LinearLayout chips = root.findViewById(R.id.historyChips);

        if (!(getActivity() instanceof Host)) return;
        HistoryService historyService = ((Host) getActivity()).getHistoryService();
        if (historyService == null) return;

        List<History> items = historyService.getHistoryPrev();

        // Une puce par type récent ; l'entrée la plus récente de chaque type
        // (l'item voisin est déjà proposé par la page elle-même).
        Map<String, History> firstByType = new HashMap<>();
        for (History h : items) {
            if (h.getType() == null) continue;
            // Les lecteurs stockent leurs types d'historique avec des libellés
            // différents des types de session ("fihirana" vs "song",
            // "baiboly" vs "verse") : on normalise avant de comparer.
            String sessionType = toSessionType(h.getType());
            if (sessionType == null || sessionType.equals(neighborType)) continue;
            History existing = firstByType.get(sessionType);
            if (existing == null || h.getTimestamp() > existing.getTimestamp()) {
                firstByType.put(sessionType, h);
            }
        }
        if (firstByType.isEmpty()) {
            bar.setVisibility(View.GONE);
            return;
        }

        List<History> suggestions = new ArrayList<>(firstByType.values());
        // Le plus récent d'abord, au maximum MAX_SUGGESTIONS puces
        suggestions.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        while (suggestions.size() > MAX_SUGGESTIONS) {
            suggestions.remove(suggestions.size() - 1);
        }

        for (final History h : suggestions) {
            View chip = getLayoutInflater()
                    .inflate(R.layout.history_suggestion_chip, chips, false);

            TextView chipLabel = chip.findViewById(R.id.historyChipLabel);
            chipLabel.setText(displayName(h));

            chip.setOnClickListener(v -> {
                if (getActivity() == null) return;
                ProgramSession.openSingle(getActivity(),
                        h.getReading(), toSessionType(h.getType()));
            });

            chips.addView(chip);
        }

        bar.setVisibility(View.VISIBLE);
    }

    /** Libellé affiché sur la puce : titre déjà lisible si possible. */
    private static String displayName(History h) {
        String reading = h.getReading();
        String type = h.getType();

        if ("fanekena".equals(type)) {
            String pretty = CodeAdapter.getDisplayName(reading);
            return pretty != null ? pretty : reading;
        }
        if ("fihirana".equals(type)) {
            // "ffpm_49" -> "FFPM 49"
            return reading == null ? "" : reading.toUpperCase().replace("_", " ");
        }
        return reading; // "baiboly" (et autres) : référence déjà lisible
    }

    /** Libellé d'historique du lecteur -> type de session correspondant. */
    private static String toSessionType(String historyType) {
        if (historyType == null) return null;
        switch (historyType) {
            case "fihirana": return "song";
            case "baiboly": return "verse";
            case "fanekena": return "fanekena";
            default: return null; // type sans lecteur : pas de suggestion
        }
    }
}
