package com.recargapay.wallet.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateWalletDTO {

    @JsonProperty("userId")
    private String userId;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("requestTransactionId")
    String requestTransactionId;
}
