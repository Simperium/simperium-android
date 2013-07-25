package com.simperium.client;

public class RemoteChangeInvalidException extends Exception {
  public RemoteChangeInvalidException() { super(); }
  public RemoteChangeInvalidException(String message) { super(message); }
  public RemoteChangeInvalidException(String message, Throwable cause) { super(message, cause); }
  public RemoteChangeInvalidException(Throwable cause) { super(cause); }
}
