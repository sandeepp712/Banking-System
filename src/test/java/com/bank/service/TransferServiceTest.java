package com.bank.service;

import com.bank.domain.*;
import com.bank.persistence.InMemoryAccountRepository;
import com.bank.persistence.TransactionLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class TransferServiceTest {

    private TransferService transferService;
    private AccountRepository repository;
    private Currency usd;


    @BeforeEach
    void setUp() throws IOException {
        repository = new InMemoryAccountRepository();
        TransactionLogger logger=new TransactionLogger("data/transaction.log");
        transferService = new TransferService(repository,logger); // ✅ Inject repository
        usd = Currency.getInstance("USD");

        // Setup standard accounts for testing
        repository.save(new Account("Acc-1", Money.of(new BigDecimal("1000.00"), usd), List.of(new Customer("Amar"))));
        repository.save(new Account("Acc-2", Money.of(new BigDecimal("1000.00"), usd), List.of(new Customer("Vijay"))));
    }

    @Test
    @DisplayName("Transfer amount a to b")
    void transferAmountAToB() {
        Money toTransfer = Money.of(new BigDecimal("10.00"), usd);
        String idempotencyKey = UUID.randomUUID().toString();

        // transfer a to b when
        Transaction tx=transferService.transfer("Acc-1","Acc-2",toTransfer,idempotencyKey);

        //Then
        assertEquals("Acc-1",tx.getFromAccountId());
        assertEquals("Acc-2",tx.getToAccountId());
        assertEquals(TransactionStatus.COMMITTED,tx.getStatus());


        //verify balance
        assertEquals(Money.of(new BigDecimal("990.00"),usd),repository.findByAccountNumber("Acc-1").get().getBalance());
        assertEquals(Money.of(new BigDecimal("1010.00"),usd),repository.findByAccountNumber("Acc-2").get().getBalance());
    }

    @Test
    @DisplayName("Transfer fails if source account does not exist")
    void transferFailsIfSourceAccountDoesNotExist() {
        Money toTransfer = Money.of(new BigDecimal("10.00"), usd);
        String idempotencyKey = UUID.randomUUID().toString();

        assertThrows(IllegalArgumentException.class, () -> transferService.transfer("Fake-1","Acc-2",toTransfer,idempotencyKey));
    }


    @Test
    @DisplayName("Concurrent transfer must not be in deadlock")
    void concurrentTransferMustNotBeInDeadlock() throws InterruptedException {
        Money toTransfer = Money.of(new BigDecimal("10.00"), usd);
        Money totalBefore=repository.findByAccountNumber("Acc-1").get().getBalance()
                .add(repository.findByAccountNumber("Acc-2").get().getBalance());

        int numofThreads = 40;
        int transfersPerThread = 10;

        //Executor service
        ExecutorService executor = Executors.newFixedThreadPool(numofThreads);

        CountDownLatch readyLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numofThreads);

        for (int i = 0; i < numofThreads; i++) {
            final boolean direction = (i % 2 == 0); // Even threads: A->B, Odd threads: B->A
            executor.submit(() -> {
                try {
                    readyLatch.await();
                    for (int j = 0; j < transfersPerThread; j++) {
                        if (direction) {
                            // This is the DEADLOCK TRAP: A->B and B->A happening simultaneously
                            transferService.transfer("Acc-1","Acc-2",toTransfer,UUID.randomUUID().toString());
                        } else {
                            transferService.transfer("Acc-2","Acc-1",toTransfer,UUID.randomUUID().toString());
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.countDown();
        doneLatch.await();

        executor.shutdown();

        Money totalAfter = repository.findByAccountNumber("Acc-1").get().getBalance()
                .add(repository.findByAccountNumber("Acc-2").get().getBalance());

        assertEquals(totalAfter, totalBefore);
    }

}
