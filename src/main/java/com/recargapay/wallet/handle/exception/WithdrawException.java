package com.recargapay.wallet.handle.exception;

import com.recargapay.wallet.handle.Message;
import lombok.Getter;

@Getter
public class WithdrawException extends ParentException{

    public WithdrawException(Message message){
        super(message.getMessage(), message.getCode());
    }
}
