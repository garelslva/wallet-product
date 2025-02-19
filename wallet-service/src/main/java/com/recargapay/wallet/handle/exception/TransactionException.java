package com.recargapay.wallet.handle.exception;

import com.recargapay.wallet.handle.Message;
import lombok.Getter;

@Getter
public class TransactionException extends ParentException{

    public TransactionException(Message message){
        super(message.getMessage(), message.getCode());
    }
}
