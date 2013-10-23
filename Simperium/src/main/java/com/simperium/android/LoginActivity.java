package com.simperium.android;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.text.TextUtils;

import org.json.*;

import com.simperium.R;
import com.simperium.Simperium;
import com.simperium.SimperiumNotInitializedException;
import com.simperium.client.User;
import com.simperium.client.AuthResponseListener;
import com.simperium.client.AuthException;
import com.simperium.util.AlertUtil;
import com.simperium.util.Logger;

import android.util.Log;

public class LoginActivity extends Activity {

    public static final String TAG = "SimperiumLoginActivity";

    public static final String EXTRA_SIGN_IN_FIRST = "signInFirst";
    private static final String URL_FORGOT_PASSWORD = "https://simple-note.appspot.com/forgot/";

    private ConnectivityManager mSystemService;

    public static final String EMAIL_EXTRA = "email";

    private EditText emailTextField;
    private EditText passwordTextField;
    private EditText passwordTextField2;

    private TextView haveAccountTextView;
    private TextView haveAccountButton;
    private TextView createAccountButton;
    private TextView termsOfService;
    private TextView forgotPasswordButton;

    private Button signupButton;
    private Button signinButton;

    private Simperium mSimperium;


    protected ProgressDialog mProgressDialog;

    public void setSimperium(Simperium simperium){
        mSimperium = simperium;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.setTheme(R.style.simperiumstyle);
        setContentView(R.layout.login);

        try {
            setSimperium(Simperium.getInstance());
        } catch (SimperiumNotInitializedException e) {
            Logger.log("Can't create the LoginActivity", e);
        }

        mSystemService = (ConnectivityManager) getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        signupButton = (Button) findViewById(R.id.signup_button);
        signupButton.setOnClickListener(signupClickListener);
        
        signinButton = (Button) findViewById(R.id.signin_button);
        signinButton.setOnClickListener(signinClickListener);
        emailTextField = (EditText) findViewById(R.id.email_address);

        passwordTextField = (EditText) findViewById(R.id.password);
        passwordTextField2 = (EditText) findViewById(R.id.password2);

        haveAccountTextView = (TextView) findViewById(R.id.no_account);
        
        Intent intent = getIntent();
        if (intent.hasExtra(EMAIL_EXTRA)) {
            emailTextField.setText(intent.getStringExtra(EMAIL_EXTRA));
        } else if (mSimperium != null){
            emailTextField.setText(mSimperium.getUser().getEmail());
        }
        if (!TextUtils.isEmpty(emailTextField.getText())) {
            passwordTextField.requestFocus();
        }
        forgotPasswordButton = (TextView) findViewById(R.id.forgot_password_button);
        forgotPasswordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Head off to the reset password form
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(URL_FORGOT_PASSWORD));
                    startActivity(i);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        haveAccountButton = (TextView) findViewById(R.id.have_account_button);
        haveAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //reset the screen to signin
                setSignInVisible();
            }
        });

        createAccountButton = (TextView) findViewById(R.id.create_account_button);
        createAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //reset the screen to signup
                forgotPasswordButton.setVisibility(View.GONE);
                createAccountButton.setVisibility(View.GONE);
                signinButton.setVisibility(View.GONE);
                termsOfService.setVisibility(View.VISIBLE);
                haveAccountButton.setVisibility(View.VISIBLE);
                passwordTextField2.setVisibility(View.VISIBLE);
                signupButton.setVisibility(View.VISIBLE);
                haveAccountTextView.setText(getString(R.string.have_account));
            }
        });

        termsOfService = (TextView) findViewById(R.id.l_agree_terms_of_service);
        termsOfService.setClickable(true);
        termsOfService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Uri uriUrl = Uri.parse("https://simperium.com/tos/");
                Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
                startActivity(launchBrowser);
            }
        });

        if (intent.hasExtra(EXTRA_SIGN_IN_FIRST))
            setSignInVisible();
    }

    private void setSignInVisible() {
        haveAccountButton.setVisibility(View.GONE);
        passwordTextField2.setVisibility(View.GONE);
        termsOfService.setVisibility(View.GONE);
        createAccountButton.setVisibility(View.VISIBLE);
        forgotPasswordButton.setVisibility(View.VISIBLE);
        signinButton.setVisibility(View.VISIBLE);
        signupButton.setVisibility(View.GONE);
        haveAccountTextView.setText(R.string.no_account);
    }

    private void registerUser(User user) {
        setResult(RESULT_OK);
        // Dismiss soft keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null)
            inputMethodManager.hideSoftInputFromWindow(passwordTextField.getWindowToken(), 0);
        finish();
    }

    private boolean checkUserData() {
        // try to create the user
        final String email = emailTextField.getText().toString().trim();
        final String password = passwordTextField.getText().toString().trim();

        if (email.equals("") || password.equals("")) {
            AlertUtil.showAlert(LoginActivity.this, R.string.required_fields,
                    R.string.username_password_required);
            return false;
        }
        
        if (password.length() < 4 ) {
            AlertUtil.showAlert(LoginActivity.this, R.string.invalid_password_title,
                    R.string.invalid_password_message);
            return false;
        }

        final String emailRegEx = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,4}";
        final Pattern emailRegExPattern = Pattern.compile(emailRegEx,
                Pattern.DOTALL);
        Matcher matcher = emailRegExPattern.matcher(email);
        if (!matcher.find()) {
            AlertUtil.showAlert(LoginActivity.this,
                    R.string.invalid_email_title,
                    R.string.invalid_email_message);
            return false;
        }

        return true;
    }

    private void showLoginError(String message) {
		if (isFinishing())
			return;
        if (mProgressDialog != null )
            mProgressDialog.dismiss();
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(
                LoginActivity.this);
        dialogBuilder.setTitle(getString(R.string.error));
        dialogBuilder.setMessage(message);
        dialogBuilder.setPositiveButton(getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                    }
                });
        dialogBuilder.setCancelable(true);
        dialogBuilder.create().show();
    }

    private AuthResponseListener mAuthListener = new AuthResponseListener() {
        @Override
        public void onSuccess(User user) {
            if(mProgressDialog != null )
                mProgressDialog.dismiss();
            registerUser(user);
        }

        @Override
        public void onFailure(User user, AuthException error) {
            String message;
            switch (error.failureType) {
                case EXISTING_ACCOUNT:
                    message = getString(R.string.login_failed_account_exists);
                    break;
                case INVALID_ACCOUNT:
                default:
                    message = getString(R.string.login_failed_message);
            }
            showLoginError(message);
        }

    };

    private void signUp() {
        // try to create the user
        final String email = emailTextField.getText().toString().trim();
        final String password = passwordTextField.getText().toString().trim();
        final String password2 = passwordTextField2.getText().toString().trim();

        if (false == checkUserData())
            return;

        if( ! password2.equals(password) ) {
            AlertUtil.showAlert(LoginActivity.this, R.string.invalid_password_title,
                    R.string.passwords_do_not_match_message);
            return;
        }

        mProgressDialog = ProgressDialog.show(LoginActivity.this,
                null,
                getString(R.string.signing_up), true, false);
        
        mSimperium.createUser(email, password, mAuthListener);
    }

    private void signIn() {
        final String email = emailTextField.getText().toString().trim();
        final String password = passwordTextField.getText().toString().trim();

        if (false == checkUserData())
            return;

        
        mProgressDialog = ProgressDialog.show(LoginActivity.this,
                null,
                getString(R.string.signing_in), true, false);
        
        mSimperium.authorizeUser(email, password, mAuthListener);
    }

    View.OnClickListener signupClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            startSignUpOrSignin(true);
        }
    };

    View.OnClickListener signinClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            startSignUpOrSignin(false);
        }
    };

    private void startSignUpOrSignin(final boolean isSignup) {
        NetworkInfo network = mSystemService.getActiveNetworkInfo();
        if (isSignup == true)
            signUp();
        else
            signIn();
        if(true) return;
        if (network == null || !network.isConnected()) {
            AlertUtil.showAlert(LoginActivity.this, R.string.no_network_title,
                    R.string.no_network_message);
        } else {
            if (isSignup == true)
                signUp();
            else
                signIn();
        }
    }
    
    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        finish();
    }
    
}