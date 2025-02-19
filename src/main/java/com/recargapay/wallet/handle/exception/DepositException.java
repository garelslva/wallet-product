package com.recargapay.wallet.handle.exception;

import com.recargapay.wallet.handle.Message;
import lombok.Getter;

@Getter
public class DepositException extends ParentException{

    public DepositException(Message message){
        super(message.getMessage(), message.getCode());
    }
}
