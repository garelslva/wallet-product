package com.recargapay.wallet.core.factory;

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
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Function;

import static com.recargapay.wallet.database.DbProperties.CONNECTION_DATABASE_TIMEOUT_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DepositTransactionTest {

    @Mock
    private TransactionFactory factory;
    @Mock
    private DatabaseClient databaseClient;
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

    private TransactionEvent event;
    private Wallet wallet;
    private Transaction dummyTransaction;
    private TransactionDTO dummyTransactionDTO;

    @BeforeEach
    public void setUp() {
        event = new TransactionEvent(
                UUID.randomUUID().toString(),
                "wallet-123",
                BigDecimal.TEN,
                TransactionType.DEPOSIT.getType()
        );

        wallet = new Wallet(
                UUID.randomUUID().toString(),
                "wallet-123",
                UUID.randomUUID().toString(),
                BigDecimal.TEN,
                LocalDateTime.now(),
                WalletStatusType.ACTIVE.getType(),
                2L
        );

        dummyTransaction = new Transaction();
        dummyTransactionDTO = new TransactionDTO(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "wallet-123",
                "wallet-123",
                TransactionType.DEPOSIT.getType(),
                TransactionStatusType.DONE.getStatus(),
                BigDecimal.TEN,
                LocalDateTime.now()
        );

        when(factory.databaseClient()).thenReturn(databaseClient);
        when(factory.walletRepository()).thenReturn(walletRepository);
        when(factory.transactionRepository()).thenReturn(transactionRepository);
        when(factory.balanceUpdateProducer()).thenReturn(balanceUpdateProducer);
        when(factory.cache()).thenReturn(cache);

        when(databaseClient.inConnection(any())).thenAnswer(invocation -> {
            Function<Connection, Mono<?>> function = invocation.getArgument(0);
            return function.apply(connection);
        });

        when(connection.beginTransaction()).thenReturn(Mono.empty());
        when(connection.commitTransaction()).thenReturn(Mono.empty());
        when(connection.rollbackTransaction()).thenReturn(Mono.empty());

        when(walletRepository.findById(anyString())).thenReturn(Mono.just(wallet));

        when(transactionRepository.save(any())).thenAnswer(invocation -> Mono.justOrEmpty(invocation.getArgument(0)));

        when(cache.clearBalanceCache(anyString())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Teste de Depósito Bem-Sucedido (Caminho Feliz)")
    public void testSuccessfulDeposit() {

        when(transactionRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        try (MockedStatic<TransactionConverter> converterMock = mockStatic(TransactionConverter.class)) {
            converterMock.when(() -> TransactionConverter.eventToTransactionEntity(anyString(), eq(event), eq(TransactionStatusType.DONE)))
                    .thenReturn(dummyTransaction);
            converterMock.when(() -> TransactionConverter.entityToTransactionDTO(any(), eq(TransactionStatusType.DONE)))
                    .thenReturn(dummyTransactionDTO);

            DepositTransaction depositTransaction = DepositTransaction.of(event);
            depositTransaction.execute(factory);
        }

        verify(balanceUpdateProducer, timeout(1000)).sendBalanceUpdate(any(BalanceUpdateEvent.class));
        verify(cache, timeout(1000)).clearBalanceCache(event.getWalletId());

        InOrder inOrder = inOrder(connection, walletRepository, transactionRepository, balanceUpdateProducer);
        inOrder.verify(connection).beginTransaction();
        inOrder.verify(walletRepository).findById(event.getWalletId());
        inOrder.verify(transactionRepository).save(any());
        inOrder.verify(balanceUpdateProducer).sendBalanceUpdate(any(BalanceUpdateEvent.class));
        inOrder.verify(connection).commitTransaction();

        assertEquals(BigDecimal.valueOf(20), wallet.getCurrentBalance());
    }

    /**
     * Caso de teste 2: Carteira não encontrada – o repositório retorna vazio.
     */
    @Test
    @DisplayName("Teste: Carteira Não Encontrada")
    public void testWalletNotFound() {
        when(walletRepository.findById(anyString())).thenReturn(Mono.empty());

        try (MockedStatic<TransactionConverter> converterMock = mockStatic(TransactionConverter.class)) {
            DepositTransaction depositTransaction = DepositTransaction.of(event);
            depositTransaction.execute(factory);
        }
        verify(connection).rollbackTransaction();
        verify(transactionRepository, never()).save(any());
    }

    /**
     * Caso de teste 3: Carteira não está ativa – o status da carteira não é ACTIVE.
     */
    @Test
    @DisplayName("Teste: Carteira Não Está Ativa")
    public void testWalletNotActive() {
        wallet.setStatus("INACTIVE");
        when(walletRepository.findById(anyString())).thenReturn(Mono.just(wallet));

        try (MockedStatic<TransactionConverter> converterMock = mockStatic(TransactionConverter.class)) {
            DepositTransaction depositTransaction = DepositTransaction.of(event);
            depositTransaction.execute(factory);
        }
        verify(connection).rollbackTransaction();
        verify(transactionRepository, never()).save(any());
    }

    /**
     * Caso de teste 4: Timeout ao buscar a carteira – a operação não completa no tempo esperado.
     */
    @Test
    @DisplayName("Teste: Tempo Esgotado ao Buscar Carteira")
    public void testTimeoutInFindWallet() {
        when(walletRepository.findById(anyString())).thenReturn(Mono.never());

        try (MockedStatic<TransactionConverter> converterMock = mockStatic(TransactionConverter.class)) {
            DepositTransaction depositTransaction = DepositTransaction.of(event);
            depositTransaction.execute(factory);
        }
        try {
            Thread.sleep((CONNECTION_DATABASE_TIMEOUT_SECONDS + 1) * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        verify(connection).rollbackTransaction();
    }

    /**
     * Caso de teste 5: Falha ao salvar a transação – o repositório retorna um erro.
     */
    @Test
    @DisplayName("Teste: Falha ao Salvar a Transação")
    public void testFailureOnSavingTransaction() throws InterruptedException {
        when(transactionRepository.save(any()))
                .thenReturn(Mono.error(new RuntimeException("Erro ao salvar a transação")));

        try (MockedStatic<TransactionConverter> converterMock = mockStatic(TransactionConverter.class)) {
            converterMock.when(() -> TransactionConverter.eventToTransactionEntity(anyString(), eq(event), eq(TransactionStatusType.DONE)))
                    .thenReturn(dummyTransaction);

            DepositTransaction depositTransaction = DepositTransaction.of(event);
            depositTransaction.execute(factory);
            Thread.sleep(500);
        }

        verify(connection).rollbackTransaction();
    }



    /**
     * Caso de teste 6: Falha ao enviar o evento de atualização de saldo – o produtor lança uma exceção.
     */
    @Test
    @DisplayName("Teste: Falha ao Enviar o Evento de Atualização de Saldo")
    public void testFailureOnBalanceUpdateEvent() {
        doThrow(new RuntimeException("Erro no evento"))
                .when(balanceUpdateProducer)
                .sendBalanceUpdate(any(BalanceUpdateEvent.class));

        try (MockedStatic<TransactionConverter> converterMock = mockStatic(TransactionConverter.class)) {
            converterMock.when(() -> TransactionConverter.eventToTransactionEntity(anyString(), eq(event), eq(TransactionStatusType.DONE)))
                    .thenReturn(dummyTransaction);
            DepositTransaction depositTransaction = DepositTransaction.of(event);
            depositTransaction.execute(factory);
        }
        verify(connection).rollbackTransaction();
    }

    /**
     * Caso de teste 7: Falha ao limpar o cache – a operação de limpeza retorna um erro.
     */
    @Test
    @DisplayName("Teste: Falha ao Limpar o Cache")
    public void testFailureOnClearCache() {
        when(cache.clearBalanceCache(anyString())).thenReturn(Mono.error(new RuntimeException("Erro ao limpar o cache")));

        try (MockedStatic<TransactionConverter> converterMock = mockStatic(TransactionConverter.class)) {
            converterMock.when(() -> TransactionConverter.eventToTransactionEntity(anyString(), eq(event), eq(TransactionStatusType.DONE)))
                    .thenReturn(dummyTransaction);
            DepositTransaction depositTransaction = DepositTransaction.of(event);
            depositTransaction.execute(factory);
        }
        verify(connection).rollbackTransaction();
    }

    /**
     * Caso de teste 8: Falha ao efetuar commit da transação – a operação de commit falha.
     */
    @Test
    @DisplayName("Teste: Falha ao Efetuar Commit da Transação")
    public void testFailureOnCommitTransaction() {
        when(connection.commitTransaction()).thenReturn(Mono.error(new RuntimeException("Erro no commit")));

        try (MockedStatic<TransactionConverter> converterMock = mockStatic(TransactionConverter.class)) {
            converterMock.when(() -> TransactionConverter.eventToTransactionEntity(anyString(), eq(event), eq(TransactionStatusType.DONE)))
                    .thenReturn(dummyTransaction);
            DepositTransaction depositTransaction = DepositTransaction.of(event);
            depositTransaction.execute(factory);
        }
        verify(connection).rollbackTransaction();
    }

    /**
     * Caso de teste 9: Falha ao efetuar rollback da transação – ocorre um erro durante o rollback.
     */
    @Test
    @DisplayName("Teste: Falha ao Efetuar Rollback da Transação")
    public void testFailureOnRollbackTransaction() {
        when(walletRepository.findById(anyString())).thenReturn(Mono.empty());
        when(connection.rollbackTransaction()).thenReturn(Mono.error(new RuntimeException("Erro no rollback")));

        try (MockedStatic<TransactionConverter> converterMock = mockStatic(TransactionConverter.class)) {
            DepositTransaction depositTransaction = DepositTransaction.of(event);
            depositTransaction.execute(factory);
        }
        verify(connection).rollbackTransaction();
    }

    /**
     * Caso de teste 10: Verificar a ordem das operações – assegurar que a cadeia reativa invoca os métodos na sequência correta.
     */
    @Test
    @DisplayName("Teste: Ordem das Operações")
    public void testOrderOfOperations() {

        when(transactionRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        try (MockedStatic<TransactionConverter> converterMock = mockStatic(TransactionConverter.class)) {
            converterMock.when(() -> TransactionConverter.eventToTransactionEntity(anyString(), eq(event), eq(TransactionStatusType.DONE)))
                    .thenReturn(dummyTransaction);
            converterMock.when(() -> TransactionConverter.entityToTransactionDTO(any(), eq(TransactionStatusType.DONE)))
                    .thenReturn(dummyTransactionDTO);

            DepositTransaction depositTransaction = DepositTransaction.of(event);
            depositTransaction.execute(factory);
        }

        verify(balanceUpdateProducer, timeout(1000)).sendBalanceUpdate(any(BalanceUpdateEvent.class));
        verify(cache, timeout(1000)).clearBalanceCache(event.getWalletId());

        InOrder inOrder = inOrder(connection, walletRepository, transactionRepository, balanceUpdateProducer, connection);
        inOrder.verify(connection).beginTransaction();
        inOrder.verify(walletRepository).findById(event.getWalletId());
        inOrder.verify(transactionRepository).save(any());
        inOrder.verify(balanceUpdateProducer).sendBalanceUpdate(any(BalanceUpdateEvent.class));
        inOrder.verify(connection).commitTransaction();

        assertEquals(BigDecimal.valueOf(20), wallet.getCurrentBalance());
    }

}
