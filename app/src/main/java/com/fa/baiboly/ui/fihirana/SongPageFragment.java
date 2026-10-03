package com.fa.baiboly.ui.fihirana;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.models.SongVerse;

import java.util.List;

/**
 * Fragment that displays the verses of a single song.
 * Used inside ViewPager2 for smooth horizontal swiping between songs.
 */
public class SongPageFragment extends Fragment {

    private static final String ARG_VERSES = "verses";
    private static final String ARG_TEXT_SIZE = "text_size";

    /**
     * Create a new instance with the given verses and text size.
     */
    public static SongPageFragment newInstance(List<SongVerse> verses, float textSize) {
        SongPageFragment fragment = new SongPageFragment();
        Bundle args = new Bundle();
        // SongVerse must be Serializable or Parcelable
        args.putSerializable(ARG_VERSES, new java.util.ArrayList<>(verses));
        args.putFloat(ARG_TEXT_SIZE, textSize);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_song_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setHasFixedSize(true);

        if (getArguments() != null) {
            @SuppressWarnings("unchecked")
            List<SongVerse> verses = (List<SongVerse>) getArguments().getSerializable(ARG_VERSES);
            float textSize = getArguments().getFloat(ARG_TEXT_SIZE, 18f);

            if (verses != null) {
                SongVersesAdapter adapter = new SongVersesAdapter(verses, textSize);
                recyclerView.setAdapter(adapter);
            }
        }
    }
}
