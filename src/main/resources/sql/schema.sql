-- Habilitar a extensão para geração de UUID, se ainda não estiver habilitada
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Opcional: Drop das tabelas se já existirem (observe a ordem para evitar problemas de FK)
--DROP TABLE IF EXISTS transactions;
--DROP TABLE IF EXISTS wallets;
--DROP TABLE IF EXISTS users;

-- Criação da tabela de usuários

CREATE TABLE IF NOT EXISTS users (
    id                     VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::text NOT NULL,
    username               VARCHAR(255) NOT NULL,
    request_transaction_id VARCHAR(255) NOT NULL,
    name                   VARCHAR(255),
    email                  VARCHAR(255),
    cpf                    VARCHAR(20)
);

-- Criação da tabela de carteiras (wallets)
CREATE TABLE IF NOT EXISTS  wallets (
    id                     VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::text NOT NULL,
    user_id                VARCHAR(255) NOT NULL,
    request_transaction_id VARCHAR(255) NOT NULL,
    current_balance        NUMERIC(19, 2) NOT NULL,
    status                 VARCHAR(50),
    last_balance_updated   TIMESTAMP,
    version                BIGINT,
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Criação da tabela de transações
CREATE TABLE IF NOT EXISTS  transactions (
    id                      VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::text NOT NULL,
    wallet_id               VARCHAR(255) NOT NULL,
    request_transaction_id  VARCHAR(255) NOT NULL,
    destination_wallet_id   VARCHAR(255),
    type                    VARCHAR(50),
    status                  VARCHAR(50),
    amount                  NUMERIC(19, 2),
    timestamp               TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_transaction_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id)
);
