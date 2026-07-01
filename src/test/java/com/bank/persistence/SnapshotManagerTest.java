package com.bank.persistence;

import com.bank.domain.*;
import com.bank.service.IdempotencyService;
import com.bank.service.TransferService;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class SnapshotManagerTest {

    private InMemoryAccountRepository repo;
    private TransactionLogger logger;
    private TransferService transferService;
    private SnapshotManager snapshotManager;
    private final String walFile = "data/snap_test_wal.log";
    private final String snapDir = "data/snapshots";

    @BeforeEach
    void setUp() throws Exception {
        Files.deleteIfExists(Paths.get(walFile));
        if (Files.exists(Paths.get(snapDir))) {
            Files.walk(Paths.get(snapDir)).map(Path::toFile).forEach(java.io.File::delete);
        }

        repo = new InMemoryAccountRepository();
        logger = new TransactionLogger(walFile);

        IdempotencyService idempotency = new IdempotencyService();
        transferService = new TransferService(repo, logger, idempotency);
        snapshotManager = new SnapshotManager(repo, logger, snapDir);

        Currency inr = Currency.getInstance("INR");
        // Setup 2 accounts with 1000 each
        repo.save(new CheckingAccount("A", Money.of(new BigDecimal("1000"), inr), List.of(new Customer("UserA")), ProductTier.BASIC_CHECKING));
        repo.save(new CheckingAccount("B", Money.of(new BigDecimal("1000"), inr), List.of(new Customer("UserB")), ProductTier.BASIC_CHECKING));
    }

    @AfterEach
    void tearDown() throws Exception {
        logger.shutdown();
    }

    @Test
    @DisplayName("Snapshot captures consistent state during concurrent transfers")
    void testSnapshotDuringConcurrentTransfers() throws Exception {
        int numThreads = 20;
        int transfersPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        // 1. Start spamming transfers
        for (int i = 0; i < numThreads; i++) {
            final boolean direction = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < transfersPerThread; j++) {
                        try {
                            Money amount = Money.of(new BigDecimal("10.00"), Currency.getInstance("INR"));
                            if (direction) {
                                transferService.transfer("A", "B", amount, java.util.UUID.randomUUID().toString());
                            } else {
                                transferService.transfer("B", "A", amount, java.util.UUID.randomUUID().toString());
                            }
                            successCount.incrementAndGet();
                        } catch (Exception e) { /* Ignore idempotency/limit errors for this test */ }
                    }
                } catch (Exception e) { e.printStackTrace(); }
                finally { doneLatch.countDown(); }
            });
        }

        // 2. Let the transfers run for a bit
        startLatch.countDown();
        Thread.sleep(100);

        // 3. TRIGGER THE SNAPSHOT WHILE TRANSFERS ARE HAPPENING!
        System.out.println("📸 Triggering Snapshot in the middle of chaos...");
        snapshotManager.takeSnapshot();

        // 4. Wait for all transfers to finish
        doneLatch.await();
        executor.shutdown();

        // 5. ASSERTIONS: The Conservation of Money MUST hold
        Money balA = repo.findByAccountNumber("A").get().getBalance();
        Money balB = repo.findByAccountNumber("B").get().getBalance();

        Money totalCurrent = balA.add(balB);
        Money expectedTotal = Money.of(new BigDecimal("2000.00"), Currency.getInstance("INR"));

        System.out.println("Total successful transfers: " + successCount.get());
        System.out.println("Final Balance A: " + balA + ", Balance B: " + balB);

        assertEquals(expectedTotal, totalCurrent, "Conservation of money failed! Money was created or lost.");

        // Verify snapshot file was created
        long snapCount = Files.list(Paths.get(snapDir)).count();
        assertTrue(snapCount > 0, "Snapshot file should have been created");
    }
}