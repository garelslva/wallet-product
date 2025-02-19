package com.recargapay.wallet.handle;

import lombok.Getter;

@Getter
public enum Message {

    CPF_ALREADY_EXISTS("CPF already exists", 400),
    USER_NOT_REGISTERED("User not registered", 404),
    WALLET_ALREADY_EXISTS("WALLET already exists", 400),
    WALLET_NOT_FOUND("Wallet not found", 404),
    WALLET_IS_NOT_ACTIVE("Wallet is not active", 402),
    INSUFFICIENT_FUNDS("Insufficient funds", 402),
    WALLET_NOT_FOUND1("Source wallet not found", 404),
    WALLET_IS_NOT_ACTIVE1("Source wallet is not active", 402),
    WALLET_DESTINATION_NOT_FOUND("Destination wallet not found", 400),
    WALLET_DESTINATION_IS_NOT_ACTIVE("Destination wallet is not active", 400),
    CONCURRENT_MODIFICATION_DETECTED_TRY_AGAIN("Concurrent modification detected. Try again.", 400),
    BALANCE_UPDATED_FOR_WALLET_INFO("Balance updated for wallet: {}, requestTransactionId: {}", 204),
    PROCESSING_BALANCE_UPDATE_INFO("Processing balance update: walletId {}, requestTransactionId: {}", 204),
    DEPOSIT_PROCESSED_SUCCESSFULLY_FOR_WALLET_INFO("Deposit processed successfully for wallet: {}, requestTransactionId: {}", 201),
    DEPOSIT_FAILED_FOR_WALLET_ERROR("Deposit failed for wallet: {}, requestTransactionId: {}, Error: {}", 402),
    WITHDRAW_PROCESSED_SUCCESSFULLY_FOR_WALLET_INFO("Withdraw processed successfully for wallet: {}, requestTransactionId: {}", 200),
    WITHDRAW_FAILED_FOR_WALLET_ERROR("Withdraw failed for wallet: {}, requestTransactionId: {}, Error: {}", 402),
    TRANSFER_PROCESSED_SUCCESSFULLY_FROM_TO("Transfer processed successfully from {} to {}", 201),
    DUPLICATE_TRANSACTION_DETECTED("Duplicate transaction detected", 400),
    TRANSFER_FAILED_FROM_TO_ERROR("Transfer failed from {} to {} - Error: {}", 402),
    CACHE_CLEARED_FOR_WALLET_INFO("Cache cleared for wallet: {}", 200),
    TRANSACTION_COMPLETED_FOR_WALLET("Transaction completed for wallet: {}", 201),
    PROCESSING_TRANSACTION("Processing transaction: {}", 204),
    TRANSACTION_ENQUEUED_INFO("Transaction enqueue: wallet_id: {}, requestTransactionId: {}", 204),
    FETCHING_BALANCE_FROM_DATABASE_FOR_WALLET("Fetching balance from database for wallet: {}", 204),
    GETTING_USER_INFO("Getting user by cpf: {}, requestTransactionId: {}", 204),
    CACHE_HIT_FOR_WALLET("Cache hit for wallet: {}", 204),
    CREATING_WALLET_FOR_USER_INFO("Creating wallet for user: {}, requestTransactionId: {}",204),
    CREATING_USER_INFO("Creating User for user: {}",204),
    FETCHING_BALANCE_FOR_WALLET_INFO("Fetching balance for wallet: {}",204),
    PROCESSING_DEPOSIT_FOR_WALLET_AMOUNT_INFO("Processing deposit for Wallet: {}, Amount: {}, RequestTransactionId: {}",204),
    PROCESSING_WITHDRAW_FOR_WALLET_AMOUNT_INFO("Processing withdraw for wallet: {}, Amount: {}, requestTransactionId: {}",204),
    PROCESSING_TRANSFER_FROM_WALLET_TO_WALLET_AMOUNT_INFO("Processing transfer from wallet: {} To wallet: {}, Amount: {}",204),
    DEPOSIT_PROCESSED_SUCCESSFULLY_INFO("Deposit processed successfully for wallet: {}, Amount: {}, requestTransactionId: {}", 204),
    FAILED_TO_PROCESS_DEPOSIT_ERROR("Failed to process deposit for wallet: {}, Amount: {}, requestTransactionId: {}, error: {}", 500),
    TRANSFER_PROCESSED_SUCCESSFULLY_INFO("Transfer processed successfully from walletSource: {} to walletDestination: {}, Amount: {}, requestTransactionId: {}", 204),
    TRANSFER_FAILED_ERROR("Transfer failed: {}", 500),
    REQUIRED_HEADER_FIELD_REQUEST_TRANSACTION_ID("Required header field 'requestTransactionId'.", 400),
    SENDING_OUTBOUND_TRANSFER_EVENT_ERROR("Error sending outbound transfer event", 500),
    SOURCE_WALLET_NOT_FOUND("Source wallet not found", 404),
    DESTINATION_WALLET_NOT_FOUND("Destination wallet not found", 404),
    TRANSACTION_WITHDRAW_WALLET_IS_EMPTY("TransactionWithdraw - wallet is empty()", 500),
    PROCESSING_THE_TRANSACTION_ERROR("Error processing the transaction: %s", 500);

    private String message;
    private int code;

    Message(String message, int code){
        this.message = message;
        this.code = code;
    }

}
