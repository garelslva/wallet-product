package com.recargapay.wallet.event.balance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recargapay.wallet.event.dto.BalanceUpdateEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import static com.recargapay.wallet.event.BrokerProperties.WALLET_BALANCE_UPDATES;

@Service
@RequiredArgsConstructor
public class BalanceUpdateProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendBalanceUpdate(BalanceUpdateEvent event) {
        try {
            String json = this.objectMapper.writeValueAsString(event);
            kafkaTemplate.send(WALLET_BALANCE_UPDATES.getTopic(), json);

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
