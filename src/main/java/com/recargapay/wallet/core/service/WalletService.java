package com.recargapay.wallet.core.service;

import com.recargapay.wallet.rest.dto.BalanceDTO;
import com.recargapay.wallet.rest.dto.CreateWalletDTO;
import com.recargapay.wallet.rest.dto.DepositDTO;
import com.recargapay.wallet.rest.dto.TransactionDTO;
import com.recargapay.wallet.rest.dto.TransactionsHistoricalDTO;
import com.recargapay.wallet.rest.dto.TransferDTO;
import com.recargapay.wallet.rest.dto.WalletDTO;
import com.recargapay.wallet.rest.dto.WithdrawDTO;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;

/**
 * Interface defining operations for the Wallet Service.
 */
public interface WalletService {
    /**
     * Creates a new wallet for a user.
     * @param request The request containing user details.
     * @return A Mono containing the created wallet.
     */
    Mono<WalletDTO> createWallet(CreateWalletDTO request);

    /**
     * Retrieves the current balance of a wallet.
     * @param walletId The ID of the wallet.
     * @return A Mono containing the balance details.
     */
    Mono<BalanceDTO> getBalance(String requestTransactionId, String walletId);

    /**
     * Retrieves the historical balance of a wallet at a given date.
     * @param walletId The ID of the wallet.
     * @param date The date for which the balance is requested.
     * @return A Mono containing the historical balance.
     */
    Mono<TransactionsHistoricalDTO> getHistoricalTransactions(String requestTransactionId, String walletId, long daysBefore);

    /**
     * Deposits funds into a wallet.
     * @param walletId The ID of the wallet.
     * @param request The deposit details.
     * @return A Mono containing the transaction details.
     */
    Mono<TransactionDTO> deposit(String walletId, DepositDTO request);

    /**
     * Withdraws funds from a wallet.
     * @param walletId The ID of the wallet.
     * @param request The withdrawal details.
     * @return A Mono containing the transaction details.
     */
    Mono<TransactionDTO> withdraw(String walletId, WithdrawDTO request);

    /**
     * Transfers funds from one wallet to another.
     * @param sourceId The ID of the source wallet.
     * @param destId The ID of the destination wallet.
     * @param request The transfer details.
     * @return A Mono containing the transaction details.
     */
    Mono<TransactionDTO> transfer(String sourceId, String destId, TransferDTO request);
}
