package com.simperium.client;

import com.simperium.SimperiumException;

public class AuthException extends SimperiumException {

    static public final String GENERIC_FAILURE_MESSAGE = "Invalid username or password";
    static public final String EXISTING_USER_FAILURE_MESSAGE = "Account already exists";

    static public final int ERROR_STATUS_CODE = -1;
    static public final int INVALID_ACCOUNT_CODE = 0x0;
    static public final int EXISTING_ACCOUNT_CODE = 0x1;

    public final FailureType failureType;

    public enum FailureType {
        INVALID_ACCOUNT, EXISTING_ACCOUNT
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
            default:
                return new AuthException(FailureType.INVALID_ACCOUNT, GENERIC_FAILURE_MESSAGE, cause);
        }
    }
}