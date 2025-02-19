package com.recargapay.wallet.core.factory.context;

import com.recargapay.wallet.cache.CacheService;
import com.recargapay.wallet.core.service.TransferService;
import com.recargapay.wallet.database.repository.TransactionReactiveRepository;
import com.recargapay.wallet.database.repository.WalletReactiveRepository;
import com.recargapay.wallet.event.balance.BalanceUpdateProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionFactory {

    private final TransferService transferService;
    private final WalletReactiveRepository walletRepository;
    private final TransactionReactiveRepository transactionRepository;
    private final DatabaseClient databaseClient;
    private final BalanceUpdateProducer balanceUpdateProducer;
    private final CacheService cache;

    public TransferService transferService() {return this.transferService; }
    public WalletReactiveRepository walletRepository(){
        return this.walletRepository;
    }
    public TransactionReactiveRepository transactionRepository(){
        return this.transactionRepository;
    }
    public DatabaseClient databaseClient(){
        return this.databaseClient;
    }
    public BalanceUpdateProducer balanceUpdateProducer(){
        return this.balanceUpdateProducer;
    }
    public CacheService cache(){
        return this.cache;
    }
}
