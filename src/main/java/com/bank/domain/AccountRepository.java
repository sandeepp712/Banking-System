package com.bank.domain;

import java.util.Optional;

public interface AccountRepository {
    void save(Account account);
    Optional<Account> findByAccountNumber(String accountNumber);
}