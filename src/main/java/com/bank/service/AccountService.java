package com.bank.service;

import com.bank.domain.*;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

public class AccountService {
    private final AccountRepository accountRepository;

    public static final String SYSTEM_ACCOUNT = "SYSTEM";

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
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

        return Transaction.builder()
                .fromAccountId(SYSTEM_ACCOUNT)
                .toAccountId(accountNumber)
                .amount(amount)
                .status(TransactionStatus.COMMITTED)
                .idempotencyKey(idempotencyKey)
                .build();

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

        return Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .fromAccountId(accountNumber)
                .toAccountId(AccountService.SYSTEM_ACCOUNT)
                .amount(amount)
                .status(TransactionStatus.COMMITTED)
                .build();
    }

}