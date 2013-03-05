package com.simperium.client;

import android.content.Intent;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import org.json.*;

public class LoginActivity extends Activity {
    
    public static final String TAG = "SimperiumLoginActivity";
    
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
    
    private void signUp(){
        // try to create the user
        String email = emailTextField.getText().toString();
        String password = passwordTextField.getText().toString();
        simperium.createUser(email, password, new User.AuthResponseHandler(){
            @Override
            public void onSuccess(User user){
                Log.i(TAG, String.format("Success! %s %s", user.getUserId(), user));
                registerUser(user);
            }
            @Override
            public void onInvalid(User user, Throwable error, JSONObject errors){
                Log.i(TAG, String.format("Invalid: %s", errors));
            }
            @Override
            public void onFailure(User user, Throwable error, String response){
                Log.i(TAG, String.format("Failed: %s", response));
            }
        });
    }
    
    private void signIn(){
        String email = emailTextField.getText().toString();
        String password = passwordTextField.getText().toString();
        simperium.authorizeUser(email, password, new User.AuthResponseHandler(){
            @Override
            public void onSuccess(User user){
                Log.i(TAG, String.format("Success! %s %s", user.getUserId(), user));
                registerUser(user);
            }
            @Override
            public void onInvalid(User user, Throwable error, JSONObject errors){
                Log.i(TAG, String.format("Invalid: %s", errors));
            }
            @Override
            public void onFailure(User user, Throwable error, String response){
                Log.i(TAG, String.format("Failed: %s", response));
            }
        });
    }
    
    View.OnClickListener signupClickListener = new View.OnClickListener(){
        @Override
        public void onClick(View v){
            signUp();
        }
    };
    
    View.OnClickListener signinClickListener = new View.OnClickListener(){
        @Override
        public void onClick(View v){
            signIn();
        }
    };
}