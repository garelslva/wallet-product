package com.recargapay.wallet.handle.exception;

import com.recargapay.wallet.handle.Message;
import lombok.Getter;

@Getter
public class WalletException extends ParentException{

    public WalletException(Message message){
        super(message.getMessage(), message.getCode());
    }
}
