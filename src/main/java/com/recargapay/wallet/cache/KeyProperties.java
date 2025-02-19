package com.recargapay.wallet.cache;

import lombok.Getter;

@Getter
public enum KeyProperties {

    DUPLICATION_TRANSACTION_KEY("processed"),
    BALANCE_KEY("balance");

    private String key;

    KeyProperties(String key){
        this.key = key;
    }

    public String getKey(String param){
        return String.format("%s:%s", this.key, param);
    }
}