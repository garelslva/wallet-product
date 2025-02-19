package com.recargapay.wallet.database.repository;

import com.recargapay.wallet.database.entity.Wallet;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface WalletReactiveRepository extends ReactiveCrudRepository<Wallet, String> {
    Flux<Wallet> findByUserId(String userId);
    Mono<Wallet> findByIdAndVersion(String id, Long version);
}
