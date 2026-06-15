# DESIGN.md
## Multithreaded Banking System — System Design Document

**Author:** [Sandeep Prajapati]
**Status:** Approved for Implementation
**Version:** 1.0

---

## 1. Core Invariants (The "Must Never Happen" Rules)

These are the non-negotiable correctness guarantees of the system:

1. **Money Conservation:** Total system balance before any transfer equals total system balance after. Money is never created or destroyed.

2. **Atomic Transfers:** A transfer between two accounts must debit one and credit the other as a single indivisible unit, or do neither. No intermediate state is observable.

3. **Idempotency:** Every transaction carries a unique idempotency key. Processing the same key twice is a no-op. Network retries cannot cause double-debits.

4. **No Overdraft:** An account's balance can never become negative (unless explicitly designed as a credit account — out of scope).

5. **Limit Enforcement:** Daily withdrawal limits are enforced atomically with the withdrawal. Two concurrent withdrawals cannot combine to exceed the limit.

6. **Crash Consistency:** After a restart, the system state reflects all committed transactions and no incomplete ones. The transaction log is the source of truth.

---

## 2. Monetary Value Object (`Money`)

The system represents all monetary amounts using an **immutable `Money` value object**. This class is shared across all threads and transactions.

### 2.1 Why Immutable?
- **Thread-safety without locks:** Multiple threads can read the same `Money` instance concurrently.
- **No accidental mutation:** Arithmetic operations return a **new** `Money`, leaving the original unchanged – critical for rollback and audit trails.
- **Predictable hashing:** Can be safely used as a key in caches (`HashMap`, `HashSet`).

### 2.2 Precision & Representation
- **Never `double` or `float`** – those cause binary rounding errors (e.g., `0.1 + 0.2 != 0.3`).
- **Use `java.math.BigDecimal`** with scale fixed to the currency’s fraction digits (e.g., 2 for USD, INR).
- **Rounding mode:** `HALF_EVEN` (bankers’ rounding) to avoid statistical bias over large numbers of transactions.
- **Scale normalization:** All amounts are stored with exactly `currency.getDefaultFractionDigits()` decimal places.

### 2.3 Currency Safety
- `Money` holds a `java.util.Currency` instance.
- Arithmetic operations (add, subtract) **validate currency equality** – mixing USD with INR throws an exception immediately.

### 2.4 Core Operations
| Method | Behavior |
|--------|----------|
| `add(Money other)` | Returns new `Money` with sum, same currency. |
| `subtract(Money other)` | Returns new `Money` with difference. |
| `multiply(BigDecimal factor)` | Returns new `Money` scaled to the same decimal places. |
| `isNegative()`, `isZero()` | Sign checks without modifying state. |

### 2.5 Equality & Hashing
- `equals()` compares **numeric value** (ignoring trailing zeros) **and currency**.
- `hashCode()` is derived from `amount.stripTrailingZeros()` and currency – consistent with `equals`.
- This ensures `HashSet` and `HashMap` behave correctly (e.g., deduplication of transaction amounts).

### 2.6 Construction Guidelines
- **No public constructors** that accept `double` – use factory method `Money.of(BigDecimal, Currency)`.
- **Parsing from strings** must be done via `new BigDecimal(string)` to preserve exact user input.
- **Storing in database:** Use `DECIMAL(19,4)` column for amounts and store currency code separately.

### 2.7 Invariants Enforced by `Money`
- **No floating point** → eliminates precision loss.
- **Currency mismatch detection** → prevents adding apples to oranges.
- **Immutable** → supports rollback and concurrent reads without locks.
