package com.shoptalk.authservice.exception;

public class InvalidCredentialException extends RuntimeException {
    public InvalidCredentialException(String credentials) {
        super("please enter the valid credentials" +credentials);
    }
}
