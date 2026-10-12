package com.fa.baiboly.ui.fandaharana;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.fa.baiboly.R;
import com.fa.baiboly.data.fihirana.FihiranaService;
import com.fa.baiboly.models.Song;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

/**
 * Sélecteur de chant en BottomSheet pour le programme : même pad numérique
 * que le FihiranaFragment (choix du sokajy + laharan'ny hira), avec les
 * mêmes contraintes de saisie et le même retour d'erreur.
 * Validation -> callback vers ProgramDetailActivity.
 */
public class SongPickerSheet extends BottomSheetDialogFragment {

    public interface OnSongPicked {
        void onSongPicked(Song song);
    }

    private final OnSongPicked listener;

    private FihiranaService service;

    private String currentInput = "";
    private String selectedCategory = "";

    private TextView tvSongNumber;
    private TextView tvCategorySelected;
    private TextView tvSongCount;
    private View btnValidate;

    public SongPickerSheet(OnSongPicked listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_song_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        service = new FihiranaService(requireContext());

        tvSongNumber = view.findViewById(R.id.tvSongNumber);
        tvCategorySelected = view.findViewById(R.id.tvCategorySelected);
        tvSongCount = view.findViewById(R.id.tvSongCount);
        btnValidate = view.findViewById(R.id.btnValidate);

        setupCategorySelector(view);
        setupNumPad(view);
        updateDisplay();
    }

    // =========================================================
    // SOKAJY (identique au FihiranaFragment)
    // =========================================================

    private void setupCategorySelector(View root) {
        List<String> categories = service.getCategories();

        if (categories != null && !categories.isEmpty()) {
            // Même défaut que l'écran Fihirana (3e sokajy, ou la dernière)
            int defaultIndex = Math.min(categories.size() - 1, 2);
            selectedCategory = categories.get(defaultIndex);
            tvCategorySelected.setText(selectedCategory);
            updateSongCount();
        }

        root.findViewById(R.id.btnSelectCategory).setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), v);
            for (String cat : categories) {
                popup.getMenu().add(cat);
            }

            popup.setOnMenuItemClickListener(item -> {
                selectedCategory = item.getTitle().toString();
                tvCategorySelected.setText(selectedCategory);
                currentInput = "";
                updateDisplay();
                updateSongCount();
                return true;
            });
            popup.show();
        });
    }

    private void updateSongCount() {
        if (!selectedCategory.isEmpty()) {
            int count = service.getSongCountByCategory(selectedCategory);
            tvSongCount.setText("Hira misy: " + count);
        }
    }

    // =========================================================
    // PAD NUMÉRIQUE (identique au FihiranaFragment)
    // =========================================================

    private void setupNumPad(View root) {
        View.OnClickListener numberListener = v -> {
            String val = (String) v.getTag();

            // Pas de zéro en tête
            if (currentInput.isEmpty() && val.equals("0")) {
                showInputError();
                return;
            }

            int maxSongs = service.getSongCountByCategory(selectedCategory);
            int maxChars = String.valueOf(maxSongs).length();

            if (currentInput.length() < maxChars) {
                String nextInput = currentInput + val;
                try {
                    // La frappe ne doit pas dépasser le nombre de hira
                    if (Integer.parseInt(nextInput) <= maxSongs) {
                        currentInput = nextInput;
                        updateDisplay();
                    } else {
                        showInputError();
                    }
                } catch (NumberFormatException e) {
                    showInputError();
                }
            } else {
                showInputError();
            }
        };

        android.view.ViewGroup numPad = root.findViewById(R.id.numPad);
        for (int i = 0; i < numPad.getChildCount(); i++) {
            View child = numPad.getChildAt(i);
            if (child instanceof Button && child.getTag() != null) {
                child.setOnClickListener(numberListener);
            }
        }

        // Effacer un chiffre
        root.findViewById(R.id.btnBackspace).setOnClickListener(v -> backspaceAction());

        // Tout effacer
        root.findViewById(R.id.btnBackspacePad).setOnClickListener(v -> {
            currentInput = "";
            updateDisplay();
        });

        // Valider -> ajouter la hira au programme
        btnValidate.setOnClickListener(v -> validateAndDeliver());

        // Tap sur le numéro -> validation aussi
        tvSongNumber.setOnClickListener(v -> validateAndDeliver());
    }

    private void backspaceAction() {
        if (!currentInput.isEmpty()) {
            currentInput = currentInput.substring(0, currentInput.length() - 1);
            updateDisplay();
        }
    }

    private void updateDisplay() {
        tvSongNumber.setText(currentInput.isEmpty() ? "---" : currentInput);

        boolean hasInput = !currentInput.isEmpty();
        btnValidate.setEnabled(hasInput);
        btnValidate.setAlpha(hasInput ? 1.0f : 0.5f);

        tvSongNumber.setClickable(hasInput);
    }

    private final Runnable resetColorRunnable = () -> {
        if (tvSongNumber != null) {
            tvSongNumber.setTextColor(ContextCompat.getColor(
                    requireContext(), R.color.blue));
        }
    };

    /** Retour visuel (flash rouge) quand la saisie est invalide. */
    private void showInputError() {
        if (tvSongNumber == null) return;

        tvSongNumber.setTextColor(ContextCompat.getColor(
                requireContext(), R.color.danger));
        tvSongNumber.removeCallbacks(resetColorRunnable);
        tvSongNumber.postDelayed(resetColorRunnable, 500);
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    /** Cherche le chant saisi et le renvoie au programme. */
    private void validateAndDeliver() {
        if (currentInput.isEmpty()) return;

        if (selectedCategory == null || selectedCategory.isEmpty()) {
            Toast.makeText(requireContext(), "Mifidiana sokajy",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int number = Integer.parseInt(currentInput);
            Song song = service.getSongByNumberAndCategory(number, selectedCategory);

            if (song != null) {
                dismiss();
                if (listener != null) listener.onSongPicked(song);
            } else {
                showInputError();
                Toast.makeText(requireContext(), "Tsy hita",
                        Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            showInputError();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (tvSongNumber != null) {
            tvSongNumber.removeCallbacks(resetColorRunnable);
        }
        tvSongNumber = null;
        tvCategorySelected = null;
        tvSongCount = null;
        btnValidate = null;
    }
}
