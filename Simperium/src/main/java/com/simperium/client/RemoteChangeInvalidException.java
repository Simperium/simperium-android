package com.simperium.client;

import com.simperium.SimperiumException;

public class RemoteChangeInvalidException extends SimperiumException {

    public final RemoteChange remoteChange;

    public RemoteChangeInvalidException(RemoteChange change, Throwable cause) {
        super(cause);
        this.remoteChange = change;
    }

    public RemoteChangeInvalidException(RemoteChange change, String message) {
        super(message);
        this.remoteChange = change;
    }

    public RemoteChangeInvalidException(RemoteChange change, String message, Throwable cause) {
        super(message, cause);
        this.remoteChange = change;
    }

}
