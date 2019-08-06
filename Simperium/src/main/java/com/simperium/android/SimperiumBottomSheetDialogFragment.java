package com.simperium.android;

import android.app.Dialog;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.simperium.R;

public class SimperiumBottomSheetDialogFragment extends BottomSheetDialogFragment {
    @Override
    public int getTheme() {
        return R.style.BottomSheetDialogTheme;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setRetainInstance(true);
        return new BottomSheetDialog(requireContext(), getTheme());
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getDialog() != null) {
            // Limit width of bottom sheet on wide screens; non-zero width defined only for large qualifier.
            int dp = (int) getDialog().getContext().getResources().getDimension(R.dimen.width_layout);

            if (dp > 0) {
                WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
                layoutParams.copyFrom(getDialog().getWindow() != null ? getDialog().getWindow().getAttributes() : null);
                layoutParams.width = dp;
                layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
                getDialog().getWindow().setAttributes(layoutParams);
            }
        }
    }
}
