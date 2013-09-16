package com.simperium.client;

import com.simperium.SimperiumException;

public class RemoteChangeInvalidException extends SimperiumException {
  public RemoteChangeInvalidException() { super(); }
  public RemoteChangeInvalidException(String message) { super(message); }
  public RemoteChangeInvalidException(String message, Throwable cause) { super(message, cause); }
  public RemoteChangeInvalidException(Throwable cause) { super(cause); }
}
