package com.bank.domain;

import java.util.Collection;
import java.util.Optional;

public interface AccountRepository {
    void save(Account account);
    Optional<Account> findByAccountNumber(String accountNumber);
    Collection<Account> getAllAccounts();
}