package com.simperium.client;

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
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.*;

import com.simperium.client.util.AlertUtil;

public class LoginActivity extends Activity {

	public static final String TAG = "SimperiumLoginActivity";

	private ConnectivityManager mSystemService;
	private ProgressDialog pd;

	public static final String EMAIL_EXTRA = "email";

	private EditText emailTextField;
	private EditText passwordTextField;
	private EditText passwordTextField2;

	private Simperium simperium;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.login);

		try {
			simperium = Simperium.getInstance();
		} catch (SimperiumNotInitializedException e) {
			Simperium.log("Can't create the LoginActivity", e);
		}

		mSystemService = (ConnectivityManager) getApplicationContext()
				.getSystemService(Context.CONNECTIVITY_SERVICE);

		final Button signupButton = (Button) findViewById(R.id.signup_button);
		signupButton.setOnClickListener(signupClickListener);
		signupButton.setVisibility(View.GONE);
		
		final Button signinButton = (Button) findViewById(R.id.signin_button);
		signinButton.setOnClickListener(signinClickListener);
		emailTextField = (EditText) findViewById(R.id.email_address);

		passwordTextField = (EditText) findViewById(R.id.password);
		passwordTextField2 = (EditText) findViewById(R.id.password2);
		passwordTextField2.setVisibility(View.GONE);
		
		Intent intent = getIntent();
		if (intent.hasExtra(EMAIL_EXTRA)) {
			emailTextField.setText(intent.getStringExtra(EMAIL_EXTRA));
		}

		final TextView l_already_have_an_account = (TextView) findViewById(R.id.l_already_have_an_account);
		final TextView dont_yet_have_an_account = (TextView) findViewById(R.id.l_dont_yet_have_an_account);
		final TextView l_agree_terms_of_service = (TextView) findViewById(R.id.l_agree_terms_of_service);
		
		l_agree_terms_of_service.setClickable(true);
		l_agree_terms_of_service.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Uri uriUrl = Uri.parse("https://simperium.com/tos/");  
				Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
				startActivity(launchBrowser); 
			}
		});
		
		l_already_have_an_account.setClickable(true);
		l_already_have_an_account.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//reset the screen to signin
				l_already_have_an_account.setVisibility(View.GONE);
				passwordTextField2.setVisibility(View.GONE);
				l_agree_terms_of_service.setVisibility(View.GONE);
				dont_yet_have_an_account.setVisibility(View.VISIBLE);
				signinButton.setVisibility(View.VISIBLE);
				signupButton.setVisibility(View.GONE);
			}
		});
		
		dont_yet_have_an_account.setClickable(true);
		dont_yet_have_an_account.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//reset the screen to signup
				dont_yet_have_an_account.setVisibility(View.GONE);
				signinButton.setVisibility(View.GONE);
				l_agree_terms_of_service.setVisibility(View.VISIBLE);
				l_already_have_an_account.setVisibility(View.VISIBLE);
				passwordTextField2.setVisibility(View.VISIBLE);
				signupButton.setVisibility(View.VISIBLE);
			}
		});
	}

	private void registerUser(User user) {
		// TODO: finish activity
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
		if(pd != null ) 
			pd.dismiss();
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

		pd = ProgressDialog.show(LoginActivity.this,
				getString(R.string.account_setup),
				getString(R.string.attempting_configure), true, false);
		
		simperium.createUser(email, password, new User.AuthResponseHandler() {
			@Override
			public void onSuccess(User user) {
				Log.i(TAG, String.format("Success! %s %s", user.getUserId(), user));
				if(pd != null ) 
					pd.dismiss();
				registerUser(user);
			}

			@Override
			public void onInvalid(User user, Throwable error, JSONObject errors) {
				showLoginError(String.format("Invalid: %s", errors));
				Log.i(TAG, String.format("Invalid: %s", errors));
			}

			@Override
			public void onFailure(User user, Throwable error, String response) {
				showLoginError(String.format("Failed: %s", response));
				Log.i(TAG, String.format("Failed: %s", response));
			}
		});
	}

	private void signIn() {
		final String email = emailTextField.getText().toString().trim();
		final String password = passwordTextField.getText().toString().trim();

		if (false == checkUserData())
			return;

		
		pd = ProgressDialog.show(LoginActivity.this,
				getString(R.string.account_setup),
				getString(R.string.attempting_configure), true, false);
		
		simperium.authorizeUser(email, password,
				new User.AuthResponseHandler() {
					@Override
					public void onSuccess(User user) {
						Log.i(TAG,
								String.format("Success! %s %s",
										user.getUserId(), user));
						if(pd != null ) 
							pd.dismiss();
						registerUser(user);
					}

					@Override
					public void onInvalid(User user, Throwable error, JSONObject errors) {
						if(error instanceof org.apache.http.client.HttpResponseException) {
							org.apache.http.client.HttpResponseException errorObj = (org.apache.http.client.HttpResponseException) error;
							if(errorObj.getStatusCode() == 401)
								showLoginError(getString(R.string.login_failed_message));
							else
								String.format("Invalid: %s", errors);
						}
						Log.i(TAG, String.format("Invalid: %s", errors));
					}

					@Override
					public void onFailure(User user, Throwable error, String response) {
						if(error instanceof org.apache.http.client.HttpResponseException) {
							org.apache.http.client.HttpResponseException errorObj = (org.apache.http.client.HttpResponseException) error;
							if(errorObj.getStatusCode() == 401)
								showLoginError(getString(R.string.login_failed_message));
							else
								showLoginError(response);
							
						}
						Log.i(TAG, String.format("Failed: %s", response));
					}
				});
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
		if (mSystemService.getActiveNetworkInfo() == null) {
			AlertUtil.showAlert(LoginActivity.this, R.string.no_network_title,
					R.string.no_network_message);
		} else {
			Thread action = new Thread() {
				public void run() {
					Looper.prepare();
					if (isSignup == true)
						signUp();
					else
						signIn();
					Looper.loop();
				}
			};
			action.start();
		}
	}
}