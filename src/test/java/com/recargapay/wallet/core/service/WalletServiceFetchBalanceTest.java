package com.recargapay.wallet.core.service;

import com.recargapay.wallet.cache.CacheService;
import com.recargapay.wallet.core.enums.TransactionStatusType;
import com.recargapay.wallet.core.service.impl.WalletServiceImpl;
import com.recargapay.wallet.database.entity.Wallet;
import com.recargapay.wallet.database.repository.TransactionReactiveRepository;
import com.recargapay.wallet.database.repository.UserReactiveRepository;
import com.recargapay.wallet.database.repository.WalletReactiveRepository;
import com.recargapay.wallet.event.transaction.TransactionProducer;
import com.recargapay.wallet.handle.exception.WalletException;
import com.recargapay.wallet.rest.dto.BalanceDTO;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.recargapay.wallet.cache.KeyProperties.BALANCE_KEY;
import static com.recargapay.wallet.handle.Message.WALLET_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletServiceFetchBalanceTest {

    @Mock
    WalletReactiveRepository walletRepository;

    @Mock
    TransactionReactiveRepository transactionRepository;

    @Mock
    UserReactiveRepository userRepository;

    @Mock
    DatabaseClient databaseClient;

    @Mock
    TransactionProducer transactionProducer;

    @Mock
    TransferService transferService;

    @Mock
    CacheService cache;

    @Mock
    ReactiveStringRedisTemplate redisTemplate;

    @Mock
    ReactiveValueOperations<String, String> valueOperations;

    @Mock
    MeterRegistry meterRegistry;

    @InjectMocks
    WalletServiceImpl walletService;

    private Wallet walletEntity;

    @BeforeEach
    void setUp() {
        walletEntity = new Wallet(
                "wallet_id",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                BigDecimal.valueOf(50),
                LocalDateTime.now(),
                TransactionStatusType.DONE.getStatus(),
                1L
        );

        MeterRegistry.Config configMock = mock(MeterRegistry.Config.class);
        when(meterRegistry.config()).thenReturn(configMock);
        when(configMock.clock()).thenReturn(Clock.SYSTEM);
        Timer timerMock = mock(Timer.class);
        when(meterRegistry.timer(anyString())).thenReturn(timerMock);

        when(cache.opsForValue()).thenReturn(valueOperations);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Sucesso: Wallet encontrada, soma de transações -> Cache set e retorna BalanceDTO")
    void shouldReturnBalanceWhenWalletFoundAndSumTransactionsOk() {
        String walletId = "wallet-123";
        LocalDateTime dateTime = LocalDateTime.now();
        String cacheKey = BALANCE_KEY.getKey(walletId);

        when(walletRepository.findById(walletId)).thenReturn(Mono.just(walletEntity));
        when(transactionRepository.findSumOfAmountByDestinationWalletId(walletId))
                .thenReturn(Mono.just(BigDecimal.valueOf(150)));

        when(cache.set(eq(cacheKey), anyString())).thenReturn(Mono.empty());

        Mono<BalanceDTO> result = walletService.fetchBalanceFromDatabase(
                UUID.randomUUID().toString(), walletId, cacheKey, dateTime
        );

        StepVerifier.create(result)
                .assertNext(balanceDto -> {
                    assertThat(balanceDto.getWalletId()).isEqualTo("wallet-123");
                    assertThat(balanceDto.getBalance()).isEqualByComparingTo("150");
                    assertThat(balanceDto.getDate()).isEqualTo(dateTime);
                })
                .verifyComplete();

        verify(walletRepository).findById(walletId);
        verify(transactionRepository).findSumOfAmountByDestinationWalletId(walletId);
        verify(cache).set(eq(cacheKey), eq("150"));
    }

    @Test
    @DisplayName("Erro: Wallet não encontrada -> 'Wallet not found'")
    void shouldFailWhenWalletNotFound() {
        String walletId = "wallet-999";
        LocalDateTime dateTime = LocalDateTime.now();
        String cacheKey = BALANCE_KEY.getKey(walletId);

        when(walletRepository.findById(walletId)).thenReturn(Mono.empty());

        Mono<BalanceDTO> result = walletService.fetchBalanceFromDatabase(
                UUID.randomUUID().toString(), walletId, cacheKey, dateTime
        );

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(WalletException.class)
                            .hasMessage(WALLET_NOT_FOUND.getMessage());
                })
                .verify();

        verify(walletRepository).findById(walletId);
        verify(transactionRepository, never()).findSumOfAmountByDestinationWalletId(anyString());
        verify(cache, never()).set(anyString(), anyString());
    }

    @Test
    @DisplayName("Erro no walletRepository -> falha ao encontrar a wallet")
    void shouldFailWhenWalletRepositoryThrowsError() {
        String walletId = "wallet-abc";
        LocalDateTime dateTime = LocalDateTime.now();
        String cacheKey = BALANCE_KEY.getKey(walletId);

        when(walletRepository.findById(walletId))
                .thenReturn(Mono.error(new RuntimeException("DB wallet error")));

        Mono<BalanceDTO> result = walletService.fetchBalanceFromDatabase(
                UUID.randomUUID().toString(), walletId, cacheKey, dateTime
        );

        StepVerifier.create(result)
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(RuntimeException.class)
                                .hasMessage("DB wallet error")
                )
                .verify();

        verify(walletRepository).findById(walletId);
        verify(transactionRepository, never()).findSumOfAmountByDestinationWalletId(anyString());
        verify(cache, never()).set(anyString(), anyString());
    }

    @Test
    @DisplayName("Erro: Falha ao somar transações -> transactionRepository.sumAmountByWalletId falha")
    void shouldFailWhenTransactionSumFails() {
        String walletId = "wallet-abc";
        LocalDateTime dateTime = LocalDateTime.now();
        String cacheKey = BALANCE_KEY.getKey(walletId);

        when(walletRepository.findById(walletId)).thenReturn(Mono.just(walletEntity));
        when(transactionRepository.findSumOfAmountByDestinationWalletId(walletId))
                .thenReturn(Mono.error(new RuntimeException("Transaction sum failed")));

        Mono<BalanceDTO> result = walletService.fetchBalanceFromDatabase(
                UUID.randomUUID().toString(), walletId, cacheKey, dateTime
        );

        StepVerifier.create(result)
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(RuntimeException.class)
                                .hasMessage("Transaction sum failed")
                )
                .verify();

        verify(walletRepository).findById(walletId);
        verify(transactionRepository).findSumOfAmountByDestinationWalletId(walletId);
        verify(cache, never()).set(anyString(), anyString());
    }

    @Test
    @DisplayName("Sucesso: Soma de transações ausente -> BigDecimal.ZERO")
    void shouldHandleEmptyTransactionSumAsZero() {
        String walletId = "wallet-xyz";
        LocalDateTime dateTime = LocalDateTime.now();
        String cacheKey = BALANCE_KEY.getKey(walletId);

        when(walletRepository.findById(walletId)).thenReturn(Mono.just(walletEntity));
        when(transactionRepository.findSumOfAmountByDestinationWalletId(walletId)).thenReturn(Mono.empty());

        when(cache.set(eq(cacheKey), anyString())).thenReturn(Mono.empty());

        Mono<BalanceDTO> result = walletService.fetchBalanceFromDatabase(
                UUID.randomUUID().toString(), walletId, cacheKey, dateTime
        );

        StepVerifier.create(result)
                .assertNext(balanceDto -> {
                    assertThat(balanceDto.getBalance()).isEqualByComparingTo("0");
                })
                .verifyComplete();

        verify(walletRepository).findById(walletId);
        verify(transactionRepository).findSumOfAmountByDestinationWalletId(walletId);
        verify(cache).set(eq(cacheKey), eq("0"));
    }

    @Test
    @DisplayName("Sucesso com falha ao salvar no cache (erro no set) - não interrompe fluxo")
    void shouldReturnBalanceEvenIfSetCacheFails() {
        String walletId = "wallet-xyz";
        LocalDateTime dateTime = LocalDateTime.now();
        String cacheKey = BALANCE_KEY.getKey(walletId);

        when(walletRepository.findById(walletId)).thenReturn(Mono.just(walletEntity));
        when(transactionRepository.findSumOfAmountByDestinationWalletId(walletId))
                .thenReturn(Mono.just(BigDecimal.valueOf(20)));
        when(cache.set(eq(cacheKey), anyString()))
                .thenReturn(Mono.error(new RuntimeException("Cache set error")));

        Mono<BalanceDTO> result = walletService.fetchBalanceFromDatabase(
                UUID.randomUUID().toString(), walletId, cacheKey, dateTime
        );

        StepVerifier.create(result)
                .assertNext(balanceDto -> {
                    assertThat(balanceDto.getBalance()).isEqualByComparingTo("20");
                })
                .verifyComplete();

        verify(walletRepository).findById(walletId);
        verify(transactionRepository).findSumOfAmountByDestinationWalletId(walletId);
        verify(cache).set(anyString(), anyString());
    }

}
