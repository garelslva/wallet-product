package com.recargapay.wallet.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ParentEvent {
    @JsonProperty
    protected String requestTransactionId;
    @JsonProperty
    protected String walletId;
    @JsonProperty
    protected BigDecimal amount;
}
