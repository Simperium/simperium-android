package com.simperium.client;

import com.simperium.util.AuthUtil;

import org.json.JSONObject;

public class AuthResponseHandler {
    private AuthProvider mProvider;
    private AuthResponseListener mListener;
    private User mUser;

    public AuthResponseHandler(User user, AuthResponseListener listener, AuthProvider provider) {
        mUser = user;
        mListener = listener;
        mProvider = provider;
    }

    public void onResponse(JSONObject response){
        if (!response.optString(AuthUtil.USERID_KEY).isEmpty() && !response.optString(AuthUtil.ACCESS_TOKEN_KEY).isEmpty()) {
            mListener.onSuccess(mUser, response.optString(AuthUtil.USERID_KEY), response.optString(AuthUtil.ACCESS_TOKEN_KEY));
        } else {
            mListener.onFailure(mUser, AuthException.defaultException());
        }
    }

    public void onError(AuthException error) {
        mListener.onFailure(mUser, error);
    }
}
