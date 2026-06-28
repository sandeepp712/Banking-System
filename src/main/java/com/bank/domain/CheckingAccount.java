package com.bank.domain;

import java.util.List;
import java.util.Set;

public class CheckingAccount extends Account{
    private static final Set<ProductTier>  ALLOWED_TIERS= Set.of(
            ProductTier.BASIC_CHECKING,
            ProductTier.PREMIUM_CHECKING
    );

    public CheckingAccount(String accountNumber, Money initialBalance, List<Customer> owners,ProductTier productTier) {
        super(accountNumber, initialBalance, owners,productTier);
    }

    public CheckingAccount(String accountNumber, Money initialBalance, List<Customer> owners) {
        super(accountNumber,initialBalance,owners,ProductTier.BASIC_CHECKING);
    }

    @Override
    protected Money getDailyLimit() {
        return this.getProductTier().getDailyLimit();
    }

    @Override
    protected boolean isValidTier(ProductTier  productTier) {
        return ALLOWED_TIERS.contains(productTier);
    }
}