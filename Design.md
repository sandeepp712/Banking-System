# DESIGN.md

## Multithreaded Banking System — System Design Document

**Author:** Sandeep Prajapati  
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

---

## 3. Transaction Data Model & Lifecycle

The system represents every financial transfer as an immutable `Transaction` record. This object captures the **intent** (what happened) and is completely decoupled from the execution logic (how it happens).

### 3.1 Transaction Fields (Immutable Data)

| Field | Type | Description |
|-------|------|-------------|
| `transactionId` | `String` | Unique primary identifier for the record. |
| `idempotencyKey` | `String` | Client-provided or generated key used to safely retry operations without duplication. |
| `fromAccountId` | `String` | Identifier of the source account. **Stored as ID, not a mutable object.** |
| `toAccountId` | `String` | Identifier of the destination account. **Stored as ID, not a mutable object.** |
| `amount` | `Money` | Immutable monetary value being transferred. |
| `timestamp` | `Instant` | UTC timestamp of when the transaction record was created. |
| `status` | `TransactionStatus` | Current lifecycle state (see 3.2). |

**Why store Account IDs instead of Account objects?**
- Ensures `Transaction` remains **truly immutable** (Account objects are mutable; their balances change over time).
- Prevents the transaction record from becoming stale or corrupted due to later changes in account state.
- Enforces **separation of concerns**: `Transaction` is data; `TransferService` holds the logic to look up current accounts and apply the change.

### 3.2 Transaction Lifecycle (Statuses)

Transactions follow a strict state machine to handle recovery, retries, and crash consistency:

1. **PENDING**
    - Initial state when the transaction is created but not yet applied to account balances.
    - Used during recovery to identify which transactions were in-flight at the time of a crash.
2. **COMMITTED**
    - The transfer has been successfully applied to both accounts.
    - Idempotency check: processing the same `idempotencyKey` again returns the existing result without re-applying.
3. **FAILED**
    - The transaction could not be applied (e.g., insufficient balance, limit breach, system error).
    - Rollback is complete; no account changes occurred.

**State Transition Rules:**
- `PENDING` → `COMMITTED` (successful application)
- `PENDING` → `FAILED` (application error or business rule violation)
- No transitions from `COMMITTED` or `FAILED` (history is immutable).

### 3.3 Idempotency (Deduplication)

- Every transaction contains a unique `idempotencyKey`.
- The system stores a mapping of `(idempotencyKey → transactionId/result)`.
- On receiving a new request:
    - If the key exists and status is `COMMITTED` → return success immediately (no-op).
    - If the key exists and status is `FAILED` → return failure or retry (configurable).
    - If the key does not exist → create a `PENDING` transaction and proceed.
- This guarantees that **network retries or duplicate client submissions never cause double-debits**.

### 3.4 Immutability & Construction

- `Transaction` is a **final class** with all fields marked `final`.
- No public setters exist. Status transitions (e.g., `PENDING` → `COMMITTED`) return a **new instance** of `Transaction` with the updated status, leaving the original record intact.
- A **Builder pattern** is used for construction to handle optional fields (auto-generation of `transactionId`, `timestamp`, and `idempotencyKey` if not provided by the client).
- This immutability guarantees thread-safety without locks and preserves a complete audit trail.

### 3.5 Separation of Concerns

| Component | Responsibility |
|-----------|----------------|
| `Transaction` (Data) | Immutable record of the transfer intent and outcome. |
| `TransferService` (Logic) | Reads the transaction, looks up accounts by ID, applies the debit/credit, and updates the status to `COMMITTED` or `FAILED`. |
| `AccountRepository` | Retrieves and persists mutable `Account` objects by ID. |

The `Transaction` itself never references `Account` objects or contains business logic for applying the transfer. This makes the system easier to test, extend, and reason about.

---

## 4. Transfer Service (Orchestration Layer)

The `TransferService` is the **single entry point** for moving money between two accounts. It is responsible for orchestrating the entire transfer process: fetching accounts, acquiring locks, applying debit/credit, and updating the transaction state.

### 4.1 Core Invariants Enforced by the Service

The `TransferService` must guarantee these non-negotiable rules:

| Invariant | Description |
|-----------|-------------|
| **Atomicity** | Either both accounts are updated, or neither is. No partial updates. |
| **Idempotency** | Processing the same `idempotencyKey` multiple times produces the same result (no double-debits). |
| **No Deadlocks** | Concurrent transfers between the same two accounts in opposite directions must never freeze the system. |
| **Money Conservation** | Total balance across all accounts remains constant before and after the transfer. |
| **Status Integrity** | A `Transaction` is never left in `PENDING` after processing; it must be `COMMITTED` or `FAILED`. |

### 4.2 Service Design: Stateless & Thread-Safe

- `TransferService` is **stateless** – it holds no instance fields (except possibly injected dependencies like `AccountRepository` and `TransactionRepository`).
- A **single instance** of `TransferService` is shared across the entire application.
- All data required for processing is passed via method parameters (e.g., `transfer(Transaction transaction)`).

**Why stateless?**  
Stateless services are inherently thread‑safe, easily testable, and can be scaled horizontally across multiple servers without session affinity.

### 4.3 Concurrency & Deadlock Prevention (Total Lock Ordering)

To prevent the classic **circular wait** deadlock (Thread 1: lock A → wait for B; Thread 2: lock B → wait for A), the service enforces **Total Lock Ordering**:

1. Extract `fromAccountId` and `toAccountId` from the `Transaction`.
2. Fetch the actual `Account` objects from the repository.
3. Add both accounts to a `List`.
4. **Sort** the list by `accountNumber` (lexicographically).
5. **Lock** the first account, then lock the second account.
6. **Unlock** in reverse order (second account first, then first).

**Why this works:**  
All threads now request locks in the same global order. Circular wait becomes impossible because a thread waiting for a lock will never hold a lock that another thread needs to proceed.

**Locking Code Pattern (Try-Finally):**

```java
// Sorting ensures deterministic order
List<Account> accounts = Arrays.asList(from, to);
accounts.sort(Comparator.comparing(Account::getAccountNumber));

Account first = accounts.get(0);
Account second = accounts.get(1);

first.lock();
try {
    second.lock();
    try {
        // Business logic (debit/credit) using ORIGINAL variables
        from.debit(amount);
        to.credit(amount);
    } finally {
        second.unlock();
    }
} finally {
    first.unlock();
}
```

---

## 5. Persistence Strategy (Write‑Ahead Log)

### 5.1 Crash Recovery Protocol (PENDING vs COMMITTED)

Every state‑changing operation follows a strict Write‑Ahead Log protocol to guarantee crash consistency.

**Normal operation sequence:**

1. **Write PENDING log entry**  
   Create an immutable `Transaction` with `status = PENDING`. Append it to the transaction log file and force the write to disk (`flush`/`fsync`).
2. **Apply to memory**  
   Update the in‑memory `Account` objects (`debit`/`credit`). This happens **only after** the durable log write.
3. **Write COMMITTED marker**  
   Append a second log entry (or rewrite the existing one) with `status = COMMITTED`. This confirms the operation is durable and fully applied.

**Recovery on restart:**

- Load the latest snapshot (if available) to get the last known good state of all accounts.
- Read the transaction log from the point after the snapshot.
- For each entry:
    - If `status == COMMITTED` → replay the transaction against the loaded accounts.
    - If `status == PENDING` → **ignore it**. The operation was interrupted before the commit marker; it never officially happened.
- After replay, memory is consistent with all committed operations.

**Why this works:**

| Crash point | Log state | Memory state | Recovery action |
|-------------|-----------|--------------|-----------------|
| Before step 1 | Nothing written | Unchanged | Nothing to recover; consistent |
| After step 1, before step 2 | PENDING written | Unchanged | Ignore PENDING |
| After step 2, before step 3 | PENDING written | Updated (lost) | Ignore PENDING; transaction discarded as never committed |
| After step 3 | COMMITTED written | Updated (lost) | Replay COMMITTED transaction |

**Checkpointing:**  
Periodically save a snapshot of all account states (`snapshot.dat`). On restart, load the latest snapshot, then replay only the log entries after its timestamp. This prevents unbounded log replay time.

### 5.2 TransactionLogger (Write‑Ahead Log Implementation)

The `TransactionLogger` is the core persistence component. It lives in the **`persistence`** package because it performs file I/O — a classic infrastructure concern. This follows the Dependency Rule: the domain and service layers never touch file operations directly.

**Architecture:**
- **Dedicated writer thread:** A single‑threaded `ExecutorService` owns the log file. No other thread ever writes to it.
- **Producer‑consumer pattern:** All service threads submit `Transaction` objects to a thread‑safe `BlockingQueue<Transaction>`. The writer thread consumes from the queue and writes entries sequentially.
- **No file‑level lock:** The single writer thread eliminates the need for a `ReentrantLock` on the file; serialisation is achieved via the queue.

**Key methods:**

```java
public class TransactionLogger {
    private final BlockingQueue<Transaction> queue;
    private final Path logFilePath;
    private final ExecutorService writerExecutor;
    private volatile boolean running;

    public TransactionLogger(String logFilePath) throws IOException { … }
    public void logTransaction(Transaction tx) throws InterruptedException { … }
    private void writeLoop() { … }   // consumes from queue, writes JSON line, flushes
    public void shutdown() throws InterruptedException { … }
}
```

### 5.3 RecoveryService (WAL Replay)

On system startup, the `RecoveryService` reads the entire transaction log, extracts `COMMITTED` entries, and replays them against the in‑memory accounts.

```java
public class RecoveryService {
    private final AccountRepository repository;
    private final Path logFilePath;
    private final ObjectMapper objectMapper;

    public RecoveryService(AccountRepository repository, String logFilePath) {
        this.repository = repository;
        this.logFilePath = Paths.get(logFilePath);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Reads the WAL, filters for COMMITTED transactions, and applies them.
     * Called exactly once during application bootstrap.
     */
    public void recover() throws IOException {
        if (!Files.exists(logFilePath)) {
            return; // No log file → nothing to recover
        }

        try (Stream<String> lines = Files.lines(logFilePath)) {
            lines.forEach(line -> {
                try {
                    Transaction tx = objectMapper.readValue(line, Transaction.class);
                    if (tx.getStatus() == TransactionStatus.COMMITTED) {
                        replayTransaction(tx);
                    }
                    // PENDING transactions are deliberately ignored
                } catch (IOException e) {
                    // Log error and potentially halt startup
                    System.err.println("Corrupt log entry: " + line);
                    throw new RuntimeException("Recovery failed", e);
                }
            });
        }
    }

    private void replayTransaction(Transaction tx) {
        Account from = repository.findById(tx.getFromAccountId())
                .orElseThrow(() -> new IllegalStateException("Account not found during recovery"));
        Account to = repository.findById(tx.getToAccountId())
                .orElseThrow(() -> new IllegalStateException("Account not found during recovery"));

        // No locking needed during recovery (single-threaded boot phase)
        from.debit(tx.getAmount());
        to.credit(tx.getAmount());
    }
}
```

**Recovery Guarantee:**
The system becomes exactly consistent with the last committed transaction before the crash. `PENDING` entries are safely discarded, ensuring atomicity (all-or-nothing).

---

## 6. Service Layer

### 6.1 AccountService

`AccountService` handles all operations that involve a single account: deposit, withdrawal, and balance inquiry. It relies on the account’s internal `ReentrantLock` for concurrency control and on `AccountRepository` for storage access.

**Responsibilities:**
- Fetch an account from the repository.
- Execute business logic (credit/debit) while the account’s lock is held.
- Persist the updated account state via the repository.
- (Optional) Record the transaction in the write‑ahead log for crash recovery.
- Return the result of the operation to the caller.

**Design Decisions:**
- The service does **not** hold any locks itself. All mutual exclusion is provided by the account object.
- The service depends on the `AccountRepository` interface (dependency inversion), making it easy to swap storage implementations or mock in tests.
- For idempotent operations, the service checks a transaction log or an in‑memory map of processed idempotency keys before executing the operation (similar to the transfer idempotency flow).

---

## 7. Account Domain Model (`Account`)

The `Account` class represents a customer's bank account. It is a **mutable entity** (its balance changes over time) but protects its state with a `ReentrantLock` for thread safety.

### 7.1 Core Fields

| Field | Type | Description |
|-------|------|-------------|
| `accountNumber` | `String` | Unique identifier for the account. |
| `balance` | `Money` | Current balance (immutable value object). |
| `ownerName` | `String` | Name of the account holder. |
| `status` | `AccountStatus` | `ACTIVE`, `FROZEN`, or `CLOSED`. |
| `dailyWithdrawalAmount` | `Money` | Total withdrawn today (resets daily). |
| `lastWithdrawalDate` | `LocalDate` | Date of the last withdrawal (for limit tracking). |
| `lock` | `ReentrantLock` | Mutual exclusion lock for balance updates. |

### 7.2 Thread-Safety Design

- **Internal Lock:** Each `Account` instance holds a `ReentrantLock`. All methods that modify the balance (`debit()`, `credit()`) **must** be called while the lock is held by the caller.
- **Lock is not exposed:** The lock is `private`; callers must use the `lock()` and `unlock()` methods provided on the account.
- **Read methods** (`getBalance()`, `getAccountNumber()`) are **not** synchronized – they rely on the fact that `balance` is an immutable `Money` object, so even if it changes, the reference is atomically updated.

### 7.3 Core Operations

| Method | Behavior | Lock Required? |
|--------|----------|----------------|
| `debit(Money amount)` | Subtracts the amount from balance. Throws `InsufficientFundsException` if balance would become negative. | Yes |
| `credit(Money amount)` | Adds the amount to balance. Always succeeds (positive amounts only). | Yes |
| `getBalance()` | Returns the current `Money` balance. | No |
| `lock()` | Acquires the `ReentrantLock`. | N/A |
| `unlock()` | Releases the lock. | N/A |

### 7.4 Withdrawal Limit Enforcement

The account enforces a daily withdrawal limit (configurable per account type):

```java
public void debit(Money amount) throws InsufficientFundsException, LimitExceededException {
    // Check if daily limit would be exceeded
    Money dailyTotal = dailyWithdrawalAmount.add(amount);
    if (dailyTotal.compareTo(DAILY_LIMIT) > 0) {
        throw new LimitExceededException("Daily withdrawal limit exceeded");
    }
    
    // Check sufficient balance
    if (balance.subtract(amount).isNegative()) {
        throw new InsufficientFundsException("Insufficient balance");
    }
    
    // Apply the debit
    this.balance = balance.subtract(amount);
    this.dailyWithdrawalAmount = dailyTotal;
    this.lastWithdrawalDate = LocalDate.now();
}
```

### 7.5 Invariants Enforced by Account

1. **Balance never negative** – `debit()` validates before mutation.
2. **Limit never exceeded** – daily withdrawal checks occur atomically with the debit.
3. **Lock held on mutation** – all balance-changing methods assert `lock.isHeldByCurrentThread()` (optional, for debugging).

---

## 8. Repository Layer (Account Persistence)

The repository layer abstracts data storage. It decouples the domain and service layers from the underlying persistence mechanism, whether in‑memory, file‑based, or a relational database.

### 8.1 Repository Interface (`AccountRepository`)

```java
public interface AccountRepository {
    Optional<Account> findById(String accountNumber);
    void save(Account account);
    void delete(String accountNumber);
    List<Account> findAll(); // For snapshots and reporting
}
```

**Design Rationale:**
1. **Interface-first:** Allows swapping implementations (in‑memory, JDBC, Redis) without changing service code.
2. **Optional return:** `findById()` returns `Optional` to clearly handle "account not found" cases.
3. **Idempotent `save()`:** Can be called multiple times; updates the existing account or inserts if new.

### 8.2 In-Memory Implementation (`InMemoryAccountRepository`)

For the current production version, we use a `ConcurrentHashMap` to store accounts:

```java
public class InMemoryAccountRepository implements AccountRepository {
    private final Map<String, Account> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Account> findById(String accountNumber) {
        return Optional.ofNullable(store.get(accountNumber));
    }

    @Override
    public void save(Account account) {
        store.put(account.getAccountNumber(), account);
    }

    @Override
    public void delete(String accountNumber) {
        store.remove(accountNumber);
    }

    @Override
    public List<Account> findAll() {
        return new ArrayList<>(store.values());
    }
}
```

**Thread-Safety Guarantee:**
1. `ConcurrentHashMap` provides fine‑grained locking for `put` and `get`, allowing high concurrency without global contention.
2. The `findAll()` snapshot returns a copy of the values; this is safe because `Account` objects themselves are thread‑safe via their internal locks.

### 8.3 Interaction with WAL

The repository does not write to the WAL directly. Instead:

1. The `TransferService` or `AccountService` calls `TransactionLogger.logTransaction()` before calling `repository.save()`.
2. The repository is responsible only for the current in‑memory state.
3. On recovery, the `RecoveryService` reads the WAL and directly calls `repository.save()` for each replayed transaction.

**This separation ensures that:**
1. The repository remains focused on storage (single responsibility).
2. The WAL is the source of truth for crash recovery.
3. The repository can be swapped (e.g., to PostgreSQL) without affecting the recovery logic.

---

## 9. Concurrency Control Summary

This table consolidates all concurrency mechanisms used across the system:

| Mechanism | Applied To | Purpose | Locking Granularity |
|-----------|------------|---------|---------------------|
| `ReentrantLock` | Each `Account` instance | Protects individual balance updates (deposit, withdraw) against concurrent writers. | Per‑account |
| Total Lock Ordering | `TransferService` | Prevents deadlocks by ensuring all threads acquire account locks in the same deterministic order (by `accountNumber`). | Global order |
| `BlockingQueue` | `TransactionLogger` | Decouples service threads from disk I/O; provides backpressure when the queue is full. | Producer‑consumer |
| Single Writer Thread | WAL file | Eliminates file‑level corruption and removes the need for file‑based locking. | N/A (serialised) |
| Immutability | `Money`, `Transaction` | Enables lock‑free concurrent reads and safe sharing across threads without defensive copying. | N/A (no locks) |
| `ConcurrentHashMap` | `InMemoryAccountRepository` | Provides thread‑safe storage of accounts with fine‑grained locking for `put` and `get`. | Per‑bucket |

---

## 10. Testing Strategy

A robust test suite is essential for a financial system. The testing strategy covers unit, integration, concurrency, crash recovery, and performance testing.

### 10.1 Example: Deadlock Prevention Test

```java
@Test
void concurrentTransfersDoNotDeadlock() throws Exception {
    Account a = new Account("A", Money.of(1000, Currency.USD));
    Account b = new Account("B", Money.of(1000, Currency.USD));
    repository.save(a);
    repository.save(b);

    ExecutorService exec = Executors.newFixedThreadPool(2);
    CountDownLatch latch = new CountDownLatch(2);

    exec.submit(() -> {
        transferService.transfer("A", "B", Money.of(100, Currency.USD));
        latch.countDown();
    });
    
    exec.submit(() -> {
        transferService.transfer("B", "A", Money.of(50, Currency.USD));
        latch.countDown();
    });

    assertTrue(latch.await(5, TimeUnit.SECONDS));
    
    // Verify final balances
    assertEquals(Money.of(950, Currency.USD), repository.findById("A").get().getBalance());
    assertEquals(Money.of(1050, Currency.USD), repository.findById("B").get().getBalance());
}
```

### 10.2 Example: Crash Recovery Test

```java
@Test
void recoveryIgnoresPendingTransactions(@TempDir Path tempDir) throws Exception {
    Path logPath = tempDir.resolve("wal.log");
    TransactionLogger logger = new TransactionLogger(logPath.toString());
    
    // Create a PENDING transaction (simulate crash before commit)
    Transaction pending = Transaction.builder()
        .idempotencyKey("key1")
        .fromAccountId("A")
        .toAccountId("B")
        .amount(Money.of(100, Currency.USD))
        .status(TransactionStatus.PENDING)
        .build();
    logger.logTransaction(pending);
    logger.shutdown();
    
    // Simulate recovery
    AccountRepository repo = new InMemoryAccountRepository();
    repo.save(new Account("A", Money.of(1000, Currency.USD)));
    repo.save(new Account("B", Money.of(500, Currency.USD)));
    
    RecoveryService recovery = new RecoveryService(repo, logPath.toString());
    recovery.recover();
    
    // PENDING transaction should NOT be replayed
    assertEquals(Money.of(1000, Currency.USD), repo.findById("A").get().getBalance());
    assertEquals(Money.of(500, Currency.USD), repo.findById("B").get().getBalance());
}
```

---

## 11. Deployment & Configuration

The system is designed to be deployed as a single self‑contained JAR with configurable runtime parameters.

### 11.1 Runtime Parameters

| Parameter | Environment Variable | Default Value | Description |
| :-------- | :------------------- | :------------ | :---------- |
| Log file path | `WAL_PATH` | `./data/transactions.log` | Location of the write‑ahead log file. |
| Snapshot file path | `SNAPSHOT_PATH` | `./data/snapshot.dat` | Location of the periodic state snapshot. |
| WAL queue capacity | `WAL_QUEUE_SIZE` | `10000` | Max pending log entries before blocking producers. |
| Flush mode | `WAL_FLUSH_MODE` | `SYNC` | `SYNC` (flush every write) or `BATCH` (flush every N writes). |
| Log level | `LOG_LEVEL` | `INFO` | Logging verbosity (`DEBUG`, `INFO`, `WARN`, `ERROR`). |

### 11.2 Startup Sequence (Bootstrap)

The application bootstraps in the following order:

1. **Load Configuration** – Read environment variables or properties file.
2. **Initialise In-Memory Repository** – Create the `ConcurrentHashMap` store.
3. **Start TransactionLogger** – Open the WAL file; start the background writer thread.
4. **Run RecoveryService** – Read the WAL and replay `COMMITTED` transactions into the repository. `PENDING` transactions are discarded.
5. **Start CLI / REST Server** – Begin accepting user requests.
6. **System Ready** – Print a startup banner and log the recovery summary.

**Command to run:**

```bash
# Run with default config
java -jar target/banking-system-1.0-SNAPSHOT.jar

# Run with custom WAL path
WAL_PATH=/var/log/bank/wal.log java -jar target/banking-system-1.0-SNAPSHOT.jar
```

### 11.3 Graceful Shutdown

**Signal handling:** On `SIGTERM` (or `Ctrl+C`), the system registers a shutdown hook.

**Shutdown hook steps:**
1. Stop accepting new requests.
2. Drain the `BlockingQueue` (write all remaining log entries).
3. Flush and close the WAL writer.
4. (Optional) Write a snapshot to speed up future recovery.
5. Exit.

---

## 12. Future Enhancements (Architectural Roadmap)

The current design is production‑ready for a single‑node, in‑memory deployment. The following enhancements are planned for future iterations:

| Enhancement | Description | Priority |
| :--- | :--- | :--- |
| **Snapshot Compaction** | Periodically take a full snapshot of all account balances and truncate the WAL. This prevents unbounded log replay time and reduces disk usage. | High |
| **PostgreSQL Integration** | Replace the `InMemoryAccountRepository` with a JDBC implementation backed by PostgreSQL. Enables persistent storage across restarts and multi‑node clusters. | High |
| **REST API Layer** | Expose all banking operations (`createAccount`, `deposit`, `transfer`, `getBalance`) via a Spring Boot REST API with OpenAPI/Swagger documentation. | Medium |
| **Idempotency Store Persistence** | Currently, idempotency keys are stored in memory. Moving them to a persistent store (database or Redis) ensures idempotency survives restarts. | Medium |
| **Distributed Transactions (2PC / Saga)** | Support transfers that span multiple banks or external financial systems. A Saga pattern with compensating transactions would be more appropriate than 2PC for this domain. | Low |
| **Metrics & Monitoring** | Expose Prometheus metrics for throughput, lock contention, queue depth, and recovery time. Provide a health check endpoint. | Medium |
| **Multi‑Currency Support** | Currently, transfers require the same currency. Support for currency conversion with exchange rates is planned for international transfers. | Low |

---

## 13. Conclusion

This design document lays out a production‑ready foundation for a concurrent banking system. By enforcing strict invariants, leveraging immutability, and using a Write‑Ahead Log with a single‑writer thread, the system achieves:

**Correctness:**
1. **Atomicity** (all-or-nothing transfers).
2. **Idempotency** (duplicate requests are safe).
3. **Money conservation** (no double‑debits or lost funds).
4. **Crash consistency** (no committed transaction is ever lost).

**Performance:**
1. **Low‑contention locks** (per‑account `ReentrantLock`).
2. **Lock‑free reads** (immutable `Money` and `Transaction`).
3. **High throughput** (non‑blocking `ConcurrentHashMap` + async WAL writes).

**Resilience:**
1. **Automatic crash recovery** (replay `COMMITTED` entries).
2. **Graceful shutdown** (drain queues, flush logs).
3. **Backpressure** (blocking queue limits prevent memory exhaustion).

**Extensibility:**
1. **Repository interface** allows swapping storage backends.
2. **Services depend on abstractions**, not concretions.
3. **Domain objects are isolated** from infrastructure (clean architecture).

The system is ready for implementation, testing, and deployment. All core invariants are documented, concurrency mechanisms are justified, and recovery protocols are validated. Future enhancements will build upon this solid foundation without compromising correctness or performance.