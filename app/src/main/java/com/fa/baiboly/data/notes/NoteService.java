package com.fa.baiboly.data.notes;

import android.content.Context;

import androidx.annotation.Nullable;

import com.fa.baiboly.data.notes.richtext.RichTextCodec;
import com.fa.baiboly.models.Note;

import java.util.List;

/**
 * 📝 Service des notes de lecture : création (attachée à un verset, un
 * chant, un programme, ou libre), sérialisation du texte enrichi,
 * recherche et compte par source.
 */
public class NoteService {

    private final NoteRepository repository;

    public NoteService(Context context) {
        this.repository = new NoteRepository(context);
    }

    // =========================================================
    // CRÉER / OUVRIR
    // =========================================================

    /**
     * Prépare une note NON ENREGISTRÉE, prête à être éditée et sauvée par
     * {@code NoteEditorActivity} :
     *
     * @param sourceType {@link Note#TYPE_FREE} / {@link Note#TYPE_VERSE} /
     *                   {@link Note#TYPE_SONG} / {@link Note#TYPE_PROGRAM}
     * @param refLabel   référence lisible ("MAT 3:16", "FFPM 49", titra fanaharana…)
     * @param refKey     clé stable ("verse:MAT:3:16:17", "song:ffpm_49", …)
     * @param quote      texte de la lecture, proposé comme bloc citation
     * @return note à éditer (id = -1 ; à persister à la sauvegarde)
     */
    public static Note draft(String sourceType, String refLabel,
                             String refKey, @Nullable String quote) {
        Note n = new Note();
        n.sourceType = sourceType == null ? Note.TYPE_FREE : sourceType;
        n.refLabel = refLabel == null ? "" : refLabel;
        n.refKey = refKey == null ? "" : refKey;
        if (quote != null && !quote.trim().isEmpty()) {
            // 🔖 La lecture est proposée en bloc "citation", avec une ligne
            // vide de séparation pour que l'utilisateur écrive juste après.
            n.bodyJson = RichTextCodec.encodeQuotedStart(quote.trim());
        }
        return n;
    }

    /** Sérialize le corps enrichi + met à jour l'aperçu texte brut. */
    public static void fillFromSpannableBuilder(Note n,
                                                CharSequence body,
                                                String plainBody) {
        n.bodyJson = RichTextCodec.encode(body);
        n.plainText = (n.refLabel == null ? "" : n.refLabel + " ")
                + (plainBody == null ? "" : plainBody);
    }

    /** Note sauvegardée existe-t-elle pour cette source ? */
    public boolean hasNotesFor(String sourceType, String refKey) {
        return countFor(sourceType, refKey) > 0;
    }

    public int countFor(String sourceType, String refKey) {
        if (refKey == null || refKey.isEmpty()) return 0;
        return repository.countForSource(sourceType, refKey);
    }

    public List<Note> notesFor(String sourceType, String refKey) {
        List<Note> all = repository.getAll(null, sourceType);
        List<Note> out = new java.util.ArrayList<>();
        for (Note n : all) {
            if (refKey == null ? (n.refKey == null || n.refKey.isEmpty())
                    : refKey.equals(n.refKey)) {
                out.add(n);
            }
        }
        return out;
    }

    // =========================================================
    // LISTER / MODIFIER
    // =========================================================
    public List<Note> getAll(@Nullable String search,
                             @Nullable String sourceType) {
        return repository.getAll(search, sourceType);
    }

    public void save(Note n) {
        if (n.id < 0) repository.insert(n);
        else repository.update(n);
    }

    public void setPinned(long id, boolean pinned) {
        repository.setPinned(id, pinned);
    }

    public void delete(long id) {
        repository.delete(id);
    }
}
