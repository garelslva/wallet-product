package com.recargapay.wallet.handle.exception;

import com.recargapay.wallet.handle.Message;
import lombok.Getter;

@Getter
public class TrackerException extends ParentException{

    public TrackerException(Message message){
        super(message.getMessage(), message.getCode());
    }
}
