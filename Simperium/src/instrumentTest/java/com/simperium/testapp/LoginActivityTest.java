package com.simperium.testapp;

import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;

import static android.test.MoreAsserts.*;
import static com.simperium.testapp.TestHelpers.*;

import com.simperium.Simperium;
import com.simperium.android.LoginActivity;

import com.simperium.client.R;

import com.simperium.testapp.mock.MockClient;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class LoginActivityTest extends ActivityInstrumentationTestCase2<LoginActivity> {


    protected MockClient mClient;
    protected Simperium mSimperium;

    public LoginActivityTest() {
        super(LoginActivity.class);
    }

    /**
     * Configures a MockClient for use in testing
     */
    public LoginActivity setupActivity(){
        LoginActivity activity = getActivity();
        mClient = new MockClient();
        mSimperium = new Simperium("fake-app", "fake-secret", mClient);
        activity.setSimperium(mSimperium);
        return activity;
    }

    @Override
    protected void setUp() throws Exception {

        super.setUp();

        setActivityInitialTouchMode(false);
    }

    @UiThreadTest
    public void testDefaultActivityLayout() throws Exception {

        LoginActivity activity = setupActivity();

        assertNotNull(activity);

        // make sure all our fields are present
        Button signupButton = (Button) activity.findViewById(R.id.signin_button);
        Button signinButton = (Button) activity.findViewById(R.id.signin_button);
        EditText emailTextField = (EditText) activity.findViewById(R.id.email_address);
        EditText passwordTextField = (EditText) activity.findViewById(R.id.password);
        EditText passwordTextField2 = (EditText) activity.findViewById(R.id.password2);
        TextView haveAccountTextView = (TextView) activity.findViewById(R.id.no_account);
        TextView forgotPasswordButton = (TextView) activity.findViewById(R.id.forgot_password_button);
        TextView haveAccountButton = (TextView) activity.findViewById(R.id.have_account_button);
        TextView createAccountButton = (TextView) activity.findViewById(R.id.create_account_button);
        TextView termsOfService = (TextView) activity.findViewById(R.id.l_agree_terms_of_service);

    }

}
