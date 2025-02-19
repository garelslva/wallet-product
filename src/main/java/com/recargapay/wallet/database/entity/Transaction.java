package com.recargapay.wallet.database.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table("transactions")
public class Transaction {

    @Id
    private String id;

    @Column("request_transaction_id")
    private String requestTransactionId;

    @Column("wallet_id")
    private String walletId;

    @Column("destination_Wallet_id")
    private String destinationWalletId;

    @Column("type")
    private String type;

    @Column("status")
    private String status;

    @Column("amount")
    private BigDecimal amount;

    @Column("timestamp")
    private LocalDateTime timestamp;
}
