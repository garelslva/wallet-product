package com.recargapay.wallet.core.enums;

import lombok.Getter;

@Getter
public enum TransactionType {

    DEPOSIT("DEPOSIT"),
    TRANSFER_OUT("TRANSFER_OUT"),
    TRANSFER_IN("TRANSFER_IN"),
    WITHDRAW("WITHDRAW");

    private String type;

    TransactionType(String type){
        this.type = type;
    }
}
