package com.simperium.client;

public class GhostMissingException extends Exception {
  public GhostMissingException() { super(); }
  public GhostMissingException(String message) { super(message); }
  public GhostMissingException(String message, Throwable cause) { super(message, cause); }
  public GhostMissingException(Throwable cause) { super(cause); }
}
