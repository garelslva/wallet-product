package com.recargapay.wallet.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class WalletDTO {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("id")
    private String id;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("requestTransactionId")
    String requestTransactionId;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("currentBalance")
    private BigDecimal currentBalance;

    @JsonProperty("status")
    private String status;
}