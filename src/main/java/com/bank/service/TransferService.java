package com.bank.service;

import com.bank.concurrency.LockOrderingHelper;
import com.bank.domain.*;
import com.bank.persistence.TransactionLogger;
import com.bank.service.Exceptions.DuplicateTransactionException;

import javax.management.RuntimeMBeanException;
import java.time.Instant;
import java.util.*;


public class TransferService {
    private final AccountRepository accountRepository;
    private final TransactionLogger logger;
    private final IdempotencyService idempotencyService;

    public TransferService(AccountRepository accountRepository, TransactionLogger logger,IdempotencyService idempotencyService) {
        this.accountRepository = accountRepository;
        this.logger = logger;
        this.idempotencyService=idempotencyService;
    }

    public Transaction transfer(String fromId, String toId, Money amount, String idempotencyKey) {
        Objects.requireNonNull(fromId, "from account cannot be null");
        Objects.requireNonNull(toId, "to account cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(idempotencyKey,"idempotencyKey cannot be null");

        if (amount.isNegative() || amount.isZero()) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("Cannot transfer from same account");
        }

        // 2. Fetch accounts
        Account from = accountRepository.findByAccountNumber(fromId)
                .orElseThrow(() -> new IllegalArgumentException("From account not found"));
        Account to = accountRepository.findByAccountNumber(toId)
                .orElseThrow(() -> new IllegalArgumentException("To account not found"));

        Optional<Transaction> existing = idempotencyService.getExisting(idempotencyKey);
        if (existing.isPresent()) {
            Transaction tx = existing.get();
            if(tx.getStatus()==TransactionStatus.COMMITTED){
                return tx;
            } else if(tx.getStatus() == TransactionStatus.PENDING){
                throw new IllegalStateException("Transaction has already been processing. Please wait.");
            } else if (tx.getStatus()==TransactionStatus.FAILED) {
                throw new IllegalStateException("Transaction attempt failed. Please try again later.");
            }
        }

        //use of lockOrdering helper
        List<Account> orderedAccount=LockOrderingHelper.getOrderedAccounts(from,to);

        Account firstAccount = orderedAccount.get(0);
        Account secondAccount = orderedAccount.get(1);

        Transaction pending=Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .fromAccountId(fromId)
                .toAccountId(toId)
                .amount(amount)
                .timestamp(Instant.now())
                .status(TransactionStatus.PENDING)
                .build();

        idempotencyService.put(idempotencyKey,pending);

        try {
            logger.logTransaction(pending);
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
            //If logging fails,remove from cache and throw
            idempotencyService.remove(idempotencyKey);
            throw new RuntimeException("Failed to log Pending transaction");
        }

        //Apply business logic with locking
        firstAccount.lock();
        try {
            secondAccount.lock();
            try {
                from.debit(amount);
                to.credit(amount);
            }finally {
                secondAccount.unlock();
            }
        } finally {
            firstAccount.unlock();
        }

        // Write commited transaction to WAL
        Transaction committed=pending.commited();


        try {
            logger.logTransaction(committed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Deposit succeeded, but logging failed due to system interruption.", e);
        }

        // update cache to committed
        idempotencyService.put(idempotencyKey,committed);

        return committed;
    }
}