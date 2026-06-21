package com.bank.service;

import com.bank.domain.*;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;


public class TransferService {
    private final AccountRepository accountRepository;

    public TransferService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Transaction transfer(String fromId, String toId, Money amount, String idempotencyKey) {

        Objects.requireNonNull(amount);
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


        Objects.requireNonNull(from, "from account cannot be null");
        Objects.requireNonNull(to, "to account cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");


        //Sort the account number for locks
        List<Account> accounts = Arrays.asList(from, to);
        accounts.sort(Comparator.comparing(Account::getAccountNumber));

        Account firstAccount = accounts.get(0);
        Account secondAccount = accounts.get(1);

        firstAccount.lock();
        try {
            secondAccount.lock();
            try {
                from.debit(amount);
                to.credit(amount);

                accountRepository.save(firstAccount);
                accountRepository.save(secondAccount);
            } finally {
                secondAccount.unlock();
            }
        } finally {
            firstAccount.unlock();
        }

        return Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .fromAccountId(fromId)
                .toAccountId(toId)
                .amount(amount)
                .status(TransactionStatus.COMMITTED)
                .build();
    }
}
