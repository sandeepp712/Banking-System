package com.bank.service;

import com.bank.domain.*;
import com.bank.persistence.TransactionLogger;
import com.bank.service.Exceptions.DuplicateTransactionException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public class AccountService {
    private final AccountRepository accountRepository;
    private final TransactionLogger logger;
    private final IdempotencyService idempotencyService;

    public static final String SYSTEM_ACCOUNT = "SYSTEM";

    public AccountService(AccountRepository accountRepository, TransactionLogger logger,IdempotencyService idempotencyService) {
        this.accountRepository = accountRepository;
        this.logger = logger;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Creates a new account and saves it to the repository.
     */
    public Account createAccount(String accountNo, Money initialBalance, List<Customer> owners) {
        if (accountRepository.findByAccountNumber(accountNo).isPresent()) {
            throw new IllegalArgumentException("Account already exists : " + accountNo);
        }

        Account account = new Account(accountNo, initialBalance, owners);
        accountRepository.save(account);

        return account;
    }

    /**
     * To get the particular account is present or not
     * @param id
     * @return
     */
    public Account getAccount(String id) {
        return accountRepository.findByAccountNumber(id).orElseThrow(() -> new IllegalArgumentException("Account not found : " + id));
    }

    /**
     * To return all account
     * @return
     */
    public Collection<Account> getAllAccounts() {
        return accountRepository.findAll();
    }


    /**
     * Deposits money into an account.
     * @return The Transaction record representing this deposit.
     */
    public Transaction deposit(String accountNumber, Money amount, String idempotencyKey) {
        if(idempotencyService.isAlreadyProcessed(idempotencyKey)){
            throw new DuplicateTransactionException("Transaction already processed :" + idempotencyKey);
        }

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found : " + accountNumber));

        //Apply the credit to self account
        account.credit(amount);
        accountRepository.save(account);
        idempotencyService.markAsProcessed(idempotencyKey);


        Transaction tx = Transaction.builder()
                .fromAccountId(SYSTEM_ACCOUNT)
                .toAccountId(accountNumber)
                .amount(amount)
                .status(TransactionStatus.COMMITTED)
                .idempotencyKey(idempotencyKey)
                .build();

        try {
            logger.logTransaction(tx);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Deposit succeeded, but logging failed due to system interruption.", e);
        }

        return tx;
    }

    /**
     * Withdraws money from an account.
     *
     * @return The Transaction record representing this withdrawal.
     */
    public Transaction withdraw(String accountNumber, Money amount, String idempotencyKey) {

        if(idempotencyService.isAlreadyProcessed(idempotencyKey)){
            throw new DuplicateTransactionException("Transaction already processed :" + idempotencyKey);
        }

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found : " + accountNumber));

        account.debit(amount);

        accountRepository.save(account);
        idempotencyService.markAsProcessed(idempotencyKey);

        Transaction tx = Transaction.builder()
                .fromAccountId(SYSTEM_ACCOUNT)
                .toAccountId(accountNumber)
                .amount(amount)
                .status(TransactionStatus.COMMITTED)
                .idempotencyKey(idempotencyKey)
                .build();

        try {
            logger.logTransaction(tx);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Deposit succeeded, but logging failed due to system interruption.", e);
        }

        return tx;
    }

}