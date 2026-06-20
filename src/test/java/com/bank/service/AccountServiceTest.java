package com.bank.service;

import com.bank.domain.*;
import com.bank.domain.Exceptions.InsufficientFundsException;
import com.bank.persistence.InMemoryAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AccountServiceTest {
    private AccountService accountService;
    private AccountRepository accountRepository;
    private Currency usd;
    private List<Customer> owners;

    @BeforeEach
    void setUp() {
        accountRepository = new InMemoryAccountRepository();
        accountService = new AccountService(accountRepository);
        usd = Currency.getInstance("USD");
        owners = List.of(new Customer("TestUser"));
    }

    // ------------------------------------------------------------
    // 1. CREATE ACCOUNT TESTS
    // ------------------------------------------------------------
    @Test
    @DisplayName("Create account successfully")
    void testCreateAccountSuccessfully() {
        String accountNo = "testAccountNo";
        Money initialBalance = Money.of(new BigDecimal("100.00"), usd);

        //When
        Account account = accountService.createAccount(accountNo, initialBalance, owners);

        //Then
        assertNotNull(account);
        assertEquals(accountNo, account.getAccountNumber());
        assertEquals(initialBalance, account.getBalance());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());

        Account found = accountService.getAccount(account.getAccountNumber());
        assertEquals(account, found);
    }


    @Test
    @DisplayName("Create account fails when account number already exist")
    void testCreateAccountFailsWhenAccountNumberAlreadyExist() {
        String accountNo = "testAccountNo";
        Money initialBalance = Money.of(new BigDecimal("100.00"), usd);

        //when
        accountService.createAccount(accountNo, initialBalance, owners);

        //then
        assertThrows(IllegalArgumentException.class, () -> accountService.createAccount(accountNo, initialBalance, owners), "" +
                "Account number already exists");

    }


    @Test
    @DisplayName("Get existing account by ID")
    void testGetAccountFound() {
        // Given
        String accountNo = "ACC-456";
        Money initialBalance = Money.of(new BigDecimal("50.00"), usd);
        accountService.createAccount(accountNo, initialBalance, owners);

        // When
        Account account = accountService.getAccount(accountNo);

        // Then
        assertNotNull(account);
        assertEquals(accountNo, account.getAccountNumber());
    }

    @Test
    @DisplayName("Deposit successfully adds money to account and returns committed transaction")
    void testDepositSuccess() {
        // Given
        String accountNo = "ACC-DEP";
        Money initialBalance = Money.of(new BigDecimal("100.00"), usd);
        accountService.createAccount(accountNo, initialBalance, owners);

        Money depositAmount = Money.of(new BigDecimal("50.00"), usd);
        String idempotencyKey = UUID.randomUUID().toString();

        // When
        Transaction transaction = accountService.deposit(accountNo, depositAmount, idempotencyKey);

        // Then: Check transaction receipt
        assertNotNull(transaction);
        assertEquals(AccountService.SYSTEM_ACCOUNT, transaction.getFromAccountId());
        assertEquals(accountNo, transaction.getToAccountId());
        assertEquals(depositAmount, transaction.getAmount());
        assertEquals(TransactionStatus.COMMITTED, transaction.getStatus());
        assertEquals(idempotencyKey, transaction.getIdempotencyKey());

        // Then: Check account balance
        Account updatedAccount = accountService.getAccount(accountNo);
        Money expectedBalance = Money.of(new BigDecimal("150.00"), usd);
        assertEquals(expectedBalance, updatedAccount.getBalance());
    }


    @Test
    @DisplayName("Withdraw successfully withdrawmoney from account and return commited transaction")
    void testWithdrawSuccess() {
        String accountNo = "ACC-456";
        Money initialBalance = Money.of(new BigDecimal("100.00"), usd);
        accountService.createAccount(accountNo, initialBalance, owners);

        Money withdrawAmount = Money.of(new BigDecimal("50.00"), usd);
        String idempotencyKey = UUID.randomUUID().toString();

        //When
        Transaction tx= accountService.withdraw(accountNo, withdrawAmount, idempotencyKey);

        //Then
        assertNotNull(tx);
        assertEquals(accountNo, tx.getFromAccountId());
        assertEquals(AccountService.SYSTEM_ACCOUNT, tx.getToAccountId());
        assertEquals(withdrawAmount, tx.getAmount());

        //Then:check account balance
        Account updatedAccount = accountService.getAccount(accountNo);
        Money expectedBalance = Money.of(new BigDecimal("50.00"), usd);
        assertEquals(expectedBalance, updatedAccount.getBalance());
    }

    @Test
    @DisplayName("Deposit fails when account does not exist")
    void testDepositAccountNotFound() {
        Money amount = Money.of(new BigDecimal("10.00"), usd);
        String idempotencyKey = UUID.randomUUID().toString();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> accountService.deposit("NON_EXISTENT", amount, idempotencyKey));

        assertTrue(exception.getMessage().contains("Account not found"));
    }

    @Test
    @DisplayName("Withdraw fails with InsufficientFundsException when balance is too low")
    void testWithdrawInsufficientFunds() {
        // Given
        String accountNo = "ACC-LOW";
        Money initialBalance = Money.of(new BigDecimal("50.00"), usd);
        accountService.createAccount(accountNo, initialBalance, owners);

        Money withdrawAmount = Money.of(new BigDecimal("100.00"), usd);
        String idempotencyKey = UUID.randomUUID().toString();

        // When / Then
        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> accountService.withdraw(accountNo, withdrawAmount, idempotencyKey));

        assertTrue(exception.getMessage().contains("Insufficient balance"));

        // Verify balance remained unchanged
        Account unchangedAccount = accountService.getAccount(accountNo);
        assertEquals(initialBalance, unchangedAccount.getBalance());
    }

}