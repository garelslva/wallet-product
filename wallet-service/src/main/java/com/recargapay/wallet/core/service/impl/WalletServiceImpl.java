package com.recargapay.wallet.core.service.impl;

import com.recargapay.wallet.cache.CacheService;
import com.recargapay.wallet.converter.BalanceConverter;
import com.recargapay.wallet.converter.TransactionConverter;
import com.recargapay.wallet.converter.TransactionsHistoricalConverter;
import com.recargapay.wallet.converter.WalletConverter;
import com.recargapay.wallet.core.enums.WalletStatusType;
import com.recargapay.wallet.core.service.TransferService;
import com.recargapay.wallet.core.service.WalletService;
import com.recargapay.wallet.database.entity.Wallet;
import com.recargapay.wallet.database.repository.TransactionReactiveRepository;
import com.recargapay.wallet.database.repository.UserReactiveRepository;
import com.recargapay.wallet.database.repository.WalletReactiveRepository;
import com.recargapay.wallet.core.enums.TransactionStatusType;
import com.recargapay.wallet.core.enums.TransactionType;
import com.recargapay.wallet.event.dto.TransactionEvent;
import com.recargapay.wallet.core.factory.DepositTransaction;
import com.recargapay.wallet.core.factory.WithdrawTransaction;
import com.recargapay.wallet.event.transaction.TransactionProducer;
import com.recargapay.wallet.handle.exception.WalletException;
import com.recargapay.wallet.handle.exception.WithdrawException;
import com.recargapay.wallet.rest.dto.BalanceDTO;
import com.recargapay.wallet.rest.dto.CreateWalletDTO;
import com.recargapay.wallet.rest.dto.DepositDTO;
import com.recargapay.wallet.rest.dto.TransactionDTO;
import com.recargapay.wallet.rest.dto.TransactionsHistoricalDTO;
import com.recargapay.wallet.rest.dto.TransferDTO;
import com.recargapay.wallet.rest.dto.WalletDTO;
import com.recargapay.wallet.rest.dto.WithdrawDTO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static com.recargapay.wallet.cache.KeyProperties.BALANCE_KEY;
import static com.recargapay.wallet.database.DbProperties.CONNECTION_DATABASE_TIMEOUT_SECONDS;
import static com.recargapay.wallet.handle.Message.CACHE_HIT_FOR_WALLET;
import static com.recargapay.wallet.handle.Message.CREATING_WALLET_FOR_USER_INFO;
import static com.recargapay.wallet.handle.Message.FETCHING_BALANCE_FOR_WALLET_INFO;
import static com.recargapay.wallet.handle.Message.FETCHING_BALANCE_FROM_DATABASE_FOR_WALLET;
import static com.recargapay.wallet.handle.Message.INSUFFICIENT_FUNDS;
import static com.recargapay.wallet.handle.Message.PROCESSING_DEPOSIT_FOR_WALLET_AMOUNT_INFO;
import static com.recargapay.wallet.handle.Message.PROCESSING_TRANSFER_FROM_WALLET_TO_WALLET_AMOUNT_INFO;
import static com.recargapay.wallet.handle.Message.PROCESSING_WITHDRAW_FOR_WALLET_AMOUNT_INFO;
import static com.recargapay.wallet.handle.Message.TRANSACTION_ENQUEUED_INFO;
import static com.recargapay.wallet.handle.Message.USER_NOT_REGISTERED;
import static com.recargapay.wallet.handle.Message.WALLET_ALREADY_EXISTS;
import static com.recargapay.wallet.handle.Message.WALLET_IS_NOT_ACTIVE;
import static com.recargapay.wallet.handle.Message.WALLET_NOT_FOUND;

@Slf4j
@Service
@AllArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletReactiveRepository walletRepository;
    private final TransactionReactiveRepository transactionRepository;
    private final UserReactiveRepository userRepository;
    private final DatabaseClient databaseClient;
    private final TransactionProducer transactionProducer;
    private final TransferService transferService;
    private final CacheService cache;
    private final MeterRegistry meterRegistry;

    @Override
    public Mono<WalletDTO> createWallet(CreateWalletDTO request) {
        log.info(CREATING_WALLET_FOR_USER_INFO.getMessage(), request.getUserId(), request.getRequestTransactionId());
        Timer.Sample sample = Timer.start(meterRegistry);

        return cache.isDuplicateTransaction(request.getRequestTransactionId())
                .then(Mono.defer(() -> databaseClient.inConnection(conn ->
                    Mono.from(conn.beginTransaction())
                        .then(userRepository.findById(request.getUserId()))
                        .switchIfEmpty(Mono.defer(() -> Mono.error(new WalletException(USER_NOT_REGISTERED))))
                        .flatMap(user -> walletRepository.findByUserId(user.getId()).hasElements()
                        .flatMap(exists -> exists
                            ? Mono.error(new WalletException(WALLET_ALREADY_EXISTS))
                            : walletRepository.save(WalletConverter.userToWalletEntity(user))
                              .map(WalletConverter::entityToWalletDto))
                        )
                        .flatMap(walletDto -> Mono.from(conn.commitTransaction()).thenReturn(walletDto))
                        .onErrorResume(e -> Mono.from(conn.rollbackTransaction()).then(Mono.error(e)))
                        .doFinally(signalType -> sample.stop(meterRegistry.timer("create_wallet_time"))))));
    }

    @Override
    public Mono<BalanceDTO> getBalance(String requestTransactionId, String walletId) {
        log.info(FETCHING_BALANCE_FOR_WALLET_INFO.getMessage(), walletId);
        LocalDateTime dateTime = LocalDateTime.now();

        String cacheKey = BALANCE_KEY.getKey(walletId);
        Timer.Sample sample = Timer.start(meterRegistry);

        return cache.get(cacheKey).cache()
        .flatMap(cachedBalance -> {
            log.info(CACHE_HIT_FOR_WALLET.getMessage(), walletId);
            return Mono.just(BalanceConverter.paramToBalanceDTO(requestTransactionId, walletId, new BigDecimal(cachedBalance), dateTime));
        })
        .switchIfEmpty(fetchBalanceFromDatabase(requestTransactionId, walletId, cacheKey, dateTime))
        .doFinally(signalType -> sample.stop(meterRegistry.timer("wallet_balance_time")));
    }

    @Override
    public Mono<TransactionsHistoricalDTO> getHistoricalTransactions(String requestTransactionId, String walletId, long daysBefore) {
        Timer.Sample sample = Timer.start(meterRegistry);

        return walletRepository.findById(walletId)
                .switchIfEmpty(Mono.error(new WalletException(WALLET_NOT_FOUND)))
                .flatMap(balance -> transactionRepository.findTransactionsFromDate(walletId, LocalDateTime.now().minusDays(daysBefore))
                .collectList()
                .map(transactions ->
                        TransactionsHistoricalConverter.entityToListDto(requestTransactionId, LocalDateTime.now(), transactions)
                )
                .timeout(Duration.ofSeconds(CONNECTION_DATABASE_TIMEOUT_SECONDS)))
                .doFinally(signalType -> sample.stop(meterRegistry.timer("wallet_historical_balance_time")));
    }

    @Override
    @Transactional
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public Mono<TransactionDTO> deposit(String walletId, DepositDTO request) {
        log.info(PROCESSING_DEPOSIT_FOR_WALLET_AMOUNT_INFO.getMessage(), walletId, request.getAmount(), request.getRequestTransactionId());
        var amount = request.getAmount();
        var type = TransactionType.DEPOSIT;
        TransactionEvent event = TransactionConverter.paramToTransactionEvent(request.getRequestTransactionId(), walletId, amount, type);
        Timer.Sample sample = Timer.start(meterRegistry);
        var metric = "wallet_deposit_time";
        return cache.isDuplicateTransaction(request.getRequestTransactionId())

                .then(walletRepository.findById(walletId)
                        .switchIfEmpty(Mono.error(new WalletException(WALLET_NOT_FOUND)))
                        .flatMap(this::depositPreValidation)
                )
                .then(Mono.defer(() -> {
                    transactionProducer.sendTransaction(DepositTransaction.of(event));
                    log.info(TRANSACTION_ENQUEUED_INFO.getMessage(), event);

                    return Mono.just(TransactionConverter.paramToTransactionDTO(
                           request.getRequestTransactionId(),
                            null,
                           walletId,
                            walletId,
                            type,
                            TransactionStatusType.PROCESSING,
                            amount
                    ));
                }))
                .doFinally(signalType -> sample.stop(meterRegistry.timer(metric)));
    }

    @Override
    @Transactional
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public Mono<TransactionDTO> withdraw(String walletId, WithdrawDTO request) {
        log.info(PROCESSING_WITHDRAW_FOR_WALLET_AMOUNT_INFO.getMessage(), walletId, request.getAmount(), request.getRequestTransactionId());
        var amount = request.getAmount().multiply(BigDecimal.valueOf(-1L));
        var type = TransactionType.WITHDRAW;
        TransactionEvent event = TransactionConverter.paramToTransactionEvent(request.getRequestTransactionId(), walletId, amount, type);
        Timer.Sample sample = Timer.start(meterRegistry);
        var metric = "wallet_withdraw_time";
        return cache.isDuplicateTransaction(request.getRequestTransactionId())
                .when(walletRepository.findById(walletId)
                    .switchIfEmpty(Mono.error(new WalletException(WALLET_NOT_FOUND)))
                    .flatMap(wallet -> withdrawPreValidation(amount, wallet))
                )
                .then(Mono.defer(() -> {
                    transactionProducer.sendTransaction(WithdrawTransaction.build(event));
                    log.info(TRANSACTION_ENQUEUED_INFO.getMessage(), event);
                    return Mono.just(TransactionConverter.paramToTransactionDTO(
                            request.getRequestTransactionId(), null, walletId, walletId, type, TransactionStatusType.PROCESSING, amount));
                }))
                .doFinally(signalType -> sample.stop(meterRegistry.timer(metric)));
    }

    @Override
    @Transactional
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public Mono<TransactionDTO> transfer(String sourceWalletId, String destinationWalletId, TransferDTO request) {

        log.info(PROCESSING_TRANSFER_FROM_WALLET_TO_WALLET_AMOUNT_INFO.getMessage(),
                sourceWalletId, destinationWalletId, request.getAmount());
        Timer.Sample sample = Timer.start(meterRegistry);

        return transferService.validateDuplicateTransaction(request.getRequestTransactionId())
                .then(Mono.defer(() -> transferService.getAndValidateSourceWallet(sourceWalletId, request.getAmount())))
                .flatMap(sourceWallet -> transferService.processDestinationWallet(destinationWalletId))
                .flatMap(wallet -> transferService.enqueueTransaction(sourceWalletId, destinationWalletId, request))
                .doFinally(signalType -> sample.stop(meterRegistry.timer("wallet_transfer_time")));
    }

    private static Mono<Wallet> withdrawPreValidation(BigDecimal amount, Wallet wallet) {
        if (!WalletStatusType.ACTIVE.getType().equals(wallet.getStatus())) {
            return Mono.error(new WithdrawException(WALLET_IS_NOT_ACTIVE));
        }
        if (wallet.getCurrentBalance().compareTo(amount.abs()) < 0) {
            return Mono.error(new WithdrawException(INSUFFICIENT_FUNDS));
        }
        return Mono.just(wallet);
    }

    private Mono<Wallet> depositPreValidation(Wallet wallet) {
        if (!WalletStatusType.ACTIVE.getType().equals(wallet.getStatus())) {
            return Mono.error(new WithdrawException(WALLET_IS_NOT_ACTIVE));
        }
        return Mono.just(wallet);
    }

    public Mono<BalanceDTO> fetchBalanceFromDatabase(String requestTransactionId, String walletId, String cacheKey, LocalDateTime dateTime) {
        return walletRepository.findById(walletId)
                .switchIfEmpty(Mono.error(new WalletException(WALLET_NOT_FOUND)))
                .flatMap(total -> transactionRepository.findSumOfAmountByDestinationWalletId(walletId)
                .defaultIfEmpty(BigDecimal.ZERO)
                .map(totalBalance -> {
                    log.info(FETCHING_BALANCE_FROM_DATABASE_FOR_WALLET.getMessage(), walletId);
                    cache.set(cacheKey, String.valueOf(totalBalance));
                    return BalanceConverter.paramToBalanceDTO(requestTransactionId, walletId, totalBalance, dateTime);
                }));
    }

}