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

    public AccountService(AccountRepository accountRepository, TransactionLogger logger, IdempotencyService idempotencyService) {
        this.accountRepository = accountRepository;
        this.logger = logger;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Creates a new account and saves it to the repository.
     */
    public Account createAccount(String accountNo, Money initialBalance, List<Customer> owners, ProductTier productTier) {
        if (accountRepository.findByAccountNumber(accountNo).isPresent()) {
            throw new IllegalArgumentException("Account already exists : " + accountNo);
        }

        Account newAccount;

        switch (productTier) {
            case BASIC_CHECKING, PREMIUM_CHECKING -> {
                newAccount = new CheckingAccount(accountNo, initialBalance, owners);
            }
            case BASIC_SAVING, PREMIUM_SAVING -> {
                newAccount = new SavingsAccount(accountNo, initialBalance, owners);
            }
            default -> {
                throw new IllegalArgumentException("Unknown product tier : " + productTier);
            }
        }
        accountRepository.save(newAccount);
        return newAccount;
    }

    /**
     * To get the particular account is present or not
     *
     * @param id
     * @return
     */
    public Account getAccount(String id) {
        return accountRepository.findByAccountNumber(id).orElseThrow(() -> new IllegalArgumentException("Account not found : " + id));
    }

    /**
     * To return all account
     *
     * @return
     */
    public Collection<Account> getAllAccounts() {
        return accountRepository.getAllAccounts();
    }


    /**
     * Deposits money into an account.
     *
     * @return The Transaction record representing this deposit.
     */
    public Transaction deposit(String accountNumber, Money amount, String idempotencyKey) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found : " + accountNumber));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account status is not ACTIVE");
        }

        //1 Check the idempotency
        Optional<Transaction> existing = idempotencyService.getExisting(idempotencyKey);
        if (existing.isPresent()) {
            Transaction tx = existing.get();
            if (tx.getStatus() == TransactionStatus.COMMITTED) {
                return tx;
            } else if (tx.getStatus() == TransactionStatus.PENDING) {
                throw new IllegalStateException("Pending transaction already processing : " + tx.getTransactionId());
            } else if (tx.getStatus() == TransactionStatus.FAILED) {
                throw new IllegalStateException("Transaction failed : " + tx.getTransactionId());
            }
        }


        //Creating transaction
        Transaction pending = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .fromAccountId(SYSTEM_ACCOUNT)
                .toAccountId(accountNumber)
                .amount(amount)
                .timestamp(Instant.now())
                .status(TransactionStatus.PENDING)
                .build();


        //2 creating pending transaction in cache
        idempotencyService.put(idempotencyKey, pending);


        //3 Writing in WAL log
        try {
            logger.logTransaction(pending);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            //If logging fail,remove the cache and throw
            idempotencyService.remove(idempotencyKey);
            throw new RuntimeException("Failed to log Pending transaction");
        }


        //4 Apply the credit to self account
        account.credit(amount);


        //5 Create committed transaction
        Transaction completedTransaction = pending.commited();


        //6 Write committed transaction in WAL
        try {
            logger.logTransaction(completedTransaction);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Deposit succeeded, but logging failed due to system interruption.", e);
        }

        //7 update cache to committed
        idempotencyService.put(idempotencyKey, completedTransaction);

        //8 return completed transaction
        return completedTransaction;
    }

    /**
     * Withdraws money from an account.
     *
     * @return The Transaction record representing this withdrawal.
     */
    public Transaction withdraw(String accountNumber, Money amount, String idempotencyKey) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found : " + accountNumber));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account status is not ACTIVE");
        }

        Optional<Transaction> existing = idempotencyService.getExisting(idempotencyKey);
        if (existing.isPresent()) {
            Transaction tx = existing.get();
            if (tx.getStatus() == TransactionStatus.COMMITTED) {
                return tx;
            } else if (tx.getStatus() == TransactionStatus.PENDING) {
                throw new IllegalStateException("Pending transaction already processing : " + tx.getTransactionId());
            } else if (tx.getStatus() == TransactionStatus.FAILED) {
                throw new IllegalStateException("Transaction failed : " + tx.getTransactionId());
            }
        }

        Transaction tx = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .fromAccountId(accountNumber)
                .toAccountId(SYSTEM_ACCOUNT)
                .amount(amount)
                .timestamp(Instant.now())
                .status(TransactionStatus.PENDING)
                .build();

        idempotencyService.put(idempotencyKey, tx);

        try {
            logger.logTransaction(tx);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            idempotencyService.remove(idempotencyKey);
            throw new RuntimeException("Failed to log Pending transaction");
        }

        account.debit(amount);

        Transaction completedTransaction = tx.commited();

        try {
            logger.logTransaction(completedTransaction);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Deposit succeeded, but logging failed due to system interruption.", e);
        }

        idempotencyService.put(idempotencyKey, completedTransaction);

        return completedTransaction;
    }

}