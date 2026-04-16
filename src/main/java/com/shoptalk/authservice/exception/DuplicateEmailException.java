package com.shoptalk.authservice.exception;

public class DuplicateEmailException extends RuntimeException{
    public DuplicateEmailException(String email){
        super("My Dear friend this email already exists "+email);
    }
}
