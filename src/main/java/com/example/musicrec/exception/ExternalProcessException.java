package com.example.musicrec.exception;

public class ExternalProcessException extends RuntimeException {
    public ExternalProcessException(String message) { super(message); }
    public ExternalProcessException(String message, Throwable cause) { super(message, cause); }
}
