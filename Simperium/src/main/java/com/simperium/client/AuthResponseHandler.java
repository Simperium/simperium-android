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
            mListener.onFailure(mUser, AuthException.defaultException());
            return;
        }

        mUser.setStatus(User.Status.AUTHORIZED);
        mListener.onSuccess(mUser);
    }

    public void onError(AuthException error){
        mListener.onFailure(mUser, error);
    }

}
