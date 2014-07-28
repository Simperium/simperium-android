package com.simperium.client;

/**
 * When a Change object does not produce any actual change this exception is
 * thrown.
 */
public class ChangeEmptyException extends ChangeException {

    public ChangeEmptyException(Change change) {
        super(change);
    }

    public ChangeEmptyException(Change change, String message) {
        super(change, message);
    }

    public ChangeEmptyException(Change change, String message, Throwable cause) {
        super(change, message, cause);
    }

    public ChangeEmptyException(Change change, Throwable cause) {
        super(change, cause);
    }

}
