package com.recargapay.wallet.handle.exception;

import com.recargapay.wallet.handle.Message;

public class WalletInactiveException extends RuntimeException{

    public WalletInactiveException(String message){
        super(message);
    }

    public WalletInactiveException(Message message){
        super(message.getMessage());
        var httpCode = message.getCode();
    }
}
