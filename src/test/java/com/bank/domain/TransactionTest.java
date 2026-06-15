package com.bank.domain;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.Currency;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionTest {
    private final static Currency currency = Currency.getInstance("USD");
    private final static Money amount = Money.of(new BigDecimal("100.00"), currency);

    @Test
    @DisplayName("testTransactionIsImmutable() check the trans is immutable")
    void testTransactionIsImmutable() {
        Transaction a = Transaction.builder().fromAccountId("abc").toAccountId("def").amount(amount).build();

        // 2. Capture original state
        String originalTxId = a.getTransactionId();
        String originalIdemKey = a.getIdempotencyKey();
        String originalFromId = a.getFromAccountId();
        String originalToId = a.getToAccountId();
        Money originalAmount = a.getAmount();
        Instant originalTimestamp = a.getTimestamp();
        TransactionStatus originalStatus = a.getStatus();

        // 3. Attempt to modify withStatus - should return a new object
        Transaction committed= a.withStatus(TransactionStatus.COMMITTED);
        assertNotSame(a,committed,"withStatus must return a new instance");
        assertEquals(TransactionStatus.COMMITTED,committed.getStatus(),"new object has update status");

        // 4. Verify that every field of the original object is unchanged
        assertEquals(originalTxId, a.getTransactionId());
        assertEquals(originalIdemKey, a.getIdempotencyKey());
        assertEquals(originalFromId, a.getFromAccountId());
        assertEquals(originalToId, a.getToAccountId());
        assertSame(originalAmount, a.getAmount()); // Money is immutable, same instance ok
        assertEquals(originalTimestamp, a.getTimestamp());
        assertEquals(originalStatus, a.getStatus());
    }

    @Test
    @DisplayName("Builder auto-generates ID and IdempotencyKey if not provided")
    void testBuilderAutoGeneration() {
        Transaction t = Transaction.builder().fromAccountId("abc").toAccountId("def").amount(amount).build();
        String originalTxId = t.getTransactionId();
        String originalIdemKey = t.getIdempotencyKey();

        assertNotNull(t.getTransactionId());
        assertNotNull(t.getIdempotencyKey());
        assertNotNull(t.getTimestamp());
    }

    @Test
    @DisplayName("Can't create transaction with same from and to account")
    void testCannotTransferToSelf() {
        assertThrows(IllegalArgumentException.class, () -> {
            Transaction.builder().fromAccountId("abc").toAccountId("abc").amount(amount).build();
        });
    }

    @Test
    @DisplayName("Can't create with negative and null amount")
    void testCannotTransferToNull() {
        assertThrows(NullPointerException.class, () -> {
            Transaction.builder().fromAccountId("abc").toAccountId(null).build();
        });

        Money userA=Money.of(new BigDecimal("-100.00"), currency);
        assertThrows(IllegalArgumentException.class, () -> {
            Transaction.builder().fromAccountId("abc").toAccountId("def").amount(userA).build();
        });
    }
}
