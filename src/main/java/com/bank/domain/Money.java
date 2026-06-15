package com.bank.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable Value Object representing a monetary amount.
 *
 * WHY IMMUTABLE?
 * 1. Thread-safe: Multiple threads can read the same Money object without locks.
 * 2. No side effects: Passing Money to a method guarantees it won't be changed.
 * 3. HashCode stability: Can be safely used as a HashMap key.
 */

public final class Money{           //Class is immutable

    //Rule 2 fields are private and final
    private final BigDecimal amount;
    private final Currency currency;

    //Rule 3 Constructors initializes all state. No setters
    Money(BigDecimal amount, Currency currency){
        //validate inputs
        if(amount == null){
            throw new IllegalArgumentException("Amount can't be null");
        }

        if(currency == null){
            throw new IllegalArgumentException("Currency can't be null");
        }

        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.currency = currency;
    }

    /**
     * Factory method for convenience.
     * Note: We avoid double in the public API to prevent floating-point errors.
     */
    public static Money of(BigDecimal amount, Currency currency){
        return new Money(amount, currency);
    }

    public Money add(Money otherMoney){
        checkConcurrency(otherMoney);
        return new Money(this.amount.add(otherMoney.amount), this.currency);
    }

    public Money subtract(Money otherMoney){
        checkConcurrency(otherMoney);
        return new Money(this.amount.subtract(otherMoney.amount), this.currency);
    }

    public Money multiply(BigDecimal multiplier){
        if(multiplier == null){
            throw new IllegalArgumentException("Multiplier can't be null");
        }
        return new Money(this.amount.multiply(multiplier).setScale(2,RoundingMode.HALF_UP), this.currency);
    }

    public boolean isNegative(){
        return this.amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isZero(){
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }


    // --- Getters (Return immutable types, so safe to expose) ---
    public BigDecimal getAmount(){
        return this.amount;     //BigDecimal is immutable, safe to return
    }

    public Currency getCurrency(){
        return this.currency;   //Currency is immutable, safe to return
    }

    //Helper method
    public void checkConcurrency(Money otherMoney){
        if(!this.currency.equals(otherMoney.currency)){
            throw new IllegalStateException(
                    "Can't operate on different currency"+this.currency+" vs "+otherMoney.currency
            );
        }
    }


    //Standard Object Methods
    @Override
    public String toString(){
        return currency.getSymbol()+" "+amount.toPlainString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public boolean equals(Object o){
        if(this==o) return true;
        if(!(o instanceof Money money)) return false;
        return amount.compareTo(money.amount) == 0 && Objects.equals(currency, money.currency);
    }

    public boolean isPositive() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }
}

