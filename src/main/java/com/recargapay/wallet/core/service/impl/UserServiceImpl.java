package com.recargapay.wallet.core.service.impl;

import com.recargapay.wallet.cache.CacheService;
import com.recargapay.wallet.converter.UserConverter;
import com.recargapay.wallet.core.service.UserService;
import com.recargapay.wallet.database.entity.User;
import com.recargapay.wallet.database.repository.UserReactiveRepository;
import com.recargapay.wallet.handle.exception.UserException;
import com.recargapay.wallet.rest.dto.UserDTO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static com.recargapay.wallet.handle.Message.CPF_ALREADY_EXISTS;
import static com.recargapay.wallet.handle.Message.CREATING_USER_INFO;
import static com.recargapay.wallet.handle.Message.GETTING_USER_INFO;

@Slf4j
@Service
@AllArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserReactiveRepository userRepository;
    private final DatabaseClient databaseClient;
    private final CacheService cache;
    private final MeterRegistry meterRegistry;

    @Override
    public Mono<UserDTO> create(UserDTO request) {
        log.info(CREATING_USER_INFO.getMessage(), request.getCpf());
        Timer.Sample sample = Timer.start(meterRegistry);

        return cache.isDuplicateTransaction(request.getRequestTransactionId())
                .then(Mono.defer(() -> databaseClient.inConnection(conn -> Mono.from(conn.beginTransaction())
                    .then(userRepository.findByCpf(request.getCpf())
                        .flatMap(existingUser -> Mono.error(new UserException(CPF_ALREADY_EXISTS)))
                        .switchIfEmpty(userRepository.save(UserConverter.dtoToUserEntity(request)))
                        .map(userEntity -> UserConverter.entityToUserDto((User) userEntity))
                    )
                    .flatMap(userDto -> Mono.from(conn.commitTransaction()).thenReturn(userDto))
                    .onErrorResume(e -> Mono.from(conn.rollbackTransaction()).then(Mono.error(e)))
                )))
                .doFinally(signalType -> sample.stop(meterRegistry.timer("create_user_time")));
    }

    @Override
    public Mono<UserDTO> getByCpf(String cpf, String requestTransactionId) {
        log.info(GETTING_USER_INFO.getMessage(), cpf, requestTransactionId);
        Timer.Sample sample = Timer.start(meterRegistry);

        return userRepository.findByCpf(cpf)
                .switchIfEmpty(Mono.error(new UserException(CPF_ALREADY_EXISTS)))
                .map(UserConverter::entityToUserDto)
                .doFinally(signalType -> sample.stop(meterRegistry.timer("getting_user_time")));
    }
}
