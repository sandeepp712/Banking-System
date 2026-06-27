package com.bank.service;

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
    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();

    /**
     * Checks if a transaction with this key is process or not.
     * @param idempotencyKey
     * @return
     */
    public boolean isAlreadyProcessed(String idempotencyKey){
        return processedKeys.contains(idempotencyKey);
    }

    /**
     * Marks a transaction key as processed
     * @param idempotencyKey
     */
    public void markAsProcessed(String idempotencyKey){
        processedKeys.add(idempotencyKey);
    }

    // For testing cleanup
    public void clear() {
        processedKeys.clear();
    }

}