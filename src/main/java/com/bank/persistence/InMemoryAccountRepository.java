package com.bank.persistence;

import com.bank.domain.Account;
import com.bank.domain.AccountRepository;

import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAccountRepository implements AccountRepository{
    private final Map<String,Account> accounts=new ConcurrentHashMap<>();

    @Override
    public void save(Account account) {
        accounts.put(account.getAccountNumber(),account);
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        return Optional.ofNullable(accounts.get(accountNumber));
    }
}