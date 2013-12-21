package com.simperium.client;

/**
 * When a Change object is unable to create the JSON representation for any
 * reason this exception is thrown.
 */
public class ChangeInvalidException extends ChangeException {

    public ChangeInvalidException(Change change) {
        super(change);
    }

    public ChangeInvalidException(Change change, String message) {
        super(change, message);
    }

    public ChangeInvalidException(Change change, String message, Throwable cause) {
        super(change, message, cause);
    }

    public ChangeInvalidException(Change change, Throwable cause) {
        super(change, cause);
    }

}