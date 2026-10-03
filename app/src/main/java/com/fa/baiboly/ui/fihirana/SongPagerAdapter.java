package com.fa.baiboly.ui.fihirana;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.fa.baiboly.data.fihirana.FihiranaService;
import com.fa.baiboly.models.Song;
import com.fa.baiboly.models.SongVerse;

import java.util.List;

/**
 * ViewPager2 adapter that displays one song per page.
 * Each page is a SongPageFragment with its own scrolling RecyclerView.
 */
public class SongPagerAdapter extends FragmentStateAdapter {

    private final List<Song> songs;
    private final float textSize;
    private final FihiranaService service;

    public SongPagerAdapter(@NonNull FragmentActivity activity,
                            List<Song> songs,
                            float textSize) {
        super(activity);
        this.songs = songs;
        this.textSize = textSize;
        this.service = new FihiranaService(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Song song = songs.get(position);
        List<SongVerse> verses = service.getSongVerses(song.getId());
        return SongPageFragment.newInstance(verses, textSize);
    }

    @Override
    public int getItemCount() {
        return songs != null ? songs.size() : 0;
    }

    /**
     * Get the song at the given position.
     */
    public Song getSong(int position) {
        if (songs == null || position < 0 || position >= songs.size()) return null;
        return songs.get(position);
    }
}
