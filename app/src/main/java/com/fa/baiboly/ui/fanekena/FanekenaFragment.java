package com.fa.baiboly.ui.fanekena;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.fa.baiboly.data.fanekena.FanekenaService;
import com.fa.baiboly.databinding.FragmentFanekenaBinding;
import com.fa.baiboly.models.Fanekena;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fragment handling song selection via a numeric keypad and category selector.
 */
public class FanekenaFragment extends Fragment {

    private FragmentFanekenaBinding binding;
    private FanekenaService service;
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFanekenaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        service = new FanekenaService(requireContext());
        setupRecyclerView();
        loadCategories();
    }


    private void setupRecyclerView() {
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 1);
        binding.recyclerCode.setLayoutManager(layoutManager);
        binding.recyclerCode.setHasFixedSize(true);
    }

    private void loadCategories() {
        List<String> codes = service.getCodes();

        // Textes des credos, pour afficher un extrait dans la liste
        Map<String, String> texts = service.getTexts();

        List<Fanekena> items = new ArrayList<>();
        for (String code : codes) {
            items.add(new Fanekena(0, code, texts.get(code)));
        }

        CodeAdapter adapter = new CodeAdapter(items, code -> {
            Intent intent = new Intent(requireContext(), FanekenaActivity.class);
            intent.putExtra("code", code);
            startActivity(intent);
        });
        binding.recyclerCode.setAdapter(adapter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}