package com.bank.service;

import com.bank.domain.Transaction;
import com.bank.domain.TransactionStatus;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for managing idempotency keys to prevent duplicate processing.
 * Idempotency ensures that processing the same request multiple times produces
 * the same results as processing it once.
 */

public class IdempotencyService {
    /**
     * Prevents duplicate processing of transaction due to network retries.
     * Uses a thread-safe set to track processed idempotency.
     */
    private final Map<String, Transaction> transaction_cache = new ConcurrentHashMap<>();

    /**
     * Checks if a transaction with this key is process or not.
     * @param key
     * @return
     */
    public Optional<Transaction> getExisting(String key) {
        return Optional.ofNullable(transaction_cache.get(key));
    }


    /**
     * Marks a transaction key as processed
     * @param key
     */
    public void put(String key, Transaction transaction) {
        transaction_cache.put(key, transaction);
    }

    public void remove(String key) {
        transaction_cache.remove(key);
    }

    // For testing cleanup
    public boolean isCommited(String key) {
        Transaction transaction = transaction_cache.get(key);
        return transaction != null && transaction.getStatus()== TransactionStatus.COMMITTED;
    }

}