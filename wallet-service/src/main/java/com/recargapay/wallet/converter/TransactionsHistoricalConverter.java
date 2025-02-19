package com.recargapay.wallet.converter;

import com.recargapay.wallet.database.entity.Transaction;
import com.recargapay.wallet.rest.dto.TransactionsHistoricalDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TransactionsHistoricalConverter {

    public static TransactionsHistoricalDTO entityToListDto(
            String requestTransactionId, LocalDateTime date, List<Transaction> transactions){

        List<TransactionsHistoricalDTO.HistoricalReportDTO> lstHistorical = new ArrayList<>();
        transactions.forEach(transaction -> lstHistorical.add(entityToHistoricalDto(transaction)));

        return new TransactionsHistoricalDTO(
                requestTransactionId,
                date,
                lstHistorical
        );
    }
    public static TransactionsHistoricalDTO.HistoricalReportDTO entityToHistoricalDto(Transaction transaction){
        return new TransactionsHistoricalDTO.HistoricalReportDTO(
                transaction.getId(),
                transaction.getRequestTransactionId(),
                transaction.getWalletId(),
                transaction.getDestinationWalletId(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getAmount(),
                transaction.getTimestamp()
        );
    }

}
