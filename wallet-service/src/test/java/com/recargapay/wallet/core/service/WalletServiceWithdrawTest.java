package com.recargapay.wallet.core.service;

import com.recargapay.wallet.cache.CacheService;
import com.recargapay.wallet.converter.TransactionConverter;
import com.recargapay.wallet.core.enums.TransactionStatusType;
import com.recargapay.wallet.core.enums.TransactionType;
import com.recargapay.wallet.core.enums.WalletStatusType;
import com.recargapay.wallet.core.factory.WithdrawTransaction;
import com.recargapay.wallet.core.service.impl.WalletServiceImpl;
import com.recargapay.wallet.database.entity.Wallet;
import com.recargapay.wallet.database.repository.WalletReactiveRepository;
import com.recargapay.wallet.event.transaction.TransactionProducer;
import com.recargapay.wallet.handle.exception.WalletException;
import com.recargapay.wallet.handle.exception.WithdrawException;
import com.recargapay.wallet.rest.dto.TransactionDTO;
import com.recargapay.wallet.rest.dto.WithdrawDTO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.recargapay.wallet.handle.Message.DUPLICATE_TRANSACTION_DETECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletServiceWithdrawTest {

    @Mock
    CacheService cache;

    @Mock
    TransactionProducer transactionProducer;

    @Mock
    WalletReactiveRepository walletRepository;

    @Mock
    MeterRegistry meterRegistry;

    @InjectMocks
    WalletServiceImpl walletService;

    private final String WALLET_ID = "wallet-123";
    private final String TRANSACTION_ID = "tx-001";
    private final BigDecimal AMOUNT = BigDecimal.valueOf(200);

    private WithdrawDTO withdrawRequest;
    private TransactionDTO expectedTransactionDTO;

    private Wallet walletEntity;

    @BeforeEach
    void setUp() {
        withdrawRequest = new WithdrawDTO(
                TRANSACTION_ID,
                "tx-001",
                AMOUNT
        );

        expectedTransactionDTO = new TransactionDTO(
                TRANSACTION_ID,
                UUID.randomUUID().toString(),
                WALLET_ID,
                WALLET_ID,
                TransactionType.WITHDRAW.getType(),
                TransactionStatusType.PROCESSING.getStatus(),
                AMOUNT,
                LocalDateTime.now()
        );

        walletEntity = new Wallet();
        walletEntity.setId(WALLET_ID);
        walletEntity.setCurrentBalance(BigDecimal.valueOf(50));
        walletEntity.setUserId(UUID.randomUUID().toString());
        walletEntity.setStatus(WalletStatusType.ACTIVE.getType());
        walletEntity.setVersion(1L);

        when(cache.isDuplicateTransaction(anyString())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Withdraw Success: Deve processar a retirada com sucesso e retornar TransactionDTO")
    void withdrawSuccess() {
        walletEntity.setCurrentBalance(BigDecimal.valueOf(200));
        Timer.Sample sampleMock = mock(Timer.Sample.class);
        when(sampleMock.stop(any(Timer.class))).thenReturn(0L);
        Timer timerMock = mock(Timer.class);
        when(meterRegistry.timer("wallet_withdraw_time")).thenReturn(timerMock);
        when(walletRepository.findById(anyString())).thenReturn(Mono.just(walletEntity));

        try (MockedStatic<Timer> timerStatic = mockStatic(Timer.class)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            doNothing().when(transactionProducer).sendTransaction(any());

            Mono<TransactionDTO> result = walletService.withdraw(WALLET_ID, withdrawRequest);

            StepVerifier.create(result)
                    .assertNext(txDto -> {
                        assertThat(txDto.getRequestTransactionId()).isEqualTo(TRANSACTION_ID);
                        assertThat(txDto.getWalletId()).isEqualTo(WALLET_ID);
                        assertThat(txDto.getWalletId()).isEqualTo(WALLET_ID);
                        assertThat(txDto.getType()).isEqualTo(TransactionType.WITHDRAW.getType());
                        assertThat(txDto.getStatus()).isEqualTo(TransactionStatusType.PROCESSING.getStatus());
                        assertThat(txDto.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-200));
                    })
                    .verifyComplete();

            verify(transactionProducer, times(1)).sendTransaction(any());
            verify(sampleMock, atLeastOnce()).stop(any(Timer.class));


        }
    }

    @Test
    @DisplayName("Withdraw Duplicate: Deve retornar erro 'Duplicate transaction'")
    void withdrawDuplicateTransaction() {
        when(cache.isDuplicateTransaction(anyString()))
                .thenReturn(Mono.error(new RuntimeException("Duplicate transaction")));
        when(walletRepository.findById(anyString())).thenReturn(Mono.just(walletEntity));

        Timer.Sample sampleMock = mock(Timer.Sample.class);
        when(sampleMock.stop(any(Timer.class))).thenReturn(0L);
        Timer timerMock = mock(Timer.class);
        when(meterRegistry.timer("wallet_withdraw_time")).thenReturn(timerMock);

        try (MockedStatic<Timer> timerStatic = mockStatic(Timer.class)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<TransactionDTO> result = walletService.withdraw(WALLET_ID, withdrawRequest);

            StepVerifier.create(result)
                    .expectErrorMatches(ex ->
                            ex instanceof WithdrawException &&
                                    ex.getMessage().equals("Insufficient funds"))
                    .verify();

            verify(transactionProducer, never()).sendTransaction(any());
            verify(sampleMock, atLeastOnce()).stop(any(Timer.class));
        }
    }

    @Test
    @DisplayName("Withdraw Error: Se transactionProducer.sendTransaction lançar exceção, propaga o erro")
    void withdrawTransactionProducerThrows() {
        when(cache.isDuplicateTransaction(anyString())).thenReturn(Mono.empty());
        doThrow(new RuntimeException("Send failed")).when(transactionProducer).sendTransaction(any());
        when(walletRepository.findById(anyString())).thenReturn(Mono.just(walletEntity));

        Timer.Sample sampleMock = mock(Timer.Sample.class);
        when(sampleMock.stop(any(Timer.class))).thenReturn(0L);
        Timer timerMock = mock(Timer.class);
        when(meterRegistry.timer("wallet_withdraw_time")).thenReturn(timerMock);

        try (MockedStatic<Timer> timerStatic = mockStatic(Timer.class)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<TransactionDTO> result = walletService.withdraw(WALLET_ID, withdrawRequest);

            StepVerifier.create(result)
                    .expectErrorMatches(ex ->
                            ex instanceof WithdrawException &&
                                    ex.getMessage().equals("Insufficient funds"))
                    .verify();

            verify(transactionProducer, never()).sendTransaction(any());
            verify(sampleMock, atLeastOnce()).stop(any(Timer.class));
        }
    }

    @Test
    @DisplayName("Withdraw Conversion Error: Duplicate transaction detected")
    void withdrawFailDuplicationTransaction() {
        when(cache.isDuplicateTransaction(anyString()))
                .thenReturn(Mono.error(new WalletException(DUPLICATE_TRANSACTION_DETECTED)));
        when(walletRepository.findById(anyString())).thenReturn(Mono.just(walletEntity));
        doNothing().when(transactionProducer).sendTransaction(any());

        Timer.Sample sampleMock = mock(Timer.Sample.class);
        when(sampleMock.stop(any(Timer.class))).thenReturn(0L);
        Timer timerMock = mock(Timer.class);
        when(meterRegistry.timer("wallet_withdraw_time")).thenReturn(timerMock);

        try (MockedStatic<Timer> timerStatic = mockStatic(Timer.class)){

            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<TransactionDTO> result = walletService.withdraw(WALLET_ID, withdrawRequest);

            StepVerifier.create(result)
                    .expectErrorMatches(ex ->
                            ex instanceof WithdrawException &&
                                    ex.getMessage().equals("Insufficient funds"))
                    .verify();

            verify(sampleMock, atLeastOnce()).stop(any(Timer.class));
        }
    }
}

