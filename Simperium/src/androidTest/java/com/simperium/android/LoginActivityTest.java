package com.simperium.android;

import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.test.TouchUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.simperium.R;

import com.simperium.android.LoginActivity;
import com.simperium.test.MockClient;

public class LoginActivityTest extends ActivityInstrumentationTestCase2<LoginActivity> {

    public static final String TAG = "Simperium.LoginActivityTest";

    protected Simperium mSimperium;

    protected LoginActivity mActivity;
    Button mSignupButton;
    Button mSigninButton;
    EditText mEmailTextField;
    EditText mPasswordTextField;
    EditText mPasswordTextField2;
    TextView mHaveAccountTextView;
    TextView mForgotPasswordButton;
    TextView mHaveAccountButton;
    TextView mCreateAccountButton;
    TextView mTermsOfService;

    public LoginActivityTest() {
        super(LoginActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        setActivityInitialTouchMode(false);
        mSimperium = Simperium.initializeClient(new MockClient());
    }

    @Override
    protected void tearDown() throws Exception {
        mSimperium.deauthorizeUser();
        super.tearDown();
    }

    public LoginActivity setupActivity(boolean signInFirst){
        if (signInFirst) {
            Intent signInFirstIntent = new Intent();
            signInFirstIntent.putExtra(LoginActivity.EXTRA_SIGN_IN_FIRST, true);
            setActivityIntent(signInFirstIntent);
        }
        return setupActivity();
    }

    public LoginActivity setupActivity(){

        setActivityInitialTouchMode(false);
        LoginActivity activity = getActivity();
  
        // make sure all our fields are present
        mSignupButton = (Button) activity.findViewById(R.id.signup_button);
        mSigninButton = (Button) activity.findViewById(R.id.signin_button);
        mEmailTextField = (EditText) activity.findViewById(R.id.email_address);
        mPasswordTextField = (EditText) activity.findViewById(R.id.password);
        mPasswordTextField2 = (EditText) activity.findViewById(R.id.password2);
        mHaveAccountTextView = (TextView) activity.findViewById(R.id.no_account);
        mForgotPasswordButton = (TextView) activity.findViewById(R.id.forgot_password_button);
        mHaveAccountButton = (TextView) activity.findViewById(R.id.have_account_button);
        mCreateAccountButton = (TextView) activity.findViewById(R.id.create_account_button);
        mTermsOfService = (TextView) activity.findViewById(R.id.l_agree_terms_of_service);

        mActivity = activity;

        return activity;
    }

    public void testDefaultSignUp() {

        LoginActivity activity = setupActivity();

        // by default it should display the sign up form controls
        assertShowsSignUpForm();
    }

    public void testSignInFirst() {

        // initialize the activity
        setupActivity(true);

        // it should display the sign in form controls
        assertShowsSignInForm();
    }

    public void testPrefillEmail(){
        String email = "user@example.com";
        Intent intent = new Intent();
        intent.putExtra(LoginActivity.EMAIL_EXTRA, email);
        setActivityIntent(intent);
        setupActivity();

        assertEquals(email, mEmailTextField.getText().toString());
        assertEquals(mPasswordTextField, mActivity.getCurrentFocus());
    }

    public void testPrefillEmailFromUser(){
        String email = "user@example.com";
        mSimperium.getUser().setEmail(email);
        setupActivity();

        assertEquals(email, mEmailTextField.getText().toString());
        assertEquals(mPasswordTextField, mActivity.getCurrentFocus());
    }

    protected void assertShowsSignInForm(){
        assertEquals(View.GONE, mSignupButton.getVisibility());
        assertEquals(View.VISIBLE, mSigninButton.getVisibility());
        assertEquals(View.VISIBLE, mEmailTextField.getVisibility());
        assertEquals(View.VISIBLE, mPasswordTextField.getVisibility());
        assertEquals(View.GONE, mPasswordTextField2.getVisibility());
        assertEquals(View.GONE, mTermsOfService.getVisibility());
        assertEquals(View.GONE, mHaveAccountButton.getVisibility());
        assertEquals(View.VISIBLE, mCreateAccountButton.getVisibility());
        assertEquals(View.VISIBLE, mForgotPasswordButton.getVisibility());
        assertEquals(mActivity.getString(R.string.no_account), mHaveAccountTextView.getText());
    }

    protected void assertShowsSignUpForm(){
        assertEquals(View.VISIBLE, mSignupButton.getVisibility());
        assertEquals(View.GONE, mSigninButton.getVisibility());
        assertEquals(View.VISIBLE, mEmailTextField.getVisibility());
        assertEquals(View.VISIBLE, mPasswordTextField.getVisibility());
        assertEquals(View.VISIBLE, mPasswordTextField2.getVisibility());
        assertEquals(View.VISIBLE, mTermsOfService.getVisibility());
        assertEquals(View.VISIBLE, mHaveAccountButton.getVisibility());
        assertEquals(View.GONE, mCreateAccountButton.getVisibility());
        assertEquals(View.GONE, mForgotPasswordButton.getVisibility());
        assertEquals(mActivity.getString(R.string.have_account), mHaveAccountTextView.getText());
    }

}
