package com.recargapay.wallet.rest;

import com.recargapay.wallet.core.service.UserService;
import com.recargapay.wallet.handle.ResponseHandler;
import com.recargapay.wallet.rest.dto.UserDTO;
import com.recargapay.wallet.rest.validate.TrackerValidate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "Create a new user",
            description = "Creates a new user for the user specified in the request body."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User successfully created.",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid data.", content = @Content(schema = @Schema(implementation = ResponseHandler.class))),
    })
    @PostMapping
    public Mono<ResponseEntity<UserDTO>> createUser(
        @RequestHeader("requestTransactionId") String requestTransactionId, @RequestBody UserDTO request){
        TrackerValidate.validateOf(requestTransactionId);

        request.setRequestTransactionId(requestTransactionId);
        return this.userService.create(request)
                .map(ResponseEntity::ok);
    }

    @Operation(
            summary = "Get user",
            description = "Retrieves the user of a specific user by its username."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User successfully retrieved.",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))
            ),
            @ApiResponse(responseCode = "404", description = "User not found.", content = @Content(schema = @Schema(implementation = ResponseHandler.class)))
    })
    @GetMapping("/{cpf}/user")
    public Mono<ResponseEntity<UserDTO>> getUserByCpf(
        @RequestHeader("requestTransactionId")
        String requestTransactionId,
        @PathVariable String cpf) {
        TrackerValidate.validateOf(requestTransactionId);

        return this.userService.getByCpf(cpf, requestTransactionId)
                .map(ResponseEntity::ok);
    }
}
