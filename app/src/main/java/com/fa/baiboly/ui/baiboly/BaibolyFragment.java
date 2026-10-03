package com.fa.baiboly.ui.baiboly;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;

import com.fa.baiboly.R;
import com.fa.baiboly.databinding.FragmentBaibolyBinding;
import com.fa.baiboly.models.Book;
import com.fa.baiboly.data.bible.BibleService;
import com.fa.baiboly.ui.verses.VersesActivity; // Assurez-vous que l'import est correct

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class BaibolyFragment extends Fragment {

    private FragmentBaibolyBinding binding;
    private BibleService bibleService;

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentBaibolyBinding.inflate(inflater, container, false);
        bibleService = new BibleService(requireContext());
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ✅ Afficher loader immédiatement
        binding.progressBar.setVisibility(View.VISIBLE);

        // 🔍 Configuration du bouton "Check" (endIcon) du TextInputLayout
        binding.layoutInput.setEndIconOnClickListener(v -> {
            String reference = binding.inputReference.getText().toString().trim();
            if (!reference.isEmpty()) {
                lancerLectureDirecte(reference);
            } else {
                binding.layoutInput.setError("Soraty ny andininy (ohatra: Gen 1:1)");
            }
        });

        // ⌨️ Lancer aussi la recherche avec la touche "Entrée" du clavier
        binding.inputReference.setOnEditorActionListener((v, actionId, event) -> {
            String reference = binding.inputReference.getText().toString().trim();
            if (!reference.isEmpty()) {
                lancerLectureDirecte(reference);
            }
            return true;
        });

        // 📚 Charger les livres
        loadBooks();
    }

    private void loadBooks() {
        executor.execute(() -> {
            // 📚 Chargement en background
            List<Book> oldTestament = bibleService.getBooks("AT", "mg");
            List<Book> newTestament = bibleService.getBooks("NT", "mg");

            mainHandler.post(() -> {
                if (binding == null) return;

                // 🎯 Adapters
                BookAdapter oldAdapter = new BookAdapter(oldTestament, this::onBookSelected);
                BookAdapter newAdapter = new BookAdapter(newTestament, this::onBookSelected);

                // 📱 UI OLD TESTAMENT (Scroll indépendant géré par le layout XML sans NestedScrollView)
                binding.recyclerOld.setLayoutManager(new GridLayoutManager(requireContext(), 1));
                binding.recyclerOld.setAdapter(oldAdapter);
                binding.recyclerOld.setHasFixedSize(true);

                // 📱 UI NEW TESTAMENT
                binding.recyclerNew.setLayoutManager(new GridLayoutManager(requireContext(), 1));
                binding.recyclerNew.setAdapter(newAdapter);
                binding.recyclerNew.setHasFixedSize(true);

                // ✅ Cacher loader
                binding.progressBar.setVisibility(View.GONE);
            });
        });
    }

    /**
     * Appelé lors du clic sur le bouton "Check"
     */
    private void lancerLectureDirecte(String reference) {
        // Optionnel : masquer l'erreur si elle était affichée
        binding.layoutInput.setError(null);

        // 🚀 Lancement de l'Activity comme demandé
        Intent intent = new Intent(requireContext(), VersesActivity.class);
        intent.putExtra("reading", reference); // On passe la chaîne saisie (ex: "Gen 1:1")
        startActivity(intent);
    }

    /**
     * Appelé lors du clic sur un livre de la liste
     */
    private void onBookSelected(Book book) {
        Bundle bundle = new Bundle();
        bundle.putSerializable("book", book);

        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.navigation_chapter, bundle);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}