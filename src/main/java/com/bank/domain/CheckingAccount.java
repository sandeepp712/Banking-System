package com.bank.domain;

import com.bank.domain.Exceptions.DailyLimitExceededException;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

public class CheckingAccount extends Account{
    private static final Money DAILY_LIMIT=Money.of(
            new BigDecimal("5000.00"),
            Currency.getInstance("INR")
    );

    public CheckingAccount(String accountNumber, Money initialbalance, List<Customer> owners) {
        super(accountNumber, initialbalance, owners);
    }

    @Override
    protected Money getDailyLimit() {
        return DAILY_LIMIT;
    }
}