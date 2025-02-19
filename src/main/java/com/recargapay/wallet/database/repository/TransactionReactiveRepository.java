package com.recargapay.wallet.database.repository;

import com.recargapay.wallet.database.entity.Transaction;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface TransactionReactiveRepository extends ReactiveCrudRepository<Transaction, String> {

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE destination_wallet_id = :walletId")
    Mono<BigDecimal> findSumOfAmountByDestinationWalletId(@Param("destinationWalletId") String destinationWalletId);

    @Query("SELECT * FROM transactions WHERE wallet_id = :walletId AND timestamp >= :startDate")
    Flux<Transaction> findTransactionsFromDate(@Param("walletId") String walletId, @Param("startDate") LocalDateTime startDate);

}
