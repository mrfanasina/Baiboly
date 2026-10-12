package com.fa.baiboly.ui.fanekena;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.text.LineBreaker;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.fa.baiboly.R;
import com.fa.baiboly.data.fanekena.FanekenaService;
import com.fa.baiboly.models.Fanekena;

/**
 * Une page du ViewPager2 de FanekenaActivity : affiche un seul fanekena
 * (titre + contenu) avec la taille de police et la justification des réglages.
 *
 * Sélection (comme dans la Bible) : le tap sur le contenu le surligne et
 * affiche une barre flottante Copier / Fermer ; le bouton Copier copie
 * référence + texte puis désélectionne.
 *
 * Scroll : quand le titre de la page disparaît derrière la toolbar, il est
 * remonté à l'activity pour être affiché dans la topbar (même principe que
 * Fihirana).
 */
public class FanekenaPageFragment extends Fragment {

    private static final String ARG_CODE = "code";
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_TEXT_SIZE = "text_size";
    private static final String KEY_JUSTIFICATION = "justification_mode";

    public static FanekenaPageFragment newInstance(String code) {
        FanekenaPageFragment fragment = new FanekenaPageFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CODE, code);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_fanekena_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String code = getArguments() != null ? getArguments().getString(ARG_CODE) : null;
        if (code == null) return;

        Fanekena fanekena = new FanekenaService(requireContext()).getByCode(code);

        android.widget.TextView title = view.findViewById(R.id.textTitle);
        android.widget.TextView content = view.findViewById(R.id.content);
        View selectionBar = view.findViewById(R.id.selectionBar);

        String displayName = CodeAdapter.getDisplayName(code);

        title.setText(displayName);
        if (fanekena != null) {
            content.setText(fanekena.getText());

            // Tap sur le contenu : bascule la sélection (comme un verset)
            content.setOnClickListener(v -> {
                boolean nowSelected = !content.isSelected();
                content.setSelected(nowSelected);
                selectionBar.setVisibility(
                        nowSelected ? View.VISIBLE : View.GONE);
            });
        }

        // Bouton Copier : référence + texte dans le presse-papiers
        view.findViewById(R.id.btnCopySelection).setOnClickListener(v -> {
            if (fanekena == null) return;
            copyFanekena(requireContext(), displayName,
                    fanekena.getText());

            content.setSelected(false);
            selectionBar.setVisibility(View.GONE);
        });

        // Bouton Fermer : désélectionne
        view.findViewById(R.id.btnCloseSelection).setOnClickListener(v -> {
            content.setSelected(false);
            selectionBar.setVisibility(View.GONE);
        });

        applyReadingSettings(content);

        setupScrollTitleTracking(view, title);
    }

    // =====================================================
    // TITRE DANS LA TOPBAR AU SCROLL
    // =====================================================

    /** Prépare le suivi du scroll : dès que le titre de la page est
     *  remonté derrière la toolbar, l'activity l'affiche dans la topbar. */
    private void setupScrollTitleTracking(@NonNull View view,
                                          android.widget.TextView title) {
        androidx.core.widget.NestedScrollView scroll =
                view.findViewById(R.id.scroll);

        scroll.setOnScrollChangeListener(
                (androidx.core.widget.NestedScrollView v, int sx, int sy,
                 int oldSx, int oldSy) -> reportTitleVisibility(title, sy));

        // État initial (utile après un swipe : la page peut être déjà
        // enroulée). Différé d'un frame : le layout n'existe pas encore
        // au moment de onViewCreated.
        scroll.post(() -> {
            if (getView() == null) return;
            reportTitleVisibility(title, scroll.getScrollY());
        });
    }

    /** Remonte l'état de visibilité du titre à l'activity. */
    private void reportTitleVisibility(android.widget.TextView title, int scrollY) {
        if (getActivity() instanceof FanekenaActivity) {
            ((FanekenaActivity) getActivity()).onTitleScrolled(
                    scrollY > title.getBottom());
        }
    }

    // =====================================================
    // SÉLECTION / COPIE
    // =====================================================

    /** Copie un fanekena (référence + texte) dans le presse-papiers. */
    private void copyFanekena(Context context, String reference, String text) {
        ClipboardManager cm = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);

        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(
                    "Fanekena", reference + " : " + text));
            Toast.makeText(context,
                    "Voadika ny « " + reference + " »",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Taille de police + justification, relus à chaque affichage de page.
     */
    private void applyReadingSettings(android.widget.TextView content) {
        SharedPreferences prefs =
                requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        content.setTextSize(prefs.getFloat(KEY_TEXT_SIZE, 18f));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean isJustified = prefs.getBoolean(KEY_JUSTIFICATION, true);

            content.setJustificationMode(
                    isJustified
                            ? LineBreaker.JUSTIFICATION_MODE_INTER_WORD
                            : LineBreaker.JUSTIFICATION_MODE_NONE
            );
        }
    }
}
