package com.bank.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest{
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency INR = Currency.getInstance("INR");

    //Helper
    private Money usd(String amount){
        return Money.of(new BigDecimal(amount),USD);
    }

    private Money inr(String amount){
        return Money.of(new BigDecimal(amount),INR);
    }

    private Money euro(String amount){
        return Money.of(new BigDecimal(amount),EUR);
    }


    // ---------- Construction tests ----------
    @Test
    @DisplayName("Constructors sets scale to 2 and rounds HALF_UP")
    void testConstructorsScalingAndRoundsHalfUp(){
        Money a=usd("100.105");
        assertEquals(new BigDecimal("100.11"), a.getAmount() );

        Money b=usd("45.10561");
        assertEquals(new BigDecimal("45.11"), b.getAmount() );

        Money c=inr("100");
        assertEquals(new BigDecimal("100.00"), c.getAmount() );
    }

    @Test
    @DisplayName("Factory method of() return the same as constructor")
    void testFactoryMethod(){
        Money byConstructor = new Money(new BigDecimal("10.00"), USD);
        Money byFactory = Money.of(new BigDecimal("10.00"), USD);
        assertEquals(byConstructor, byFactory);

        Money byConstructor2 = new Money(new BigDecimal("10.1"), USD);
        Money byFactory2 = Money.of(new BigDecimal("10"), USD);
        assertNotEquals(byConstructor2, byFactory2);
    }


    //Arithmetic tests
    @Test
    @DisplayName("add() returns new Money with sum")
    void testAdd(){
        Money a=new Money(new BigDecimal("100"),EUR);
        Money b=new Money(new BigDecimal("300"),EUR);

        assertEquals(Money.of(new BigDecimal("400"),EUR),a.add(b));
        assertNotSame(a,a.add(b));
        assertNotSame(a,b);
    }

    @Test
    @DisplayName("subtract() returns new Money with difference")
    void testSubtractMoney(){
        Money a=new Money(new BigDecimal("100"),INR);
        Money b=new Money(new BigDecimal("300"),INR);
        assertEquals(Money.of(new BigDecimal("-200"),INR),a.subtract(b));
    }


    @Test
    @DisplayName("multiply() returns new Money with multiplication upto 2 decimal")
    void testMultiply(){
        Money a=usd("100");
        Money product = a.multiply(new BigDecimal("0.33333"));

        assertEquals(new BigDecimal("33.33"),product.getAmount());

        Money zeroProduct=a.multiply(new BigDecimal("0"));
        assertEquals(new BigDecimal("0.00"),zeroProduct.getAmount());
    }

    @Test
    @DisplayName("multiply() rejects null multiplier")
    void testMultiplyNull() {
        Money amount = usd("10.00");
        assertThrows(IllegalArgumentException.class, () -> amount.multiply(null));
    }

    @Test
    @DisplayName("Arithmetic across diff currency throws exception")
    void testCurrencyMismatch() {
        Money UserA=Money.of(new BigDecimal("100"),USD);
        Money UserB=Money.of(new BigDecimal("300"),EUR);

        assertThrows(IllegalStateException.class, () -> UserA.add(UserB));
        assertThrows(IllegalStateException.class, () -> UserA.subtract(UserB));
    }

    // ---------- equals() and hashCode() tests ----------
    @Test
    @DisplayName("equals() returns true for same numeric value ignoring trailing zeros")
    void testEqualsIgnoresTrailingZeros() {
        Money a = Money.of(new BigDecimal("2.0"), USD);
        Money b = Money.of(new BigDecimal("2.00"), USD);
        assertEquals(a, b);
        assertEquals(b, a);
    }

    @Test
    @DisplayName("equals() returns false for different currencies")
    void testEqualsDifferentCurrencies() {
        Money dollars = usd("100.00");
        Money euros = Money.of(new BigDecimal("100.00"), EUR);
        assertNotEquals(dollars, euros);
    }

    @Test
    @DisplayName("equals() returns false for different numeric values")
    void testEqualsDifferentAmounts() {
        Money a = usd("10.00");
        Money b = usd("10.01");
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("hashCode() consistent with equals() (same object yields same hash)")
    void testHashCodeConsistency() {
        Money a = Money.of(new BigDecimal("2.0"), USD);
        Money b = Money.of(new BigDecimal("2.00"), USD);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("Money can be used as HashSet key (contract test)")
    void testHashSetUsage() {
        Set<Money> set = new HashSet<>();
        Money key1 = Money.of(new BigDecimal("100.00"), USD);
        Money key2 = Money.of(new BigDecimal("100.00"), USD); // equal to key1
        set.add(key1);
        assertTrue(set.contains(key2));  // relies on equals+hashCode
        assertEquals(1, set.size());
    }


    // ---------- Immutability tests ----------
    @Test
    @DisplayName("Money objects are immutable - operations return new instances")
    void testImmutability() {
        Money original = usd("50.00");
        Money afterAdd = original.add(usd("10.00"));
        Money afterSubtract = original.subtract(usd("5.00"));
        Money afterMultiply = original.multiply(new BigDecimal("2"));

        assertEquals(new BigDecimal("50.00"), original.getAmount());
        assertEquals(new BigDecimal("60.00"), afterAdd.getAmount());
        assertEquals(new BigDecimal("45.00"), afterSubtract.getAmount());
        assertEquals(new BigDecimal("100.00"), afterMultiply.getAmount());
    }

    @Test
    @DisplayName("getAmount() returns the exact Big decimal")
    void testGetAmount() {
        Money a=Money.of(new BigDecimal("100.00"), USD);
        BigDecimal b=a.getAmount();
        assertSame(b, a.getAmount());
    }

    @Test
    @DisplayName("getCurrency() return the Currency")
    void testGetCurrency() {
        Money a=usd("100.00");
        assertNotEquals(EUR, a.getCurrency());
    }

    @Test
    @DisplayName("toString() contains currency symbol and amount")
    void testToString() {
        Money money = usd("10.50");
        String str = money.toString();
        assertTrue(str.contains("USD") || str.contains("$")); // depends on locale, but safe to check
        assertTrue(str.contains("10.50"));
    }

    // ---------- Edge cases ----------
    @Test
    @DisplayName("Very large amounts do not overflow (BigDecimal)")
    void testLargeAmounts() {
        BigDecimal huge = new BigDecimal("1e1000");
        Money hugeMoney = Money.of(huge, USD);
        assertEquals(huge.setScale(2, RoundingMode.HALF_UP), hugeMoney.getAmount());
    }

    @Test
    @DisplayName("Precision is not lost for values with 2 decimals")
    void testExactTwoDecimals() {
        Money m = usd("99.99");
        assertEquals(new BigDecimal("99.99"), m.getAmount());
    }
}