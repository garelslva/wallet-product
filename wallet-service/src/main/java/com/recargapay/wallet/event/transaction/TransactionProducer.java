package com.recargapay.wallet.event.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recargapay.wallet.core.factory.context.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import static com.recargapay.wallet.event.BrokerProperties.WALLET_TRANSACTIONS;

@Component
@RequiredArgsConstructor
public class TransactionProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendTransaction(Transaction event) {
        try {
            var json = this.objectMapper.writeValueAsString(event);
            kafkaTemplate.send(WALLET_TRANSACTIONS.getTopic(), json);

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
