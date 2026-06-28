package com.bank.domain;

import java.math.BigDecimal;
import java.util.Currency;

public enum ProductTier{
    BASIC_CHECKING(
            "Basic Checking",
            //No min Balance
            Money.of(BigDecimal.ZERO,Currency.getInstance("INR")),
            //Daily Limit
            Money.of(new BigDecimal("5000.00"),Currency.getInstance("INR"))
    ),
    PREMIUM_CHECKING(
            "Premium Checking",
            //No min Balance
            Money.of(new BigDecimal("100000.00"),Currency.getInstance("INR")),
            //Daily Limit
            Money.of(new BigDecimal("25000.00"),Currency.getInstance("INR"))
    ),
    BASIC_SAVING(
            "Basic saving",
            //No min Balance
            Money.of(BigDecimal.ZERO,Currency.getInstance("INR")),
            //Daily Limit
            Money.of(new BigDecimal("10000.00"),Currency.getInstance("INR"))
    ),
    PREMIUM_SAVING(
            "Premium saving",
            //No min Balance
            Money.of(new BigDecimal("100000.00"),Currency.getInstance("INR")),
            //Daily Limit
            Money.of(new BigDecimal("20000.00"),Currency.getInstance("INR"))
    );

    private String displayName;
    private Money minBalance;
    private Money dailyLimit;

    ProductTier(String displayName, Money minBalance, Money dailyLimit) {
        this.displayName=displayName;
        this.minBalance=minBalance;
        this.dailyLimit=dailyLimit;
    }

    public String getDisplayName() {
        return displayName;
    }
    public Money getMinBalance() {
        return minBalance;
    }
    public Money getDailyLimit() {
        return dailyLimit;
    }
}