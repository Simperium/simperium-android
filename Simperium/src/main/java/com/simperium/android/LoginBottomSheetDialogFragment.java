package com.simperium.android;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.simperium.R;

public class LoginBottomSheetDialogFragment extends SimperiumBottomSheetDialogFragment {
    public static final String TAG = LoginBottomSheetDialogFragment.class.getSimpleName();

    private LoginSheetListener mLoginSheetListener;

    public LoginBottomSheetDialogFragment(@NonNull final LoginSheetListener loginSheetListener) {
        mLoginSheetListener = loginSheetListener;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        mLoginSheetListener.onLoginSheetCanceled();
        super.onCancel(dialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View layout = inflater.inflate(R.layout.sheet_login, null);

        AppCompatButton buttonEmail = layout.findViewById(R.id.button_email);
        buttonEmail.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mLoginSheetListener.onLoginSheetEmailClicked();
                }
            }
        );

        AppCompatButton buttonOther = layout.findViewById(R.id.button_other);
        buttonOther.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mLoginSheetListener.onLoginSheetOtherClicked();
                }
            }
        );

        if (getDialog() != null) {
            getDialog().setContentView(layout);

            // Set peek height to full height of view to avoid buttons being off screen when
            // bottom sheet is shown with small screen height (e.g. landscape orientation).
            final BottomSheetBehavior behavior = BottomSheetBehavior.from((View) layout.getParent());
            getDialog().setOnShowListener(
                new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                        behavior.setPeekHeight(layout.getHeight());
                    }
                }
            );
        }

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    public interface LoginSheetListener {
        void onLoginSheetCanceled();
        void onLoginSheetEmailClicked();
        void onLoginSheetOtherClicked();
    }
}
