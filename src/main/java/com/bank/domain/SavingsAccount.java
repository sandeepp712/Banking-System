package com.bank.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

public class SavingsAccount extends Account {
    private static final Money DAILY_LIMIT = Money.of(
            new BigDecimal("10000.00"),
            Currency.getInstance("INR")
    );

    public SavingsAccount(String account, Money initialBalance, List<Customer> owners) {
        super(account, initialBalance, owners);
    }

    @Override
    protected Money getDailyLimit() {
        return DAILY_LIMIT;
    }
}