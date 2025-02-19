package com.recargapay.wallet.rest.validate;

import com.recargapay.wallet.handle.exception.TrackerException;

import java.util.Objects;

import static com.recargapay.wallet.handle.Message.REQUIRED_HEADER_FIELD_REQUEST_TRANSACTION_ID;

public class TrackerValidate {

    public static void validateOf(String requestTransactionId) {
        if (Objects.isNull(requestTransactionId) || requestTransactionId.length() < 5){
            throw new TrackerException(REQUIRED_HEADER_FIELD_REQUEST_TRANSACTION_ID);
        }
    }
}
