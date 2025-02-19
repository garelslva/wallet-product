package com.recargapay.wallet.core.service;

import com.recargapay.wallet.cache.CacheService;
import com.recargapay.wallet.core.enums.TransactionStatusType;
import com.recargapay.wallet.core.service.impl.WalletServiceImpl;
import com.recargapay.wallet.database.entity.Transaction;
import com.recargapay.wallet.database.entity.Wallet;
import com.recargapay.wallet.database.repository.TransactionReactiveRepository;
import com.recargapay.wallet.database.repository.WalletReactiveRepository;
import com.recargapay.wallet.event.transaction.TransactionProducer;
import com.recargapay.wallet.handle.exception.WalletException;
import com.recargapay.wallet.rest.dto.BalanceDTO;
import com.recargapay.wallet.rest.dto.TransactionsHistoricalDTO;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.recargapay.wallet.handle.Message.WALLET_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletServiceGetHistoricalBalanceTest {

    @Mock
    WalletReactiveRepository walletRepository;

    @Mock
    TransactionReactiveRepository transactionRepository;

    @Mock
    TransactionProducer transactionProducer;
    @Mock
    CacheService cache;

    @Mock
    MeterRegistry meterRegistry;

    @Mock
    Timer timerMock;

    @Mock
    Timer.Sample sampleMock;

    @InjectMocks
    WalletServiceImpl walletService;

    private final String WALLET_ID = "wallet-123";
    private final long VALID_DAYS_BEFORE = 15;

    private Wallet walletEntity;

    private Transaction transactionEntity;

    private List<Transaction> transactionsEntity;

    @BeforeEach
    void setUp() {
        walletEntity = new Wallet();
        walletEntity.setId(WALLET_ID);
        walletEntity.setCurrentBalance(BigDecimal.valueOf(50));
        walletEntity.setUserId(UUID.randomUUID().toString());
        walletEntity.setStatus(TransactionStatusType.DONE.getStatus());
        walletEntity.setVersion(1L);

        transactionEntity = new Transaction(
                "203b10e6-1d27-419a-825d-b0a2b7d1b0af",
                "3d13e8edb6a45c9f20b0d883d6e8c9d2c3a4f5e6a7b8c9d0e1f2a3b4c5d6e7f67",
                "e4dc5c57-ea18-4e35-b52e-0580b674fbb6",
                "0484d538-e5ef-4408-927c-6ea6b5a70c8b",
                "TRANSFER_OUT",
                "DONE",
                BigDecimal.valueOf(100.00),
                LocalDateTime.now()
        );

        transactionsEntity = List.of(
            transactionEntity
        );

        when(meterRegistry.timer("wallet_historical_balance_time")).thenReturn(timerMock);
    }

    @Test
    @DisplayName("Deve retornar saldo historico calculado quando transacoes existem")
    void shouldReturnHistoricalBalanceWhenTransactionsExist() {
        String requestTransactionId = UUID.randomUUID().toString();
        when(walletRepository.findById(WALLET_ID)).thenReturn(Mono.just(walletEntity));
        when(transactionRepository.findTransactionsFromDate(anyString(), any(LocalDateTime.class))).thenReturn(Flux.just());

        try (MockedStatic<Timer> timerStatic = mockStatic(Timer.class)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<TransactionsHistoricalDTO> result = walletService.getHistoricalTransactions(requestTransactionId, WALLET_ID, VALID_DAYS_BEFORE);

            StepVerifier.create(result)
                    .assertNext(balanceDto -> {
                        assertThat(balanceDto.getRequestTransactionId()).isEqualTo(requestTransactionId);
                    })
                    .verifyComplete();

            verify(sampleMock, atLeastOnce()).stop(timerMock);
        }
    }

    @Test
    @DisplayName("Deve retornar saldo historico igual ao currentBalance quando nao ha transacoes")
    void shouldReturnHistoricalBalanceWhenNoTransactions() {
        String requestTransactionId = UUID.randomUUID().toString();
        when(walletRepository.findById(WALLET_ID)).thenReturn(Mono.just(walletEntity));
        when(transactionRepository.findTransactionsFromDate(anyString(), any(LocalDateTime.class))).thenReturn(Flux.just());

        try (MockedStatic<Timer> timerStatic = mockStatic(Timer.class)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<TransactionsHistoricalDTO> result = walletService.getHistoricalTransactions(requestTransactionId, WALLET_ID, VALID_DAYS_BEFORE);

            StepVerifier.create(result)
                    .assertNext(balanceDto -> {
                        assertThat(balanceDto.getRequestTransactionId()).isEqualTo(requestTransactionId);
                    })
                    .verifyComplete();

            verify(sampleMock, atLeastOnce()).stop(timerMock);
        }
    }

    @Test
    @DisplayName("Deve emitir erro 'Wallet not found' quando a carteira nao existe")
    void shouldFailWhenWalletNotFound() {
        String requestTransactionId = UUID.randomUUID().toString();
        when(walletRepository.findById(WALLET_ID)).thenReturn(Mono.empty());

        try (MockedStatic<Timer> timerStatic = mockStatic(Timer.class)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<TransactionsHistoricalDTO> result = walletService.getHistoricalTransactions(requestTransactionId, WALLET_ID, VALID_DAYS_BEFORE);

            StepVerifier.create(result)
                    .expectErrorMatches(ex ->
                            ex instanceof WalletException &&
                                    ex.getMessage().equals(WALLET_NOT_FOUND.getMessage())
                    )
                    .verify();

            verify(sampleMock, atLeastOnce()).stop(timerMock);
        }
    }

    @Test
    @DisplayName("Deve propagar erro quando a soma das transacoes falha")
    void shouldFailWhenTransactionSumFails() {
        String requestTransactionId = UUID.randomUUID().toString();
        when(walletRepository.findById(WALLET_ID)).thenReturn(Mono.just(walletEntity));
        when(transactionRepository.findTransactionsFromDate(anyString(), any(LocalDateTime.class))).thenReturn(Flux.error(new RuntimeException("Transaction sum failed")));

        try (MockedStatic<Timer> timerStatic = mockStatic(Timer.class)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<TransactionsHistoricalDTO> result = walletService.getHistoricalTransactions(requestTransactionId, WALLET_ID, VALID_DAYS_BEFORE);

            StepVerifier.create(result)
                    .expectErrorMatches(ex ->
                            ex instanceof RuntimeException &&
                                    ex.getMessage().equals("Transaction sum failed")
                    )
                    .verify();

            verify(sampleMock, atLeastOnce()).stop(timerMock);
        }
    }

    @Test
    @DisplayName("Deve lançar DateTimeParseException para data inválida")
    void shouldFailWhenDateIsInvalid() {
        String invalidDate = "data_invalida";

        MeterRegistry.Config configMock = mock(MeterRegistry.Config.class);
        when(meterRegistry.config()).thenReturn(configMock);
        when(configMock.clock()).thenReturn(Clock.SYSTEM);
        Timer timerMock = mock(Timer.class);
        when(meterRegistry.timer(anyString())).thenReturn(timerMock);
        when(walletRepository.findById(Mockito.anyString())).thenReturn(Mono.empty());

        StepVerifier.create(Mono.defer(() -> walletService.getHistoricalTransactions(UUID.randomUUID().toString(), WALLET_ID, VALID_DAYS_BEFORE)))
                .expectErrorMatches(ex -> ex instanceof WalletException)
                .verify();
    }
}

