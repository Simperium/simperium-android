package com.simperium.client;

public class BucketObjectMissingException extends Exception {
    public BucketObjectMissingException() { super(); }
    public BucketObjectMissingException(String message) { super(message); }
    public BucketObjectMissingException(String message, Throwable cause) { super(message, cause); }
    public BucketObjectMissingException(Throwable cause) { super(cause); }    
}