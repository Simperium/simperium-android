package com.simperium.client;

import com.simperium.SimperiumException;

/**
 * Base exception for exceptions related to Change instances.
 */
public class ChangeException extends SimperiumException {

    public final Change change;

    public ChangeException(Change change) {
        super();
        this.change = change;
    }

    public ChangeException(Change change, String message) {
        super(message);
        this.change = change;
    }

    public ChangeException(Change change, String message, Throwable cause) {
        super(message, cause);
        this.change = change;
    }

    public ChangeException(Change change, Throwable cause) {
        super(cause);
        this.change = change;
    }

    public Change getChange() {
        return this.change;
    }


}