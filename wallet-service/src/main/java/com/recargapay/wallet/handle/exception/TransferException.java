package com.recargapay.wallet.handle.exception;

import com.recargapay.wallet.handle.Message;
import lombok.Getter;

@Getter
public class TransferException extends ParentException{

    public TransferException(Message message){
        super(message.getMessage(), message.getCode());
    }
}
