package com.bank.domain;

import java.util.List;
import java.util.Set;

public class SavingsAccount extends Account {
    private static final Set<ProductTier> ALLOWED_TIERS= Set.of(
            ProductTier.BASIC_SAVING,
            ProductTier.PREMIUM_SAVING
    );

    public SavingsAccount(String account, Money initialBalance, List<Customer> owners,ProductTier productTier) {
        super(account, initialBalance, owners,productTier);
    }

    public SavingsAccount(String account, Money initialBalance, List<Customer> owners) {
        super(account, initialBalance, owners,ProductTier.BASIC_SAVING);
    }

    @Override
    protected Money getDailyLimit() {
        return this.getProductTier().getDailyLimit();
    }

    @Override
    protected boolean isValidTier(ProductTier productTier){
        return ALLOWED_TIERS.contains(productTier);
    }
}