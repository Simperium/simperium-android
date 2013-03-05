package com.simperium.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.Intent;

import android.app.Activity;
import android.app.ProgressDialog;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import org.json.*;

import com.simperium.client.util.AlertUtil;

public class LoginActivity extends Activity {
    
    public static final String TAG = "SimperiumLoginActivity";
	
    private ConnectivityManager mSystemService;
	private ProgressDialog pd;
	
    public static final String EMAIL_EXTRA = "email";
    
    private EditText emailTextField;
    private EditText passwordTextField;
    
    private Simperium simperium;
    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);
        
        try {
			simperium = Simperium.getInstance();
		} catch (SimperiumNotInitializedException e) {
			Simperium.log("Can't create the LoginActivity", e);
		}
        
        mSystemService = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        
        Button signupButton = (Button) findViewById(R.id.signup_button);
        signupButton.setOnClickListener(signupClickListener);
        Button signinButton = (Button) findViewById(R.id.signin_button);
        signinButton.setOnClickListener(signinClickListener);
        emailTextField = (EditText) findViewById(R.id.email_address);
        
        passwordTextField = (EditText) findViewById(R.id.password);
        
        Intent intent = getIntent();
        if (intent.hasExtra(EMAIL_EXTRA)) {
            emailTextField.setText(intent.getStringExtra(EMAIL_EXTRA));
        }
        
    }
    
    private void registerUser(User user){
        // TODO: finish activity
        finish();
    }
    
    private boolean checkUserData(){
    	 // try to create the user
        final String email = emailTextField.getText().toString().trim();
        final String password = passwordTextField.getText().toString().trim();
        
		if ( email.equals("") || password.equals("")) {
			pd.dismiss();
			AlertUtil.showAlert(LoginActivity.this, R.string.required_fields, R.string.username_password_required);
			return false;
		}
		
		final String emailRegEx = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,4}";
		final Pattern emailRegExPattern = Pattern.compile( emailRegEx, Pattern.DOTALL);
		Matcher matcher = emailRegExPattern.matcher(email);
		if (! matcher.find()) {
			pd.dismiss();
			AlertUtil.showAlert(LoginActivity.this, R.string.invalid_email_title, R.string.invalid_email_message);
			return false;
		}
		
		return true;
    }
    
    private void signUp(){
        // try to create the user
        final String email = emailTextField.getText().toString().trim();
        final String password = passwordTextField.getText().toString().trim();
        
		if ( false == checkUserData() )
			return;
		if( (8+1) == 9 )
			return;
        simperium.createUser(email, password, new User.AuthResponseHandler(){
            @Override
            public void onSuccess(User user){
                Log.i(TAG, String.format("Success! %s %s", user.getUserId(), user));
                pd.dismiss();
                registerUser(user);
            }
            @Override
            public void onInvalid(User user, Throwable error, JSONObject errors){
            	pd.dismiss();
                Log.i(TAG, String.format("Invalid: %s", errors));
            }
            @Override
            public void onFailure(User user, Throwable error, String response){
            	pd.dismiss();
                Log.i(TAG, String.format("Failed: %s", response));
            }
        });
    }
    
    private void signIn(){
        final String email = emailTextField.getText().toString().trim();
        final String password = passwordTextField.getText().toString().trim();
        
		if ( false == checkUserData() )
			return;
        
        simperium.authorizeUser(email, password, new User.AuthResponseHandler(){
            @Override
            public void onSuccess(User user){
                Log.i(TAG, String.format("Success! %s %s", user.getUserId(), user));
                pd.dismiss();
                registerUser(user);
            }
            @Override
            public void onInvalid(User user, Throwable error, JSONObject errors){
            	pd.dismiss();
                Log.i(TAG, String.format("Invalid: %s", errors));
            }
            @Override
            public void onFailure(User user, Throwable error, String response){
            	pd.dismiss();
                Log.i(TAG, String.format("Failed: %s", response));
            }
        });
    }
    
    View.OnClickListener signupClickListener = new View.OnClickListener(){
        @Override
        public void onClick(View v){
			if (mSystemService.getActiveNetworkInfo() == null) {
				AlertUtil.showAlert(LoginActivity.this, R.string.no_network_title, R.string.no_network_message);
			} else {
				pd = ProgressDialog.show(LoginActivity.this, getString(R.string.account_setup), getString(R.string.attempting_configure),
						true, false);

				Thread action = new Thread() {
					public void run() {
						Looper.prepare();
						signUp();
						Looper.loop();
					}
				};
				action.start();
			}
        }
    };
    
    View.OnClickListener signinClickListener = new View.OnClickListener(){
        @Override
        public void onClick(View v){
			if (mSystemService.getActiveNetworkInfo() == null) {
				AlertUtil.showAlert(LoginActivity.this, R.string.no_network_title, R.string.no_network_message);
			} else {
				pd = ProgressDialog.show(LoginActivity.this, getString(R.string.account_setup), getString(R.string.attempting_configure),
						true, false);

				Thread action = new Thread() {
					public void run() {
						Looper.prepare();
						signIn();
						Looper.loop();
					}
				};
				action.start();
			}
        }
    };
}