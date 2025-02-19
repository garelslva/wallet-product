package com.recargapay.wallet.core.factory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.recargapay.wallet.converter.TransactionConverter;
import com.recargapay.wallet.core.enums.TransactionStatusType;
import com.recargapay.wallet.core.enums.TransactionType;
import com.recargapay.wallet.core.enums.WalletStatusType;
import com.recargapay.wallet.core.factory.context.TransactionFactory;
import com.recargapay.wallet.database.entity.Wallet;
import com.recargapay.wallet.event.dto.BalanceUpdateEvent;
import com.recargapay.wallet.event.dto.TransactionEvent;
import com.recargapay.wallet.core.factory.context.Transaction;
import com.recargapay.wallet.handle.exception.DepositException;
import com.recargapay.wallet.rest.dto.TransactionDTO;
import io.r2dbc.spi.Connection;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static com.recargapay.wallet.database.DbProperties.CONNECTION_DATABASE_TIMEOUT_SECONDS;
import static com.recargapay.wallet.handle.Message.DEPOSIT_FAILED_FOR_WALLET_ERROR;
import static com.recargapay.wallet.handle.Message.DEPOSIT_PROCESSED_SUCCESSFULLY_FOR_WALLET_INFO;
import static com.recargapay.wallet.handle.Message.DEPOSIT_PROCESSED_SUCCESSFULLY_INFO;
import static com.recargapay.wallet.handle.Message.FAILED_TO_PROCESS_DEPOSIT_ERROR;
import static com.recargapay.wallet.handle.Message.WALLET_IS_NOT_ACTIVE;
import static com.recargapay.wallet.handle.Message.WALLET_NOT_FOUND;

/**
 * Deposit transaction implementation following best modeling practices and clean code.
 */
@Slf4j
public class DepositTransaction implements Transaction {

    @JsonProperty
    private final TransactionEvent event;

    public DepositTransaction() {
        this.event = null;
    }

    public DepositTransaction(TransactionEvent event) {
        this.event = event;
    }

    /**
     * Factory method to create a DepositTransaction instance.
     *
     * @param event Transaction event
     * @return Instance of DepositTransaction
     */
    public static DepositTransaction of(TransactionEvent event) {
        return new DepositTransaction(event);
    }

    /**
     * Executes the deposit transaction.
     *
     * <p>The operation is executed within a reactive connection, where:
     * <ol>
     *   <li>A database transaction is started;</li>
     *   <li>The wallet is retrieved and validated;</li>
     *   <li>The deposit is processed and the balance updated;</li>
     *   <li>The balance update event is dispatched and the cache cleared;</li>
     *   <li>If everything goes well, the transaction is committed; otherwise, a rollback is performed.</li>
     * </ol>
     *
     * @param factory Factory providing the necessary components (database, repositories, event producers, cache)
     */
    @Override
    public void execute(TransactionFactory factory) {
        factory.databaseClient().inConnection(conn ->
                beginTransaction(conn)
                        .then(findWallet(factory, event.getWalletId()))
                        .flatMap(wallet -> validateAndProcessDeposit(factory, wallet))
                        .flatMap(transactionEntity -> commitTransaction(conn, transactionEntity))
                        .onErrorResume(error -> rollbackTransaction(conn, error))
        ).subscribe(
                result -> log.info(DEPOSIT_PROCESSED_SUCCESSFULLY_INFO.getMessage(), event.getWalletId(), event.getAmount(), event.getRequestTransactionId()),
                error -> log.error(FAILED_TO_PROCESS_DEPOSIT_ERROR.getMessage(), event.getWalletId(), event.getAmount(), event.getRequestTransactionId(), error.getMessage())
        );
    }

    private Mono<Void> beginTransaction(Connection conn) {
        return Mono.from(conn.beginTransaction());
    }

    private Mono<Wallet> findWallet(TransactionFactory factory, String walletId) {
        return factory.walletRepository().findById(walletId)
                .timeout(Duration.ofSeconds(CONNECTION_DATABASE_TIMEOUT_SECONDS))
                .switchIfEmpty(Mono.error(new DepositException(WALLET_NOT_FOUND)));
    }

    /**
     * Validates if the wallet is active, updates the balance, and saves the transaction.
     *
     * @param factory Factory providing the necessary repositories
     * @param wallet Wallet retrieved from the repository
     * @return Mono containing the persisted transaction entity
     */
    private Mono<com.recargapay.wallet.database.entity.Transaction> validateAndProcessDeposit(TransactionFactory factory, Wallet wallet) {
        if (!WalletStatusType.ACTIVE.name().equals(wallet.getStatus())) {
            return Mono.error(new DepositException(WALLET_IS_NOT_ACTIVE));
        }
        wallet.setCurrentBalance(wallet.getCurrentBalance().add(event.getAmount()));

        com.recargapay.wallet.database.entity.Transaction transactionEntity = TransactionConverter.eventToTransactionEntity(wallet.getId(), event, TransactionStatusType.DONE);
        return factory.transactionRepository().save(transactionEntity)
                .flatMap(savedTransaction -> sendBalanceUpdateAndClearCache(factory, savedTransaction));
    }

    /**
     * Dispatches the balance update event, clears the cache, and returns the transaction.
     *
     * @param factory Factory providing the event producer and cache
     * @param transactionEntity Saved transaction entity
     * @return Mono containing the transaction entity
     */
    private Mono<com.recargapay.wallet.database.entity.Transaction> sendBalanceUpdateAndClearCache(TransactionFactory factory, com.recargapay.wallet.database.entity.Transaction transactionEntity) {
        BalanceUpdateEvent balanceUpdateEvent = new BalanceUpdateEvent(
                event.getRequestTransactionId(),
                event.getWalletId(),
                event.getAmount(),
                TransactionType.DEPOSIT.getType()
        );
        return Mono.fromRunnable(() -> factory.balanceUpdateProducer().sendBalanceUpdate(balanceUpdateEvent))
                .then(clearWalletCache(factory))
                .thenReturn(transactionEntity)
                .doOnSuccess(tx -> log.info(DEPOSIT_PROCESSED_SUCCESSFULLY_FOR_WALLET_INFO.getMessage(), event.getWalletId(), event.getRequestTransactionId()))
                .doOnError(error -> log.error(DEPOSIT_FAILED_FOR_WALLET_ERROR.getMessage(), event.getWalletId(), event.getRequestTransactionId(), error.getMessage()));
    }

    private Mono<Void> clearWalletCache(TransactionFactory factory) {
        return factory.cache().clearBalanceCache(event.getWalletId());
    }

    private Mono<TransactionDTO> commitTransaction(Connection conn, com.recargapay.wallet.database.entity.Transaction transactionEntity) {
        return Mono.from(conn.commitTransaction())
                .thenReturn(TransactionConverter.entityToTransactionDTO(transactionEntity, TransactionStatusType.DONE));
    }

    private Mono<TransactionDTO> rollbackTransaction(Connection conn, Throwable error) {
        return Mono.from(conn.rollbackTransaction())
                .then(Mono.error(error));
    }
}
