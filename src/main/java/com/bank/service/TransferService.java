package com.bank.service;

import com.bank.concurrency.LockOrderingHelper;
import com.bank.domain.*;
import com.bank.persistence.TransactionLogger;
import com.bank.service.Exceptions.DuplicateTransactionException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;


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

        if(idempotencyService.isAlreadyProcessed(idempotencyKey)){
            throw new DuplicateTransactionException("The idempotency key has already been processed"+idempotencyKey);
        }


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




        //use of lockOrdering helper
        List<Account> orderedAccount=LockOrderingHelper.getOrderedAccounts(from,to);

        Account firstAccount = orderedAccount.get(0);
        Account secondAccount = orderedAccount.get(1);

        firstAccount.lock();
        try {
            secondAccount.lock();
            try {
                from.debit(amount);
                to.credit(amount);

                accountRepository.save(firstAccount);
                accountRepository.save(secondAccount);

                //Mark as processed Only after the money has successfully moved
                idempotencyService.markAsProcessed(idempotencyKey);
            }finally {
                secondAccount.unlock();
            }
        } finally {
            firstAccount.unlock();
        }


        Transaction tx = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .fromAccountId(fromId)
                .toAccountId(toId)
                .amount(amount)
                .status(TransactionStatus.COMMITTED)
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