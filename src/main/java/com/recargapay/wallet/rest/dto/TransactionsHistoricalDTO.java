package com.recargapay.wallet.rest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TransactionsHistoricalDTO {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("requestTransactionId")
    private String requestTransactionId;

    @JsonProperty("date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss.SSSSSS")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime date;

    @JsonProperty("transactions")
    private List<HistoricalReportDTO> transactions;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HistoricalReportDTO{

        @JsonProperty("id")
        private String id;

        @JsonProperty("requestTransactionId")
        private String requestTransactionId;

        @JsonProperty("walletId")
        private String walletId;

        @JsonProperty("destinationWalletId")
        private String destinationWalletId;

        @JsonProperty("type")
        private String type;

        @JsonProperty("status")
        private String status;

        @JsonProperty("amount")
        private BigDecimal amount;

        @JsonProperty("timestamp")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss.SSSSSS")
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        @JsonSerialize(using = LocalDateTimeSerializer.class)
        private LocalDateTime timestamp;

    }
}