# ATPEZMS Phase 4 (Billing) -- Detailed Design

This document is the **Level 2 Detailed Design** for Phase 4 (Billing), as required by `DESIGN_RULES.md`.

It refines `DESIGN.md §5.4` (Billing bounded context) and the relevant functional requirements into a concrete, implementable design.

Implementation mechanics (Spring class names, annotations, wiring) are in `PHASE_04_BILLING_IMPLEMENTATION.md`.

---

## 1. Scope

### 1.1 Primary Goal

Phase 4 delivers the Billing bounded context: the financial ledger that records every charge to a wristband, aggregates the final bill per visit, and processes payment at checkout.

This is **Phase 4.1** (a single sub-slice). There is no Phase 4.2 currently planned.

### 1.2 Functional Requirements Covered

| Requirement | Description |
|---|---|
| FR-FB1 | Wristband Billing -- append purchase amounts to the visitor's consolidated tab |
| FR-EB1 | Bill Aggregation -- generate final bill summing all charges minus pre-paid deposits |
| FR-EB2 | Exit Control -- exit turnstile locked if visitor has outstanding balance |
| CO-2 | Transaction Consistency -- all financial transactions must be atomic |
| SI-1 | Payment Gateway -- mocked during development |

### 1.3 Explicit Non-Goals

- **Loyalty point accrual (FR-LY1):** Deferred to Phase 11 (Loyalty). Billing will expose a hook point later but does not implement it now.
- **Discount application (FR-LY2):** Deferred to Phase 11. Discount logic will be an extension point in the service layer.
- **Revenue reports (FR-MG3):** Deferred to Phase 12 (Analytics). Analytics will read from Billing's tables directly (read-only).
- **Refunds:** Not in the spec. Credits can be recorded as negative CHARGE transactions later if needed.
- **Real payment gateway:** Mocked always-succeed implementation.
- **Bill history:** One bill per visit (the current bill). No bill versioning or historical snapshots.
- **Currency handling:** Single currency (USD). Multi-currency deferred.

### 1.4 Security Note

Phase 3.2 (Security Skeleton) is skipped. All Billing endpoints are currently unauthenticated (same as existing Ticketing and Park endpoints). When security is eventually added, Billing endpoints will require ROLE_TICKET_STAFF or ROLE_ADMIN. This is documented but not enforced in code yet.

---

## 2. Key Decisions (With Rationale)

### 2.1 Transaction: Append-Only Ledger

Transactions are immutable. Once created, a transaction is never modified or deleted. This is enforced at three levels:

1. **JPA:** The Transaction entity has no mutator methods (setters). The `update()` method does not exist.
2. **API:** No PUT/PATCH/DELETE endpoints for transactions. Only POST (create) and GET (read).
3. **Business rule:** The financial ledger is a historical record. Modifying a past transaction would silently change the financial history, making audits unreliable.

**Why append-only rather than mutable?** The spec requires auditability. A food charge that was 500 cents one moment and 300 cents the next would mean an unlogged financial discrepancy. Credits (refunds) are expressed as new transactions with negative amounts, not by modifying existing ones.

### 2.2 Bill: One Per Visit, Running Total

One Bill exists per Visit. The Bill maintains running totals:
- `total_charges_cents`: sum of all CHARGE transactions (positive and negative)
- `prepayment_cents`: sum of all PREPAID_DEPOSIT transactions
- `balance_cents`: `total_charges - prepayment - settled_amount`

When a new transaction is recorded, the BillingService recalculates the Bill's running totals. The Bill is re-saved with the new totals. This is the **materialized aggregate** pattern: the Bill is a denormalized view that avoids re-scanning all transactions on every read.

**Why not derive the bill from transactions on every read?** The Bill is read on every exit gate scan (Phase 5+). Scanning N transactions per exit scan is O(N). A running total is O(1). The Bill is the hot path.

**Why not use a database SUM() aggregate instead?** A materialized total is more explicit and testable. It also avoids the first-level cache staleness issue with JPQL aggregates (same issue documented in Phase 2.2 ParkConfiguration).

### 2.3 Bill ↔ Visit: Plain Long FK (Cross-Context)

The Bill stores `visitId` as a plain `Long`, not a JPA `@ManyToOne` relationship. The Visit entity lives in the Ticketing context; Billing must not depend on Ticketing's domain objects. The cross-context rule from DESIGN.md §6.2 is explicit: "store the referenced entity's ID as a plain value."

The Bill does have a `UNIQUE` constraint on `visit_id` to enforce one bill per visit.

### 2.4 Transaction ↔ Bill: JPA Relationship (Intra-Context)

Transaction and Bill are both in the Billing context. A `@ManyToOne` JPA relationship is appropriate because both entities are owned by the same bounded context. This avoids manually querying transactions by bill ID when the relationship is already a natural FK.

### 2.5 Payment Gateway: Interface + Mock

The PaymentGateway is a plain Java interface in the billing context:

```java
public interface PaymentGateway {
    PaymentResult processPayment(String paymentToken, int amountCents, String currency);
}
```

A mock implementation is wired as a `@Profile("test")` or `@Profile("default")` bean that always returns success. This satisfies SI-1 ("Mocked during development"). A real implementation would call an external card processor API and return success/failure.

**Why not live in `common`?** The PaymentGateway is owned by Billing. Other contexts never call it directly — they call Billing's service. Placing it in common would violate the bounded context ownership principle.

### 2.6 Checkout Flow

Checkout is orchestrated by Ticketing (DESIGN.md §5.3). The flow is:

```
Ticketing.checkout(visitId)
  ├─ Billing.getBill(visitId) → load Bill
  ├─ if balance <= 0: Bill is already settled (nothing owed)
  ├─ if balance > 0:
  │    ├─ PaymentGateway.processPayment(token, balance) → PaymentResult
  │    ├─ if success: Bill.settledAt = now, Bill.status = SETTLED
  │    ├─ if failure: reject checkout (422)
  │    └─ Record a SETTLEMENT transaction for the payment
  └─ Ticketing marks Visit as ENDED, turns on exit gate
```

**Why a SETTLEMENT transaction?** The payment itself is a financial event that belongs in the ledger. Recording it as a CHARGE transaction with a "Settlement" description keeps the audit trail complete.

### 2.7 TransactionSource Enum

The source context that originated a transaction. Stored as a string in the database via `@Enumerated(EnumType.STRING)`:

```java
public enum TransactionSource {
    TICKET,
    FOOD,
    MERCHANDISE,
    EVENT
}
```

No PREPAID source — ticket prepayments use TransactionType.PREPAID_DEPOSIT with source TICKET.

### 2.8 TransactionType Enum

Distinguishes between charges and prepayments:

```java
public enum TransactionType {
    CHARGE,
    PREPAID_DEPOSIT
}
```

CHARGE transactions add to the running total. PREPAID_DEPOSIT transactions add to the prepayment amount. Credits (refunds) are expressed as negative CHARGE transactions.

---

## 3. Entities

### 3.1 Bill

Package: `com.atpezms.atpezms.billing.entity`

| Field | Type | Column | Constraints |
|---|---|---|---|
| id | Long | BIGINT AUTO_INCREMENT | PK |
| visitId | Long | BIGINT NOT NULL | UNIQUE (one bill per visit) |
| totalChargesCents | Integer | INT NOT NULL DEFAULT 0 | Running total of CHARGE transactions |
| prepaymentCents | Integer | INT NOT NULL DEFAULT 0 | Running total of PREPAID_DEPOSIT transactions |
| settledAmountCents | Integer | INT NOT NULL DEFAULT 0 | Amount actually paid at checkout |
| status | BillStatus | VARCHAR(20) NOT NULL DEFAULT 'OPEN' | OPEN or SETTLED |
| settledAt | Instant | TIMESTAMP (nullable) | When the bill was settled |

Extends BaseEntity (inherits createdAt, updatedAt, createdBy, updatedBy).

**Invariant:** `balanceCents` is derived: `totalChargesCents - prepaymentCents - settledAmountCents`. This is computed on demand (a getter), not stored as a column. Storing it would create a 3-way invariant (total, prepayment, settled, balance) that could drift.

### 3.2 Transaction

Package: `com.atpezms.atpezms.billing.entity`

| Field | Type | Column | Constraints |
|---|---|---|---|
| id | Long | BIGINT AUTO_INCREMENT | PK |
| bill | Bill | bill_id BIGINT NOT NULL | FK to bills(id) |
| type | TransactionType | VARCHAR(30) NOT NULL | CHARGE or PREPAID_DEPOSIT |
| source | TransactionSource | VARCHAR(30) NOT NULL | TICKET, FOOD, MERCHANDISE, EVENT |
| description | String | VARCHAR(500) NOT NULL | Human-readable |
| amountCents | Integer | INT NOT NULL | Positive for charges, negative for credits |
| currency | String | VARCHAR(3) NOT NULL DEFAULT 'USD' | ISO 4217 |
| createdAt | Instant | TIMESTAMP NOT NULL | via BaseEntity |

Extends BaseEntity (inherits createdBy, updatedBy, updatedAt — though Transaction is append-only, updatedAt is set once at creation).

### 3.3 Enums

| Enum | Values |
|---|---|
| BillStatus | OPEN, SETTLED |
| TransactionType | CHARGE, PREPAID_DEPOSIT |
| TransactionSource | TICKET, FOOD, MERCHANDISE, EVENT |

---

## 4. Endpoints

### 4.1 POST /api/billing/transactions

Record a new charge or prepayment.

**Request:**
```json
{
  "visitId": 1,
  "type": "CHARGE",
  "source": "FOOD",
  "description": "Burger at Food Court",
  "amountCents": 500,
  "currency": "USD"
}
```

**Validation:**
- visitId: not null, positive
- type: not null
- source: not null
- description: not blank, max 500 chars
- amountCents: positive for CHARGE, can be negative for credits but must not be zero
- currency: not blank, max 3 chars (defaults to "USD" if null)

**Business rules:**
- If no Bill exists for the visit, auto-create one with status OPEN
- Cannot record transactions on a SETTLED bill (422 `BILL_ALREADY_SETTLED`)

**Response:** 201 Created with TransactionResponse.

### 4.2 GET /api/billing/visits/{visitId}/bill

Retrieve the bill for a visit.

**Response:** 200 with BillResponse (including computed balance).

**Errors:**
- 404 `BILL_NOT_FOUND` if no bill exists for the visit (e.g., visitor hasn't purchased anything yet)

### 4.3 POST /api/billing/visits/{visitId}/checkout

Settle the bill for a visit.

**Request:**
```json
{
  "paymentToken": "tok_visa_mock_1234"
}
```

**Business rules:**
- Bill must exist and be OPEN (422 `BILL_NOT_FOUND` or `BILL_ALREADY_SETTLED`)
- If balance <= 0: settle without calling payment gateway (nothing owed)
- If balance > 0: call PaymentGateway.processPayment() with the payment token
- On gateway success: record SETTLEMENT transaction, update bill status to SETTLED
- On gateway failure: 422 `PAYMENT_FAILED` with error details from gateway

**Response:** 200 with SettleBillResponse (final bill state).

---

## 5. DTOs

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
) {}
```

### BillResponse

```java
public record BillResponse(
    Long id,
    Long visitId,
    int totalChargesCents,
    int prepaymentCents,
    int settledAmountCents,
    int balanceCents,        // derived: totalCharges - prepayment - settled
    String status,
    Instant settledAt,
    Instant createdAt,
    Instant updatedAt
) {
    // Static factory from(Bill) that computes balanceCents from bill fields
}
```

### RecordTransactionRequest

```java
public record RecordTransactionRequest(
    @NotNull @Positive Long visitId,
    @NotNull TransactionType type,
    @NotNull TransactionSource source,
    @NotBlank @Size(max = 500) String description,
    @NotNull @Min(1) int amountCents,
    String currency          // defaults to "USD" in service
) {}
```

### SettleBillRequest

```java
public record SettleBillRequest(
    String paymentToken       // null if balance <= 0 (no payment needed)
) {}
```

---

## 6. Exceptions

| Class | Extends | Error Code | HTTP |
|---|---|---|---|
| BillNotFoundException | ResourceNotFoundException | BILL_NOT_FOUND | 404 |
| BillAlreadySettledException | BusinessRuleViolationException | BILL_ALREADY_SETTLED | 422 |
| PaymentFailedException | BusinessRuleViolationException | PAYMENT_FAILED | 422 |

---

## 7. Test Strategy

### 7.1 Controller Tests (@WebMvcTest)

**BillingControllerTest:**
- Record transaction: 201 created, 400 validation errors, 404 bill-not-found on settled bill, 422 on settled bill transactions
- Get bill: 200 with computed balance, 404 not found
- Checkout: 200 settled, 422 payment failed, 422 already settled

### 7.2 Integration Tests (@SpringBootTest)

**BillingIntegrationTest:**
- Full flow: record several transactions, verify bill totals, checkout, verify settled status
- Auto-create bill on first transaction
- Cannot record transaction on settled bill
- Balance computation: charges - prepayments

---

## 8. Step Plan

### Phase 4.1 Steps

1. Write V009 migration (bills + transactions tables).
2. Add enums: BillStatus, TransactionType, TransactionSource.
3. Add Bill entity.
4. Add Transaction entity.
5. Add BillRepository.
6. Add TransactionRepository.
7. Add DTOs: RecordTransactionRequest, SettleBillRequest, TransactionResponse, BillResponse.
8. Add exceptions: BillNotFoundException, BillAlreadySettledException, PaymentFailedException.
9. Add PaymentGateway interface + MockPaymentGateway.
10. Add BillingService (recordTransaction, getBill, settleBill).
11. Add BillingController (POST transactions, GET bill, POST checkout).
12. Write BillingControllerTest.
13. Write BillingIntegrationTest.
14. Run `./gradlew test`. Fix any issues.
15. Adversarial review.
16. Commit.
