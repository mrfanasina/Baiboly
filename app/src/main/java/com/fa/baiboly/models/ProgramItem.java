package com.fa.baiboly.models;

/**
 * Represents a single item inside a Program (Fandaharana).
 * type: "verse", "song", "fanekena", "prayer", "text", "preaching",
 *       "study", "announcement", "image", "audio", "video",
 *       "document", "link", "event", "other"
 * reference: e.g. "Jao 1:1-5", "ffpm_171", "fanekena_code", URL, texte libre...
 * title: display title
 * description: optional user note
 * favorite: pinned at the top of the list
 * position: order in the program (for drag & drop)
 */
public class ProgramItem {
    private long id;
    private long programId;
    private String type;
    private String reference;
    private String title;
    private String description;
    private boolean favorite;
    private int position;

    public ProgramItem() {}

    public ProgramItem(long id, long programId, String type, String reference,
                       String title, int position) {
        this.id = id;
        this.programId = programId;
        this.type = type;
        this.reference = reference;
        this.title = title;
        this.position = position;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getProgramId() { return programId; }
    public void setProgramId(long programId) { this.programId = programId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
