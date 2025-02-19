package com.recargapay.wallet.handle.exception;

import com.recargapay.wallet.handle.Message;
import lombok.Getter;

@Getter
public class BalanceException extends ParentException{

    public BalanceException(Message message){
        super(message.getMessage(), message.getCode());
    }
}
