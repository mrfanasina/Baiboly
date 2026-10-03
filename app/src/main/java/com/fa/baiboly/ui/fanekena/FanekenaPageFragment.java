package com.fa.baiboly.ui.fanekena;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.text.LineBreaker;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.fa.baiboly.R;
import com.fa.baiboly.data.fanekena.FanekenaService;
import com.fa.baiboly.models.Fanekena;

/**
 * Une page du ViewPager2 de FanekenaActivity : affiche un seul fanekena
 * (titre + contenu) avec la taille de police et la justification des réglages.
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

        title.setText(CodeAdapter.getDisplayName(code));
        if (fanekena != null) {
            content.setText(fanekena.getText());
        }

        applyReadingSettings(content);
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
