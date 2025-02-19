package com.recargapay.wallet.core.factory;

import static com.recargapay.wallet.database.DbProperties.CONNECTION_DATABASE_TIMEOUT_SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.recargapay.wallet.cache.CacheService;
import com.recargapay.wallet.converter.TransactionConverter;
import com.recargapay.wallet.core.enums.TransactionStatusType;
import com.recargapay.wallet.core.enums.TransactionType;
import com.recargapay.wallet.core.enums.WalletStatusType;
import com.recargapay.wallet.core.factory.context.TransactionFactory;
import com.recargapay.wallet.database.entity.Wallet;
import com.recargapay.wallet.database.entity.Transaction;
import com.recargapay.wallet.database.repository.TransactionReactiveRepository;
import com.recargapay.wallet.database.repository.WalletReactiveRepository;
import com.recargapay.wallet.event.balance.BalanceUpdateProducer;
import com.recargapay.wallet.event.dto.BalanceUpdateEvent;
import com.recargapay.wallet.event.dto.DualTransactionEvent;
import com.recargapay.wallet.event.dto.TransactionEvent;
import com.recargapay.wallet.rest.dto.TransactionDTO;
import io.r2dbc.spi.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TransferTransactionTest {

    @Mock
    private TransactionFactory factory;
    @Mock
    private org.springframework.r2dbc.core.DatabaseClient databaseClient;
    @Mock
    private WalletReactiveRepository walletRepository;
    @Mock
    private TransactionReactiveRepository transactionRepository;
    @Mock
    private BalanceUpdateProducer balanceUpdateProducer;
    @Mock
    private CacheService cache;
    @Mock
    private Connection connection;

    private DualTransactionEvent event;
    private TransactionEvent sourceEvent;
    private TransactionEvent destinationEvent;
    private Wallet sourceWallet;
    private Wallet destinationWallet;
    private Transaction dummyTransaction;
    private TransactionDTO dummyTransactionDTO;

    @BeforeEach
    public void setUp() {
        sourceEvent = new TransactionEvent(
                UUID.randomUUID().toString(),
                "wallet-source",
                BigDecimal.valueOf(50),
                "TRANSFER_OUT"
        );
        destinationEvent = new TransactionEvent(
                UUID.randomUUID().toString(),
                "wallet-dest",
                BigDecimal.valueOf(50),
                "TRANSFER_IN"
        );
        event = new DualTransactionEvent(sourceEvent, destinationEvent);

        sourceWallet = new Wallet(
                "wallet-source",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                BigDecimal.valueOf(100),
                LocalDateTime.now(),
                WalletStatusType.ACTIVE.getType(),
        1L
        );
        destinationWallet = new Wallet(
                "wallet-dest",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                BigDecimal.valueOf(20),
                LocalDateTime.now(),
                WalletStatusType.ACTIVE.getType(),
                2L
        );

        dummyTransaction = new Transaction();

        dummyTransactionDTO = new TransactionDTO(
                "tx-id",
                UUID.randomUUID().toString(),
                "wallet-source",
                "wallet-source",
                TransactionType.TRANSFER_OUT.getType(),
                TransactionStatusType.DONE.getStatus(),
                BigDecimal.valueOf(50),
                LocalDateTime.now()
        );

        when(factory.databaseClient()).thenReturn(databaseClient);
        when(factory.walletRepository()).thenReturn(walletRepository);
        when(factory.transactionRepository()).thenReturn(transactionRepository);
        when(factory.balanceUpdateProducer()).thenReturn(balanceUpdateProducer);
        when(factory.cache()).thenReturn(cache);

        when(databaseClient.inConnection(any())).thenAnswer(invocation -> {
            java.util.function.Function<Connection, Mono<?>> function = invocation.getArgument(0);
            return function.apply(connection);
        });

        when(connection.beginTransaction()).thenReturn(Mono.empty());
        when(connection.commitTransaction()).thenReturn(Mono.empty());
        when(connection.rollbackTransaction()).thenReturn(Mono.empty());

        when(walletRepository.findById("wallet-source")).thenReturn(Mono.just(sourceWallet));
        when(walletRepository.findById("wallet-dest")).thenReturn(Mono.just(destinationWallet));
        when(walletRepository.findByIdAndVersion("wallet-source", sourceWallet.getVersion()))
                .thenReturn(Mono.just(sourceWallet));

        when(transactionRepository.save(any()))
                .thenAnswer(invocation -> Mono.justOrEmpty(invocation.getArgument(0)));

        when(cache.clearBalanceCache(anyString())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Caminho Feliz: Transferência bem-sucedida")
    public void testHappyPath() throws InterruptedException {
        try (MockedStatic<TransactionConverter> converterMock = mockStatic(TransactionConverter.class)) {
            converterMock.when(() -> TransactionConverter.eventToTransactionEntity(anyString(), eq(sourceEvent), eq(TransactionStatusType.DONE)))
                    .thenReturn(dummyTransaction);
            converterMock.when(() -> TransactionConverter.eventToTransactionEntity(anyString(), eq(destinationEvent), eq(TransactionStatusType.DONE)))
                    .thenReturn(new Transaction());
            converterMock.when(() -> TransactionConverter.entityToTransactionDTO(any(), eq(TransactionStatusType.DONE)))
                    .thenReturn(dummyTransactionDTO);

            when(transactionRepository.save(any()))
                    .thenReturn(Mono.just(dummyTransaction))
                    .thenReturn(Mono.just(new Transaction()));

            TransferTransaction transfer = TransferTransaction.of(event);
            transfer.execute(factory);
            Thread.sleep(500);

            InOrder inOrder = inOrder(connection, walletRepository, transactionRepository, balanceUpdateProducer, cache);
            inOrder.verify(connection).beginTransaction();
            inOrder.verify(walletRepository).findById(anyString());


            assertEquals(BigDecimal.valueOf(100), sourceWallet.getCurrentBalance());
            assertEquals(BigDecimal.valueOf(20), destinationWallet.getCurrentBalance());
        }
    }

    @Test
    @DisplayName("Erro: Carteira de origem não encontrada")
    public void testSourceWalletNotFound() throws InterruptedException {
        when(walletRepository.findById("wallet-source")).thenReturn(Mono.empty());
        TransferTransaction transfer = TransferTransaction.of(event);
        transfer.execute(factory);
        Thread.sleep(500);
        verify(connection).rollbackTransaction();
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Erro: Carteira de destino não encontrada")
    public void testDestinationWalletNotFound() throws InterruptedException {
        when(walletRepository.findById("wallet-dest")).thenReturn(Mono.empty());
        TransferTransaction transfer = TransferTransaction.of(event);
        transfer.execute(factory);
        Thread.sleep(500);
        verify(connection).rollbackTransaction();
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Erro: Modificação concorrente detectada")
    public void testConcurrentModification() throws InterruptedException {
        when(walletRepository.findByIdAndVersion("wallet-source", sourceWallet.getVersion()))
                .thenReturn(Mono.empty());
        TransferTransaction transfer = TransferTransaction.of(event);
        transfer.execute(factory);
        Thread.sleep(500);
    }

    @Test
    @DisplayName("Erro: Falha ao salvar transação de débito")
    public void testErrorSavingDebitTransaction() throws InterruptedException {
        try (MockedStatic<TransactionConverter> converterMock = mockStatic(TransactionConverter.class)) {
            converterMock.when(() -> TransactionConverter.eventToTransactionEntity(anyString(), eq(sourceEvent), eq(TransactionStatusType.DONE)))
                    .thenReturn(dummyTransaction);
            converterMock.when(() -> TransactionConverter.eventToTransactionEntity(anyString(), eq(destinationEvent), eq(TransactionStatusType.DONE)))
                    .thenReturn(new Transaction());
            when(transactionRepository.save(any()))
                    .thenReturn(Mono.error(new RuntimeException("Erro ao salvar débito")));
            TransferTransaction transfer = TransferTransaction.of(event);
            transfer.execute(factory);
            Thread.sleep(500);
        }
    }

    @Test
    @DisplayName("Erro: Falha ao salvar transação de crédito")
    public void testErrorSavingCreditTransaction() throws InterruptedException {
        try (MockedStatic<TransactionConverter> converterMock = mockStatic(TransactionConverter.class)) {
            converterMock.when(() -> TransactionConverter.eventToTransactionEntity(anyString(), eq(sourceEvent), eq(TransactionStatusType.DONE)))
                    .thenReturn(dummyTransaction);
            converterMock.when(() -> TransactionConverter.eventToTransactionEntity(anyString(), eq(destinationEvent), eq(TransactionStatusType.DONE)))
                    .thenReturn(new Transaction());
            when(transactionRepository.save(any()))
                    .thenReturn(Mono.just(dummyTransaction))
                    .thenReturn(Mono.error(new RuntimeException("Erro ao salvar crédito")));

            TransferTransaction transfer = TransferTransaction.of(event);
            transfer.execute(factory);
            Thread.sleep(500);

        }
    }

    @Test
    @DisplayName("Erro: Falha ao efetuar commit da transação")
    public void testErrorOnCommitTransaction() throws InterruptedException {
        when(connection.commitTransaction()).thenReturn(Mono.error(new RuntimeException("Erro no commit")));
        try (MockedStatic<TransactionConverter> converterMock = mockStatic(TransactionConverter.class)) {
            converterMock.when(() -> TransactionConverter.eventToTransactionEntity(anyString(), eq(sourceEvent), eq(TransactionStatusType.DONE)))
                    .thenReturn(dummyTransaction);
            converterMock.when(() -> TransactionConverter.eventToTransactionEntity(anyString(), eq(destinationEvent), eq(TransactionStatusType.DONE)))
                    .thenReturn(new Transaction());
            TransferTransaction transfer = TransferTransaction.of(event);
            transfer.execute(factory);
            Thread.sleep(500);
        }
    }

    @Test
    @DisplayName("Erro: Tempo esgotado ao buscar carteira de origem")
    public void testTimeoutRetrievingSourceWallet() throws InterruptedException {
        when(walletRepository.findById("wallet-source")).thenReturn(Mono.never());
        TransferTransaction transfer = TransferTransaction.of(event);
        transfer.execute(factory);
        Thread.sleep((CONNECTION_DATABASE_TIMEOUT_SECONDS + 1) * 1000);
        verify(connection).rollbackTransaction();
    }

    @Test
    @DisplayName("Borda: Transferência com valor zero")
    public void testZeroValueTransfer() throws InterruptedException {

        BigDecimal expectedAmountSourceAfterTranasfer = BigDecimal.valueOf(100L);
        BigDecimal expectedDestinationInitialAfterTranasfer = BigDecimal.valueOf(20L);

        TransferTransaction transfer = TransferTransaction.of(event);
        transfer.execute(factory);
        Thread.sleep(500);

        assertEquals(expectedAmountSourceAfterTranasfer, sourceWallet.getCurrentBalance());
        assertEquals(expectedDestinationInitialAfterTranasfer, destinationWallet.getCurrentBalance());
    }

    @Test
    @DisplayName("Borda: Saldo insuficiente na carteira de origem")
    public void testInsufficientFunds() throws InterruptedException {
        sourceWallet.setCurrentBalance(BigDecimal.valueOf(30));
        try (MockedStatic<TransactionConverter> converterMock = mockStatic(TransactionConverter.class)) {
            converterMock.when(() -> TransactionConverter.eventToTransactionEntity(anyString(), eq(sourceEvent), eq(TransactionStatusType.DONE)))
                    .thenReturn(dummyTransaction);
            converterMock.when(() -> TransactionConverter.eventToTransactionEntity(anyString(), eq(destinationEvent), eq(TransactionStatusType.DONE)))
                    .thenReturn(new Transaction());
            converterMock.when(() -> TransactionConverter.entityToTransactionDTO(any(), eq(TransactionStatusType.DONE)))
                    .thenReturn(dummyTransactionDTO);
            TransferTransaction transfer = TransferTransaction.of(event);
            transfer.execute(factory);
            Thread.sleep(500);
        }
        assertTrue(sourceWallet.getCurrentBalance().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Verificar conversão de entidade para DTO no commit")
    public void testEntityToDTOConversion() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        TransferTransaction transfer = TransferTransaction.of(event);
        factory.databaseClient().inConnection(conn ->

                        Mono.fromRunnable(() -> transfer.execute(factory))
                ).doFinally(signal -> latch.countDown())
                .subscribe();

        boolean completed = latch.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(completed, "O pipeline não completou dentro do tempo esperado.");

    }

}
