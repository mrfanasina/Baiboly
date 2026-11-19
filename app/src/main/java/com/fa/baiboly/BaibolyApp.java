package com.fa.baiboly;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import com.fa.baiboly.ui.common.AppBackgrounds;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Applique les photos de fond de l'application partout, SAUF sur les écrans
 * de lecture (chants/versets, toko/vakiteny, fanekena, litorjia, mofonaina).
 */
public class BaibolyApp extends Application {

    /** Écrans de lecture : on n'y touche pas, le texte doit rester sur fond uni. */
    private static final Set<String> SKIP_ACTIVITIES = new HashSet<>(Arrays.asList(
            "com.fa.baiboly.MainActivity",              // fond géré par les fragments
            "com.fa.baiboly.SplashActivity",
            "com.fa.baiboly.ui.verses.VersesActivity",
            "com.fa.baiboly.ui.fihirana.SongDetailActivity",
            "com.fa.baiboly.ui.fihirana.SolfaActivity",
            "com.fa.baiboly.ui.fanekena.FanekenaActivity",
            "com.fa.baiboly.ui.mofonaina.MofonainaActivity",
            "com.fa.baiboly.ui.transfer.PortraitCaptureActivity"
    ));

    private static final Set<String> SKIP_FRAGMENTS = new HashSet<>(Arrays.asList(
            "com.fa.baiboly.ui.baiboly.ChapterFragment",
            "com.fa.baiboly.ui.baiboly.ReadingFragment",
            "com.fa.baiboly.ui.verses.VersePageFragment",
            "com.fa.baiboly.ui.reader.ReaderNeighborPageFragment",
            "com.fa.baiboly.ui.fihirana.SongPageFragment",
            "com.fa.baiboly.ui.fanekena.FanekenaPageFragment",
            "com.fa.baiboly.ui.verses.VerseBottomSheetFragment",
            "com.fa.baiboly.SettingsFragment"       // ni fond ni images dans les cartes
    ));

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {

            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle saved) {
                if (activity instanceof FragmentActivity) {
                    ((FragmentActivity) activity).getSupportFragmentManager()
                            .registerFragmentLifecycleCallbacks(
                                    new FragmentManager.FragmentLifecycleCallbacks() {
                                        @Override
                                        public void onFragmentViewCreated(
                                                @NonNull FragmentManager fm,
                                                @NonNull Fragment fragment,
                                                @NonNull View view,
                                                @Nullable Bundle savedInstanceState) {
                                            String name = fragment.getClass().getName();
                                            if (!SKIP_FRAGMENTS.contains(name)) {
                                                AppBackgrounds.applyScreen(view, name,
                                                        fragment instanceof BottomSheetDialogFragment);
                                            }
                                        }
                                    }, true);
                }
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (SKIP_ACTIVITIES.contains(activity.getClass().getName())) {
                    return;
                }
                View content = activity.findViewById(android.R.id.content);
                if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
                    AppBackgrounds.applyScreen(((ViewGroup) content).getChildAt(0),
                            activity.getClass().getName());
                }
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity,
                                                    @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        });
    }
}
