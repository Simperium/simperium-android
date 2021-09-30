package com.simperium.client;

import com.simperium.SimperiumException;

import java.util.Objects;

public class AuthException extends SimperiumException {

    static public final String GENERIC_FAILURE_MESSAGE = "Invalid username or password";
    static public final String EXISTING_USER_FAILURE_MESSAGE = "Account already exists";
    static public final String UNVERIFIED_ACCOUNT_MESSAGE = "Account verification required";
    static public final String COMPROMISED_PASSWORD_MESSAGE = "Password has been compromised";
    static public final String TOO_MANY_REQUESTS_MESSAGE = "Too many log in attempts";

    static public final String COMPROMISED_PASSWORD_BODY = "compromised password";
    static public final String VERIFICATION_REQUIRED_BODY = "verification required";

    static public final int ERROR_STATUS_CODE = -1;

    public final FailureType failureType;

    public enum FailureType {
        INVALID_ACCOUNT, EXISTING_ACCOUNT, COMPROMISED_PASSWORD, UNVERIFIED_ACCOUNT, TOO_MANY_REQUESTS
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
        String message = cause != null && cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";

        if (statusCode == 409) {
            return new AuthException(FailureType.EXISTING_ACCOUNT, EXISTING_USER_FAILURE_MESSAGE, cause);
        } else if (statusCode == 429) {
            return new AuthException(FailureType.TOO_MANY_REQUESTS, TOO_MANY_REQUESTS_MESSAGE, cause);
        } else if (statusCode == 403 && Objects.equals(message, VERIFICATION_REQUIRED_BODY)) {
            return new AuthException(FailureType.UNVERIFIED_ACCOUNT, UNVERIFIED_ACCOUNT_MESSAGE, cause);
        } else if (statusCode == 401 && Objects.equals(message, COMPROMISED_PASSWORD_BODY)) {
            return new AuthException(FailureType.COMPROMISED_PASSWORD, COMPROMISED_PASSWORD_MESSAGE, cause);
        } else {
            return new AuthException(FailureType.INVALID_ACCOUNT, GENERIC_FAILURE_MESSAGE, cause);
        }
    }
}
