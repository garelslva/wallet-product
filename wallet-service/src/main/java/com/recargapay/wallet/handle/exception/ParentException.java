package com.recargapay.wallet.handle.exception;

import lombok.Getter;

@Getter
public class ParentException extends RuntimeException{

    protected final String message;
    protected final int statusCode;

    ParentException(String message, int statusCode){
        super(message);
        this.message = message;
        this.statusCode = statusCode;
    }
}
