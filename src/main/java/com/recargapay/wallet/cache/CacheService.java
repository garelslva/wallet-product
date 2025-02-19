package com.recargapay.wallet.cache;

import com.recargapay.wallet.handle.exception.TransactionException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static com.recargapay.wallet.cache.CacheProperties.CONNECTION_REDIS_TIMEOUT_MINUTES;
import static com.recargapay.wallet.cache.KeyProperties.BALANCE_KEY;
import static com.recargapay.wallet.cache.KeyProperties.DUPLICATION_TRANSACTION_KEY;
import static com.recargapay.wallet.handle.Message.CACHE_CLEARED_FOR_WALLET_INFO;
import static com.recargapay.wallet.handle.Message.DUPLICATE_TRANSACTION_DETECTED;

@Slf4j
@Service
@AllArgsConstructor
public class CacheService {

    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<Void> isDuplicateTransaction(String requestTransactionId) {
        ReactiveValueOperations<String, String> operations = redisTemplate.opsForValue();
        return operations.get(requestTransactionId)
                .flatMap(value -> Mono.just(true))
                .switchIfEmpty(Mono.defer(() -> operations.set(requestTransactionId, DUPLICATION_TRANSACTION_KEY.getKey()).thenReturn(false)))
                    .flatMap(isDuplicate -> isDuplicate ? Mono.error(new TransactionException(DUPLICATE_TRANSACTION_DETECTED)) : Mono.empty());
    }

    public Mono<Void> set(String cacheKey, String value) {
        opsForValue()
           .set(cacheKey, value)
           .subscribe();
        return Mono.empty();
    }

    public Mono<Void> clearBalanceCache(String walletId) {
        String cacheKey = BALANCE_KEY.getKey(walletId);
        return redisTemplate.delete(cacheKey)
                .doOnSuccess(v -> log.info(CACHE_CLEARED_FOR_WALLET_INFO.getMessage(), walletId))
                .then();
    }

    public ReactiveValueOperations<String, String> opsForValue() {
        return this.redisTemplate.opsForValue();
    }

    public Mono<String> get(String cacheKey) {
        return this.opsForValue().get(cacheKey);
    }
}
