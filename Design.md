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

