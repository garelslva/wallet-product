package com.recargapay.wallet.core.service;

import com.recargapay.wallet.cache.CacheService;
import com.recargapay.wallet.core.enums.TransactionStatusType;
import com.recargapay.wallet.core.service.impl.WalletServiceImpl;
import com.recargapay.wallet.database.entity.Wallet;
import com.recargapay.wallet.database.repository.TransactionReactiveRepository;
import com.recargapay.wallet.database.repository.UserReactiveRepository;
import com.recargapay.wallet.database.repository.WalletReactiveRepository;
import com.recargapay.wallet.event.transaction.TransactionProducer;
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
import org.mockito.MockedStatic;
import org.mockito.Mockito;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletServiceGetBalanceTest {

    @Mock
    ReactiveStringRedisTemplate redisTemplate;

    @Mock
    ReactiveValueOperations<String, String> valueOperations;

    @Mock
    MeterRegistry meterRegistry;

    @Mock
    Timer.Sample sample;

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

    @InjectMocks
    WalletServiceImpl walletService;

    private static final String WALLET_ID = "wallet-123";
    private static final String CACHE_KEY = BALANCE_KEY.getKey(WALLET_ID);

    private Wallet walletEntity;

    @BeforeEach
    void setup() {
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

        when(sample.stop(any())).thenReturn(0L);

        when(cache.opsForValue()).thenReturn(valueOperations);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Deve retornar saldo diretamente do cache (cache hit)")
    void cacheHitReturnsBalanceFromCache() {

        Timer.Sample sampleMock = mock(Timer.Sample.class);
        when(sampleMock.stop(any())).thenReturn(1L);

        ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);

        when(cache.opsForValue()).thenReturn(valueOperations);

        when(cache.get(Mockito.anyString())).thenReturn(Mono.just("100.00"));

        when(walletRepository.findById(Mockito.anyString())).thenReturn(Mono.empty());

        try (MockedStatic<Timer> timerStatic = mockStatic(Timer.class)) {
            timerStatic.when(() -> Timer.start(meterRegistry)).thenReturn(sampleMock);

            Mono<BalanceDTO> result = walletService.getBalance(UUID.randomUUID().toString(), WALLET_ID);

            StepVerifier.create(result)
                    .assertNext(balanceDto -> {
                        assertThat(balanceDto.getWalletId()).isEqualTo(WALLET_ID);
                        assertThat(balanceDto.getBalance()).isEqualByComparingTo("100.00");
                    })
                    .verifyComplete();

            verify(walletRepository).findById(Mockito.anyString());
            verify(sampleMock, atLeastOnce()).stop(any());
        }
    }

    @Test
    @DisplayName("Deve buscar saldo no DB e retornar com sucesso quando cache está vazio")
    void cacheMissThenSuccessFromDatabase() {
        when(cache.get(CACHE_KEY)).thenReturn(Mono.empty());

        when(walletRepository.findById(WALLET_ID)).thenReturn(Mono.just(walletEntity));

        when(transactionRepository.findSumOfAmountByDestinationWalletId(WALLET_ID))
                .thenReturn(Mono.just(BigDecimal.valueOf(200)));

        when(cache.set(anyString(), anyString())).thenReturn(Mono.empty());

        Mono<BalanceDTO> result = walletService.getBalance(UUID.randomUUID().toString(), WALLET_ID);

        StepVerifier.create(result)
                .assertNext(balanceDto -> {
                    assertThat(balanceDto.getWalletId()).isEqualTo("wallet-123");
                    assertThat(balanceDto.getBalance()).isEqualByComparingTo("200");
                })
                .verifyComplete();

        verify(walletRepository).findById(anyString());
        verify(cache).set(eq("balance:wallet-123"), eq("200"));
    }

    @Test
    @DisplayName("Deve falhar com 'Wallet not found' quando carteira não existe no DB")
    void cacheMissThenWalletNotFound() {
        when(cache.get(CACHE_KEY)).thenReturn(Mono.empty());
        when(walletRepository.findById(WALLET_ID)).thenReturn(Mono.empty());

        Mono<BalanceDTO> result = walletService.getBalance(UUID.randomUUID().toString(), WALLET_ID);

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(RuntimeException.class);
                    assertThat(error).hasMessage("Wallet not found");
                })
                .verify();

        verify(walletRepository).findById(WALLET_ID);
        verify(transactionRepository, never()).findSumOfAmountByDestinationWalletId(anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Deve falhar com erro no repositório de wallet (ex. DB offline)")
    void cacheMissThenWalletRepoError() {
        when(cache.get(CACHE_KEY)).thenReturn(Mono.empty());
        when(walletRepository.findById(WALLET_ID))
                .thenReturn(Mono.error(new RuntimeException("DB walletRepo error")));

        Mono<BalanceDTO> result = walletService.getBalance(UUID.randomUUID().toString(), WALLET_ID);

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(RuntimeException.class)
                            .hasMessage("DB walletRepo error");
                })
                .verify();

        verify(walletRepository).findById(WALLET_ID);
        verify(transactionRepository, never()).findSumOfAmountByDestinationWalletId(anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Deve falhar se transactionRepository.sumAmountByWalletId falhar")
    void cacheMissThenTransactionSumError() {
        when(cache.get(CACHE_KEY)).thenReturn(Mono.empty());
        when(walletRepository.findById(WALLET_ID)).thenReturn(Mono.just(walletEntity));
        when(transactionRepository.findSumOfAmountByDestinationWalletId(WALLET_ID))
                .thenReturn(Mono.error(new RuntimeException("Transaction sum failed")));

        Mono<BalanceDTO> result = walletService.getBalance(UUID.randomUUID().toString(), WALLET_ID);

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(RuntimeException.class)
                            .hasMessage("Transaction sum failed");
                })
                .verify();

        verify(walletRepository).findById(WALLET_ID);
        verify(transactionRepository).findSumOfAmountByDestinationWalletId(WALLET_ID);
        verify(valueOperations, never()).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Deve retornar saldo mesmo se falhar ao salvar no cache (erro assíncrono)")
    void cacheMissThenSavesCacheButSetOperationFails() {
        when(cache.get(CACHE_KEY)).thenReturn(Mono.empty());
        when(walletRepository.findById(WALLET_ID)).thenReturn(Mono.just(walletEntity));
        when(transactionRepository.findSumOfAmountByDestinationWalletId(WALLET_ID))
                .thenReturn(Mono.just(BigDecimal.ZERO));
        when(cache.set(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("Redis write error")));

        Mono<BalanceDTO> result = walletService.getBalance(UUID.randomUUID().toString(), WALLET_ID);

        StepVerifier.create(result)
                .assertNext(balanceDto -> {
                    assertThat(balanceDto.getWalletId()).isEqualTo("wallet-123");
                    assertThat(balanceDto.getBalance()).isEqualByComparingTo("0");
                })
                .verifyComplete();

        verify(walletRepository).findById(WALLET_ID);
        verify(transactionRepository).findSumOfAmountByDestinationWalletId(WALLET_ID);
        verify(cache).set(eq(CACHE_KEY), eq("0"));
    }

}
