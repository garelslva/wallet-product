package com.recargapay.wallet.converter;

import com.recargapay.wallet.database.entity.User;
import com.recargapay.wallet.database.entity.Wallet;
import com.recargapay.wallet.core.enums.WalletStatusType;
import com.recargapay.wallet.rest.dto.WalletDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WalletConverter {

    private WalletConverter(){}

    public static WalletDTO entityToWalletDto(Wallet wallet) {
        return new WalletDTO(
                wallet.getId(),
                wallet.getRequestTransactionId(),
                wallet.getUserId(),
                wallet.getCurrentBalance(),
                wallet.getStatus()
        );
    }

    public static Wallet userToWalletEntity(User user) {
        return new Wallet(
                null,
                user.getId(),
                user.getRequestTransactionId(),
                BigDecimal.ZERO,
                LocalDateTime.now(),
                WalletStatusType.ACTIVE.getType(),
                null
        );
    }
}
