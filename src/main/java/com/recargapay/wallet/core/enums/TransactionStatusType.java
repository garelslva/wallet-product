package com.recargapay.wallet.core.enums;

import lombok.Getter;

@Getter
public enum TransactionStatusType {
    PROCESSING("PROCESSING"),
    BLOCKED("BLOCKED"),
    DONE("DONE"),
    ERROR("ERROR");

    private String status;

    TransactionStatusType(String status){
        this.status = status;
    }
}
