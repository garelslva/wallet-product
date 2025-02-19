package com.recargapay.wallet.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.recargapay.wallet.cache.CacheService;
import com.recargapay.wallet.converter.TransactionConverter;
import com.recargapay.wallet.core.enums.TransactionStatusType;
import com.recargapay.wallet.core.enums.TransactionType;
import com.recargapay.wallet.core.enums.WalletStatusType;
import com.recargapay.wallet.core.factory.TransferTransaction;
import com.recargapay.wallet.core.factory.context.Transaction;
import com.recargapay.wallet.database.entity.Wallet;
import com.recargapay.wallet.database.repository.TransactionReactiveRepository;
import com.recargapay.wallet.database.repository.WalletReactiveRepository;
import com.recargapay.wallet.event.dto.DualTransactionEvent;
import com.recargapay.wallet.event.dto.TransactionEvent;
import com.recargapay.wallet.event.transaction.TransactionProducer;
import com.recargapay.wallet.handle.Message;
import com.recargapay.wallet.handle.exception.InsufficientFundsException;
import com.recargapay.wallet.handle.exception.WalletInactiveException;
import com.recargapay.wallet.handle.exception.WalletNotFoundException;
import com.recargapay.wallet.rest.dto.TransactionDTO;
import com.recargapay.wallet.rest.dto.TransferDTO;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;
import java.util.UUID;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.MockedStatic;

/**
 * Testes unitários para a classe TransferService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TransferServiceTest {

    @Mock
    private WalletReactiveRepository walletRepository;

    @Mock
    private TransactionReactiveRepository transactionRepository;

    @Mock
    private TransactionProducer transactionProducer;

    @Mock
    private KafkaTemplate kafkaTemplate;

    @Mock
    private CacheService cache;

    @Mock
    private MeterRegistry meterRegistry;

    @InjectMocks
    private TransferService transferService;

    /**
     * Método auxiliar para criar uma instância de Wallet.
     */
    private Wallet createWallet(String id, Long version, String status, BigDecimal currentBalance) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setVersion(version);
        wallet.setStatus(status);
        wallet.setCurrentBalance(currentBalance);
        return wallet;
    }

    /**
     * Método auxiliar para criar uma instância de TransferDTO.
     */
    private TransferDTO createTransferDTO(String transactionId, BigDecimal amount) {
        return new TransferDTO(
                UUID.randomUUID().toString(),
                transactionId,
                amount
        );
    }

    @BeforeEach
    public void setUp(){
        this.kafkaTemplate = mock(KafkaTemplate.class);
    }

    @Test
    public void testValidateDuplicateTransaction_NotDuplicate() {
        String transactionId = "tx123";
        when(cache.isDuplicateTransaction(transactionId)).thenReturn(Mono.empty());

        StepVerifier.create(transferService.validateDuplicateTransaction(transactionId))
                .verifyComplete();

        verify(cache, times(1)).isDuplicateTransaction(transactionId);
    }

    @Test
    public void testValidateDuplicateTransaction_Duplicate() {
        String transactionId = "txDuplicate";
        RuntimeException duplicateException = new RuntimeException("Duplicate transaction");
        when(cache.isDuplicateTransaction(transactionId)).thenReturn(Mono.error(duplicateException));

        StepVerifier.create(transferService.validateDuplicateTransaction(transactionId))
                .expectErrorMatches(throwable -> throwable.equals(duplicateException))
                .verify();

        verify(cache, times(1)).isDuplicateTransaction(transactionId);
    }


    @Test
    public void testGetAndValidateSourceWallet_WalletNotFound() {
        String sourceWalletId = "wallet1";
        BigDecimal amount = BigDecimal.TEN;

        when(walletRepository.findById(sourceWalletId)).thenReturn(Mono.empty());

        StepVerifier.create(transferService.getAndValidateSourceWallet(sourceWalletId, amount))
                .expectErrorMatches(throwable -> throwable instanceof WalletNotFoundException &&
                        throwable.getMessage().equals(Message.WALLET_NOT_FOUND1.getMessage()))
                .verify();
    }

    @Test
    public void testGetAndValidateSourceWallet_Success() {
        String sourceWalletId = "wallet1";
        BigDecimal amount = new BigDecimal("50");
        Wallet wallet = createWallet(sourceWalletId, 1L, WalletStatusType.ACTIVE.getType(), new BigDecimal("100"));

        when(walletRepository.findById(sourceWalletId)).thenReturn(Mono.just(wallet));
        when(walletRepository.findByIdAndVersion(wallet.getId(), wallet.getVersion())).thenReturn(Mono.just(wallet));

        StepVerifier.create(transferService.getAndValidateSourceWallet(sourceWalletId, amount))
                .expectNext(wallet)
                .verifyComplete();
    }


    @Test
    public void testValidateSourceWallet_WalletInactive() {
        Wallet wallet = createWallet("walletInactive", 1L, "INACTIVE", new BigDecimal("100"));
        BigDecimal amount = new BigDecimal("50");

        StepVerifier.create(transferService.validateSourceWallet(wallet, amount))
                .expectErrorMatches(throwable -> throwable instanceof WalletInactiveException &&
                        throwable.getMessage().equals(Message.WALLET_IS_NOT_ACTIVE1.getMessage()))
                .verify();
    }

    @Test
    public void testValidateSourceWallet_InsufficientFunds() {
        Wallet wallet = createWallet("walletActive", 1L, WalletStatusType.ACTIVE.getType(), new BigDecimal("30"));
        BigDecimal amount = new BigDecimal("50");

        StepVerifier.create(transferService.validateSourceWallet(wallet, amount))
                .expectErrorMatches(throwable -> throwable instanceof InsufficientFundsException &&
                        throwable.getMessage().equals(Message.INSUFFICIENT_FUNDS.getMessage()))
                .verify();
    }

    @Test
    public void testValidateSourceWallet_ConcurrentModification() {
        String walletId = "walletActive";
        Wallet wallet = createWallet(walletId, 1L, WalletStatusType.ACTIVE.getType(), new BigDecimal("100"));
        BigDecimal amount = new BigDecimal("50");

        when(walletRepository.findByIdAndVersion(wallet.getId(), wallet.getVersion())).thenReturn(Mono.empty());

        StepVerifier.create(transferService.validateSourceWallet(wallet, amount))
                .expectErrorMatches(throwable -> throwable instanceof ConcurrentModificationException &&
                        throwable.getMessage().equals(Message.CONCURRENT_MODIFICATION_DETECTED_TRY_AGAIN.getMessage()))
                .verify();
    }

    @Test
    public void testValidateSourceWallet_Success() {
        String walletId = "walletActive";
        Wallet wallet = createWallet(walletId, 1L, WalletStatusType.ACTIVE.getType(), new BigDecimal("100"));
        BigDecimal amount = new BigDecimal("50");

        when(walletRepository.findByIdAndVersion(wallet.getId(), wallet.getVersion())).thenReturn(Mono.just(wallet));

        StepVerifier.create(transferService.validateSourceWallet(wallet, amount))
                .expectNext(wallet)
                .verifyComplete();
    }


    @Test
    public void testProcessDestinationWallet_WalletNotFound() {
        String destinationWalletId = "destWallet";
        when(walletRepository.findById(destinationWalletId)).thenReturn(Mono.empty());

        StepVerifier.create(transferService.processDestinationWallet(destinationWalletId))
                .expectErrorMatches(throwable -> throwable instanceof WalletNotFoundException &&
                        throwable.getMessage().equals(Message.WALLET_DESTINATION_NOT_FOUND.getMessage()))
                .verify();
    }

    @Test
    public void testProcessDestinationWallet_WalletInactive() {
        String destinationWalletId = "destWallet";
        Wallet wallet = createWallet(destinationWalletId, 1L, "INACTIVE", new BigDecimal("100"));
        when(walletRepository.findById(destinationWalletId)).thenReturn(Mono.just(wallet));

        StepVerifier.create(transferService.processDestinationWallet(destinationWalletId))
                .expectErrorMatches(throwable -> throwable instanceof WalletInactiveException &&
                        throwable.getMessage().equals(Message.WALLET_DESTINATION_IS_NOT_ACTIVE.getMessage()))
                .verify();
    }

    @Test
    public void testProcessDestinationWallet_Success() {
        String destinationWalletId = "destWallet";
        Wallet wallet = createWallet(destinationWalletId, 1L, WalletStatusType.ACTIVE.getType(), new BigDecimal("100"));
        when(walletRepository.findById(destinationWalletId)).thenReturn(Mono.just(wallet));

        StepVerifier.create(transferService.processDestinationWallet(destinationWalletId))
                .expectNext(wallet)
                .verifyComplete();
    }


    @Test
    public void testValidateDestinationWallet_WalletInactive() {
        Wallet wallet = createWallet("destWallet", 1L, "INACTIVE", new BigDecimal("100"));

        StepVerifier.create(transferService.validateDestinationWallet(wallet))
                .expectErrorMatches(throwable -> throwable instanceof WalletInactiveException &&
                        throwable.getMessage().equals(Message.WALLET_DESTINATION_IS_NOT_ACTIVE.getMessage()))
                .verify();
    }

    @Test
    public void testValidateDestinationWallet_Success() {
        Wallet wallet = createWallet("destWallet", 1L, WalletStatusType.ACTIVE.getType(), new BigDecimal("100"));

        StepVerifier.create(transferService.validateDestinationWallet(wallet))
                .expectNext(wallet)
                .verifyComplete();
    }


    @Test
    public void testEnqueueTransaction_Success() {
        String sourceWalletId = "sourceWallet";
        String destinationWalletId = "destWallet";
        BigDecimal amount = new BigDecimal("100");
        String transactionId = "tx123";

        TransferDTO transferDTO = createTransferDTO(transactionId, amount);

        doNothing().when(transactionProducer).sendTransaction(any(Transaction.class));

        Mono<TransactionDTO> result = transferService.enqueueTransaction(sourceWalletId, destinationWalletId, transferDTO);

        StepVerifier.create(result)
                .assertNext(txDto -> {
                    assertThat(txDto.getRequestTransactionId()).isEqualTo(transferDTO.getRequestTransactionId());
                    assertThat(txDto.getWalletId()).isEqualTo(sourceWalletId);
                    assertThat(txDto.getDestinationWalletId()).isEqualTo(destinationWalletId);
                    assertThat(txDto.getType()).isEqualTo(TransactionType.TRANSFER_OUT.getType());
                    assertThat(txDto.getStatus()).isEqualTo(TransactionStatusType.PROCESSING.getStatus());
                    assertThat(txDto.getAmount()).isEqualByComparingTo(transferDTO.getAmount());
                })
                .verifyComplete();

        verify(transactionProducer).sendTransaction(any(Transaction.class));
    }

}
