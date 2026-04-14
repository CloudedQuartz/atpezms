# Project Activity Log

## 2026-04-12 - Established Global Blueprint And Standards
Created the Level 1 global architecture blueprint (`DESIGN.md`) and global implementation standards (`IMPLEMENTATION.md`) under the Iterative Waterfall rules. Defined bounded contexts, cross-context interaction rules, transaction and consistency rules (including commit-based idempotency), and global error-handling conventions.
This set the baseline that all future vertical slices must align to.

## 2026-04-12 - Shifted Roadmap To JIT (YAGNI) Dependencies
Removed a dedicated upfront "infrastructure phase" from the roadmap and adopted a Just-In-Time (YAGNI) approach where dependencies and shared infrastructure are introduced only when a vertical slice actually needs them. Set Ticketing as the foundational Phase 1 slice.
Documented that other contexts (Park management APIs, Identity/Security, Billing, etc.) will be implemented after Ticketing.

## 2026-04-12 - Drafted Phase 1 Ticketing Slice Docs
Created dedicated Phase 1 slice documents for Ticketing design and implementation (`PHASE_01_TICKETING_DESIGN.md`, `PHASE_01_TICKETING_IMPLEMENTATION.md`). Adopted a pragmatic approach where minimal Park reference/config tables are seeded via Flyway during Phase 1 to unblock Ticketing pricing and capacity enforcement, while deferring Park CRUD APIs to Phase 2.

## 2026-04-13 - Added Common Exception Hierarchy Baseline
Introduced the shared exception taxonomy (`BaseException`, `ResourceNotFoundException`, `DuplicateResourceException`, `StateConflictException`, `BusinessRuleViolationException`) to normalize error semantics before controller work begins. Also standardized 422 handling with `HttpStatus.UNPROCESSABLE_ENTITY` to keep status intent explicit and avoid magic-number lookups.

## 2026-04-13 - Implemented Global Error Handling (Common)
Implemented the shared `ErrorResponse` DTO and a centralized `@RestControllerAdvice` (`GlobalExceptionHandler`) that maps domain exceptions and validation failures into consistent JSON error responses. Added explicit server-side logging for unexpected exceptions and completed handler coverage with tests for state conflicts and generic 500 behavior without leaking internal exception details.

## 2026-04-13 - Required Same-Session Documentation Sync Workflow
Added an explicit rule that non-trivial changes must re-read relevant project docs before coding and update the affected documentation in the same work session. This formalizes anti-drift behavior so design intent and implementation stay aligned commit by commit.
