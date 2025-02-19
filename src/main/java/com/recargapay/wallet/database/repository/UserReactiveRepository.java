package com.recargapay.wallet.database.repository;

import com.recargapay.wallet.database.entity.User;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface UserReactiveRepository extends ReactiveCrudRepository<User, String> {
    Mono<User> findByCpf(String cpf);
}
