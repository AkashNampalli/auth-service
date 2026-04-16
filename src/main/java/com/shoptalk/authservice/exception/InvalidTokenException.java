package com.shoptalk.authservice.exception;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String token) {
        super("please enter the valid token" +token);
    }
}
