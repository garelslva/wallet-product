package com.recargapay.wallet.core.enums;

import lombok.Getter;

@Getter
public enum WalletStatusType {

    ACTIVE("ACTIVE");

    private String type;

    WalletStatusType(String type){
        this.type = type;
    }
}
