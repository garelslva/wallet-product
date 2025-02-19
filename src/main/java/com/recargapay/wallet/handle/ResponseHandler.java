package com.recargapay.wallet.handle;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ResponseHandler {

    @JsonProperty("status")
    private String status;
    @JsonProperty("error")
    private String error;
    @JsonProperty("message")
    private String message;
    @JsonProperty("path")
    private String path;
}
