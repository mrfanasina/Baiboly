package com.fa.baiboly.ui.fihirana;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.models.SongVerse;

import java.util.List;

/**
 * Fragment that displays the verses of a single song.
 * Used inside ViewPager2 for smooth horizontal swiping between songs.
 *
 * L'en-tête ("Fihirana FFPM | 49") est le premier item du RecyclerView :
 * il scrolle avec les paroles et le tap ouvre le pad de sélection d'un
 * autre chant. Pendant le scroll vertical, la position de l'en-tête est
 * remontée à l'activité pour permuter le titre de la toolbar ("Fihirana"
 * -> "FFPM 49") avec un petit fondu.
 *
 * Sélection des versets : un tap surligne un verset (comme dans la Bible) ;
 * quand au moins un verset est sélectionné, une barre flottante
 * Copier / Fermer apparaît en bas à droite.
 */
public class SongPageFragment extends Fragment {

    private static final String ARG_VERSES = "verses";
    private static final String ARG_TEXT_SIZE = "text_size";
    private static final String ARG_SONG_ID = "song_id";

    /**
     * Create a new instance with the given song id, verses and text size.
     */
    public static SongPageFragment newInstance(String songId,
                                               List<SongVerse> verses,
                                               float textSize) {
        SongPageFragment fragment = new SongPageFragment();
        Bundle args = new Bundle();
        // SongVerse must be Serializable or Parcelable
        args.putSerializable(ARG_VERSES, new java.util.ArrayList<>(verses));
        args.putFloat(ARG_TEXT_SIZE, textSize);
        args.putString(ARG_SONG_ID, songId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_song_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setHasFixedSize(true);

        setupScrollTracking(recyclerView);

        // Barre flottante Copier / Fermer (sélection de versets)
        View selectionBar = view.findViewById(R.id.selectionBar);
        View btnCopy = view.findViewById(R.id.btnCopySelection);
        View btnClose = view.findViewById(R.id.btnCloseSelection);

        btnClose.setOnClickListener(v -> {
            if (currentAdapter != null) currentAdapter.clearSelection();
        });

        btnCopy.setOnClickListener(v -> copySelection());

        if (getArguments() != null) {
            @SuppressWarnings("unchecked")
            List<SongVerse> verses = (List<SongVerse>) getArguments().getSerializable(ARG_VERSES);
            float textSize = getArguments().getFloat(ARG_TEXT_SIZE, 18f);
            String songId = getArguments().getString(ARG_SONG_ID);

            if (verses != null) {
                SongVersesAdapter adapter = new SongVersesAdapter(verses, textSize, songId,
                        () -> {
                            if (getActivity() instanceof SongDetailActivity) {
                                ((SongDetailActivity) getActivity()).openSongPicker();
                            }
                        },
                        count -> {
                            // Barre visible seulement avec >= 1 verset choisi
                            selectionBar.setVisibility(
                                    count > 0 ? View.VISIBLE : View.GONE);
                        });
                currentAdapter = adapter;
                recyclerView.setAdapter(adapter);
            }
        }
    }

    /** Adaptateur courant (pour vider la sélection depuis la barre). */
    private SongVersesAdapter currentAdapter;

    /** Copie les versets sélectionnés (référence + texte) et désélectionne. */
    private void copySelection() {
        if (currentAdapter == null) return;

        List<SongVerse> picked = currentAdapter.getSelectedVerses();
        if (picked.isEmpty()) return;

        String songId = getArguments() != null
                ? getArguments().getString(ARG_SONG_ID) : null;

        // Référence du chant ("FFPM 49"), même logique que l'en-tête
        String ref = "";
        if (songId != null && !songId.isEmpty()) {
            String[] parts = songId.split("_");
            ref = parts[0].toUpperCase();
            if (parts.length > 1) ref += " " + parts[1];
        }

        StringBuilder sb = new StringBuilder();

        for (SongVerse verse : picked) {
            String label = verse.getFiverenany()
                    ? "Fiv"
                    : (verse.getAndininy() != 0 ? verse.getAndininy() + "." : "");

            String lineRef = (ref + (label.isEmpty() ? "" : " " + label)).trim();

            if (sb.length() > 0) sb.append("\n");
            if (!lineRef.isEmpty()) sb.append(lineRef).append(" — ");
            sb.append(verse.getTononkira().trim());
        }

        ClipboardManager cm = (ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Tononkira", sb.toString()));
            Toast.makeText(requireContext(), "Voadika", Toast.LENGTH_SHORT).show();
        }

        currentAdapter.clearSelection();
    }

    /**
     * Prépare le suivi du scroll vertical : la position de l'en-tête est
     * remontée à l'activité pour permuter le titre de la toolbar.
     */
    private void setupScrollTracking(RecyclerView recyclerView) {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                reportHeaderState(rv);
            }
        });

        // État initial de la page (utile après un swipe : la page peut être
        // déjà enroulée sur un chant précédent). Différé d'un frame : au
        // moment de onViewCreated le layout n'existe pas encore.
        recyclerView.post(() -> {
            if (getView() == null) return;
            reportHeaderState(recyclerView);
        });
    }

    /**
     * Calcule la position de l'en-tête et la remonte à l'activité.
     * Si le premier item visible n'est pas l'en-tête (page déjà enroulée),
     * on considère l'en-tête comme disparu derrière la toolbar.
     */
    private void reportHeaderState(RecyclerView rv) {
        View first = rv.getChildAt(0);
        if (first == null) return;

        boolean isHeader = rv.getChildAdapterPosition(first) == 0;

        // Bas de l'en-tête (s'il est en haut) : <= 0 quand il disparaît
        // derrière la toolbar (bord de coupe du RecyclerView).
        float headerBottom = isHeader ? first.getBottom() : Float.NEGATIVE_INFINITY;

        // Bas de page atteint ? (le bouton flottant "Feo" n'apparaît
        // QUE quand on est arrivé tout en bas)
        boolean atBottom = !rv.canScrollVertically(1);

        if (getActivity() instanceof SongDetailActivity) {
            SongDetailActivity activity = (SongDetailActivity) getActivity();
            activity.onHeaderScrolled(headerBottom);
            activity.onPageAtBottom(atBottom);
        }
    }
}
