package com.bank.service;

import com.bank.domain.Account;
import com.bank.domain.Customer;
import com.bank.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class TransferServiceTest {

    @Test
    @DisplayName("Transfer amount a to b")
    void transferAmountAToB() {
        Currency currency = Currency.getInstance("USD");
        Money money = Money.of(new BigDecimal("100.00"), currency);

        Account account1 = new Account("Acc-1", money, List.of(new Customer("Amar")));
        Account account2 = new Account("Acc-2", money, List.of(new Customer("Vijay")));

        Money toTransfer = Money.of(new BigDecimal("10.00"), currency);

        TransferService transferService = new TransferService();
        transferService.transfer(account1, account2, toTransfer);

        Money expected = Money.of(new BigDecimal("110.00"), currency);
        assertEquals(expected, account2.getBalance());
    }


    @Test
    @DisplayName("Concurrent transfer must not be in deadlock")
    void concurrentTransferMustNotBeInDeadlock() throws InterruptedException {
        Currency currency = Currency.getInstance("USD");
        Money money = Money.of(new BigDecimal("1000.00"), currency);

        Account account1 = new Account("Acc-1", money, List.of(new Customer("Amar")));
        Account account2 = new Account("Acc-2", money, List.of(new Customer("Vijay")));

        Money totalBefore = account1.getBalance()
                .add(account2.getBalance());

        Money toTransfer = Money.of(new BigDecimal("10.00"), currency);

        int numofThreads = 40;
        int transfersPerThread = 10;
        TransferService transferService = new TransferService();

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
                            transferService.transfer(account1, account2, toTransfer);
                        } else {
                            transferService.transfer(account2, account1, toTransfer);
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

        Money totalAfter = account1.getBalance().add(account2.getBalance());

        assertEquals(totalAfter, totalBefore);
    }

}
