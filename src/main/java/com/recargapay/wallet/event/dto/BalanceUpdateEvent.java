package com.recargapay.wallet.event.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class BalanceUpdateEvent extends ParentEvent{
    private String transactionType; // DEPOSIT, WITHDRAW, TRANSFER_OUT, TRANSFER_IN

    public BalanceUpdateEvent(String requestTransactionId, String id, BigDecimal amount, String transactionType){
        super(requestTransactionId, id, amount);
        this.transactionType = transactionType;
    }
}
