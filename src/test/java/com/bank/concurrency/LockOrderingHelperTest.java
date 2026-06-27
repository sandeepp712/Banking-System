package com.bank.concurrency;


import com.bank.domain.Account;
import com.bank.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LockOrderingHelperTest {
    private Money amount;
    private Currency currency;

    @BeforeEach
    void setUp() {
        currency = Currency.getInstance("USD");
        amount = Money.of(new BigDecimal("1000.00"), currency);
    }

    @Test
    @DisplayName("Sort the account Number lexicographical")
    void sortAccountNumberLexicographical() {
        Account firstAccount = new Account("Acc-1", amount, new ArrayList<>());
        Account secondAccount = new Account("Acc-3", amount, new ArrayList<>());
        Account thirdAccount = new Account("Acc-2", amount, new ArrayList<>());

        //when
        List<Account> orderList = LockOrderingHelper.getOrderedAccounts(firstAccount, secondAccount, thirdAccount);

        //then
        assertEquals(3, orderList.size());
        assertEquals("Acc-2", orderList.get(1).getAccountNumber());
        assertEquals(orderList, new ArrayList<>(List.of(firstAccount, thirdAccount, secondAccount)));
    }

    @Test
    @DisplayName("Passing null argument in lock order should throw NullPointerException")
    void passNullArgumentInLockOrder() {
        Account validAccount = new Account("Acc-1", amount, new ArrayList<>());
        // Test null array
        assertThrows(NullPointerException.class,
                () -> LockOrderingHelper.getOrderedAccounts((Account[]) null),
                "Null array should throw NullPointerException");

        // Test null individual account
        assertThrows(NullPointerException.class,
                () -> LockOrderingHelper.getOrderedAccounts(validAccount, null),
                "Null account should throw NullPointerException");

        // Test all null accounts
        assertThrows(NullPointerException.class,
                () -> LockOrderingHelper.getOrderedAccounts(null, null, null),
                "Multiple null accounts should throw NullPointerException");
    }


    @Test
    @DisplayName("Empty accounts should return empty list")
    void emptyAccountsShouldReturnEmptyList() {
        // When
        List<Account> orderList = LockOrderingHelper.getOrderedAccounts();

        // Then
        assertTrue(orderList.isEmpty());
    }


    @Test
    @DisplayName("Sorting should not modify original array")
    void sortingShouldNotModifyOriginalArray() {
        // Given
        Account firstAccount = new Account("Acc-5", amount, new ArrayList<>());
        Account secondAccount = new Account("Acc-1", amount, new ArrayList<>());
        Account[] originalArray = {firstAccount, secondAccount};

        // When
        List<Account> orderList = LockOrderingHelper.getOrderedAccounts(originalArray);

        // Then - original array remains unchanged
        assertEquals("Acc-5", originalArray[0].getAccountNumber());
        assertEquals("Acc-1", originalArray[1].getAccountNumber());

        // But sorted list has different order
        assertEquals("Acc-1", orderList.get(0).getAccountNumber());
        assertEquals("Acc-5", orderList.get(1).getAccountNumber());
    }
}