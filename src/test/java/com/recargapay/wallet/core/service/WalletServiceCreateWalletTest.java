package com.recargapay.wallet.core.service;

import com.recargapay.wallet.cache.CacheService;
import com.recargapay.wallet.core.enums.TransactionStatusType;
import com.recargapay.wallet.core.service.impl.WalletServiceImpl;
import com.recargapay.wallet.database.entity.User;
import com.recargapay.wallet.database.entity.Wallet;
import com.recargapay.wallet.database.repository.TransactionReactiveRepository;
import com.recargapay.wallet.database.repository.UserReactiveRepository;
import com.recargapay.wallet.database.repository.WalletReactiveRepository;
import com.recargapay.wallet.event.transaction.TransactionProducer;
import com.recargapay.wallet.rest.dto.CreateWalletDTO;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.r2dbc.spi.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.reactivestreams.Publisher;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletServiceCreateWalletTest {

    @Mock
    ReactiveStringRedisTemplate redisTemplate;

    @Mock
    CacheService cache;

    @Mock
    UserReactiveRepository userRepository;

    @Mock
    WalletReactiveRepository walletRepository;

    @Mock
    DatabaseClient databaseClient;

    @Mock
    MeterRegistry meterRegistry;

    @Mock
    TransactionReactiveRepository transactionRepository;

    @Mock
    TransactionProducer transactionProducer;

    @Mock
    TransferService transferService;

    @Mock
    Timer.Sample sample;

    @InjectMocks
    WalletServiceImpl walletService;

    private CreateWalletDTO createWalletDTO;
    private User userEntity;
    private Wallet walletEntity;

    @BeforeEach
    void setUp() {
        createWalletDTO = new CreateWalletDTO(
                "user_id",
                UUID.randomUUID().toString()
        );

        userEntity = new User(
                "user_id",
                UUID.randomUUID().toString(),
                "username1",
                "Name Teste",
                "teste1@teste.com.br",
                "12345678900"
        );

        walletEntity = new Wallet(
                UUID.randomUUID().toString(),
                "user_id",
                userEntity.getId(),
                BigDecimal.ZERO,
                LocalDateTime.now(),
                TransactionStatusType.DONE.getStatus(),
                12L
        );

        configureMeterRegistry();
    }

    private void configureMeterRegistry() {
        meterRegistry = mock(MeterRegistry.class);
        MeterRegistry.Config configMock = mock(MeterRegistry.Config.class);
        Timer timerMock = mock(Timer.class);

        when(meterRegistry.config()).thenReturn(configMock);
        when(configMock.clock()).thenReturn(Clock.SYSTEM);
        when(meterRegistry.timer(anyString())).thenReturn(timerMock);

        walletService = new WalletServiceImpl(
                walletRepository,
                transactionRepository,
                userRepository,
                databaseClient,
                transactionProducer,
                transferService,
                cache,
                meterRegistry
        );
    }

    private void stubDatabaseTransaction() {
        when(databaseClient.inConnection(any()))
                .thenAnswer(invocation -> {
                    Function<Connection, ? extends Publisher<?>> function =
                            invocation.getArgument(0);
                    Connection mockConn = mock(Connection.class);
                    when(mockConn.beginTransaction()).thenReturn(Mono.empty());
                    when(mockConn.commitTransaction()).thenReturn(Mono.empty());
                    when(mockConn.rollbackTransaction()).thenReturn(Mono.empty());
                    return Mono.defer(() -> Mono.from(function.apply(mockConn)));
                });
    }

    @Test
    void shouldFailWhenDuplicateTransaction() {
        when(cache.isDuplicateTransaction(anyString()))
                .thenReturn(Mono.error(new RuntimeException("Duplicate transaction")));

        StepVerifier.create(walletService.createWallet(createWalletDTO))
                .expectErrorSatisfies(error -> {
                    assertThat(error)
                            .isInstanceOf(RuntimeException.class)
                            .hasMessage("Duplicate transaction");
                })
                .verify();
    }

    @Test
    void shouldFailWhenUserSaveFails() {
        stubDatabaseTransaction();

        when(cache.isDuplicateTransaction(anyString())).thenReturn(Mono.empty());
        when(userRepository.findByCpf(anyString())).thenReturn(Mono.just(userEntity));
        when(walletRepository.findByUserId(anyString())).thenReturn(Flux.empty());
        when(walletRepository.save(any()))
                .thenReturn(Mono.error(new RuntimeException("last")));

        StepVerifier.create(walletService.createWallet(createWalletDTO))
                .expectErrorSatisfies(error -> {
                    assertThat(error)
                            .isInstanceOf(RuntimeException.class)
                            .hasMessage("last");
                })
                .verify();
    }

}
