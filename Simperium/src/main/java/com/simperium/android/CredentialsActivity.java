package com.simperium.android;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.ContextThemeWrapper;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputLayout;
import com.simperium.R;
import com.simperium.Simperium;
import com.simperium.SimperiumNotInitializedException;
import com.simperium.client.AuthException;
import com.simperium.client.AuthProvider;
import com.simperium.client.AuthResponseListener;
import com.simperium.client.User;
import com.simperium.util.Logger;
import com.simperium.util.NetworkUtil;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.regex.Pattern;

import static com.simperium.android.AuthenticationActivity.EXTRA_IS_LOGIN;
import static org.apache.http.protocol.HTTP.UTF_8;

public class CredentialsActivity extends AppCompatActivity {
    private static final Pattern PATTERN_NEWLINES_RETURNS_TABS = Pattern.compile("[\n\r\t]");
    private static final Pattern PATTERN_WHITESPACE = Pattern.compile("(\\s)");
    private static final String EXTRA_AUTOMATE_LOGIN = "EXTRA_AUTOMATE_LOGIN";
    private static final String EXTRA_PASSWORD = "EXTRA_PASSWORD";
    private static final String STATE_EMAIL = "STATE_EMAIL";
    private static final String STATE_PASSWORD = "STATE_PASSWORD";
    private static final int DELAY_AUTOMATE_LOGIN = 600;
    private static final int PASSWORD_LENGTH_LOGIN = 4;
    private static final int PASSWORD_LENGTH_MINIMUM = 8;

    protected ProgressDialogFragment mProgressDialogFragment;

    private AppCompatButton mButton;
    private Simperium mSimperium;
    private TextInputLayout mInputEmail;
    private TextInputLayout mInputPassword;
    private boolean mIsLogin;

    private AuthResponseListener mAuthListener = new AuthResponseListener() {
        @Override
        public void onFailure(final User user, final AuthException error) {
            runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        switch (error.failureType) {
                            case EXISTING_ACCOUNT:
                                showDialogErrorExistingAccount();
                                break;
                            case INVALID_ACCOUNT:
                            default:
                                showDialogError(getString(
                                    mIsLogin ?
                                        R.string.simperium_dialog_message_login :
                                        R.string.simperium_dialog_message_signup
                                ));
                        }

                        Logger.log(error.getMessage(), error);
                    }
                }
            );
        }

        @Override
        public void onSuccess(final User user, final String userId, final String token, final AuthProvider provider) {
            runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        hideDialogProgress();
                        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

                        if (inputMethodManager != null) {
                            inputMethodManager.hideSoftInputFromWindow(mButton.getWindowToken(), 0);
                        }

                        // Use isValidPasswordLength(false) to check if password meets PASSWORD_LENGTH_MINIMUM.
                        if (isValidPassword(user.getEmail(), user.getPassword()) && isValidPasswordLength(false)) {
                            user.setStatus(User.Status.AUTHORIZED);
                            user.setAccessToken(token);
                            user.setUserId(userId);
                            provider.saveUser(user);
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            user.setStatus(User.Status.NOT_AUTHORIZED);
                            user.setAccessToken("");
                            user.setUserId("");
                            provider.saveUser(user);
                            showDialogErrorLoginReset();
                        }
                    }
                }
            );
        }
    };

    @Override
    public void onBackPressed() {
        startActivity(new Intent(CredentialsActivity.this, AuthenticationActivity.class));
        super.onBackPressed();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setTheme(R.style.Simperium);
        setContentView(R.layout.activity_credentials);

        try {
            mSimperium = Simperium.getInstance();
        } catch (SimperiumNotInitializedException exception) {
            Logger.log("Can't create CredentialsActivity", exception);
        }

        if (getIntent().getExtras() != null && getIntent().hasExtra(EXTRA_IS_LOGIN)) {
            mIsLogin = getIntent().getBooleanExtra(EXTRA_IS_LOGIN, false);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(mIsLogin ?
            R.string.simperium_button_login :
            R.string.simperium_button_signup
        );
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        mInputEmail = findViewById(R.id.input_email);

        if (mInputEmail.getEditText() != null) {
            if (getIntent().getExtras() != null && getIntent().hasExtra(Intent.EXTRA_EMAIL)) {
                mInputEmail.getEditText().setText(getIntent().getStringExtra(Intent.EXTRA_EMAIL));
            }

            mInputEmail.getEditText().addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        setButtonState();
                    }

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }
                }
            );
            mInputEmail.getEditText().setOnFocusChangeListener(
                new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean hasFocus) {
                        if (!hasFocus && !isValidEmail(getEditTextString(mInputEmail))) {
                            mInputEmail.setError(getString(R.string.simperium_error_email));
                        } else {
                            mInputEmail.setError("");
                        }
                    }
                }
            );
        }

        mInputPassword = findViewById(R.id.input_password);

        if (mInputPassword.getEditText() != null) {
            if (getIntent().getExtras() != null && getIntent().hasExtra(EXTRA_PASSWORD)) {
                mInputPassword.getEditText().setText(getIntent().getStringExtra(EXTRA_PASSWORD));
            }

            mInputPassword.getEditText().addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        setButtonState();
                    }

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }
                }
            );
            mInputPassword.getEditText().setOnFocusChangeListener(
                new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean hasFocus) {
                        if (hasFocus) {
                            mInputPassword.setError("");
                        } else if (!isValidPasswordLength(mIsLogin)) {
                            mInputPassword.setError(getString(R.string.simperium_error_password));
                        }
                    }
                }
            );
        }

        mButton = findViewById(R.id.button);
        mButton.setText(mIsLogin ?
            R.string.simperium_button_login :
            R.string.simperium_button_signup
        );
        mButton.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (NetworkUtil.isNetworkAvailable(CredentialsActivity.this)) {
                        if (mIsLogin) {
                            startLogin();
                        } else {
                            startSignup();
                        }
                    } else {
                        showDialogError(getString(R.string.simperium_dialog_message_network));
                    }
                }
            }
        );

        String colorLink = Integer.toHexString(ContextCompat.getColor(CredentialsActivity.this, R.color.text_link) & 0xffffff);
        final TextView footer = findViewById(R.id.text_footer);
        footer.setText(
            Html.fromHtml(
                String.format(
                    mIsLogin ?
                        getResources().getString(R.string.simperium_footer_login) :
                        getResources().getString(R.string.simperium_footer_signup),
                    "<span style=\"color:#",
                    colorLink,
                    "\">",
                    "</span>"
                )
            )
        );
        footer.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Uri uri = Uri.parse(mIsLogin ?
                        getString(R.string.simperium_footer_login_url, getEditTextString(mInputEmail)) :
                        getString(R.string.simperium_footer_signup_url)
                    );
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                }
            }
        );

        setButtonState();

        if (savedInstanceState != null) {
            setEditTextString(mInputEmail, savedInstanceState.getString(STATE_EMAIL, ""));
            setEditTextString(mInputPassword, savedInstanceState.getString(STATE_PASSWORD, ""));
        }

        if (getIntent().getExtras() != null && getIntent().hasExtra(EXTRA_AUTOMATE_LOGIN) && getIntent().getBooleanExtra(EXTRA_AUTOMATE_LOGIN, false)) {
            new Handler().postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        startLogin();
                    }
                },
                    DELAY_AUTOMATE_LOGIN
            );
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() ==  android.R.id.home) {
            onBackPressed();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_EMAIL, getEditTextString(mInputEmail));
        outState.putString(STATE_PASSWORD, getEditTextString(mInputPassword));
    }

    private void clearPassword() {
        if (mInputPassword.getEditText() != null) {
            mInputPassword.getEditText().getText().clear();
        }
    }

    private String getEditTextString(@NonNull TextInputLayout inputLayout) {
        return inputLayout.getEditText() != null ? inputLayout.getEditText().getText().toString() : "";
    }

    private void hideDialogProgress() {
        if (mProgressDialogFragment != null && !mProgressDialogFragment.isHidden()) {
            mProgressDialogFragment.dismiss();
            mProgressDialogFragment = null;
        }
    }

    private boolean isValidEmail(String text) {
        return Patterns.EMAIL_ADDRESS.matcher(text).matches();
    }

    // Password is valid if:
    // - Meets minimum length requirement based on login (PASSWORD_LENGTH_LOGIN) and signup (PASSWORD_LENGTH_MINIMUM)
    // - Does not have new lines or tabs (PATTERN_NEWLINES_TABS)
    // - Does not match email address
    private boolean isValidPassword(String email, String password) {
        return isValidPasswordLength(mIsLogin) && !PATTERN_NEWLINES_RETURNS_TABS.matcher(password).find() && !email.contentEquals(password);
    }

    private boolean isValidPasswordLength(boolean isLogin) {
        return mInputPassword.getEditText() != null &&
            (isLogin ?
                getEditTextString(mInputPassword).length() >= PASSWORD_LENGTH_LOGIN :
                getEditTextString(mInputPassword).length() >= PASSWORD_LENGTH_MINIMUM
            );
    }

    private boolean isValidPasswordLogin(String password) {
        return isValidPasswordLength(mIsLogin) && !PATTERN_WHITESPACE.matcher(password).find();
    }

    private void setButtonState() {
        mButton.setEnabled(
            mInputEmail.getEditText() != null &&
            mInputPassword.getEditText() != null &&
            isValidEmail(getEditTextString(mInputEmail)) &&
            isValidPasswordLength(mIsLogin)
        );
    }

    private void setEditTextString(@NonNull TextInputLayout inputLayout, String text) {
        if (inputLayout.getEditText() != null ) {
            inputLayout.getEditText().setText(text);
        }
    }

    private void showDialogError(String message) {
        hideDialogProgress();
        Context context = new ContextThemeWrapper(CredentialsActivity.this, getTheme());
        new AlertDialog.Builder(context)
            .setTitle(R.string.simperium_dialog_title_error)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private void showDialogErrorExistingAccount() {
        hideDialogProgress();
        Context context = new ContextThemeWrapper(CredentialsActivity.this, getTheme());
        new AlertDialog.Builder(context)
            .setTitle(R.string.simperium_dialog_title_error)
            .setMessage(R.string.simperium_dialog_message_signup_existing)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.simperium_button_login,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(CredentialsActivity.this, CredentialsActivity.class);
                        intent.putExtra(EXTRA_IS_LOGIN, true);
                        intent.putExtra(Intent.EXTRA_EMAIL, getEditTextString(mInputEmail));
                        intent.putExtra(EXTRA_PASSWORD, getEditTextString(mInputPassword));
                        intent.putExtra(EXTRA_AUTOMATE_LOGIN, true);
                        startActivity(intent);
                        finish();
                    }
                }
            )
            .show();
    }

    private void showDialogErrorLoginReset() {
        hideDialogProgress();
        Context context = new ContextThemeWrapper(CredentialsActivity.this, getTheme());
        new AlertDialog.Builder(context)
            .setTitle(R.string.simperium_dialog_title_error)
            .setMessage(getString(R.string.simperium_dialog_message_login_reset, PASSWORD_LENGTH_MINIMUM))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.simperium_button_login_reset,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Uri uri = Uri.parse(getString(R.string.simperium_dialog_button_reset_url, URLEncoder.encode(getEditTextString(mInputEmail), UTF_8)));
                            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                            startActivity(intent);
                            clearPassword();
                        } catch (UnsupportedEncodingException e) {
                            throw new RuntimeException("Unable to parse URL", e);
                        }
                    }
                }
            )
            .show();
    }

    private void startLogin() {
        final String email = getEditTextString(mInputEmail);
        final String password = getEditTextString(mInputPassword);

        if (isValidPasswordLogin(password)) {
            mProgressDialogFragment = ProgressDialogFragment.newInstance(getString(R.string.simperium_dialog_progress_logging_in));
            mProgressDialogFragment.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.Simperium);
            mProgressDialogFragment.show(getSupportFragmentManager(), ProgressDialogFragment.TAG);
            mSimperium.authorizeUser(email, password, mAuthListener);
        } else {
            showDialogError(getString(R.string.simperium_dialog_message_password_login, PASSWORD_LENGTH_LOGIN));
        }
    }

    private void startSignup() {
        final String email = getEditTextString(mInputEmail);
        final String password = getEditTextString(mInputPassword);

        if (isValidPassword(email, password)) {
            mProgressDialogFragment = ProgressDialogFragment.newInstance(getString(R.string.simperium_dialog_progress_signing_up));
            mProgressDialogFragment.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.Simperium);
            mProgressDialogFragment.show(getSupportFragmentManager(), ProgressDialogFragment.TAG);
            mSimperium.createUser(email, password, mAuthListener);
        } else {
            showDialogError(getString(R.string.simperium_dialog_message_password, PASSWORD_LENGTH_MINIMUM));
        }
    }
}
