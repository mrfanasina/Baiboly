package com.fa.baiboly.ui.fanekena;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.List;

/**
 * ViewPager2 adapter : une FanekenaPageFragment par code de fanekena,
 * pour feuilleter les credos au swipe comme les chants dans SongDetailActivity.
 */
public class FanekenaPagerAdapter extends FragmentStateAdapter {

    private final List<String> codes;

    public FanekenaPagerAdapter(@NonNull FragmentActivity activity, List<String> codes) {
        super(activity);
        this.codes = codes;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return FanekenaPageFragment.newInstance(codes.get(position));
    }

    @Override
    public int getItemCount() {
        return codes != null ? codes.size() : 0;
    }

    /**
     * Code du fanekena à la position donnée, ou null si hors limites.
     */
    public String getCode(int position) {
        if (codes == null || position < 0 || position >= codes.size()) return null;
        return codes.get(position);
    }

    /**
     * Position du code donné, ou -1 si introuvable.
     */
    public int indexOf(String code) {
        if (codes == null || code == null) return -1;
        for (int i = 0; i < codes.size(); i++) {
            if (code.equalsIgnoreCase(codes.get(i))) return i;
        }
        return -1;
    }
}
