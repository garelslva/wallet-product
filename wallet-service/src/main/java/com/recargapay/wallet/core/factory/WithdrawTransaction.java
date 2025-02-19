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
import com.recargapay.wallet.handle.exception.InternalServerErrorException;
import com.recargapay.wallet.handle.exception.WithdrawException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import static com.recargapay.wallet.handle.Message.INSUFFICIENT_FUNDS;
import static com.recargapay.wallet.handle.Message.TRANSACTION_WITHDRAW_WALLET_IS_EMPTY;
import static com.recargapay.wallet.handle.Message.WALLET_IS_NOT_ACTIVE;
import static com.recargapay.wallet.handle.Message.WITHDRAW_FAILED_FOR_WALLET_ERROR;
import static com.recargapay.wallet.handle.Message.WITHDRAW_PROCESSED_SUCCESSFULLY_FOR_WALLET_INFO;

@Slf4j
public class WithdrawTransaction implements Transaction {

    @JsonProperty
    private TransactionEvent event;

    public WithdrawTransaction(){
        this.event = null;
    }
    public WithdrawTransaction(TransactionEvent event){
        this.event = event;
    }

    public static Transaction build(TransactionEvent event) {
        return new WithdrawTransaction(event);
    }

    @Override
    public void execute(TransactionFactory factory) {

        Mono.from(factory.walletRepository().findById(event.getWalletId()))
        .switchIfEmpty(Mono.error(
                new InternalServerErrorException(TRANSACTION_WITHDRAW_WALLET_IS_EMPTY))
        )
        .flatMap(this::validateAndProcessWithdraw)
        .flatMap(wallet -> transactionExecute(wallet, factory))
        .subscribe();
    }

    private Mono<Wallet> validateAndProcessWithdraw(Wallet wallet) {
            if (!WalletStatusType.ACTIVE.getType().equals(wallet.getStatus())) {
                return Mono.error(new WithdrawException(WALLET_IS_NOT_ACTIVE));
            }
            if (wallet.getCurrentBalance().compareTo(event.getAmount().abs()) < 0) {
                return Mono.error(new WithdrawException(INSUFFICIENT_FUNDS));
            }
            return Mono.just(wallet);
    }

    private Mono<Void> transactionExecute(Wallet wallet, TransactionFactory factory) {

        wallet.setCurrentBalance(wallet.getCurrentBalance().subtract(event.getAmount()));
        var transaction = TransactionConverter.eventToTransactionEntity(event.getWalletId(), event, TransactionStatusType.DONE);

        return factory.databaseClient().inConnection(conn ->
                Mono.from(conn.beginTransaction())
                        .then(factory.transactionRepository().save(transaction))
                        .doOnNext(tx -> updateCurrentBalance(factory))
                        .flatMap(walletDto -> Mono.from(conn.commitTransaction()))
                        .onErrorResume(e -> Mono.from(conn.rollbackTransaction()).then(Mono.error(e)))
        );
    }

    private void updateCurrentBalance(TransactionFactory factory) {
        factory.balanceUpdateProducer().sendBalanceUpdate(new BalanceUpdateEvent(
                event.getRequestTransactionId(),
                event.getWalletId(),
                event.getAmount(),
                TransactionType.WITHDRAW.getType())
        );
        log.info(WITHDRAW_PROCESSED_SUCCESSFULLY_FOR_WALLET_INFO.getMessage(), event.getWalletId(), event.getRequestTransactionId());
        factory.cache().clearBalanceCache(event.getWalletId()).subscribe();
    }

}
