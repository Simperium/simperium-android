package com.simperium.client;

import com.simperium.SimperiumException;

public class GhostMissingException extends SimperiumException {
  public GhostMissingException() { super(); }
  public GhostMissingException(String message) { super(message); }
  public GhostMissingException(String message, Throwable cause) { super(message, cause); }
  public GhostMissingException(Throwable cause) { super(cause); }
}
