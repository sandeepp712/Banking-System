package com.bank.domain;

import com.bank.domain.Exceptions.InsufficientFundsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Currency;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;


import static org.junit.jupiter.api.Assertions.*;

public class AccountTest {

    @Test
    @DisplayName("Stress test:50 threads debiting concurrently must not lose money")
    void testConcurrentDebitsAreThreadSafe() throws InterruptedException{
        //Setup: 1 Account with $1000
        Currency currency = Currency.getInstance("USD");
        Money initialBalance=Money.of(new  BigDecimal("1000.00"),currency);
        List<Customer> joint = List.of(
                new Customer("CUST-1")
        );

        Account account=new Account("ACC-1",initialBalance, joint);

        //Setup: 50 threads,each trying to debit $10
        int threadCount=50;
        Money debitAmount=Money.of(new  BigDecimal("10.00"),currency);

        ExecutorService executor=Executors.newFixedThreadPool(threadCount);

        //Act: Fire all threads at the exact same time
        for(int i=0;i<threadCount;i++){
            executor.submit(()->{
                try {
                    account.debit(debitAmount);
                }catch (InsufficientFundsException e){
                    throw e;
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        Money expectedBalance=Money.of(new  BigDecimal("500.00"),currency);

        assertEquals(expectedBalance,account.getBalance(),
                "Race conditions detected! Money was lost or created during concurrent debits.");
    }
}
