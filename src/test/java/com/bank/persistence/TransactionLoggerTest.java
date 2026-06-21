package com.bank.persistence;

import com.bank.domain.Money;
import com.bank.domain.Transaction;
import com.bank.domain.TransactionStatus;
import com.bank.persistence.TransactionLogger;
import org.junit.jupiter.api.*;


import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionLoggerTest {
    private TransactionLogger logger;
    private final String testLogFile = "target/data/test_transaction_log.log";

    @BeforeEach
    void setUp() throws Exception {
        Files.deleteIfExists(Paths.get(testLogFile));
        logger = new TransactionLogger(testLogFile);
    }

    @AfterEach
    void tearDown() throws Exception {
        logger.shutdown();
    }


    @Test
    @DisplayName("Concurrent log writes from 50 threads must not corrupt file")
    void testConcurrentLogWrites() throws Exception {
        int numThreads = 50;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch countDownLatch = new CountDownLatch(numThreads);

        // ✅ FIX 3: Track if any thread failed
        AtomicReference<Throwable> threadError = new AtomicReference<>();

        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    Transaction tx = Transaction.builder()
                            .fromAccountId("Acc-1")
                            .toAccountId("Acc-2")
                            .amount(Money.of(new BigDecimal("100.22"), Currency.getInstance("INR")))
                            .status(TransactionStatus.COMMITTED)
                            .idempotencyKey(UUID.randomUUID().toString())
                            .build();

                    logger.logTransaction(tx);
                } catch (Throwable t) {
                    threadError.compareAndSet(null,t);
                } finally {
                    countDownLatch.countDown();
                }
            });
        }

        countDownLatch.await();
        Thread.sleep(500);

        // ✅ Fail the test immediately if a thread threw an exception
        if (threadError.get() != null) {
            fail("A thread failed during logging: " + threadError.get().getMessage(), threadError.get());
        }

        long lineCount = Files.lines(Paths.get(testLogFile)).count();
        assertEquals(50, lineCount, "File should have been written to log file");
    }
}