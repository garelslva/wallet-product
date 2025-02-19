package com.recargapay.wallet.event;

import lombok.Getter;

@Getter
public enum BrokerProperties {


    WALLET_BALANCE_UPDATES("wallet-balance-updates", "wallet-service-group"),
    WALLET_TRANSACTIONS("wallet-transactions", "wallet-service-group");

    private String topic;
    private String group;

    BrokerProperties(String topic, String group){
        this.topic = topic;
        this.group = group;
    }
}
