package com.fa.baiboly.ui.baiboly;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.fa.baiboly.R;
import com.fa.baiboly.data.bible.BibleService;
import com.fa.baiboly.data.fandaharana.FandaharanaService;
import com.fa.baiboly.databinding.FragmentChapterBinding;
import com.fa.baiboly.models.Book;
import com.fa.baiboly.models.Chapter;
import com.fa.baiboly.models.Program;
import com.fa.baiboly.ui.verses.VersesActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment responsible for:
 *
 * - Chapter selection
 * - Start verse selection
 * - End verse selection
 * - Multi-chapter verse range selection
 *
 * Example:
 * Genesis 1:10-2:5
 */
public class ChapterFragment extends Fragment {

    private FragmentChapterBinding binding;
    private BibleService bibleService;
    private FandaharanaService fandaharanaService;
    private Book selectedBook;

    /**
     * Currently selected chapter
     */
    private Chapter selectedChapter = null;

    /**
     * Next chapter (used for cross-chapter selection)
     */
    private Chapter nextChapter = null;

    /**
     * Start verse selected by user
     */
    private int selectedStartVerse = -1;

    /**
     * End verse selected by user
     */
    private int selectedEndVerse = -1;

    /**
     * Chapter number of the end verse
     */
    private int selectedEndChapterNum = -1;

    /**
     * UI state controller
     */
    private enum SelectionState {
        CHAPTERS,
        START_VERSE,
        END_VERSE
    }

    private SelectionState currentState =
            SelectionState.CHAPTERS;

    // Mode: read or add to program
    private String currentMode = "read";
    private List<Program> programs = new ArrayList<>();
    private int selectedProgramIndex = -1;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentChapterBinding.inflate(
                inflater,
                container,
                false
        );

        bibleService = new BibleService(requireContext());
        fandaharanaService = new FandaharanaService(requireContext());

        // Get selected book from arguments
        if (getArguments() != null) {
            selectedBook =
                    (Book) getArguments().getSerializable("book");
        }

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {

        super.onViewCreated(view, savedInstanceState);

        if (selectedBook != null) {

            // Set toolbar title
            if (getActivity() != null &&
                    ((AppCompatActivity) getActivity())
                            .getSupportActionBar() != null) {

                ((AppCompatActivity) getActivity())
                        .getSupportActionBar()
                        .setTitle(selectedBook.getLongName());
            }

            updateInputText();
            showChapters();
        }

        // Back navigation button
        binding.btnBackToChapters
                .setOnClickListener(v -> handleBackNavigation());

        // Open reading from input icon
        binding.layoutInput
                .setEndIconOnClickListener(v -> openReading());

        // Open reading button
        binding.btnRead
                .setOnClickListener(v -> openReading());

        // Mode selector
        binding.modeLire.setOnClickListener(v -> selectMode("read"));
        binding.modeAddProgram.setOnClickListener(v -> selectMode("program"));

        loadPrograms();
    }

    private void selectMode(String mode) {
        currentMode = mode;
        if ("read".equals(mode)) {
            binding.modeLire.setBackgroundResource(R.drawable.bg_badge_verset);
            binding.modeLire.setTextColor(android.graphics.Color.WHITE);
            binding.modeAddProgram.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            binding.modeAddProgram.setTextColor(android.graphics.Color.parseColor("#9CA3AF"));
            binding.programSelector.setVisibility(android.view.View.GONE);
            binding.btnRead.setText("Vakiana");
        } else {
            binding.modeAddProgram.setBackgroundResource(R.drawable.bg_badge_verset);
            binding.modeAddProgram.setTextColor(android.graphics.Color.WHITE);
            binding.modeLire.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            binding.modeLire.setTextColor(android.graphics.Color.parseColor("#9CA3AF"));
            binding.programSelector.setVisibility(android.view.View.VISIBLE);
            binding.btnRead.setText("Ampidiro");
        }
    }

    private void loadPrograms() {
        programs = fandaharanaService.getAllPrograms();
        List<String> names = new ArrayList<>();
        for (Program p : programs) { names.add(p.getName()); }
        if (names.isEmpty()) names.add("Fandaharana voalohany");

        android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, names);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerPrograms.setAdapter(spinnerAdapter);
        binding.spinnerPrograms.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) { selectedProgramIndex = pos; }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
    }

    /**
     * Display chapters list
     */
    private void showChapters() {

        currentState = SelectionState.CHAPTERS;

        binding.btnBackToChapters.setVisibility(View.GONE);
        binding.btnRead.setVisibility(View.GONE);
        binding.tvLabelAction.setText("Toko faha :");

        List<Chapter> chapters =
                bibleService.getChapters(selectedBook.getId());

        ChapterAdapter adapter =
                new ChapterAdapter(chapters, chapter -> {

                    selectedChapter = chapter;

                    selectedEndChapterNum =
                            chapter.getChapterNumber();

                    selectedStartVerse = -1;
                    selectedEndVerse = -1;

                    prepareNextChapter(chapters, chapter);

                    updateInputText();
                    updateReadButton();

                    showStartVerseSelection();
                });

        binding.recyclerChapters.setLayoutManager(
                new GridLayoutManager(getContext(), 4)
        );

        binding.recyclerChapters.setAdapter(adapter);
    }

    /**
     * Prepare next chapter for cross-chapter selection
     */
    private void prepareNextChapter(
            List<Chapter> allChapters,
            Chapter current
    ) {

        nextChapter = null;

        for (int i = 0; i < allChapters.size(); i++) {

            if (allChapters.get(i).getChapterNumber()
                    == current.getChapterNumber()) {

                if (i + 1 < allChapters.size()) {
                    nextChapter = allChapters.get(i + 1);
                }

                break;
            }
        }
    }

    /**
     * Start verse selection (current chapter only)
     */
    private void showStartVerseSelection() {

        currentState = SelectionState.START_VERSE;

        binding.btnBackToChapters.setVisibility(View.VISIBLE);
        binding.btnRead.setVisibility(View.VISIBLE);
        binding.tvLabelAction.setText("Andininy faha :");

        int currentCount = selectedChapter.getVerseCount();

        VerseNumberAdapter adapter =
                new VerseNumberAdapter(
                        currentCount,
                        0,
                        -1,
                        (chapterOffset, verse) -> {

                            // Start verse always belongs to current chapter
                            selectedStartVerse = verse;
                            selectedEndVerse = verse;

                            selectedEndChapterNum =
                                    selectedChapter.getChapterNumber();

                            updateInputText();
                            updateReadButton();

                            showEndVerseSelection();
                        }
                );

        binding.recyclerChapters.setLayoutManager(
                new GridLayoutManager(getContext(), 5)
        );

        binding.recyclerChapters.setAdapter(adapter);
    }

    /**
     * End verse selection (supports multi-chapter)
     */
    private void showEndVerseSelection() {

        currentState = SelectionState.END_VERSE;

        binding.tvLabelAction.setText("Ka hatramin'ny :");

        int currentCount = selectedChapter.getVerseCount();

        int nextCount =
                (nextChapter != null)
                        ? nextChapter.getVerseCount()
                        : 0;

        VerseNumberAdapter adapter =
                new VerseNumberAdapter(
                        currentCount,
                        nextCount,
                        nextChapter != null
                                ? nextChapter.getChapterNumber()
                                : -1,
                        (chapterOffset, verse) -> {

                            // Current chapter
                            if (chapterOffset == 0) {

                                selectedEndChapterNum =
                                        selectedChapter.getChapterNumber();

                                selectedEndVerse = verse;

                            } else {

                                // Next chapter
                                selectedEndChapterNum =
                                        nextChapter.getChapterNumber();

                                selectedEndVerse = verse;
                            }

                            updateInputText();
                            updateReadButton();
                            openReading();
                        }
                );

        adapter.setMinVerse(selectedStartVerse);

        GridLayoutManager layoutManager =
                new GridLayoutManager(getContext(), 5);

        layoutManager.setSpanSizeLookup(
                new GridLayoutManager.SpanSizeLookup() {

                    @Override
                    public int getSpanSize(int position) {

                        // Header takes full width
                        if (adapter.getItemViewType(position) == 1) {
                            return 5;
                        }

                        return 1;
                    }
                }
        );

        binding.recyclerChapters.setLayoutManager(layoutManager);
        binding.recyclerChapters.setAdapter(adapter);
    }

    /**
     * Handle back navigation between states
     */
    private void handleBackNavigation() {

        if (currentState == SelectionState.END_VERSE) {

            selectedStartVerse = -1;
            selectedEndChapterNum =
                    selectedChapter.getChapterNumber();

            showStartVerseSelection();

        } else if (currentState == SelectionState.START_VERSE) {

            selectedChapter = null;
            showChapters();
        }

        updateInputText();
        updateReadButton();
    }

    /**
     * Build Bible reference string
     */
    private void updateInputText() {

        if (selectedBook == null) return;

        StringBuilder sb =
                new StringBuilder(selectedBook.getShortName())
                        .append(" ");

        if (selectedChapter != null) {

            sb.append(selectedChapter.getChapterNumber());

            if (selectedStartVerse != -1) {

                sb.append(":").append(selectedStartVerse);

                if (selectedEndVerse != -1) {

                    if (selectedEndChapterNum
                            != selectedChapter.getChapterNumber()) {

                        sb.append("-")
                                .append(selectedEndChapterNum)
                                .append(":")
                                .append(selectedEndVerse);

                    } else if (selectedEndVerse
                            != selectedStartVerse) {

                        sb.append("-")
                                .append(selectedEndVerse);
                    }
                }
            }
        }

        binding.inputReference.setText(sb.toString());

        binding.inputReference.setSelection(
                binding.inputReference.getText().length()
        );
    }

    /**
     * Update read button UI
     */
    private void updateReadButton() {

        if (selectedChapter == null) {
            binding.btnRead.setVisibility(View.GONE);
            return;
        }

        binding.btnRead.setVisibility(View.VISIBLE);

        if ("read".equals(currentMode)) {
            binding.btnRead.setText(binding.inputReference.getText().toString());
        } else {
            binding.btnRead.setText("Ampidiro: " + binding.inputReference.getText().toString());
        }
    }

    private void openReading() {
        String ref = binding.inputReference.getText().toString().trim();
        if (selectedChapter == null) return;

        if ("program".equals(currentMode)) {
            if (programs.isEmpty()) {
                long id = fandaharanaService.createProgram("Fandaharana voalohany");
                fandaharanaService.addItem(id, "verse", ref, ref);
                android.widget.Toast.makeText(getContext(), "Napetraka ao amin'ny fandaharana", android.widget.Toast.LENGTH_SHORT).show();
                loadPrograms();
            } else {
                Program targetProgram = programs.get(selectedProgramIndex);
                fandaharanaService.addItem(targetProgram.getId(), "verse", ref, ref);
                android.widget.Toast.makeText(getContext(), "Napetraka ao amin'ny \"" + targetProgram.getName() + "\"",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        } else {
            Intent intent = new Intent(getContext(), VersesActivity.class);
            intent.putExtra("reading", ref);
            // Choix direct : on passe le numéro canonique + les nombres
            // sélectionnés. VersesActivity construit la lecture SANS
            // re-parser le nom du livre (résolution 100% fiable).
            if (selectedBook != null) {
                intent.putExtra("bookNumber", selectedBook.getId());
                intent.putExtra("startChapter", selectedChapter.getChapterNumber());
                intent.putExtra("endChapter",
                        selectedEndChapterNum > 0
                                ? selectedEndChapterNum
                                : selectedChapter.getChapterNumber());
                if (selectedStartVerse > 0) {
                    intent.putExtra("startVerse", selectedStartVerse);
                    intent.putExtra("endVerse",
                            selectedEndVerse > 0 ? selectedEndVerse : selectedStartVerse);
                }
            }
            startActivity(intent);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}