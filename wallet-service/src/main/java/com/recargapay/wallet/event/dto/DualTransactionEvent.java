package com.recargapay.wallet.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DualTransactionEvent {

    @JsonProperty
    private TransactionEvent eventSource;
    @JsonProperty
    private TransactionEvent eventDestination;

}