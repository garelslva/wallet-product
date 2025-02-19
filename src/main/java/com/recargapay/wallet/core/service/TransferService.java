package com.recargapay.wallet.core.service;

import com.recargapay.wallet.cache.CacheService;
import com.recargapay.wallet.converter.TransactionConverter;
import com.recargapay.wallet.core.enums.TransactionStatusType;
import com.recargapay.wallet.core.enums.TransactionType;
import com.recargapay.wallet.core.enums.WalletStatusType;
import com.recargapay.wallet.core.factory.TransferTransaction;
import com.recargapay.wallet.database.entity.Wallet;
import com.recargapay.wallet.database.repository.TransactionReactiveRepository;
import com.recargapay.wallet.database.repository.WalletReactiveRepository;
import com.recargapay.wallet.event.dto.DualTransactionEvent;
import com.recargapay.wallet.event.transaction.TransactionProducer;
import com.recargapay.wallet.handle.exception.InsufficientFundsException;
import com.recargapay.wallet.handle.exception.WalletInactiveException;
import com.recargapay.wallet.handle.exception.WalletNotFoundException;
import com.recargapay.wallet.rest.dto.TransactionDTO;
import com.recargapay.wallet.rest.dto.TransferDTO;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ConcurrentModificationException;

import static com.recargapay.wallet.database.DbProperties.CONNECTION_DATABASE_TIMEOUT_SECONDS;
import static com.recargapay.wallet.handle.Message.CONCURRENT_MODIFICATION_DETECTED_TRY_AGAIN;
import static com.recargapay.wallet.handle.Message.INSUFFICIENT_FUNDS;
import static com.recargapay.wallet.handle.Message.TRANSACTION_ENQUEUED_INFO;
import static com.recargapay.wallet.handle.Message.WALLET_DESTINATION_IS_NOT_ACTIVE;
import static com.recargapay.wallet.handle.Message.WALLET_DESTINATION_NOT_FOUND;
import static com.recargapay.wallet.handle.Message.WALLET_IS_NOT_ACTIVE1;
import static com.recargapay.wallet.handle.Message.WALLET_NOT_FOUND1;

@Slf4j
@AllArgsConstructor
@Service
public class TransferService {

    public final WalletReactiveRepository walletRepository;
    public final TransactionReactiveRepository transactionRepository;
    public final TransactionProducer transactionProducer;
    public final CacheService cache;
    public final MeterRegistry meterRegistry;

    /**
     * Verifica se a transação já foi processada anteriormente.
     */
    public Mono<Void> validateDuplicateTransaction(String transactionId) {
        return cache.isDuplicateTransaction(transactionId);
    }

    /**
     * Busca e valida a carteira de origem.
     */
    public Mono<Wallet> getAndValidateSourceWallet(String sourceWalletId, BigDecimal amount) {
        return walletRepository.findById(sourceWalletId)
                .timeout(Duration.ofSeconds(CONNECTION_DATABASE_TIMEOUT_SECONDS))
                .switchIfEmpty(Mono.error(new WalletNotFoundException(WALLET_NOT_FOUND1.getMessage())))
                .flatMap(wallet -> validateSourceWallet(wallet, amount));
    }

    /**
     * Valida se a carteira de origem está ativa e tem saldo suficiente.
     */
    public Mono<Wallet> validateSourceWallet(Wallet wallet, BigDecimal amount) {
        if (!wallet.getStatus().equals(WalletStatusType.ACTIVE.getType())) {
            return Mono.error(new WalletInactiveException(WALLET_IS_NOT_ACTIVE1.getMessage()));
        }
        if (wallet.getCurrentBalance().compareTo(amount.abs()) < 0) {
            return Mono.error(new InsufficientFundsException(INSUFFICIENT_FUNDS));
        }
        return walletRepository.findByIdAndVersion(wallet.getId(), wallet.getVersion())
                .switchIfEmpty(Mono.error(new ConcurrentModificationException(CONCURRENT_MODIFICATION_DETECTED_TRY_AGAIN.getMessage())));
    }

    /**
     * Processa e valida a carteira de destino.
     */
    public Mono<Wallet> processDestinationWallet(String destinationWalletId) {
        return walletRepository.findById(destinationWalletId)
                .switchIfEmpty(Mono.error(new WalletNotFoundException(WALLET_DESTINATION_NOT_FOUND.getMessage())))
                .flatMap(this::validateDestinationWallet);
    }

    /**
     * Valida se a carteira de destino está ativa e previne concorrência.
     */
    public Mono<Wallet> validateDestinationWallet(Wallet destinationWallet) {
        if (!destinationWallet.getStatus().equals(WalletStatusType.ACTIVE.getType())) {
            return Mono.error(new WalletInactiveException(WALLET_DESTINATION_IS_NOT_ACTIVE.getMessage()));
        }
        return Mono.just(destinationWallet);
    }

    /**
     * Enfileira a transação e retorna a resposta DTO.
     */
    public Mono<TransactionDTO> enqueueTransaction(String sourceWalletId, String destinationWalletId, TransferDTO request) {
        var eventSource = TransactionConverter.paramToTransactionEvent(
                request.getRequestTransactionId(),
                sourceWalletId,
                request.getAmount(),
                TransactionType.TRANSFER_OUT
        );
        var eventDestination = TransactionConverter.paramToTransactionEvent(
                request.getRequestTransactionId(),
                destinationWalletId,
                request.getAmount(),
                TransactionType.TRANSFER_IN
        );

        log.info(TRANSACTION_ENQUEUED_INFO.getMessage(), eventSource.getRequestTransactionId(), eventSource.getWalletId());
        log.info(TRANSACTION_ENQUEUED_INFO.getMessage(), eventDestination.getRequestTransactionId(), eventDestination.getWalletId());

        transactionProducer.sendTransaction(TransferTransaction.of(new DualTransactionEvent(eventSource, eventDestination)));

        return Mono.just(TransactionConverter.paramToTransactionDTO(
                request.getRequestTransactionId(),
                null,
                sourceWalletId,
                destinationWalletId,
                TransactionType.TRANSFER_OUT,
                TransactionStatusType.PROCESSING,
                request.getAmount())
        );
    }

}
