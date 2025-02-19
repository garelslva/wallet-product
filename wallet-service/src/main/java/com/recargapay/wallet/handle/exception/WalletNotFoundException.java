package com.recargapay.wallet.handle.exception;

import com.recargapay.wallet.handle.Message;
import lombok.Getter;

@Getter
public class WalletNotFoundException extends RuntimeException{

    public WalletNotFoundException(String message){
        super(message);
    }

    public WalletNotFoundException(Message message){
        super(message.getMessage());
        var httpCode = message.getCode();
    }
}
