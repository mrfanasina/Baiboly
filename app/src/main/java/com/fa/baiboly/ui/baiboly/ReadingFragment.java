package com.fa.baiboly.ui.baiboly;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.fa.baiboly.databinding.FragmentReadingBinding;
import com.fa.baiboly.data.reading.ReadingService;

public class ReadingFragment extends Fragment {

    private FragmentReadingBinding binding;
    private ReadingService readingService;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentReadingBinding.inflate(inflater, container, false);

        readingService = new ReadingService(requireContext());

        displayTodayReading();

        return binding.getRoot();
    }

    // =========================
    // 📅 Lecture du jour
    // =========================
    private void displayTodayReading() {

        String reading = readingService.getTodayReading();

        binding.tvReadingContent.setText(reading);

        binding.tvReadingTitle.setText("Mofonaina androany");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}