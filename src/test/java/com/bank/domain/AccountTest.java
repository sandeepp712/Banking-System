package com.bank.domain;

import com.bank.domain.Exceptions.AccountFrozenException;
import com.bank.domain.Exceptions.DailyLimitExceededException;
import com.bank.domain.Exceptions.InsufficientFundsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;


import static org.junit.jupiter.api.Assertions.*;

public class AccountTest {

    @Test
    @DisplayName("Debit subtracts the specified amount from the account balance")
    void testDebitAmount() {
        //Setup: 1 Account with $1000
        Currency currency = Currency.getInstance("INR");
        Money initialBalance = Money.of(new BigDecimal("500.00"), currency);
        List<Customer> owners = List.of(
                new Customer("Amar")
        );

        Account account = new CheckingAccount("Acc-1", initialBalance, owners, ProductTier.BASIC_CHECKING);
        Money ToDebit = Money.of(new BigDecimal("50.00"), currency);

        account.debit(ToDebit);

        //To be expected after debit
        Money expectedBalance = Money.of(new BigDecimal("450.00"), currency);

        //testing the expected and getting after debit operations
        assertEquals(expectedBalance, account.getBalance());
    }

    @Test
    @DisplayName("Credit balance to a sing;e account.")
    void testCreditBalance() {
        Currency currency = Currency.getInstance("INR");
        Money initialBalance = Money.of(new BigDecimal("10.00"), currency);
        List<Customer> owners = List.of(
                new Customer("Tarun")
        );
        Account account = new CheckingAccount("Acc-1", initialBalance, owners, ProductTier.BASIC_CHECKING);

        Money ToCredit = Money.of(new BigDecimal("500.00"), currency);

        account.credit(ToCredit);


        Money expectedBalance = Money.of(new BigDecimal("510.00"), currency);
        assertEquals(expectedBalance, account.getBalance());
    }

    @Test
    @DisplayName("Check the validation to debit from low balance account")
    void testDebitAmountLowBalance() {
        Currency currency = Currency.getInstance("INR");
        Money initialBalance = Money.of(new BigDecimal("500.00"), currency);
        List<Customer> owners = List.of(
                new Customer("Tarun")
        );
        Money ToDebit = Money.of(new BigDecimal("1000.00"), currency);

        Account account = new CheckingAccount("Acc-3", initialBalance, owners, ProductTier.BASIC_CHECKING);

        assertThrows(InsufficientFundsException.class, () -> account.debit(ToDebit), "Insufficient funds in the your account no." + account.getAccountNumber());
        assertEquals(initialBalance, account.getBalance(), "Balance should remain unchanged after failed debit.");
    }

    @Test
    @DisplayName("Debit fails with AccountFrozenException when account is frozen")
    void testDebitFailsWhenAccountFrozen() {
        Currency currency = Currency.getInstance("INR");
        Money initialBalance = Money.of(new BigDecimal("500.00"), currency);
        List<Customer> owners = List.of(
                new Customer("Tarun")
        );

        Account account = new CheckingAccount("Acc-1", initialBalance, owners, ProductTier.BASIC_CHECKING);
        account.freeze();

        //Money to debit from account
        Money ToDebit = Money.of(new BigDecimal("501.00"), currency);

        assertThrows(AccountFrozenException.class, () -> account.debit(ToDebit), "" +
                "Your account is frozen");

        assertEquals(initialBalance, account.getBalance());
    }


    @Test
    @DisplayName("Checking Account should reject withdrawal exceeding daily limits")
    void testDailyLimit() {
        Currency currency = Currency.getInstance("INR");
        Account acc = new CheckingAccount("Acc-1", Money.of(new BigDecimal("10000.00"), currency), List.of(), ProductTier.BASIC_CHECKING);

        acc.debit(Money.of(new BigDecimal("4000.00"), currency));
        acc.debit(Money.of(new BigDecimal("1000.00"), currency));

        assertThrows(DailyLimitExceededException.class, () -> acc.debit(Money.of(new BigDecimal("110.00"), currency)));
    }

    @Test
    @DisplayName("Stress test:50 threads debiting concurrently must not lose money")
    void testConcurrentDebitsAreThreadSafe() throws InterruptedException {
        //Setup: 1 Account with $1000
        Currency currency = Currency.getInstance("INR");
        Money initialBalance = Money.of(new BigDecimal("10000.00"), currency);
        List<Customer> owners = List.of(
                new Customer("Sand")
        );

        Account account = new CheckingAccount("Acc-1", initialBalance, owners, ProductTier.BASIC_CHECKING);

        // Exception throw by thread
        AtomicReference<Throwable> threadError = new AtomicReference<>();

        int numberOfThreads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        //Debit amount
        Money debitamount = Money.of(new BigDecimal("10.00"), currency);

        //The starting gun latch
        CountDownLatch readyLatch = new CountDownLatch(1);
        //The Finish Line latch
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    readyLatch.await();
                    account.debit(debitamount);
                } catch (Throwable t) {
                    threadError.compareAndSet(null, t);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        readyLatch.countDown();
        finishLatch.await();

        executor.shutdown();

        if (threadError.get() != null) {
            fail("A thread failed during concurrent execution: " + threadError.get().getMessage(), threadError.get());
        }

        Money expectedBalance = Money.of(new BigDecimal("9000.00"), currency);
        assertEquals(expectedBalance, account.getBalance(),
                "Balance mismatch after 100 debits of 10.Expected $9000,got " + account.getBalance());
    }

    @Test
    @DisplayName("Account upgrade requires minimum balance")
    void testAccountUpgrade() throws InsufficientFundsException {
        Money initialBalance = Money.of(new BigDecimal("52000.00"), Currency.getInstance("INR"));
        Account account = new SavingsAccount("Acc-1", initialBalance, List.of(), ProductTier.BASIC_SAVING);

        //When: try to upgrade with low balance
        //then: should throw error
        assertThrows(IllegalArgumentException.class, () ->
                account.upgradeTier(ProductTier.PREMIUM_SAVING)
        );

        //when credit amount to reach min 1,00,000
        account.credit(Money.of(new BigDecimal("65000.00"), Currency.getInstance("INR")));

        //Then
        assertDoesNotThrow(() ->
                account.upgradeTier(ProductTier.PREMIUM_SAVING)
        );

        assertEquals(ProductTier.PREMIUM_SAVING, account.getProductTier());
    }


    @Test
    @DisplayName("Changing the Account from Checking to saving")
    void testAccountFromCheckingToSaving() {
        Money initialBalance = Money.of(new BigDecimal("2000.00"), Currency.getInstance("INR"));
        Account acc1 = new CheckingAccount("Acc-1", initialBalance, List.of(), ProductTier.BASIC_CHECKING);
        Account acc2 = new SavingsAccount("Acc-1", initialBalance, List.of(), ProductTier.BASIC_SAVING);


        //When: try to change account from checking to saving
        //then: should throw error
        assertThrows(IllegalArgumentException.class, () ->
                new CheckingAccount("Acc-1", initialBalance, List.of(), ProductTier.BASIC_SAVING)
        );

        //When: try to change account from saving to checking
        //then: should throw error
        assertThrows(IllegalArgumentException.class, () ->
                new SavingsAccount("Acc-2", initialBalance, List.of(), ProductTier.PREMIUM_CHECKING)
        );


        //When: try to upgrade to other account
        assertThrows(IllegalArgumentException.class, () ->
                acc1.upgradeTier(ProductTier.PREMIUM_SAVING)
        );

        //When:try to upgrad to other account
        assertThrows(IllegalArgumentException.class, () ->
                acc2.upgradeTier(ProductTier.PREMIUM_CHECKING)
        );
    }
}


