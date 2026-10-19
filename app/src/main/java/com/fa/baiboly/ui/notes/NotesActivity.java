package com.fa.baiboly.ui.notes;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.data.ColorManager;
import com.fa.baiboly.data.notes.NoteService;
import com.fa.baiboly.data.notes.richtext.RichTextCodec;
import com.fa.baiboly.databinding.ActivityNotesBinding;
import com.fa.baiboly.models.Note;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * 📝 Écran central des notes de lecture : recherche, filtres par source
 * (versets / chants / programmes / libres), épinglées d'abord, tap long
 * = menu (épingler, copier, partager, supprimer), FAB = note libre.
 */
public class NotesActivity extends AppCompatActivity
        implements NotesAdapter.Listener {

    private ActivityNotesBinding binding;
    private NoteService noteService;
    private NotesAdapter adapter;

    private String search = "";
    private String sourceFilter = "all";
    private final Handler searchHandler = new Handler(Looper.getMainLooper());

    private final Runnable reloadRunnable = this::reload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyGlobalTheme();
        super.onCreate(savedInstanceState);
        binding = ActivityNotesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        noteService = new NoteService(this);

        Toolbar toolbar = binding.toolbar;
        toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new NotesAdapter(this);
        binding.recyclerNotes.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerNotes.setAdapter(adapter);

        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                search = s.toString().trim();
                // 300 ms de debounce : ne frappe la base qu'une fois
                searchHandler.removeCallbacks(reloadRunnable);
                searchHandler.postDelayed(reloadRunnable, 300);
            }
        });

        binding.chipTypes.setOnCheckedStateChangeListener(
                (ChipGroup group, List<Integer> checkedIds) -> {
                    if (checkedIds.isEmpty()) return;
                    int id = checkedIds.get(0);
                    if (id == R.id.chipVerses) sourceFilter = Note.TYPE_VERSE;
                    else if (id == R.id.chipSongs) sourceFilter = Note.TYPE_SONG;
                    else if (id == R.id.chipPrograms) sourceFilter = Note.TYPE_PROGRAM;
                    else if (id == R.id.chipFree) sourceFilter = Note.TYPE_FREE;
                    else sourceFilter = "all";
                    reload();
                });

        binding.fabNew.setOnClickListener(v ->
                NoteEditorActivity.open(this, null, Note.TYPE_FREE, "", "", null));

        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload(); // les éditions de l'éditeur arrivent ici
    }

    private void reload() {
        List<Note> notes = noteService.getAll(search, sourceFilter);
        adapter.setItems(notes);
        binding.textEmpty.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
        binding.recyclerNotes.setVisibility(notes.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // =========================================================
    // ACTIONS SUR UNE NOTE (tap / tap long)
    // =========================================================
    @Override
    public void onNoteClicked(Note note) {
        NoteEditorActivity.open(this, note, note.sourceType,
                note.refLabel, note.refKey, null);
    }

    @Override
    public void onNoteLongClicked(Note note) {
        String title = note.refLabel == null || note.refLabel.isEmpty()
                ? "Fanamarihana" : note.refLabel;
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setItems(new CharSequence[]{
                        note.pinned ? "Désépingler" : "Épingler",
                        "Adikao (copier)",
                        "Zaraina",
                        "Fafao"
                }, (d, which) -> {
                    if (which == 0) {
                        noteService.setPinned(note.id, !note.pinned);
                        reload();
                    } else if (which == 1) {
                        copyNote(note);
                    } else if (which == 2) {
                        shareNote(note);
                    } else {
                        confirmDelete(note);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String plainOf(Note note) {
        String text = RichTextCodec.toPlainText(note.bodyJson);
        if (note.refLabel != null && !note.refLabel.isEmpty()) {
            text = note.refLabel + " \u2014 " + text;
        }
        return text;
    }

    private void copyNote(Note note) {
        ClipboardManager cm = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("note", plainOf(note)));
        }
        Toast.makeText(this, "Voakopia", Toast.LENGTH_SHORT).show();
    }

    private void shareNote(Note note) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, plainOf(note));
        startActivity(Intent.createChooser(share, "Zaraina"));
    }

    private void confirmDelete(Note note) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Ffafana ilay izy ?")
                .setMessage(note.refLabel == null
                        ? "Ny fanamarihana ho lasy tanteraka."
                        : "Ny fanamarihana amin'ny " + note.refLabel
                        + " ho lasy tanteraka.")
                .setPositiveButton("Fafao", (d, w) -> {
                    noteService.delete(note.id);
                    reload();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void applyGlobalTheme() {
        SharedPreferences prefs =
                getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        int savedMode = prefs.getInt("theme_mode",
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);
        ColorManager.applyTheme(this);
    }

    /** Entrée pratique depuis le menu principal. */
    public static void open(android.content.Context context) {
        context.startActivity(new Intent(context, NotesActivity.class));
    }
}
