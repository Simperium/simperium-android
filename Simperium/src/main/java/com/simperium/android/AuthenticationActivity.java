package com.simperium.android;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.simperium.R;
import com.simperium.android.LoginBottomSheetDialogFragment.LoginSheetListener;

public class AuthenticationActivity extends AppCompatActivity implements LoginSheetListener {
    public static final String EXTRA_IS_LOGIN = "EXTRA_IS_LOGIN";
    public static final String TAG = AuthenticationActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        this.setTheme(R.style.Simperium);
        setContentView(R.layout.activity_authentication);

        // Handle edge-to-edge display for Android 15+ where system bar colors are deprecated
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            setupEdgeToEdgeForAndroid15();
        }

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

    private void setupEdgeToEdgeForAndroid15() {
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        // Determine if we're in light or dark theme
        boolean isLightTheme = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) 
            != Configuration.UI_MODE_NIGHT_YES;
        
        // Set system bar appearance based on theme
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
            getWindow(), 
            getWindow().getDecorView()
        );
        
        if (controller != null) {
            controller.setAppearanceLightStatusBars(isLightTheme);
            controller.setAppearanceLightNavigationBars(isLightTheme);
        }
        
        // Apply window insets to the main content
        View contentView = findViewById(R.id.activity_authentication_root);
        if (contentView == null) {
            // Fallback to the layout's root view
            contentView = findViewById(android.R.id.content);
        }
        
        if (contentView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
                Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                
                // Apply padding to avoid overlap with system bars
                v.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
                );
                
                return WindowInsetsCompat.CONSUMED;
            });
        }
    }
}
