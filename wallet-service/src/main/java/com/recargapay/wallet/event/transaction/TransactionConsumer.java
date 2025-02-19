package com.recargapay.wallet.event.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recargapay.wallet.core.enums.TransactionType;
import com.recargapay.wallet.core.factory.DepositTransaction;
import com.recargapay.wallet.core.factory.TransferTransaction;
import com.recargapay.wallet.core.factory.WithdrawTransaction;
import com.recargapay.wallet.core.factory.context.Transaction;
import com.recargapay.wallet.core.factory.context.TransactionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.recargapay.wallet.handle.Message.PROCESSING_THE_TRANSACTION_ERROR;
import static com.recargapay.wallet.handle.Message.PROCESSING_TRANSACTION;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionConsumer {

    private final TransactionFactory factory;
    private final ObjectMapper objectMapper;

    private static final Map<String, Class<? extends Transaction>> TRANSACTION_MAP = Map.of(
            TransactionType.DEPOSIT.getType(), DepositTransaction.class,
            TransactionType.TRANSFER_IN.getType(), TransferTransaction.class,
            TransactionType.TRANSFER_OUT.getType(), TransferTransaction.class,
            TransactionType.WITHDRAW.getType(), WithdrawTransaction.class
    );

    @RetryableTopic(autoCreateTopics = "true", include = {RuntimeException.class})
    @KafkaListener(topics = "wallet-transactions", groupId = "wallet-service-group")
    public void processTransaction(String event) {
        log.info(PROCESSING_TRANSACTION.getMessage(), event);

        try {
            LinkedHashMap<?, ?> param = objectMapper.readValue(event, LinkedHashMap.class);
            LinkedHashMap<?, ?> eventMap = (LinkedHashMap<?, ?>) param.get("event");
            Object typeSerialize = eventMap.get("type");

            Class<? extends Transaction> transactionClass = TRANSACTION_MAP.get(
                    typeSerialize != null ? typeSerialize : ""
            );
            if (transactionClass == null) {
                transactionClass = TransferTransaction.class;
            }

            Transaction transaction = objectMapper.readValue(event, transactionClass);
            transaction.execute(factory);

            Thread.sleep(60);

        } catch (JsonProcessingException | InterruptedException e) {
            throw new RuntimeException(String.format(PROCESSING_THE_TRANSACTION_ERROR.getMessage(), e.getMessage()), e);
        }
    }
}
