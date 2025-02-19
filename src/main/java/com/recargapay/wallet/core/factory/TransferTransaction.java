package com.recargapay.wallet.core.factory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.recargapay.wallet.converter.TransactionConverter;
import com.recargapay.wallet.core.enums.TransactionStatusType;
import com.recargapay.wallet.core.enums.TransactionType;
import com.recargapay.wallet.core.factory.context.TransactionFactory;
import com.recargapay.wallet.core.factory.context.Transaction;
import com.recargapay.wallet.database.entity.Wallet;
import com.recargapay.wallet.event.dto.BalanceUpdateEvent;
import com.recargapay.wallet.event.dto.DualTransactionEvent;
import com.recargapay.wallet.handle.exception.TransferException;
import com.recargapay.wallet.rest.dto.TransactionDTO;
import io.r2dbc.spi.Connection;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;

import static com.recargapay.wallet.database.DbProperties.CONNECTION_DATABASE_TIMEOUT_SECONDS;
import static com.recargapay.wallet.handle.Message.CONCURRENT_MODIFICATION_DETECTED_TRY_AGAIN;
import static com.recargapay.wallet.handle.Message.DESTINATION_WALLET_NOT_FOUND;
import static com.recargapay.wallet.handle.Message.SENDING_OUTBOUND_TRANSFER_EVENT_ERROR;
import static com.recargapay.wallet.handle.Message.SOURCE_WALLET_NOT_FOUND;
import static com.recargapay.wallet.handle.Message.TRANSFER_FAILED_ERROR;
import static com.recargapay.wallet.handle.Message.TRANSFER_FAILED_FROM_TO_ERROR;
import static com.recargapay.wallet.handle.Message.TRANSFER_PROCESSED_SUCCESSFULLY_FROM_TO;
import static com.recargapay.wallet.handle.Message.TRANSFER_PROCESSED_SUCCESSFULLY_INFO;

@Slf4j
public class TransferTransaction implements Transaction {

    @JsonProperty
    private final DualTransactionEvent event;

    public TransferTransaction() {
        this.event = null;
    }
    public TransferTransaction(DualTransactionEvent event) {
        this.event = event;
    }

    /**
     * Factory method to create a new TransferTransaction.
     *
     * @param event the dual transaction event
     * @return a new TransferTransaction instance
     */
    public static TransferTransaction of(DualTransactionEvent event) {
        return new TransferTransaction(event);
    }

    /**
     * Executes the transfer transaction using the provided factory.
     *
     * The operation includes:
     * <ol>
     *   <li>Starting a database transaction</li>
     *   <li>Retrieving the source and destination wallets</li>
     *   <li>Validating the source wallet version to avoid concurrent modifications</li>
     *   <li>Updating wallet balances</li>
     *   <li>Saving both debit and credit transactions</li>
     *   <li>Dispatching balance update events and clearing the cache</li>
     *   <li>Committing the transaction (or rolling back in case of error)</li>
     * </ol>
     */
    @Override
    public void execute(TransactionFactory factory) {
        factory.databaseClient().inConnection(conn ->
                beginTransaction(conn)
                        .then(findSourceWallet(factory))
                        .timeout(Duration.ofSeconds(CONNECTION_DATABASE_TIMEOUT_SECONDS))
                        .flatMap(sourceWallet -> findDestinationWallet(factory)
                                .flatMap(destinationWallet -> processTransfer(factory, sourceWallet, destinationWallet)))
                        .flatMap(transactionEntity -> commitTransaction(conn))
                        .onErrorResume(e -> rollbackTransaction(conn, e).then())
        ).subscribe(
                dto -> log.info(TRANSFER_PROCESSED_SUCCESSFULLY_INFO.getMessage(),
                        event.getEventSource().getWalletId(), event.getEventDestination().getWalletId(),
                        event.getEventSource().getAmount(), event.getEventSource().getRequestTransactionId()),
                error -> log.error(TRANSFER_FAILED_ERROR.getMessage(), error.getMessage())
        );
    }

    private Mono<Void> beginTransaction(Connection conn) {
        return Mono.from(conn.beginTransaction());
    }

    private Mono<Wallet> findSourceWallet(TransactionFactory factory) {
        return factory.walletRepository()
                .findById(event.getEventSource().getWalletId())
                .switchIfEmpty(Mono.error(new TransferException(SOURCE_WALLET_NOT_FOUND)))
                .flatMap(wallet -> factory.transferService().validateSourceWallet(wallet, event.getEventSource().getAmount()))
                ;
    }

    private Mono<Wallet> findDestinationWallet(TransactionFactory factory) {
        return factory.walletRepository()
                .findById(event.getEventDestination().getWalletId())
                .switchIfEmpty(Mono.error(new TransferException(DESTINATION_WALLET_NOT_FOUND)));
    }

    private Mono<Void> processTransfer(
            TransactionFactory factory,
            Wallet sourceWallet,
            Wallet destinationWallet) {

        factory.walletRepository()
                .findByIdAndVersion(sourceWallet.getId(), sourceWallet.getVersion())
                .switchIfEmpty(Mono.error(new TransferException(CONCURRENT_MODIFICATION_DETECTED_TRY_AGAIN)))
                .flatMap(updatedSourceWallet -> {
                    var amount = event.getEventSource().getAmount();
                    updatedSourceWallet.setCurrentBalance(
                            updatedSourceWallet.getCurrentBalance().subtract(amount));

                    destinationWallet.setCurrentBalance(
                            destinationWallet.getCurrentBalance().add(amount));

                    event.getEventSource().setAmount(event.getEventSource().getAmount().multiply(BigDecimal.valueOf(-1)));

                    com.recargapay.wallet.database.entity.Transaction debitTransaction =
                            TransactionConverter.eventToTransactionEntity(event.getEventSource().getWalletId(), event.getEventSource(), TransactionStatusType.DONE);
                    com.recargapay.wallet.database.entity.Transaction creditTransaction =
                            TransactionConverter.eventToTransactionEntity(event.getEventSource().getWalletId(), event.getEventDestination(), TransactionStatusType.DONE);
                    creditTransaction.setWalletId(event.getEventSource().getWalletId());

                    return factory.transactionRepository().save(debitTransaction)
                            .then(factory.transactionRepository().save(creditTransaction))
                            .doOnSuccess(tx -> {
                                Mono.fromRunnable(() -> updateCurrentBallance(factory))
                                        .onErrorResume(e -> {
                                            log.error(SENDING_OUTBOUND_TRANSFER_EVENT_ERROR.getMessage(), e);
                                            return Mono.empty();
                                        })
                                        .doOnSuccess(sc -> {
                                                log.info(TRANSFER_PROCESSED_SUCCESSFULLY_FROM_TO.getMessage(),
                                                event.getEventSource().getWalletId(),
                                                event.getEventDestination().getWalletId());
                                                factory.cache().clearBalanceCache(event.getEventSource().getWalletId()).subscribe();
                                                factory.cache().clearBalanceCache(event.getEventDestination().getWalletId()).subscribe();
                                        })
                                        .subscribe();
                            })
                            .doOnError(error ->
                                    log.error(TRANSFER_FAILED_FROM_TO_ERROR.getMessage(),
                                            event.getEventSource().getWalletId(),
                                            event.getEventDestination().getWalletId(),
                                            error.getMessage()))
                            .then(Mono.just(debitTransaction));
                }).subscribe();

        return Mono.empty();
    }

    private void updateCurrentBallance(TransactionFactory factory) {
        factory.balanceUpdateProducer().sendBalanceUpdate(
                new BalanceUpdateEvent(
                        event.getEventSource().getRequestTransactionId(),
                        event.getEventSource().getWalletId(),
                        event.getEventSource().getAmount(),
                        TransactionType.TRANSFER_OUT.getType()));

        factory.balanceUpdateProducer().sendBalanceUpdate(
                new BalanceUpdateEvent(
                        event.getEventDestination().getRequestTransactionId(),
                        event.getEventDestination().getWalletId(),
                        event.getEventDestination().getAmount(),
                        TransactionType.TRANSFER_IN.getType()));
    }


    private Mono<Void> commitTransaction(Connection conn) {
        return Mono.from(conn.commitTransaction());
    }

    private Mono<TransactionDTO> rollbackTransaction(Connection conn, Throwable error) {
        return Mono.from(conn.rollbackTransaction()).then(Mono.error(error));
    }
}
