package com.simperium.client;

import org.json.JSONObject;
import org.json.JSONException;

import com.simperium.util.AuthUtil;

public class AuthResponseHandler {

    private AuthResponseListener mListener;
    private User mUser;

    public AuthResponseHandler(User user, AuthResponseListener listener){
        mUser = user;
        mListener = listener;
    }

    public void onResponse(JSONObject response){

        try {
            mUser.setUserId(response.getString(AuthUtil.USERID_KEY));
            mUser.setAccessToken(response.getString(AuthUtil.ACCESS_TOKEN_KEY));
        } catch(JSONException error){
            mListener.onError(mUser, error);
            return;
        }

        mUser.setStatus(User.Status.AUTHORIZED);
        mListener.onSuccess(mUser);

        // throw new RuntimeException(String.format("Response %s", responseJSON));
        //
        // check the validity of the user and call correct listener method
        // returns an AsyncHttpResponseHandlert
        // return new JsonHttpResponseHandler(){
        //     @Override
        //     public void onSuccess(int statusCode, JSONObject response){
        //         // parse the response to JSON
        //         // user was created, notify of a new user
        //     }
        //     @Override
        //     public void onFailure(Throwable error, JSONObject response){
        //         handler.onInvalid(user, error, response);
        //     }
        //     @Override
        //     public void onFailure(Throwable error, String response){
        //         handler.onFailure(user, error, response);
        //     }
        // };
    }

    public void onError(Throwable error){
        mListener.onError(mUser, error);
    }

}
