package com.fa.baiboly.ui.fandaharana;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fa.baiboly.R;
import com.fa.baiboly.data.fanekena.FanekenaService;
import com.fa.baiboly.models.Fanekena;
import com.fa.baiboly.ui.fanekena.CodeAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sélecteur de fanekena en BottomSheet : liste des credos disponibles
 * (réutilise FanekenaService + CodeAdapter de l'écran existant).
 * Choix -> code + nom affiché transmis au programme.
 */
public class FanekenaPickerSheet extends BottomSheetDialogFragment {

    /** Résultat transmis à ProgramDetailActivity. */
    public interface OnFanekenaPicked {
        void onFanekenaPicked(String code, String displayName);
    }

    private final OnFanekenaPicked listener;

    public FanekenaPickerSheet(OnFanekenaPicked listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_code_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());

        RecyclerView recycler = view.findViewById(R.id.recyclerCodes);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Tous les credos (code + texte) pour afficher un aperçu
        FanekenaService service = new FanekenaService(requireContext());
        Map<String, String> texts = service.getTexts();

        List<Fanekena> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : texts.entrySet()) {
            list.add(new Fanekena(0, entry.getKey(), entry.getValue()));
        }

        recycler.setAdapter(new CodeAdapter(list, code -> {
            dismiss();
            if (listener != null) {
                listener.onFanekenaPicked(code, CodeAdapter.getDisplayName(code));
            }
        }));
    }
}
