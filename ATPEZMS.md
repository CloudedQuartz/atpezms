# ATPEZMS - Project Specification

## Automated Theme Park and Entertainment Zone Management System

**Course:** Software Engineering  
**Group:** 17  
**Date:** February 2026

---

## 1. What This System Is

ATPEZMS manages the daily operations of a large, multi-zone theme park. The park contains adventure rides (roller coasters, drop towers), family rides (carousels, mini-trains), water rides, VR gaming arenas, food courts, and merchandise stores.

### 1.1 The Post-Paid Wristband Model

The defining characteristic of this system is a **cashless, post-paid economy** built around RFID wristbands.

When a visitor enters the park, they receive a physical RFID wristband. From that moment on, the wristband is their sole identity. It is what they scan to board a ride, to buy a burger, to reserve a seat at a show, and to walk out the exit gate. Every purchase made during the day is charged to the wristband -- no cash or card changes hands at individual counters. At the end of the visit, the visitor settles the full accumulated tab at a checkout station, and only then does the exit gate unlock.

This model means: every wristband effectively carries an open credit line for the duration of the visit.

### 1.2 What The System Provides

ATPEZMS is the backend that makes this model work. It is a server that exposes APIs consumed by:
- Point-of-Sale terminals at food courts and stores
- Turnstile hardware at ride entrances and park exits
- Self-service kiosks for event reservations
- Staff dashboards for management and operations

The system does **not** include any graphical user interface. It is an API server only.

---

## 2. Users

| Role | What They Do |
|------|-------------|
| **Visitor** | Wears the wristband. Rides, eats, shops, attends events, pays at exit. |
| **Ticket Counter Staff** | Registers visitors, sells ticket packages, issues wristbands. |
| **Ride Operator** | Monitors ride status, queues, and availability. Receives access-denied alerts. |
| **Food Court Staff** | Records food purchases by scanning the visitor's wristband. |
| **Store Staff** | Records merchandise sales, monitors stock levels. |
| **Event Coordinator** | Manages show schedules, capacities, and reservations. |
| **Park Manager** | Views reports: revenue, visitor load, congestion, inventory consumption. |
| **System Administrator** | System configuration, user management, security settings. |

---

## 3. Functional Requirements

### 3.1 Visitor Registration and Ticketing (Priority: High)

- **FR-VT1 (Pass Types):** The system shall support Single-day, Multi-day, Ride-specific, Family, and Fast-track passes.
- **FR-VT2 (Pricing):** Ticket prices shall vary based on age group, season, weekday/weekend, and peak/off-peak designation.
- **FR-VT3 (Capacity Enforcement):** The system shall deny ticket issuance and return "Sold Out" when daily visitor count reaches the configured maximum capacity.
- **FR-VT4 (RFID Association):** Upon successful ticket purchase, the system shall map a unique RFID tag to the visitor's profile.

### 3.2 Ride Access and Queue Management (Priority: High)

- **FR-RQ1 (Eligibility Validation):** On wristband scan, the system shall validate: (a) the ticket/pass is active for this zone, and (b) the visitor meets age/height restrictions for the ride.
- **FR-RQ2 (Turnstile Actuation):** The system shall send an unlock signal to the turnstile hardware only if validation passes. Otherwise, it shall keep the gate locked and return a denial reason.
- **FR-RQ3 (Queue Monitoring):** The system shall calculate real-time queue wait times based on entry/exit timestamps, updated at least every 60 seconds.
- **FR-RQ4 (Alternate Ride Suggestions):** When a ride's wait time exceeds a configurable threshold, the system shall suggest alternative rides with shorter queues.

### 3.3 Food Court and Restaurant Billing (Priority: Medium)

- **FR-FB1 (Wristband Billing):** The system shall append the purchase amount to the visitor's consolidated tab. No immediate payment at the counter.
- **FR-FB2 (Inventory Updates):** The system shall decrement item stock immediately upon transaction confirmation.
- **FR-FB3 (Low Stock Alerts):** The system shall alert kitchen staff when an item's stock falls below its configured reorder threshold.

### 3.4 Merchandise Store Operations (Priority: Medium)

- **FR-MS1 (Inventory Validation):** The system shall prevent a sale if the item's stock count is zero.
- **FR-MS2 (Cross-Store Suggestions):** If an item is out of stock at one store, the system shall show which other park stores currently have it.
- **FR-MS3 (Sales Logging):** Every purchase shall be recorded with quantity and visitor identity.

### 3.5 Special Events and Show Reservations (Priority: Medium)

- **FR-ER1 (Reservation Management):** Visitors shall reserve time slots or seats for events via wristband scan or kiosk.
- **FR-ER2 (Conflict Resolution):** The system shall prevent double-booking by temporarily locking a selected slot during the confirmation window.
- **FR-ER3 (Notifications):** The system shall notify affected visitors automatically when a show is rescheduled or cancelled.

### 3.6 Lost & Found and Emergency Handling (Priority: High)

- **FR-LF1 (Item Matching):** The system shall provide a search that matches found-item logs against lost-item reports by category, color, and date.
- **FR-EH1 (Incident Reporting):** Emergency incidents shall be logged with timestamp (to the second), location, and medical team dispatch status.
- **FR-EH2 (Emergency Override):** The system shall accept a command that unlocks all turnstiles park-wide for evacuation.

### 3.7 End-of-Visit Billing and Checkout (Priority: High)

- **FR-EB1 (Bill Aggregation):** The system shall generate a final bill summing all charges (tickets + food + merchandise + upgrades) minus any pre-paid deposits.
- **FR-EB2 (Exit Control):** The exit turnstile shall remain locked if the visitor has an outstanding balance.

### 3.8 Membership and Loyalty Program (Priority: Medium)

- **FR-LY1 (Point Tracking):** The system shall automatically calculate and award loyalty points based on transaction value.
- **FR-LY2 (Benefit Application):** Configured discounts (percentage or fixed) shall be applied automatically at the POS for eligible loyalty tiers.
- **FR-LY3 (Recommendations):** The system shall provide personalised recommendations based on visit history.

### 3.9 Management Reporting and Analytics (Priority: Medium)

- **FR-MG1 (Visitor Forecasts):** Daily visitor load forecasts for the upcoming week, available 7 days in advance.
- **FR-MG2 (Congestion Analysis):** A heat map of visitor density per zone, derived from real-time RFID scan data.
- **FR-MG3 (Revenue Breakdown):** Financial reports segmented by revenue stream (tickets vs. food vs. merchandise).
- **FR-MG4 (Inventory Reports):** Inventory consumption reports.
- **FR-MG5 (Loyalty Analytics):** Loyalty program engagement metrics.

---

## 4. Non-Functional Requirements

### 4.1 Performance

- **PR-1:** Wristband scan to turnstile unlock: < 1.0 second.
- **PR-2:** Sustained load: 500 concurrent write operations per second without data loss.
- **PR-3:** Report generation: < 10 seconds.

### 4.2 Safety

- **SF-1:** Strict ride capacity limit enforcement.
- **SF-2:** Age and height eligibility validation before ride access.
- **SF-3:** Emergency incidents logged with time, location, and response details.
- **SF-4:** Lost-and-found records securely maintained and traceable.

### 4.3 Security

- **SE-1:** All visitor PII and payment tokens encrypted at rest and in transit.
- **SE-2:** Administrative functions (price changes, log deletion) restricted to verified Manager role.
- **SE-3:** Unauthorized or duplicate RFID wristband usage detected and logged.

### 4.4 Quality

- **AV-1 (Availability):** 99% uptime during park operating hours.
- **US-1 (Usability):** POS interface usable by new staff with < 1 hour training.
- **SC-1 (Scalability):** Adding new rides, zones, food courts, or stores requires no architectural changes.

---

## 5. Constraints

- **CO-1 (Identity):** RFID wristbands are the sole identifier for all visitor interactions.
- **CO-2 (Transaction Consistency):** All financial transactions (billing + inventory deduction) must be atomic -- completed fully or not at all. Partial states are prohibited.
- **CO-3 (Privacy):** Visitor PII encrypted at rest and in transit.

---

## 6. External Interfaces

### 6.1 Hardware

- **HI-1 (RFID Readers):** Must read wristband IDs from at least 5 cm distance. Deployed at every ride entrance, POS terminal, and park gate.
- **HI-2 (Turnstiles):** System communicates open/close/status signals. Deployed at ride entrances and park entry/exit.
- **HI-3 (POS Terminals):** Standard point-of-sale hardware with receipt printers at food courts and stores.

### 6.2 Software

- **SI-1 (Payment Gateway):** External service for processing card payments at checkout. Mocked during development.
- **SI-2 (Notifications):** SMS and email services for reservation confirmations and emergency alerts. Mocked during development.
- **SI-3 (Report Export):** Reports exportable to CSV and PDF.

### 6.3 Communication

- **CI-1:** All data transmission between client terminals and the server shall use encrypted transport.

---

## 7. Scope Boundaries

**In scope:**
- All backend API services for the domains listed in Section 3.
- Integration contracts (request/response formats) for hardware and external software listed in Section 6.
- Role-based access rules for the user roles listed in Section 2.

**Out of scope:**
- Graphical user interfaces (web, mobile, or desktop).
- Hardware driver implementation for RFID readers or turnstile motors.
- Direct credit card processing (delegated to external payment gateway).
- Mobile app development.

---

## 8. Source Documents

The following documents produced by Group 17 informed this specification (stored in `docs_reference/`):

- **Software Requirements Specification (SRS)** -- 10 pages, IEEE 830-1998 format.
- **Data Dictionary** -- Data elements, data flows, and data stores.
- **DFD Model** -- Level 0 context diagram and Level 1 decomposition.
- **UML Diagrams** -- Class diagram and 6 sequence diagrams (Registration, Ride Access, Purchase, Checkout, Reservation, Emergency).
- **Use Case Document** -- (.doc format)

---

## Glossary

| Term | Definition |
|------|-----------|
| **RFID** | Radio Frequency Identification. The wireless technology used by the wristbands. |
| **POS** | Point of Sale. The terminal where staff ring up food or merchandise. |
| **PII** | Personally Identifiable Information. Names, phone numbers, emails. |
| **Consolidated Bill / Final Bill** | The running total of all charges accumulated on a wristband during a visit. Settled at checkout. |
| **Pass Type** | The category of ticket purchased (Single-day, Multi-day, Fast-track, etc.) which determines what rides/zones the visitor can access. |
| **Zone** | A logical area of the park (e.g., Adventure Zone, Water Zone, Food Court Area). |
| **Turnstile** | A physical gate that the system can lock or unlock via electronic signal. |
| **Checkout** | The end-of-visit process where the visitor pays their consolidated bill and the exit gate unlocks. |
| **Reorder Threshold** | The minimum stock level for an item. When stock falls below this, the system triggers an alert. |
