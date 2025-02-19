package com.recargapay.wallet.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent extends ParentEvent{
    @JsonProperty
    private String type; // DEPOSIT, WITHDRAW, TRANSFER_OUT, TRANSFER_IN

    public TransactionEvent(String requestTransactionId, String walletId, BigDecimal amount, String type){
        super(requestTransactionId, walletId, amount);
        this.type = type;
    }
}