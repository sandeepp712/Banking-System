package com.bank.service;

import com.bank.domain.Account;
import com.bank.domain.Money;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class TransferService {


    public void transfer(Account from, Account to, Money amount) {
        Objects.requireNonNull(from, "from account cannot be null");
        Objects.requireNonNull(to, "to account cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");

        if (amount.isNegative() || amount.isZero()) {
            throw new IllegalArgumentException("amount cannot be negative");
        }

        if (from.getAccountNumber().equals(to.getAccountNumber())) {
            throw new IllegalArgumentException("Same accounts transfer are not allowed currently");
        }

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
            } finally {
                secondAccount.unlock();
            }
        } finally {
            firstAccount.unlock();
        }
    }

}
