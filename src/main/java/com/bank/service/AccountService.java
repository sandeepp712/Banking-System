package com.bank.service;

import com.bank.domain.*;
import com.bank.persistence.TransactionLogger;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public class AccountService {
    private final AccountRepository accountRepository;
    private final TransactionLogger logger;

    public static final String SYSTEM_ACCOUNT = "SYSTEM";

    public AccountService(AccountRepository accountRepository, TransactionLogger logger) {
        this.accountRepository = accountRepository;
        this.logger = logger;
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

    public Account getAccount(String id) {
        return accountRepository.findByAccountNumber(id).orElseThrow(() -> new IllegalArgumentException("Account not found : " + id));
    }

    public Collection<Account> getAllAccounts() {
        return accountRepository.findAll();
    }


    /**
     * Deposits money into an account.
     *
     * @return The Transaction record representing this deposit.
     */
    public Transaction deposit(String accountNumber, Money amount, String idempotencyKey) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found : " + accountNumber));

        //Apply the credit to self account
        account.credit(amount);
        accountRepository.save(account);


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

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found : " + accountNumber));

        account.debit(amount);

        accountRepository.save(account);

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