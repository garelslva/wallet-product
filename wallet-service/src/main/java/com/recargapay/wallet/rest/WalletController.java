package com.recargapay.wallet.rest;

import com.recargapay.wallet.handle.ResponseHandler;
import com.recargapay.wallet.rest.dto.BalanceDTO;
import com.recargapay.wallet.rest.dto.CreateWalletDTO;
import com.recargapay.wallet.rest.dto.DepositDTO;
import com.recargapay.wallet.rest.dto.TransactionDTO;
import com.recargapay.wallet.rest.dto.TransactionsHistoricalDTO;
import com.recargapay.wallet.rest.dto.TransferDTO;
import com.recargapay.wallet.rest.dto.WalletDTO;
import com.recargapay.wallet.rest.dto.WithdrawDTO;
import com.recargapay.wallet.core.service.WalletService;
import com.recargapay.wallet.rest.validate.TrackerValidate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
class WalletController {

    private final WalletService walletService;

    @Operation(
            summary = "Create a new wallet",
            description = "Creates a new wallet for the user specified in the request body. The initial balance is 0."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Wallet successfully created.",
                    content = @Content(schema = @Schema(implementation = WalletDTO.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid data.", content = @Content(schema = @Schema(implementation = ResponseHandler.class)))
    })
    @PostMapping
    public Mono<ResponseEntity<WalletDTO>> createWallet(
        @RequestHeader("requestTransactionId") String requestTransactionId, @RequestBody CreateWalletDTO request) {
        TrackerValidate.validateOf(requestTransactionId);

        request.setRequestTransactionId(requestTransactionId);
        return walletService.createWallet(request)
                .map(ResponseEntity::ok);
    }

    @Operation(
            summary = "Get current balance",
            description = "Retrieves the current balance of a specific wallet identified by its ID."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Balance successfully retrieved.",
                    content = @Content(schema = @Schema(implementation = BalanceDTO.class))
            ),
            @ApiResponse(responseCode = "404", description = "Wallet not found.", content = @Content(schema = @Schema(implementation = ResponseHandler.class)))
    })
    @GetMapping("/{id}/balance")
    public Mono<ResponseEntity<BalanceDTO>> getBalance(
        @RequestHeader("requestTransactionId") String requestTransactionId, @PathVariable String id) {
        TrackerValidate.validateOf(requestTransactionId);

        return walletService.getBalance(requestTransactionId, id)
                .map(ResponseEntity::ok);
    }

    @Operation(
            summary = "Get historical balance",
            description = "Retrieves the historical balance of a wallet at a specific date/time."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Historical balance successfully retrieved.",
                    content = @Content(schema = @Schema(implementation = BalanceDTO.class))
            ),
            @ApiResponse(responseCode = "404", description = "Wallet not found.", content = @Content(schema = @Schema(implementation = ResponseHandler.class)))
    })
    @GetMapping("/{id}/balance/history")
    public Mono<ResponseEntity<TransactionsHistoricalDTO>> getHistoricalBalance(
        @RequestHeader("requestTransactionId") String requestTransactionId, @PathVariable String id, @RequestParam long daysBefore) {
        TrackerValidate.validateOf(requestTransactionId);

        return walletService.getHistoricalTransactions(requestTransactionId, id, daysBefore)
                .map(ResponseEntity::ok);
    }

    @Operation(
            summary = "Deposit funds",
            description = "Deposits a specified amount into a wallet."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Deposit successful.",
                    content = @Content(schema = @Schema(implementation = TransactionDTO.class))
            ),
            @ApiResponse(responseCode = "404", description = "Wallet not found.", content = @Content(schema = @Schema(implementation = ResponseHandler.class))),
            @ApiResponse(responseCode = "400", description = "Invalid amount.", content = @Content(schema = @Schema(implementation = ResponseHandler.class)))
    })
    @PostMapping("/{id}/deposit")
    public Mono<ResponseEntity<TransactionDTO>> deposit(
        @RequestHeader("requestTransactionId") String requestTransactionId, @PathVariable String id, @RequestBody DepositDTO request) {
        TrackerValidate.validateOf(requestTransactionId);

        request.setRequestTransactionId(requestTransactionId);
        return walletService.deposit(id, request)
                .map(ResponseEntity::ok);
    }

    @Operation(
            summary = "Withdraw funds",
            description = "Withdraws a specified amount from a wallet, validating if there is enough balance."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Withdrawal successful.",
                    content = @Content(schema = @Schema(implementation = TransactionDTO.class))
            ),
            @ApiResponse(responseCode = "404", description = "Wallet not found.", content = @Content(schema = @Schema(implementation = ResponseHandler.class))),
            @ApiResponse(responseCode = "400", description = "Insufficient balance or invalid amount.", content = @Content(schema = @Schema(implementation = ResponseHandler.class)))
    })
    @PostMapping("/{id}/withdraw")
    public Mono<ResponseEntity<TransactionDTO>> withdraw(
        @RequestHeader("requestTransactionId") String requestTransactionId, @PathVariable String id, @RequestBody WithdrawDTO request) {
        TrackerValidate.validateOf(requestTransactionId);

        request.setRequestTransactionId(requestTransactionId);
        return walletService.withdraw(id, request)
                .map(ResponseEntity::ok);
    }

    @Operation(
            summary = "Transfer funds",
            description = "Transfers a specified amount from the source wallet to the destination wallet in a single atomic operation."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Transfer successful.",
                    content = @Content(schema = @Schema(implementation = TransactionDTO.class))
            ),
            @ApiResponse(responseCode = "404", description = "Wallet not found.", content = @Content(schema = @Schema(implementation = ResponseHandler.class))),
            @ApiResponse(responseCode = "400", description = "Insufficient balance or invalid amount.", content = @Content(schema = @Schema(implementation = ResponseHandler.class)))
    })
    @PostMapping("/{sourceId}/transfer/{destId}")
    public Mono<ResponseEntity<TransactionDTO>> transfer(
        @RequestHeader("requestTransactionId") String requestTransactionId,
        @PathVariable String sourceId, @PathVariable String destId, @RequestBody TransferDTO request) {
        TrackerValidate.validateOf(requestTransactionId);

        request.setRequestTransactionId(requestTransactionId);
        return walletService.transfer(sourceId, destId, request)
                .map(ResponseEntity::ok);
    }
}
