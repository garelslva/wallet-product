package com.recargapay.wallet.converter;

import com.recargapay.wallet.database.entity.User;
import com.recargapay.wallet.rest.dto.CreateWalletDTO;
import com.recargapay.wallet.rest.dto.UserDTO;

public class UserConverter {

    private UserConverter(){}

    public static User dtoToUserEntity(UserDTO request) {
        return new User(
                request.getId(),
                request.getRequestTransactionId(),
                request.getUsername(),
                request.getName(),
                request.getEmail(),
                request.getCpf()
        );
    }

    public static UserDTO entityToUserDto(User entity) {
        return new UserDTO(
                entity.getId(),
                entity.getRequestTransactionId(),
                entity.getUsername(),
                entity.getName(),
                entity.getEmail(),
                entity.getCpf()
        );
    }
}
