package com.recargapay.wallet.handle.exception;

import com.recargapay.wallet.handle.Message;
import lombok.Getter;

@Getter
public class EventException extends ParentException{

    public EventException(Message message){
        super(message.getMessage(), message.getCode());
    }
}
