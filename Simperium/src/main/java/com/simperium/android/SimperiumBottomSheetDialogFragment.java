package com.simperium.android;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

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
                FrameLayout bottomSheetLayout = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);

                if (bottomSheetLayout != null) {
                    ViewParent bottomSheetParent = bottomSheetLayout.getParent();

                    if (bottomSheetParent instanceof CoordinatorLayout) {
                        CoordinatorLayout.LayoutParams coordinatorLayoutParams = (CoordinatorLayout.LayoutParams) bottomSheetLayout.getLayoutParams();
                        coordinatorLayoutParams.width = dp;
                        bottomSheetLayout.setLayoutParams(coordinatorLayoutParams);

                        CoordinatorLayout coordinatorLayout = (CoordinatorLayout) bottomSheetParent;
                        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) coordinatorLayout.getLayoutParams();
                        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
                        coordinatorLayout.setLayoutParams(layoutParams);
                    }
                }
            }
        }
    }
}
