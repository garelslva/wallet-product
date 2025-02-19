package com.recargapay.wallet.core.service;

import com.recargapay.wallet.core.enums.TransactionStatusType;
import com.recargapay.wallet.core.enums.TransactionType;
import com.recargapay.wallet.core.service.impl.WalletServiceImpl;
import com.recargapay.wallet.database.entity.Wallet;
import com.recargapay.wallet.rest.dto.TransactionDTO;
import com.recargapay.wallet.rest.dto.TransferDTO;
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
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties = "retry.shouldRetry=false")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletServiceTransferTest {

    @Mock
    private TransferService transferService;

    @Mock
    private MeterRegistry meterRegistry;

    @InjectMocks
    private WalletServiceImpl walletService;

    private final String SOURCE_WALLET_ID = "source-wallet";
    private final String DEST_WALLET_ID = "destination-wallet";
    private final String TRANSACTION_ID = "tx-123";
    private final BigDecimal AMOUNT = BigDecimal.valueOf(150);

    private TransferDTO transferRequest;
    private TransactionDTO expectedTransactionDTO;

    @BeforeEach
    void setUp() {
        transferRequest = new TransferDTO(
                TRANSACTION_ID,
                "tx-123",
                AMOUNT
        );

        expectedTransactionDTO = new TransactionDTO(
                TRANSACTION_ID,
                "tx-123",
                DEST_WALLET_ID,
                SOURCE_WALLET_ID,
                TransactionType.TRANSFER_OUT.getType(),
                TransactionStatusType.PROCESSING.getStatus(),
                AMOUNT,
                LocalDateTime.now()
        );

        when(transferService.validateDuplicateTransaction(anyString())).thenReturn(Mono.empty());
    }

    private MockedStatic<Timer> mockTimerStatic(Timer.Sample sampleMock, Timer timerMock) {
        when(meterRegistry.timer("wallet_transfer_time")).thenReturn(timerMock);
        return mockStatic(Timer.class);
    }

    @Test
    @DisplayName("Transfer Success: Deve transferir com sucesso e retornar TransactionDTO")
    void transferSuccess() {
        Wallet sourceWallet = new Wallet();
        sourceWallet.setId(SOURCE_WALLET_ID);
        sourceWallet.setCurrentBalance(BigDecimal.valueOf(300));
        sourceWallet.setStatus("ACTIVE");
        sourceWallet.setVersion(1L);
        when(transferService.getAndValidateSourceWallet(eq(SOURCE_WALLET_ID), eq(AMOUNT)))
                .thenReturn(Mono.just(sourceWallet));

        Wallet destinationWallet = new Wallet();
        destinationWallet.setId(DEST_WALLET_ID);
        destinationWallet.setStatus("ACTIVE");
        when(transferService.processDestinationWallet(eq(DEST_WALLET_ID)))
                .thenReturn(Mono.just(destinationWallet));

        when(transferService.enqueueTransaction(eq(SOURCE_WALLET_ID), eq(DEST_WALLET_ID), eq(transferRequest)))
                .thenReturn(Mono.just(expectedTransactionDTO));

        Timer.Sample sampleMock = mock(Timer.Sample.class);
        when(sampleMock.stop(any(Timer.class))).thenReturn(0L);
        Timer timerMock = mock(Timer.class);
        try (MockedStatic<Timer> timerStatic = mockTimerStatic(sampleMock, timerMock)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<TransactionDTO> result = walletService.transfer(SOURCE_WALLET_ID, DEST_WALLET_ID, transferRequest);

            StepVerifier.create(result)
                    .assertNext(txDto -> {
                        assertThat(txDto.getRequestTransactionId()).isEqualTo(TRANSACTION_ID);
                        assertThat(txDto.getWalletId()).isEqualTo(SOURCE_WALLET_ID);
                        assertThat(txDto.getDestinationWalletId()).isEqualTo(DEST_WALLET_ID);
                        assertThat(txDto.getType()).isEqualTo(TransactionType.TRANSFER_OUT.getType());
                        assertThat(txDto.getStatus()).isEqualTo(TransactionStatusType.PROCESSING.getStatus());
                        assertThat(txDto.getAmount()).isEqualByComparingTo(AMOUNT);
                    })
                    .verifyComplete();

            verify(sampleMock, atLeastOnce()).stop(any(Timer.class));
        }
    }

    @Test
    @DisplayName("Transfer Duplicate: Deve emitir erro 'Duplicate transaction'")
    void transferDuplicateTransaction() {
        when(transferService.validateDuplicateTransaction(anyString()))
                .thenReturn(Mono.error(new RuntimeException("Duplicate transaction")));

        when(transferService.getAndValidateSourceWallet(anyString(), any()))
                .thenReturn(Mono.empty());

        Timer.Sample sampleMock = mock(Timer.Sample.class);
        when(sampleMock.stop(any(Timer.class))).thenReturn(0L);
        Timer timerMock = mock(Timer.class);
        try (MockedStatic<Timer> timerStatic = mockStatic(Timer.class)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);
            when(meterRegistry.timer("wallet_transfer_time")).thenReturn(timerMock);

            Mono<TransactionDTO> result = walletService.transfer("source-wallet", "destination-wallet", transferRequest);

            StepVerifier.create(result)
                    .expectErrorMatches(ex ->
                            ex instanceof RuntimeException &&
                                    ex.getMessage().equals("Duplicate transaction"))
                    .verify();

            verify(transferService, never()).getAndValidateSourceWallet(anyString(), any());
            verify(sampleMock, atLeastOnce()).stop(any(Timer.class));
        }
    }

    @Test
    @DisplayName("Transfer Error: Deve emitir erro se a carteira de origem não for encontrada")
    void transferSourceWalletNotFound() {
        when(transferService.getAndValidateSourceWallet(eq(SOURCE_WALLET_ID), eq(AMOUNT)))
                .thenReturn(Mono.error(new RuntimeException("Wallet not found")));

        Timer.Sample sampleMock = mock(Timer.Sample.class);
        when(sampleMock.stop(any(Timer.class))).thenReturn(0L);
        Timer timerMock = mock(Timer.class);
        try (MockedStatic<Timer> timerStatic = mockTimerStatic(sampleMock, timerMock)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<TransactionDTO> result = walletService.transfer(SOURCE_WALLET_ID, DEST_WALLET_ID, transferRequest);

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof RuntimeException &&
                            ex.getMessage().equals("Wallet not found"))
                    .verify();

            verify(sampleMock, atLeastOnce()).stop(any(Timer.class));
        }
    }

    @Test
    @DisplayName("Transfer Error: Deve emitir erro se a carteira de origem estiver inativa")
    void transferSourceWalletInactive() {
        when(transferService.getAndValidateSourceWallet(eq(SOURCE_WALLET_ID), eq(AMOUNT)))
                .thenReturn(Mono.error(new RuntimeException("Wallet inactive")));

        Timer.Sample sampleMock = mock(Timer.Sample.class);
        when(sampleMock.stop(any(Timer.class))).thenReturn(0L);
        Timer timerMock = mock(Timer.class);
        try (MockedStatic<Timer> timerStatic = mockTimerStatic(sampleMock, timerMock)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<TransactionDTO> result = walletService.transfer(SOURCE_WALLET_ID, DEST_WALLET_ID, transferRequest);

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof RuntimeException &&
                            ex.getMessage().equals("Wallet inactive"))
                    .verify();

            verify(sampleMock, atLeastOnce()).stop(any(Timer.class));
        }
    }

    @Test
    @DisplayName("Transfer Error: Deve emitir erro se a carteira de origem tiver saldo insuficiente")
    void transferInsufficientFunds() {
        when(transferService.getAndValidateSourceWallet(eq(SOURCE_WALLET_ID), eq(AMOUNT)))
                .thenReturn(Mono.error(new RuntimeException("Insufficient funds")));

        Timer.Sample sampleMock = mock(Timer.Sample.class);
        when(sampleMock.stop(any(Timer.class))).thenReturn(0L);
        Timer timerMock = mock(Timer.class);
        try (MockedStatic<Timer> timerStatic = mockTimerStatic(sampleMock, timerMock)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<TransactionDTO> result = walletService.transfer(SOURCE_WALLET_ID, DEST_WALLET_ID, transferRequest);

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof RuntimeException &&
                            ex.getMessage().equals("Insufficient funds"))
                    .verify();

            verify(sampleMock, atLeastOnce()).stop(any(Timer.class));
        }
    }

    @Test
    @DisplayName("Transfer Error: Deve emitir erro de concorrência se a versão da carteira de origem for inválida")
    void transferConcurrentModification() {
        when(transferService.getAndValidateSourceWallet(eq(SOURCE_WALLET_ID), eq(AMOUNT)))
                .thenReturn(Mono.error(new RuntimeException("Concurrent modification detected")));

        Timer.Sample sampleMock = mock(Timer.Sample.class);
        when(sampleMock.stop(any(Timer.class))).thenReturn(0L);
        Timer timerMock = mock(Timer.class);
        try (MockedStatic<Timer> timerStatic = mockTimerStatic(sampleMock, timerMock)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<TransactionDTO> result = walletService.transfer(SOURCE_WALLET_ID, DEST_WALLET_ID, transferRequest);

            StepVerifier.create(result)
                    .expectErrorMatches(ex ->
                            ex instanceof RuntimeException &&
                                    ex.getMessage().equals("Concurrent modification detected"))
                    .verify();

            verify(sampleMock, atLeastOnce()).stop(any(Timer.class));
        }
    }

    @Test
    @DisplayName("Transfer Error: Deve emitir erro se a carteira de destino não for encontrada")
    void transferDestinationWalletNotFound() {
        Wallet sourceWallet = new Wallet();
        sourceWallet.setId(SOURCE_WALLET_ID);
        sourceWallet.setCurrentBalance(BigDecimal.valueOf(300));
        sourceWallet.setStatus("ACTIVE");
        sourceWallet.setVersion(1L);
        when(transferService.getAndValidateSourceWallet(eq(SOURCE_WALLET_ID), eq(AMOUNT)))
                .thenReturn(Mono.just(sourceWallet));

        when(transferService.processDestinationWallet(eq(DEST_WALLET_ID)))
                .thenReturn(Mono.error(new RuntimeException("Destination wallet not found")));

        Timer.Sample sampleMock = mock(Timer.Sample.class);
        when(sampleMock.stop(any(Timer.class))).thenReturn(0L);
        Timer timerMock = mock(Timer.class);
        try (MockedStatic<Timer> timerStatic = mockTimerStatic(sampleMock, timerMock)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<TransactionDTO> result = walletService.transfer(SOURCE_WALLET_ID, DEST_WALLET_ID, transferRequest);

            StepVerifier.create(result)
                    .expectErrorMatches(ex ->
                            ex instanceof RuntimeException &&
                                    ex.getMessage().equals("Destination wallet not found"))
                    .verify();

            verify(sampleMock, atLeastOnce()).stop(any(Timer.class));
        }
    }

    @Test
    @DisplayName("Transfer Error: Deve emitir erro se a carteira de destino estiver inativa")
    void transferDestinationWalletInactive() {
        Wallet sourceWallet = new Wallet();
        sourceWallet.setId(SOURCE_WALLET_ID);
        sourceWallet.setCurrentBalance(BigDecimal.valueOf(300));
        sourceWallet.setStatus("ACTIVE");
        sourceWallet.setVersion(1L);
        when(transferService.getAndValidateSourceWallet(eq(SOURCE_WALLET_ID), eq(AMOUNT)))
                .thenReturn(Mono.just(sourceWallet));

        when(transferService.processDestinationWallet(eq(DEST_WALLET_ID)))
                .thenReturn(Mono.error(new RuntimeException("Destination wallet inactive")));

        Timer.Sample sampleMock = mock(Timer.Sample.class);
        when(sampleMock.stop(any(Timer.class))).thenReturn(0L);
        Timer timerMock = mock(Timer.class);
        try (MockedStatic<Timer> timerStatic = mockTimerStatic(sampleMock, timerMock)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<TransactionDTO> result = walletService.transfer(SOURCE_WALLET_ID, DEST_WALLET_ID, transferRequest);

            StepVerifier.create(result)
                    .expectErrorMatches(ex ->
                            ex instanceof RuntimeException &&
                                    ex.getMessage().equals("Destination wallet inactive"))
                    .verify();

            verify(sampleMock, atLeastOnce()).stop(any(Timer.class));
        }
    }

    @Test
    @DisplayName("Transfer Error: Deve propagar erro se a conversão para TransactionDTO falhar")
    void transferEnqueueConversionFails() {
        Wallet sourceWallet = new Wallet();
        sourceWallet.setId(SOURCE_WALLET_ID);
        sourceWallet.setCurrentBalance(BigDecimal.valueOf(300));
        sourceWallet.setStatus("ACTIVE");
        sourceWallet.setVersion(1L);
        Wallet destinationWallet = new Wallet();
        destinationWallet.setId(DEST_WALLET_ID);
        destinationWallet.setStatus("ACTIVE");

        when(transferService.getAndValidateSourceWallet(eq(SOURCE_WALLET_ID), eq(AMOUNT)))
                .thenReturn(Mono.just(sourceWallet));
        when(transferService.processDestinationWallet(eq(DEST_WALLET_ID)))
                .thenReturn(Mono.just(destinationWallet));
        when(transferService.enqueueTransaction(eq(SOURCE_WALLET_ID), eq(DEST_WALLET_ID), eq(transferRequest)))
                .thenReturn(Mono.error(new RuntimeException("Conversion failed")));

        Timer.Sample sampleMock = mock(Timer.Sample.class);
        when(sampleMock.stop(any(Timer.class))).thenReturn(0L);
        Timer timerMock = mock(Timer.class);
        try (MockedStatic<Timer> timerStatic = mockTimerStatic(sampleMock, timerMock)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<TransactionDTO> result = walletService.transfer(SOURCE_WALLET_ID, DEST_WALLET_ID, transferRequest);

            StepVerifier.create(result)
                    .expectErrorMatches(ex ->
                            ex instanceof RuntimeException &&
                                    ex.getMessage().equals("Conversion failed"))
                    .verify();

            verify(sampleMock, atLeastOnce()).stop(any(Timer.class));
        }
    }

    @Test
    @DisplayName("Transfer Error: Deve propagar erro se o envio da transação falhar")
    void transferEnqueueSendFails() {
        Wallet sourceWallet = new Wallet();
        sourceWallet.setId(SOURCE_WALLET_ID);
        sourceWallet.setCurrentBalance(BigDecimal.valueOf(300));
        sourceWallet.setStatus("ACTIVE");
        sourceWallet.setVersion(1L);
        Wallet destinationWallet = new Wallet();
        destinationWallet.setId(DEST_WALLET_ID);
        destinationWallet.setStatus("ACTIVE");

        when(transferService.getAndValidateSourceWallet(eq(SOURCE_WALLET_ID), eq(AMOUNT)))
                .thenReturn(Mono.just(sourceWallet));
        when(transferService.processDestinationWallet(eq(DEST_WALLET_ID)))
                .thenReturn(Mono.just(destinationWallet));
        when(transferService.enqueueTransaction(eq(SOURCE_WALLET_ID), eq(DEST_WALLET_ID), eq(transferRequest)))
                .thenReturn(Mono.error(new RuntimeException("Send failed")));

        Timer.Sample sampleMock = mock(Timer.Sample.class);
        when(sampleMock.stop(any(Timer.class))).thenReturn(0L);
        Timer timerMock = mock(Timer.class);
        try (MockedStatic<Timer> timerStatic = mockTimerStatic(sampleMock, timerMock)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<TransactionDTO> result = walletService.transfer(SOURCE_WALLET_ID, DEST_WALLET_ID, transferRequest);

            StepVerifier.create(result)
                    .expectErrorMatches(ex ->
                            ex instanceof RuntimeException &&
                                    ex.getMessage().equals("Send failed"))
                    .verify();

            verify(sampleMock, atLeastOnce()).stop(any(Timer.class));
        }
    }
}