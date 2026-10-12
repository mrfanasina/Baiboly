package com.fa.baiboly.ui.reader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * Adapter commun aux lecteurs en mode session de programme : éventuellement
 * une page "Teo aloha" (item précédent), puis N pages de contenu créées par
 * la factory du lecteur hôte (versets, chants, credos...), puis
 * éventuellement une page "Manaraka" (item suivant). Les pages voisines
 * sont des {@link ReaderNeighborPageFragment}.
 */
public class ReaderSessionPagerAdapter extends FragmentStateAdapter {

    /** Crée la page de contenu à la position donnée (0 = 1re page de contenu). */
    public interface PageFactory {
        Fragment createFragment(int contentPosition);
    }

    private static final long PREV_PAGE_ID = Long.MIN_VALUE;
    private static final long NEXT_PAGE_ID = Long.MAX_VALUE;

    private final int contentCount;
    private final PageFactory factory;
    @Nullable
    private final ProgramSession.Item prevItem;
    @Nullable
    private final ProgramSession.Item nextItem;

    public ReaderSessionPagerAdapter(@NonNull FragmentActivity activity,
                                     @Nullable ProgramSession.Item prevItem,
                                     int contentCount,
                                     @NonNull PageFactory factory,
                                     @Nullable ProgramSession.Item nextItem) {
        super(activity);
        this.prevItem = prevItem;
        this.contentCount = contentCount;
        this.factory = factory;
        this.nextItem = nextItem;
    }

    private boolean hasPrev() {
        return prevItem != null;
    }

    /** Nombre de pages avant le contenu (0 ou 1). */
    public int contentOffset() {
        return prevItem != null ? 1 : 0;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (hasPrev() && position == 0) {
            return ReaderNeighborPageFragment.newInstance(
                    prevItem.title, prevItem.type, true);
        }
        if (position < contentOffset() + contentCount) {
            return factory.createFragment(position - contentOffset());
        }
        return ReaderNeighborPageFragment.newInstance(
                nextItem.title, nextItem.type, false);
    }

    @Override
    public int getItemCount() {
        return contentOffset() + contentCount + (nextItem != null ? 1 : 0);
    }

    @Override
    public long getItemId(int position) {
        if (hasPrev() && position == 0) return PREV_PAGE_ID;
        if (position < contentOffset() + contentCount) {
            return position - contentOffset();
        }
        return NEXT_PAGE_ID;
    }

    @Override
    public boolean containsItem(long itemId) {
        if (itemId == PREV_PAGE_ID) return hasPrev();
        if (itemId == NEXT_PAGE_ID) return nextItem != null;
        return itemId >= 0 && itemId < contentCount;
    }
}
