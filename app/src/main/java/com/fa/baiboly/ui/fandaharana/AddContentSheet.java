package com.fa.baiboly.ui.fandaharana;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fa.baiboly.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu d'ajout d'un contenu au programme : grille de types construite
 * depuis {@link ProgramContentTypes}. Un type est choisi -> callback.
 */
public class AddContentSheet extends BottomSheetDialogFragment {

    public interface OnTypeChosen {
        void onTypeChosen(ProgramContentTypes.TypeDef type);
    }

    private final OnTypeChosen listener;

    public AddContentSheet(OnTypeChosen listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_add_content, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        LinearLayout grid = view.findViewById(R.id.gridTypes);
        LayoutInflater inflater = getLayoutInflater();

        int columns = 3;
        LinearLayout currentRow = null;

        for (ProgramContentTypes.TypeDef type : ProgramContentTypes.TYPES) {

            if (grid.getChildCount() == 0
                    || (currentRow != null && currentRow.getChildCount() >= columns)) {
                currentRow = new LinearLayout(requireContext());
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                currentRow.setLayoutParams(rowLp);
                grid.addView(currentRow);
            }

            View cell = inflater.inflate(R.layout.item_content_type, grid, false);
            TextView label = cell.findViewById(R.id.textTypeLabel);
            label.setText(type.label);

            // Cercle coloré + icône du type (même style que les items)
            View iconBg = cell.findViewById(R.id.typeIconBg);
            android.widget.ImageView icon = cell.findViewById(R.id.typeIcon);
            android.graphics.drawable.GradientDrawable circle =
                    new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(android.graphics.Color.parseColor(type.colorHex));
            iconBg.setBackground(circle);
            icon.setImageResource(type.iconRes);

            cell.setOnClickListener(v -> {
                dismiss();
                if (listener != null) listener.onTypeChosen(type);
            });

            // weight uniforme dans la ligne
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cell.setLayoutParams(lp);

            if (currentRow != null) {
                currentRow.addView(cell);
            }
        }
    }
}
