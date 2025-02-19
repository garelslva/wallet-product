package com.recargapay.wallet.core.service;

import com.recargapay.wallet.rest.dto.UserDTO;
import reactor.core.publisher.Mono;

/**
 * This interface defines the services related to user management.
 * <p>
 * It provides methods to create a new user and to retrieve users by their username.
 * The operations are reactive and use Project Reactor.
 * </p>
 */
public interface UserService {

    /**
     * Creates a new user based on the provided information.
     *
     * @param user the {@code UserDTO} object containing the information of the user to be created.
     */
    Mono<UserDTO> create(UserDTO user);

    /**
     * Retrieves a list of users whose username matches the specified value.
     * <p>
     * This method returns a {@code Mono} that, when subscribed to, will emit a {@code List} of {@code UserDTO}
     * objects that satisfy the search criterion.
     * </p>
     *
     * @param cpf the cpf to search for.
     * @return a {@code Mono} emitting a {@code UserDTO} objects if matching users are found.
     */
    Mono<UserDTO> getByCpf(String cpf, String requestTransactionId);
}

