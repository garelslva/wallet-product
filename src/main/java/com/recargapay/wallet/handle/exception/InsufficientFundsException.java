package com.recargapay.wallet.handle.exception;

import com.recargapay.wallet.handle.Message;

public class InsufficientFundsException extends RuntimeException{

    public InsufficientFundsException(String message){
        super(message);
    }

    public InsufficientFundsException(Message message){
        super(message.getMessage());
        var httpCode = message.getCode();
    }
}
