package com.recargapay.wallet.database.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table("users")
public class User {
    @Id
    private String id;

    @Column("request_transaction_id")
    private String requestTransactionId;

    @Column("username")
    private String username;

    @Column("name")
    private String name;

    @Column("email")
    private String email;

    @Column("cpf")
    private String cpf;
}
