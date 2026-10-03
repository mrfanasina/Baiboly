package com.fa.baiboly.ui.fihirana;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.fa.baiboly.R;
import com.fa.baiboly.data.fihirana.FihiranaService;
import com.fa.baiboly.databinding.FragmentNotificationsBinding;
import com.fa.baiboly.models.Song;

import java.util.List;

/**
 * Fragment handling song selection via a numeric keypad and category selector.
 */
public class FihiranaFragment extends Fragment {

    private FragmentNotificationsBinding binding;
    private FihiranaService service;
    private String currentInput = "";
    private String selectedCategory = "";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        service = new FihiranaService(requireContext());

        setupNumPad();
        setupRecyclerView();
        loadCategories();
        setupCategorySelector();

        // Setup click on the central number to open the song directly
        binding.tvSongNumber.setOnClickListener(v -> validateAndOpenSong());
    }

    /**
     * Initializes the numeric keypad logic and constraints.
     */
    private void setupNumPad() {
        View.OnClickListener numberListener = v -> {
            String val = (String) v.getTag();

            // Constraint: Prevent leading zeros
            if (currentInput.isEmpty() && val.equals("0")) {
                showInputError();
                return;
            }

            int maxSongs = service.getSongCountByCategory(selectedCategory);
            int maxChars = String.valueOf(maxSongs).length();

            if (currentInput.length() < maxChars) {
                String nextInput = currentInput + val;
                try {
                    // Constraint: Check if current typing exceeds max songs
                    if (Integer.parseInt(nextInput) <= maxSongs) {
                        currentInput = nextInput;
                        updateDisplay();
                    } else {
                        showInputError(); // Feedback if number is too high
                    }
                } catch (NumberFormatException ignored) {
                    showInputError();
                }
            } else {
                showInputError(); // Feedback if max characters reached
            }
        };

        for (int i = 0; i < binding.numPad.getChildCount(); i++) {
            View child = binding.numPad.getChildAt(i);
            if (child instanceof Button && child.getTag() != null) {
                child.setOnClickListener(numberListener);
            }
        }

        binding.btnBackspace.setOnClickListener(v -> backspaceAction());

        binding.btnBackspacePad.setOnClickListener(v -> {
            currentInput = "";
            updateDisplay();
        });

        binding.btnValidate.setOnClickListener(v -> validateAndOpenSong());
    }

    private final Runnable resetColorRunnable = () -> {
        if (binding != null) {
            binding.tvSongNumber.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.blue)
            );
        }
    };

    /**
     * Shows a visual feedback (shake + red color)
     * when input is invalid or exceeds max.
     */
    private void showInputError() {

        // Animation
        Animation shake = AnimationUtils.loadAnimation(
                requireContext(),
                android.R.anim.fade_in
        );

        binding.tvSongNumber.startAnimation(shake);

        // Rouge temporaire
        binding.tvSongNumber.setTextColor(
                Color.parseColor("#EF4444")
        );

        // Annule anciens callbacks
        binding.tvSongNumber.removeCallbacks(resetColorRunnable);

        // Revenir à la couleur d'origine
        binding.tvSongNumber.postDelayed(
                resetColorRunnable,
                500
        );
    }

    /**
     * Validates input and opens the corresponding song activity.
     */
    private void validateAndOpenSong() {
        if (currentInput.isEmpty()) return;

        if (selectedCategory == null || selectedCategory.isEmpty()) {
            Toast.makeText(requireContext(), "Mifidiana sokajy", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int number = Integer.parseInt(currentInput);
            Song song = service.getSongByNumberAndCategory(number, selectedCategory);

            if (song != null) {
                SongDetailActivity.open(requireContext(), song.getId(), song.getTitle());
                // Reset after successful open
                currentInput = "";
                updateDisplay();
            } else {
                showInputError();
                Toast.makeText(requireContext(), "Tsy hita", Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    private void backspaceAction() {
        if (!currentInput.isEmpty()) {
            currentInput = currentInput.substring(0, currentInput.length() - 1);
            updateDisplay();
        }
    }

    private void setupCategorySelector() {
        List<String> categories = service.getCategories();

        if (categories != null && !categories.isEmpty()) {
            int defaultIndex = Math.min(categories.size() - 1, 2);
            selectedCategory = categories.get(defaultIndex);
            binding.tvCategorySelected.setText(selectedCategory);
            updateSongCount();
        }

        binding.btnSelectCategory.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), v);
            for (String cat : categories) {
                popup.getMenu().add(cat);
            }

            popup.setOnMenuItemClickListener(item -> {
                selectedCategory = item.getTitle().toString();
                binding.tvCategorySelected.setText(selectedCategory);
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
            binding.tvSongCount.setText("Hira misy: " + count);
        }
    }

    private void updateDisplay() {
        binding.tvSongNumber.setText(currentInput.isEmpty() ? "---" : currentInput);

        boolean hasInput = !currentInput.isEmpty();
        binding.btnValidate.setEnabled(hasInput);
        binding.btnValidate.setAlpha(hasInput ? 1.0f : 0.5f);

        // Make the textview clickable only when there is input
        binding.tvSongNumber.setClickable(hasInput);
    }

    private void setupRecyclerView() {
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 2);
        binding.recyclerCategories.setLayoutManager(layoutManager);
        binding.recyclerCategories.setHasFixedSize(true);
    }

    private void loadCategories() {
        List<String> categories = service.getCategories();
        CategoryAdapter adapter = new CategoryAdapter(categories, category -> {
            Intent intent = new Intent(requireContext(), SongListActivity.class);
            intent.putExtra("category", category);
            startActivity(intent);
        });
        binding.recyclerCategories.setAdapter(adapter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}