package com.recargapay.wallet.converter;

import com.recargapay.wallet.database.entity.Transaction;
import com.recargapay.wallet.core.enums.TransactionStatusType;
import com.recargapay.wallet.core.enums.TransactionType;
import com.recargapay.wallet.event.dto.TransactionEvent;
import com.recargapay.wallet.rest.dto.TransactionDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionConverter {

    private TransactionConverter(){}

    public static TransactionDTO paramToTransactionDTO(String requestTransactionId, String transactionId, String sourceWalletId, String destinationWalletId, TransactionType withdraw, TransactionStatusType status, BigDecimal amount) {
        return new TransactionDTO(
                transactionId,
                requestTransactionId,
                destinationWalletId,
                sourceWalletId,
                withdraw.getType(),
                status.getStatus(),
                amount,
                LocalDateTime.now()
        );
    }

    public static TransactionEvent paramToTransactionEvent(String requestTransactionId, String walletId, BigDecimal amount, TransactionType transactionType) {
        return new TransactionEvent(
                requestTransactionId,
                walletId,
                amount,
                transactionType.getType()
        );
    }

    public static TransactionDTO entityToTransactionDTO(Transaction transaction, TransactionStatusType status) {
        var destinationWalletId = transaction.getDestinationWalletId() == null ? transaction.getWalletId() : transaction.getDestinationWalletId();
        return new TransactionDTO(
                transaction.getRequestTransactionId(),
                transaction.getId(),
                destinationWalletId,
                transaction.getWalletId(),
                transaction.getType(),
                status.getStatus(),
                transaction.getAmount(),
                transaction.getTimestamp());
    }

    public static Transaction eventToTransactionEntity(String targetWalletId, TransactionEvent event, TransactionStatusType status) {
        return new com.recargapay.wallet.database.entity.Transaction(
                null,
                event.getRequestTransactionId(),
                targetWalletId,
                event.getWalletId(),
                event.getType(),
                status.getStatus(),
                event.getAmount(),
                LocalDateTime.now());
    }
}
