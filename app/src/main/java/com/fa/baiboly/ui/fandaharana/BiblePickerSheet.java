package com.fa.baiboly.ui.fandaharana;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.data.bible.BibleService;
import com.fa.baiboly.models.Book;
import com.fa.baiboly.models.Chapter;
import com.fa.baiboly.models.Reading;
import com.fa.baiboly.models.Verse;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Sélecteur Bible en BottomSheet réutilisant BibleService (la bibliothèque
 * Bible existante) : Livre -> Chapitre -> Verset(s) -> ajout au programme.
 * Trois étapes dans une seule feuille, bouton retour selon l'étape.
 */
public class BiblePickerSheet extends BottomSheetDialogFragment {

    /** Résultat transmis à ProgramDetailActivity. */
    public interface OnBiblePicked {
        void onBiblePicked(String reference, String title);
    }

    private static final int STEP_BOOK = 0;
    private static final int STEP_CHAPTER = 1;
    private static final int STEP_VERSE = 2;

    private final OnBiblePicked listener;

    private BibleService bibleService;

    private TextView tvTitle, tvSelection, btnConfirm;
    private TextView btnFullChapter;
    private ImageButton btnBack;
    private RecyclerView recycler;

    private List<Book> books = new ArrayList<>();
    private Book currentBook;
    private Chapter currentChapter;
    private int startVerse = -1;
    private int endVerse = -1;

    /** Dernier chapitre affiché (pour "chapitre feno" depuis l'étape chapitre). */
    private int lastShownChapter = -1;

    private int step = STEP_BOOK;

    public BiblePickerSheet(OnBiblePicked listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_bible_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bibleService = new BibleService(requireContext());

        tvTitle = view.findViewById(R.id.tvTitle);
        tvSelection = view.findViewById(R.id.tvSelection);
        btnConfirm = view.findViewById(R.id.btnConfirm);
        btnFullChapter = view.findViewById(R.id.btnFullChapter);
        btnBack = view.findViewById(R.id.btnBack);
        recycler = view.findViewById(R.id.recyclerPicker);

        recycler.setLayoutManager(new GridLayoutManager(requireContext(), 4));

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());
        btnBack.setOnClickListener(v -> goBack());
        btnConfirm.setOnClickListener(v -> confirmVerses());
        btnFullChapter.setOnClickListener(v -> confirmFullChapter());

        showBooks();
    }

    // =====================================================
    // NAVIGATION ENTRE ÉTAPES
    // =====================================================

    private void goBack() {
        if (step == STEP_VERSE) {
            showChapters(currentBook);
        } else if (step == STEP_CHAPTER) {
            showBooks();
        } else {
            dismiss();
        }
    }

    private void updateChrome() {
        btnBack.setVisibility(step != STEP_BOOK ? View.VISIBLE : View.INVISIBLE);
        btnFullChapter.setVisibility(step == STEP_CHAPTER ? View.VISIBLE : View.GONE);
        btnConfirm.setVisibility(step == STEP_VERSE ? View.VISIBLE : View.GONE);

        if (step == STEP_BOOK) {
            tvTitle.setText("Safidy Bibilia");
            tvSelection.setVisibility(View.GONE);
        } else if (step == STEP_CHAPTER) {
            tvTitle.setText(currentBook.getLongName());
            tvSelection.setVisibility(View.GONE);
        } else {
            tvTitle.setText(currentBook.getLongName() + " "
                    + currentChapter.getChapterNumber());
            tvSelection.setVisibility(View.VISIBLE);
            updateSelectionText();
        }
    }

    private void updateSelectionText() {
        if (startVerse == -1) {
            tvSelection.setText("Safidio ny andininy (na maromaro)");
            btnConfirm.setEnabled(false);
        } else if (endVerse == -1) {
            tvSelection.setText("Andininy " + startVerse
                    + " — tsindrio ny farany (na tsindrio indray)");
            btnConfirm.setEnabled(false);
        } else {
            tvSelection.setText(startVerse == endVerse
                    ? "Andininy " + startVerse
                    : "Andininy " + startVerse + "-" + endVerse);
            btnConfirm.setEnabled(true);
        }
    }

    // =====================================================
    // ÉTAPE 1 : LIVRES
    // =====================================================

    private void showBooks() {
        step = STEP_BOOK;
        updateChrome();

        if (books.isEmpty()) {
            books.addAll(bibleService.getAllBooksOrdered());
        }

        final List<Book> list = books;
        recycler.setAdapter(new RecyclerView.Adapter<CellVH>() {
            @NonNull
            @Override
            public CellVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_picker_cell, parent, false);
                return new CellVH(v);
            }

            @Override
            public void onBindViewHolder(@NonNull CellVH holder, int position) {
                Book b = list.get(position);
                holder.text.setText(b.getShortName());
                holder.text.setBackgroundResource(R.drawable.bg_picker_cell);
                holder.itemView.setOnClickListener(v -> showChapters(b));
            }

            @Override
            public int getItemCount() {
                return list.size();
            }
        });
    }

    // =====================================================
    // ÉTAPE 2 : CHAPITRES
    // =====================================================

    private void showChapters(Book book) {
        step = STEP_CHAPTER;
        currentBook = book;
        startVerse = -1;
        endVerse = -1;
        updateChrome();

        final List<Chapter> chapters = bibleService.getChapters(book.getId());

        recycler.setAdapter(new RecyclerView.Adapter<CellVH>() {
            @NonNull
            @Override
            public CellVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_picker_cell, parent, false);
                return new CellVH(v);
            }

            @Override
            public void onBindViewHolder(@NonNull CellVH holder, int position) {
                Chapter c = chapters.get(position);
                holder.text.setText(String.valueOf(c.getChapterNumber()));
                holder.text.setBackgroundResource(R.drawable.bg_picker_cell);
                holder.itemView.setOnClickListener(v -> {
                    lastShownChapter = c.getChapterNumber();
                    showVerses(c);
                });
            }

            @Override
            public int getItemCount() {
                return chapters.size();
            }
        });
    }

    // =====================================================
    // ÉTAPE 3 : VERSETS
    // =====================================================

    private void showVerses(Chapter chapter) {
        step = STEP_VERSE;
        currentChapter = chapter;
        startVerse = -1;
        endVerse = -1;
        updateChrome();

        final List<Verse> verses = bibleService.getVerseObjectsFromReading(
                new Reading(currentBook, chapter.getChapterNumber(), 1,
                        chapter.getChapterNumber(), 999));

        RecyclerView.Adapter<CellVH> verseAdapter = new RecyclerView.Adapter<CellVH>() {
            @NonNull
            @Override
            public CellVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_picker_cell, parent, false);
                return new CellVH(v);
            }

            @Override
            public void onBindViewHolder(@NonNull CellVH holder, int position) {
                final int verseNum = verses.get(position).getNumber();
                holder.text.setText(String.valueOf(verseNum));

                // Mise en évidence de la sélection courante
                boolean selected =
                        (verseNum == startVerse)
                                || (endVerse != -1
                                && verseNum >= startVerse && verseNum <= endVerse);
                holder.text.setBackgroundResource(selected
                        ? R.drawable.bg_selected_item : R.drawable.bg_picker_cell);
                holder.text.setTextColor(selected
                        ? Color.WHITE : holder.defaultTextColor());

                holder.itemView.setOnClickListener(v -> {
                    onVerseClicked(verseNum);
                    notifyDataSetChanged();
                });
            }

            @Override
            public int getItemCount() {
                return verses.size();
            }
        };

        recycler.setAdapter(verseAdapter);
    }

    /** Logique de sélection : début -> fin (ou simple clic = verset unique). */
    private void onVerseClicked(int verseNum) {
        if (startVerse == -1) {
            startVerse = verseNum;
        } else if (endVerse == -1 && verseNum >= startVerse) {
            endVerse = verseNum; // 2e clic >= début : ferme la plage
        } else {
            // Nouvelle sélection
            startVerse = verseNum;
            endVerse = -1;
        }
        updateSelectionText();
    }

    // =====================================================
    // CONFIRMATIONS
    // =====================================================

    private void confirmFullChapter() {
        if (currentBook == null) return;

        // Chapitre choisi dans l'étape chapitre ou versets
        int chapter = currentChapter != null
                ? currentChapter.getChapterNumber() : lastShownChapter;
        if (chapter == -1) return;

        String ref = currentBook.getShortName() + " " + chapter;
        deliver(ref, ref);
    }

    private void confirmVerses() {
        if (currentBook == null || currentChapter == null
                || startVerse == -1 || endVerse == -1) return;

        String ref = currentBook.getShortName() + " "
                + currentChapter.getChapterNumber() + ":"
                + startVerse
                + (endVerse != startVerse ? "-" + endVerse : "");
        String title = currentBook.getLongName() + " "
                + currentChapter.getChapterNumber() + ":"
                + startVerse
                + (endVerse != startVerse ? "-" + endVerse : "");
        deliver(ref, title);
    }

    private void deliver(String reference, String title) {
        if (listener != null) {
            listener.onBiblePicked(reference, title);
        }
        dismiss();
    }

    // =====================================================
    // VIEW HOLDER
    // =====================================================

    static class CellVH extends RecyclerView.ViewHolder {
        TextView text;
        private final int defaultTextColor;

        CellVH(View v) {
            super(v);
            text = v.findViewById(R.id.cellText);
            // Couleur du thème capturée avant tout changement de sélection
            defaultTextColor = text.getCurrentTextColor();
        }

        int defaultTextColor() {
            return defaultTextColor;
        }
    }
}
