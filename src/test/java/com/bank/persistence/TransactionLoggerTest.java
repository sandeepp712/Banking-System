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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionLoggerTest {
    private TransactionLogger logger;
    private final String testLogFile = "data/transaction.log";

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

    @Test
    @DisplayName("Logger restart preserve preious data sequence number and continue appending new logs")
    void testLoggerRestartPreservesDataAndSequence() throws Exception {

        // =============================================
        // PHASE 1: First logger session — 10 transactions
        // =============================================
        TransactionLogger logger1 = new TransactionLogger(testLogFile.toString());

        for (int i = 1; i <= 10; i++) {
            Transaction tx = Transaction.builder()
                    .fromAccountId("SYSTEM")
                    .toAccountId("Acc-" + (i % 5 + 1))
                    .amount(Money.of(new BigDecimal("100.00"), Currency.getInstance("INR")))
                    .status(TransactionStatus.COMMITTED)
                    .idempotencyKey(UUID.randomUUID().toString())
                    .build();
            logger1.logTransaction(tx);
        }

        // Shut down first logger — this flushes and closes the file
        logger1.shutdown();



        // =============================================
        // PHASE 2: Second logger session — 10 more transactions
        // =============================================
        TransactionLogger logger2 = new TransactionLogger(testLogFile.toString());

        for (int i = 11; i <= 20; i++) {
            Transaction tx = Transaction.builder()
                    .fromAccountId("SYSTEM")
                    .toAccountId("Acc-" + (i % 5 + 1))
                    .amount(Money.of(new BigDecimal("100.00"), Currency.getInstance("INR")))
                    .status(TransactionStatus.COMMITTED)
                    .idempotencyKey(UUID.randomUUID().toString())
                    .build();
            logger2.logTransaction(tx);
        }

        // Shut down second logger
        logger2.shutdown();

        // =============================================
        // ASSERTIONS: Prove the math is correct
        // =============================================
        List<String> lines = Files.readAllLines(Paths.get(testLogFile.toString()));

        // 1. We should have exactly 20 lines
        assertEquals(20, lines.size(), "File should have exactly 20 transactions");

        // 2. The first line should be sequence 1
        assertTrue(lines.get(0).contains("\"sequence_No\":\"1\""), "First transaction should be seq 1");

        // 3. The last line should be sequence 20
        assertTrue(lines.get(19).contains("\"sequence_No\":\"20\""), "Last transaction should be seq 20");

        System.out.println("✅ Logger successfully resumed counter after restart!");
    }
}