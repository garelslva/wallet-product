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
public class UserDTO {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("id")
    private String id;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("requestTransactionId")
    String requestTransactionId;

    @JsonProperty("username")
    private String username;

    @JsonProperty("name")
    private String name;

    @JsonProperty("email")
    private String email;

    @JsonProperty("cpf")
    private String cpf;
}
