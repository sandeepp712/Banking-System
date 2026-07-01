package com.bank.persistence;

import com.bank.domain.Account;
import com.bank.domain.AccountRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class InMemoryAccountRepository implements AccountRepository {
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    // The Global Lock: Allows concurrent reads, but exclusive writes (for snapshots)
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();


    @Override
    public void save(Account account) {
        // Structural changes (adding a new account) require exclusive access
        rwLock.writeLock().lock();
        try {
            accounts.put(account.getAccountNumber(), account);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        return Optional.ofNullable(accounts.get(accountNumber));
    }

    @Override
    public Collection<Account> getAllAccounts() {
        rwLock.readLock().lock();
        try {
            return List.copyOf(accounts.values());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void acquiredSnapshotLock() {
        rwLock.writeLock().lock();
    }

    public void releasedSnapshotLock() {
        rwLock.writeLock().unlock();
    }

    public void acquiredReadLock() {
        rwLock.readLock().lock();
    }

    public void releasedReadLock() {
        rwLock.readLock().unlock();
    }
}