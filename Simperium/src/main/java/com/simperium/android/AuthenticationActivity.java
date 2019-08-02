package com.simperium.android;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.simperium.R;
import com.simperium.android.LoginBottomSheetDialogFragment.LoginSheetListener;

public class AuthenticationActivity extends AppCompatActivity implements LoginSheetListener {
    public static final String EXTRA_IS_LOGIN = "EXTRA_IS_LOGIN";
    public static final String TAG = AuthenticationActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.setTheme(R.style.Simperium);
        setContentView(R.layout.activity_authentication);

        AppCompatButton buttonLogin = findViewById(R.id.button_login);
        buttonLogin.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    buttonLoginClicked();
                }
            }
        );

        AppCompatButton buttonSignup = findViewById(R.id.button_signup);
        buttonSignup.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    buttonSignupClicked();
                }
            }
        );
    }

    @Override
    public void onLoginSheetCanceled() {
    }

    @Override
    public void onLoginSheetEmailClicked() {
        Intent intent = new Intent(AuthenticationActivity.this, CredentialsActivity.class);
        intent.putExtra(EXTRA_IS_LOGIN, true);
        startActivity(intent);
        finish();
    }

    @Override
    public void onLoginSheetOtherClicked() {
    }

    protected void buttonLoginClicked() {
        LoginBottomSheetDialogFragment loginBottomSheetDialogFragment = new LoginBottomSheetDialogFragment(AuthenticationActivity.this);
        loginBottomSheetDialogFragment.show(getSupportFragmentManager(), LoginBottomSheetDialogFragment.TAG);
    }

    protected void buttonSignupClicked() {
        Intent intent = new Intent(AuthenticationActivity.this, CredentialsActivity.class);
        intent.putExtra(EXTRA_IS_LOGIN, false);
        startActivity(intent);
        finish();
    }
}
