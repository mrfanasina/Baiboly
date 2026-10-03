package com.fa.baiboly.ui.verses;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.fa.baiboly.R;
import com.fa.baiboly.data.bible.BibleService;
import com.fa.baiboly.data.parser.ReadingParser;
import com.fa.baiboly.models.Reading;
import com.fa.baiboly.models.Verse;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

/**
 * Modal moderne (bottom sheet) affiché quand l'utilisateur touche une
 * référence de verset (carte "Fitarihana", "Mofonaina anio", ou lien
 * détecté dans le texte de réflexion).
 *
 * Affiche un aperçu du texte du verset, et propose : copier la référence,
 * la partager, ou ouvrir la vue complète (VersesActivity).
 */
public class VerseBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_REFERENCE = "arg_reference";

    // Nombre max de versets affichés dans l'aperçu (au-delà, on tronque)
    private static final int MAX_PREVIEW_VERSES = 4;

    public static void display(FragmentManager fragmentManager, String reference) {

        VerseBottomSheetFragment fragment = new VerseBottomSheetFragment();

        Bundle args = new Bundle();
        args.putString(ARG_REFERENCE, reference);
        fragment.setArguments(args);

        fragment.show(fragmentManager, "verse_bottom_sheet");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(
                R.layout.fragment_verse_bottom_sheet,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String reference = getArguments() != null
                ? getArguments().getString(ARG_REFERENCE, "")
                : "";

        TextView textReference = view.findViewById(R.id.textReferenceSheet);
        TextView textContent = view.findViewById(R.id.textVerseContentSheet);

        textReference.setText(reference);

        // =====================================================
        // CHARGEMENT DU TEXTE DU VERSET (aperçu)
        // =====================================================

        loadVersePreview(reference, textContent);

        view.findViewById(R.id.btnCopy).setOnClickListener(v -> {

            ClipboardManager clipboard = (ClipboardManager)
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE);

            ClipData clip = ClipData.newPlainText("Andininy", reference + " : " + textContent.getText());
            clipboard.setPrimaryClip(clip);

            Toast.makeText(
                    requireContext(),
                    "Voadika ny « " + reference + " »",
                    Toast.LENGTH_SHORT
            ).show();
        });

        view.findViewById(R.id.btnShare).setOnClickListener(v -> {

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, reference);

            startActivity(Intent.createChooser(shareIntent, "Zarao ny andininy"));
        });

        view.findViewById(R.id.btnOpenFull).setOnClickListener(v -> {

            Intent intent = new Intent(requireContext(), VersesActivity.class);
            intent.putExtra("reading", reference);
            startActivity(intent);

            dismiss();
        });
    }

    /**
     * Récupère et affiche un aperçu textuel des versets correspondant
     * à la référence (ex: "Jao 1:1-5"). En cas d'échec de parsing ou
     * d'absence de résultat, le TextView est simplement masqué —
     * le bouton "Hijery feno" reste la solution de repli.
     */
    private void loadVersePreview(String reference, TextView textContent) {

        if (reference == null || reference.trim().isEmpty()) {
            textContent.setVisibility(View.GONE);
            return;
        }

        try {
            BibleService bibleService = new BibleService(requireContext());

            ReadingParser parser = new ReadingParser();
            parser.setService(bibleService);

            Reading reading = parser.parse(reference);

            if (reading == null) {
                textContent.setVisibility(View.GONE);
                return;
            }

            List<Verse> verseList =
                    bibleService.getVerseObjectsFromReading(reading);

            if (verseList == null || verseList.isEmpty()) {
                textContent.setVisibility(View.GONE);
                return;
            }

            StringBuilder preview = new StringBuilder();
            int limit = Math.min(verseList.size(), MAX_PREVIEW_VERSES);

            for (int i = 0; i < limit; i++) {
                Verse v = verseList.get(i);

                preview.append(v.getNumber())
                        .append(". ")
                        .append(v.getText().replace("\n", " ").trim());

                if (i < limit - 1) {
                    preview.append("  ");
                }
            }

            if (verseList.size() > MAX_PREVIEW_VERSES) {
                preview.append(" …");
            }

            textContent.setText(preview.toString());
            textContent.setVisibility(View.VISIBLE);

        } catch (Exception e) {
            // On ne bloque jamais l'ouverture du sheet pour un souci d'aperçu
            textContent.setVisibility(View.GONE);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        // Fond transparent pour que les coins arrondis du layout soient visibles
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}