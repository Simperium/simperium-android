package com.simperium;

public class SimperiumException extends Exception {
  public SimperiumException() { super(); }
  public SimperiumException(String message) { super(message); }
  public SimperiumException(String message, Throwable cause) { super(message, cause); }
  public SimperiumException(Throwable cause) { super(cause); }
}
