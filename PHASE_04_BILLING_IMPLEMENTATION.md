# ATPEZMS Phase 4 (Billing) -- Implementation Notes

This document is the **Level 2 implementation companion** for Phase 4 (Billing). It specifies the concrete Spring/JPA mechanics that bring `PHASE_04_BILLING_DESIGN.md` to life.

Cross-cutting rules (naming, DTO conventions, transaction placement, etc.) are in `IMPLEMENTATION.md`. This document records only Phase-4-specific decisions not already covered there.

---

## 1. New Exceptions

| Class | Extends | Error Code | HTTP |
|---|---|---|---|
| `BillNotFoundException` | `ResourceNotFoundException` | `BILL_NOT_FOUND` | 404 |
| `BillAlreadySettledException` | `BusinessRuleViolationException` | `BILL_ALREADY_SETTLED` | 422 |
| `PaymentFailedException` | `BusinessRuleViolationException` | `PAYMENT_FAILED` | 422 |

All three live in `com.atpezms.atpezms.billing.exception`.

---

## 2. Gradle Dependency Changes

**No new dependencies required.** Phase 4 uses only JPA, Spring MVC, and validation -- all already present from prior phases.

---

## 3. Flyway Migration

### V008 -- Billing schema (bills + transactions)

File: `V008__add_billing_tables.sql`

```sql
CREATE TABLE bills (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    visit_id             BIGINT       NOT NULL,
    total_charges_cents  BIGINT       NOT NULL DEFAULT 0,
    prepayment_cents     BIGINT       NOT NULL DEFAULT 0,
    settled_amount_cents BIGINT       NOT NULL DEFAULT 0,
    status               VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    settled_at           TIMESTAMP,
    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP    NOT NULL,
    created_by           VARCHAR(100),
    updated_by           VARCHAR(100),
    CONSTRAINT pk_bills PRIMARY KEY (id),
    CONSTRAINT uk_bills_visit_id UNIQUE (visit_id),
    CONSTRAINT chk_bills_status CHECK (status IN ('OPEN', 'SETTLED')),
    CONSTRAINT chk_bills_total_nonneg CHECK (total_charges_cents >= 0),
    CONSTRAINT chk_bills_prepayment_nonneg CHECK (prepayment_cents >= 0),
    CONSTRAINT chk_bills_settled_nonneg CHECK (settled_amount_cents >= 0)
);

CREATE TABLE transactions (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    bill_id       BIGINT       NOT NULL,
    type          VARCHAR(30)  NOT NULL,
    source        VARCHAR(30)  NOT NULL,
    description   VARCHAR(500) NOT NULL,
    amount_cents  BIGINT       NOT NULL,
    currency      VARCHAR(3)   NOT NULL DEFAULT 'USD',
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT fk_transactions_bill FOREIGN KEY (bill_id) REFERENCES bills(id),
    CONSTRAINT chk_transactions_type CHECK (type IN ('CHARGE', 'PREPAID_DEPOSIT')),
    CONSTRAINT chk_transactions_source CHECK (source IN ('TICKET', 'FOOD', 'MERCHANDISE', 'EVENT')),
    CONSTRAINT chk_transactions_amount_nonzero CHECK (amount_cents != 0)
);

CREATE INDEX idx_bills_visit_id ON bills(visit_id);
CREATE INDEX idx_transactions_bill_id ON transactions(bill_id);
```

**Why CHECK constraints on enums?** Defense in depth. The JPA `@Enumerated(EnumType.STRING)` prevents invalid enum values at the application layer, but a direct SQL INSERT could bypass it. The CHECK constraints enforce the closed set at the database level, matching the pattern established in Phase 1 (V004 access_entitlements CHECK).

**Why `amount_cents != 0`?** A zero-amount transaction is meaningless -- it represents neither a charge nor a credit. Rejecting it at the database level prevents accidental no-op ledger entries.

**Why `created_by`/`updated_by` columns?** Phase 4 runs after Phase 3.1 (which added V008 for actor audit columns). These columns are populated by JPA auditing via `AuditorAwareImpl`. Since Phase 3.2 (Security) is skipped, `AuditorAwareImpl` returns `Optional.empty()` for unauthenticated requests, so these columns will be NULL for Phase 4 rows. When security is eventually added, they will be populated with the authenticated username.

---

## 4. Entities

### 4.1 Enums

Package: `com.atpezms.atpezms.billing.entity`

```java
public enum BillStatus { OPEN, SETTLED }
public enum TransactionType { CHARGE, PREPAID_DEPOSIT }
public enum TransactionSource { TICKET, FOOD, MERCHANDISE, EVENT }
```

Enum values are uppercase strings. Stored via `@Enumerated(EnumType.STRING)` in the database.

### 4.2 Bill Entity

Package: `com.atpezms.atpezms.billing.entity`

```java
@Entity
@Table(name = "bills")
public class Bill extends BaseEntity {

    @Column(name = "visit_id", nullable = false, unique = true)
    private Long visitId;

    @Column(name = "total_charges_cents", nullable = false)
    private int totalChargesCents = 0;

    @Column(name = "prepayment_cents", nullable = false)
    private int prepaymentCents = 0;

    @Column(name = "settled_amount_cents", nullable = false)
    private int settledAmountCents = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BillStatus status = BillStatus.OPEN;

    @Column(name = "settled_at")
    private Instant settledAt;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Transaction> transactions = new ArrayList<>();

    protected Bill() {}

    public Bill(Long visitId) {
        if (visitId == null || visitId <= 0) {
            throw new IllegalArgumentException("visitId must be positive");
        }
        this.visitId = visitId;
    }

    public int getBalanceCents() {
        return totalChargesCents - prepaymentCents - settledAmountCents;
    }

    // Mutation methods -- called by BillingService only (package-private would work
    // since BillingService is in billing.service and Bill is in billing.entity,
    // but they are different packages. Public is needed for cross-package access.)
    public void addCharge(int amountCents) {
        if (status != BillStatus.OPEN) {
            throw new IllegalStateException("Cannot add charge to a settled bill");
        }
        this.totalChargesCents += amountCents;
    }

    public void addPrepayment(int amountCents) {
        if (status != BillStatus.OPEN) {
            throw new IllegalStateException("Cannot add prepayment to a settled bill");
        }
        this.prepaymentCents += amountCents;
    }

    public void settle(int amountCents, Instant settledAt) {
        if (status != BillStatus.OPEN) {
            throw new IllegalStateException("Bill is already settled");
        }
        this.settledAmountCents += amountCents;
        this.status = BillStatus.SETTLED;
        this.settledAt = settledAt;
    }
}
```

**Why `getBalanceCents()` is a computed getter, not a persisted column?**
Storing `balanceCents` as a column would create a 4-way invariant:
`balance = totalCharges - prepayment - settled`. If any of the three components is updated but balance is not, the invariant breaks. Computing it on demand from the three source fields guarantees consistency. The performance cost is negligible (a single subtraction).

**Why `@OneToMany(mappedBy = "bill")` on transactions?**
The `mappedBy` attribute tells JPA that the owning side of the relationship is the `bill` field in `Transaction`. This means the FK (`bill_id`) lives in the `transactions` table, not in `bills`. The `cascade = CascadeType.ALL` means that when a Bill is saved, its transactions are saved too. `orphanRemoval = true` means that if a Transaction is removed from the list, it is deleted from the database.

**Why is `transactions` a `List` and not a `Set`?**
Transactions have a natural order (chronological). A `List` preserves insertion order, which is useful for displaying the ledger in chronological order. A `Set` would not guarantee order.

### 4.3 Transaction Entity

Package: `com.atpezms.atpezms.billing.entity`

```java
@Entity
@Table(name = "transactions")
public class Transaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private TransactionSource source;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    protected Transaction() {}

    public Transaction(Bill bill, TransactionType type, TransactionSource source,
                       String description, int amountCents, String currency) {
        if (bill == null) throw new IllegalArgumentException("bill must not be null");
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (source == null) throw new IllegalArgumentException("source must not be null");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
        if (amountCents == 0) throw new IllegalArgumentException("amountCents must not be zero");

        this.bill = bill;
        this.type = type;
        this.source = source;
        this.description = description;
        this.amountCents = amountCents;
        this.currency = currency != null ? currency : "USD";
    }

    // Getters only -- no setters (append-only ledger)
    public Bill getBill() { return bill; }
    public TransactionType getType() { return type; }
    public TransactionSource getSource() { return source; }
    public String getDescription() { return description; }
    public int getAmountCents() { return amountCents; }
    public String getCurrency() { return currency; }
}
```

**Why `FetchType.LAZY` on `@ManyToOne` to Bill?**
BillingService methods that look up a Transaction by ID don't need the Bill object. Lazy loading avoids an unnecessary JOIN when the Bill is not needed. When the Bill IS needed (e.g., in `recordTransaction`), the Transaction is created fresh with a Bill reference -- no lazy load occurs.

**Why no setters?**
The Transaction entity is append-only. Once created, it is never modified. Removing setters enforces this at the code level. The only way to change a Transaction is through the constructor at creation time.

---

## 5. Repositories

### 5.1 BillRepository

```java
public interface BillRepository extends JpaRepository<Bill, Long> {
    Optional<Bill> findByVisitId(Long visitId);
}
```

`findByVisitId` is the primary lookup method. The `visit_id` column has a UNIQUE constraint (one bill per visit), so this returns at most one result. The `idx_bills_visit_id` index makes this an index seek.

### 5.2 TransactionRepository

```java
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByBillIdOrderByCreatedAtAsc(Long billId);
}
```

`findByBillIdOrderByCreatedAtAsc` returns all transactions for a bill in chronological order. Used for displaying the ledger. The `idx_transactions_bill_id` index makes this efficient.

---

## 6. PaymentGateway Interface

Package: `com.atpezms.atpezms.billing.service`

```java
public interface PaymentGateway {
    PaymentResult processPayment(String paymentToken, int amountCents, String currency);
}
```

### PaymentResult

```java
public record PaymentResult(boolean succeeded, String errorMessage) {
    public static PaymentResult success() {
        return new PaymentResult(true, null);
    }
    public static PaymentResult failure(String errorMessage) {
        return new PaymentResult(false, errorMessage);
    }
}
```

### MockPaymentGateway

Package: `com.atpezms.atpezms.billing.service`

```java
@Component
@Profile("!prod")  // active in dev, test, and any non-production profile
public class MockPaymentGateway implements PaymentGateway {
    @Override
    public PaymentResult processPayment(String paymentToken, int amountCents, String currency) {
        return PaymentResult.success();
    }
}
```

**Why `@Profile("!real-integrations")`?** The mock is active in all profiles except `real-integrations`. This matches the convention in IMPLEMENTATION.md §11. A real implementation would be annotated with `@Profile("real-integrations")`, so only one implementation is active at a time.

**Why not `@Primary`?** There is only one `PaymentGateway` bean in the current codebase (the mock). `@Primary` is needed when multiple beans of the same type exist. If a real gateway implementation is added later, it would be annotated with `@Profile("prod")` and `@Primary`, and the mock would be `@Profile("!prod")`.

---

## 7. Services

### 7.1 BillingService

Package: `com.atpezms.atpezms.billing.service`

Methods:

- `recordTransaction(RecordTransactionRequest)` -- `@Transactional` -- auto-creates bill if needed, creates Transaction, updates Bill running total. Returns `TransactionResponse`.
- `getBill(Long visitId)` -- `@Transactional(readOnly = true)` -- returns `BillResponse` with computed balance. Throws `BillNotFoundException`.
- `settleBill(Long visitId, String paymentToken)` -- `@Transactional` -- checks bill exists and is OPEN, calls PaymentGateway if balance > 0, records SETTLEMENT transaction, marks bill SETTLED. Returns `BillResponse`.

**Transaction boundary rationale:**
All three methods are `@Transactional` because they involve multiple entity writes (Transaction + Bill update). The `@Transactional` annotation ensures that if any step fails, all writes are rolled back.

**Auto-create bill logic (in `recordTransaction`):**
```java
Bill bill = billRepository.findByVisitId(request.visitId())
    .orElseGet(() -> {
        Bill newBill = new Bill(request.visitId());
        billRepository.save(newBill);
        return newBill;
    });
```

This uses `save()` (not `saveAndFlush()`) because the Bill will be flushed when the Transaction is saved. The auto-created Bill starts with zero totals and OPEN status.

**Checkout flow (in `settleBill`):**
1. Find bill by visitId → throw `BillNotFoundException` if not found
2. Check bill is OPEN → throw `BillAlreadySettledException` if SETTLED
3. Compute balance = `bill.getBalanceCents()`
4. If balance > 0:
   - Call `paymentGateway.processPayment(paymentToken, balance, "USD")`
   - If `!result.success()`: throw `PaymentFailedException(result.errorMessage())`
5. Record SETTLEMENT transaction: `new Transaction(bill, CHARGE, TICKET, "Settlement payment", balance, "USD")`
6. Call `bill.settle(balance, Instant.now(clock))`
7. Save bill and transaction

**Why record a SETTLEMENT transaction?** The payment is a financial event that belongs in the ledger. Without it, the ledger would show charges but no corresponding payment, making the audit trail incomplete.

---

## 8. DTOs

### RecordTransactionRequest

```java
public record RecordTransactionRequest(
    @NotNull @Positive Long visitId,
    @NotNull TransactionType type,
    @NotNull TransactionSource source,
    @NotBlank @Size(max = 500) String description,
    @NotNull @Min(1) int amountCents,
    String currency
) {}
```

`currency` defaults to "USD" in the service layer if null.

### SettleBillRequest

```java
public record SettleBillRequest(
    String paymentToken  // null if balance <= 0
) {}
```

### TransactionResponse

```java
public record TransactionResponse(
    Long id,
    Long billId,
    String type,
    String source,
    String description,
    int amountCents,
    String currency,
    Instant createdAt
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
            t.getId(),
            t.getBill().getId(),
            t.getType().name(),
            t.getSource().name(),
            t.getDescription(),
            t.getAmountCents(),
            t.getCurrency(),
            t.getCreatedAt()
        );
    }
}
```

### BillResponse

```java
public record BillResponse(
    Long id,
    Long visitId,
    int totalChargesCents,
    int prepaymentCents,
    int settledAmountCents,
    int balanceCents,
    String status,
    Instant settledAt,
    Instant createdAt,
    Instant updatedAt
) {
    public static BillResponse from(Bill bill) {
        return new BillResponse(
            bill.getId(),
            bill.getVisitId(),
            bill.getTotalChargesCents(),
            bill.getPrepaymentCents(),
            bill.getSettledAmountCents(),
            bill.getBalanceCents(),
            bill.getStatus().name(),
            bill.getSettledAt(),
            bill.getCreatedAt(),
            bill.getUpdatedAt()
        );
    }
}
```

---

## 9. Controller

### BillingController

Package: `com.atpezms.atpezms.billing.controller`

Base path: `/api/billing`

Endpoints:

| Method | Path | Handler | Description |
|---|---|---|---|
| POST | `/api/billing/transactions` | `recordTransaction` | Record a charge or prepayment |
| GET | `/api/billing/visits/{visitId}/bill` | `getBill` | Get bill for a visit |
| POST | `/api/billing/visits/{visitId}/checkout` | `checkout` | Settle bill and pay |

All endpoints follow the thin controller pattern: deserialize → validate → delegate → return.

**Note on `@PreAuthorize`:** Since Phase 3.2 (Security) is skipped, no `@PreAuthorize` annotations are added yet. When security is implemented, these endpoints will require `ROLE_TICKET_STAFF` or `ROLE_ADMIN`.

---

## 10. Test Strategy

### 10.1 Controller Tests (@WebMvcTest)

**BillingControllerTest:**

| Test | Expected |
|---|---|
| Record transaction → 201 | Valid request creates transaction |
| Record transaction → 400 | Missing fields (e.g., null visitId) |
| Record transaction → 422 | Transaction on SETTLED bill |
| Get bill → 200 | Returns bill with computed balance |
| Get bill → 404 | No bill for visitId |
| Checkout → 200 | Settles bill successfully |
| Checkout → 422 | Payment gateway failure |
| Checkout → 422 | Bill already settled |

### 10.2 Integration Tests (@SpringBootTest)

**BillingIntegrationTest:**

| Test | Expected |
|---|---|
| Full flow | Record 2 charges, verify bill totals, checkout, verify SETTLED |
| Auto-create bill | First transaction creates bill automatically |
| Cannot transact on settled | After checkout, new transaction → 422 |
| Balance computation | Charges - prepayments = balance |
| Settlement transaction | After checkout, ledger shows SETTLEMENT transaction |
| Zero balance checkout | Bill with prepayment >= charges settles without payment |

---

## 11. Step Plan

1. Write V009 migration.
2. Add enums: BillStatus, TransactionType, TransactionSource.
3. Add Bill entity.
4. Add Transaction entity.
5. Add BillRepository and TransactionRepository.
6. Add DTOs.
7. Add exceptions.
8. Add PaymentGateway interface + MockPaymentGateway.
9. Add BillingService.
10. Add BillingController.
11. Write BillingControllerTest.
12. Write BillingIntegrationTest.
13. Run `./gradlew test`. Fix any issues.
14. Adversarial review.
15. Commit.
