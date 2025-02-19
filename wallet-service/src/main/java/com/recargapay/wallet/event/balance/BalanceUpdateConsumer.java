package com.recargapay.wallet.event.balance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recargapay.wallet.converter.WalletConverter;
import com.recargapay.wallet.database.entity.Wallet;
import com.recargapay.wallet.database.repository.WalletReactiveRepository;
import com.recargapay.wallet.event.dto.BalanceUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

import static com.recargapay.wallet.handle.Message.BALANCE_UPDATED_FOR_WALLET_INFO;
import static com.recargapay.wallet.handle.Message.PROCESSING_BALANCE_UPDATE_INFO;

@Service
@Slf4j
@RequiredArgsConstructor
public class BalanceUpdateConsumer {

    private final WalletReactiveRepository walletRepository;
    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            autoCreateTopics = "true",
            include = {RuntimeException.class})
    @KafkaListener(topics = "wallet-balance-updates", groupId = "wallet-service-group")
    public void processBalanceUpdate(String eventStr) {

        BalanceUpdateEvent[] event = {new BalanceUpdateEvent()};
        try {
            event[0] = this.objectMapper.readValue(eventStr, BalanceUpdateEvent.class);

            log.info(PROCESSING_BALANCE_UPDATE_INFO.getMessage(), event[0].getWalletId(), event[0].getRequestTransactionId());

            databaseClient.inConnection(conn ->
                    Mono.from(conn.beginTransaction())
                            .then(walletRepository.findById(event[0].getWalletId()))
                            .flatMap(wallet -> applyNewBalanceValue(wallet, event[0].getAmount()))
                            .flatMap(updatedWallet -> Mono.from(conn.commitTransaction())
                                            .thenReturn(WalletConverter.entityToWalletDto(updatedWallet))
                            )
                            .doOnSuccess(walletDto ->
                                    log.info(BALANCE_UPDATED_FOR_WALLET_INFO.getMessage(), walletDto.getId(), walletDto.getRequestTransactionId())
                            )
                            .onErrorResume(e ->
                                    Mono.from(conn.rollbackTransaction()).then(Mono.error(e))
                            )
            ).subscribe();

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    private Mono<Wallet> applyNewBalanceValue(Wallet wallet, BigDecimal amount) {
        wallet.setCurrentBalance(wallet.getCurrentBalance().add(amount));
        return walletRepository.save(wallet);
    }
}