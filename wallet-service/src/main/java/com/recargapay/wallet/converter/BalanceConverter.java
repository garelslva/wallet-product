package com.recargapay.wallet.converter;

import com.recargapay.wallet.rest.dto.BalanceDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BalanceConverter {

    private BalanceConverter(){}

    public static BalanceDTO paramToBalanceDTO(String requestTransactionId, String walletId, BigDecimal balance, LocalDateTime dateTime) {
        return new BalanceDTO(
                requestTransactionId,
                walletId,
                balance,
                dateTime
        );
    }
}
