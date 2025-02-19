package com.recargapay.wallet.handle.exception;

import com.recargapay.wallet.handle.Message;
import lombok.Getter;

@Getter
public class InternalServerErrorException extends ParentException{

    public InternalServerErrorException(Message message){
        super(message.getMessage(), message.getCode());
    }
}
