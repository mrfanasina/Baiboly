package com.fa.baiboly.ui.verses;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.List;

/**
 * ViewPager2 adapter : une VersePageFragment interactive par page.
 * Chaque page possède toutes les fonctionnalités (sélection, annotations,
 * références cliquables, surlignages, favoris, réglages texte).
 *
 * Les ids stables évitent que FragmentStateAdapter ne recrée pas les
 * fragments quand la liste de pages change.
 */
public class VersePagerAdapter extends FragmentStateAdapter {

    private final List<String> readings;

    public VersePagerAdapter(@NonNull FragmentActivity activity, List<String> readings) {
        super(activity);
        this.readings = readings;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return VersePageFragment.newInstance(readings.get(position));
    }

    @Override
    public int getItemCount() {
        return readings != null ? readings.size() : 0;
    }

    @Override
    public long getItemId(int position) {
        // Id stable = la référence elle-même : deux pages ne sont jamais confondues
        return readings.get(position).hashCode();
    }

    @Override
    public boolean containsItem(long itemId) {
        for (String reading : readings) {
            if (reading.hashCode() == itemId) return true;
        }
        return false;
    }

    public String getReading(int position) {
        if (readings == null || position < 0 || position >= readings.size()) return null;
        return readings.get(position);
    }
}
