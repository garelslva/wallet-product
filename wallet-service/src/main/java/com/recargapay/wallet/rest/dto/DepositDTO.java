package com.recargapay.wallet.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DepositDTO {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("id")
    private String id;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("requestTransactionId")
    String requestTransactionId;

    @JsonProperty("amount")
    private BigDecimal amount;
}
