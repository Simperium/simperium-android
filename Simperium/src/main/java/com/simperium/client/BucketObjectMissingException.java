package com.simperium.client;

import com.simperium.SimperiumException;

public class BucketObjectMissingException extends SimperiumException {
    public BucketObjectMissingException() { super(); }
    public BucketObjectMissingException(String message) { super(message); }
    public BucketObjectMissingException(String message, Throwable cause) { super(message, cause); }
    public BucketObjectMissingException(Throwable cause) { super(cause); }    
}