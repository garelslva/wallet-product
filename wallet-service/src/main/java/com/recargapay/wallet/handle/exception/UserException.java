package com.recargapay.wallet.handle.exception;

import com.recargapay.wallet.handle.Message;
import lombok.Getter;

@Getter
public class UserException extends ParentException{

    public UserException(Message message){
        super(message.getMessage(), message.getCode());
    }
}
