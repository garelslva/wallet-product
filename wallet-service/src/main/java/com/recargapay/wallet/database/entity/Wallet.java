package com.recargapay.wallet.database.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table("wallets")
public class Wallet {

    @Id
    private String id;

    @Column("user_id")
    private String userId;

    @Column("request_transaction_id")
    private String requestTransactionId;

    @Column("current_balance")
    private BigDecimal currentBalance;

    @Column("last_balance_updated")
    private LocalDateTime lastBalanceUpdated;

    @Column("status")
    private String status;

    @Version
    private Long version;
}

