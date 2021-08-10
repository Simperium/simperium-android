package com.simperium.client;

import com.simperium.SimperiumException;

import java.util.Objects;

public class AuthException extends SimperiumException {

    static public final String GENERIC_FAILURE_MESSAGE = "Invalid username or password";
    static public final String EXISTING_USER_FAILURE_MESSAGE = "Account already exists";
    static public final String COMPROMISED_PASSWORD_MESSAGE = "Password has been compromised";
    static public final String INVALID_LOGIN_BODY = "invalid login";

    static public final int ERROR_STATUS_CODE = -1;

    public final FailureType failureType;

    public enum FailureType {
        INVALID_ACCOUNT, EXISTING_ACCOUNT, COMPROMISED_PASSWORD
    }

    public AuthException(FailureType code, String message){
        super(message);
        failureType = code;
    }

    public AuthException(FailureType code, String message, Throwable cause){
        super(message, cause);
        failureType = code;
    }

    public static AuthException defaultException() {
        return exceptionForStatusCode(ERROR_STATUS_CODE);
    }

    public static AuthException exceptionForStatusCode(int statusCode) {
        return exceptionForStatusCode(statusCode, null);
    }

    public static AuthException exceptionForStatusCode(int statusCode, Throwable cause){
        switch (statusCode) {
            case 409:
                return new AuthException(FailureType.EXISTING_ACCOUNT, EXISTING_USER_FAILURE_MESSAGE, cause);
            case 401:
                // Code 401 can be obtain because credentials are wrong or the user's password has been compromised
                // To differentiate both responses, we check the response's body
                if (cause != null && Objects.equals(cause.getMessage(), INVALID_LOGIN_BODY)) {
                    return new AuthException(FailureType.INVALID_ACCOUNT, GENERIC_FAILURE_MESSAGE, cause);
                } else {
                    return new AuthException(FailureType.COMPROMISED_PASSWORD, COMPROMISED_PASSWORD_MESSAGE, cause);
                }
            default:
                return new AuthException(FailureType.INVALID_ACCOUNT, GENERIC_FAILURE_MESSAGE, cause);
        }
    }
}