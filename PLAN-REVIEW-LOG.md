# Plan Review Log: 体育场地预约平台 MVP 范围与研发实施方案
Act 1 (grill) complete — plan locked with the user. MAX_ROUNDS=5.

## Round 1 — Codex

1. **Critical: The plan implements a different MVP.** The PRD requires a production transaction loop, while the plan defines a test-only mock milestone and permanently excludes several PRD features. **Fix:** Establish one authoritative scope and map every PRD acceptance ID to an implementation milestone or approved PRD amendment.
2. **Critical: The payment architecture contradicts the PRD.** The PRD mandates WeChat Pay platform collection and profit sharing, but the plan postpones provider selection, implements monthly offline billing, and does not commit to profit sharing later. **Fix:** Run the channel proof before freezing the schema and either implement the PRD’s profit-sharing ledger or formally change the PRD’s commercial model.
3. **Critical: The highest-risk inventory work is for an explicitly excluded feature.** The plan prioritizes full/half-court composite resources, while the PRD requires independent resources and explicitly excludes composite inventory. **Fix:** Remove composite inventory from the implementation or amend the PRD before development.
4. **Critical: Slot granularity and selling modes are incompatible.** Thirty-minute fixed slots cannot represent the PRD’s 15-minute atoms and free continuous selection, and adding them later requires inventory-key and order-snapshot migration. **Fix:** Use 15-minute atoms and support both fixed and continuous modes from the initial schema.
5. **Critical: Pending orders have no specified durable payment hold.** Database row locks end when the creation transaction commits; the plan never defines a persisted `PAYMENT_LOCKED` owner and expiry, allowing multiple pending orders to reserve the same inventory sequentially. **Fix:** Atomically persist a hold with `order_id`, `expires_at`, and compare-and-set transitions enforced by database constraints.
6. **Critical: Unknown payment results are treated as failures.** Closing and releasing inventory after a processing exception can resell a slot whose external payment actually succeeded. **Fix:** Add `UNKNOWN/PROCESSING` states, query-and-close reconciliation, durable callback inboxes, and automatic refund for confirmed late payments.
7. **High: Cancellation rules cannot pass the PRD.** Full refunds before start replace the three refund templates, boundary calculations, partial refunds, retained amounts, and repeated refund adjustments. **Fix:** Implement versioned refund-rule snapshots and the full 0/50/100-percent state transitions required by `CAN-01` and `PAY-01`.
8. **High: Removing after-sales creates a settlement race.** The plan accrues fees 24 hours after completion but has no complaint freeze or atomic competition between after-sales creation and settlement. **Fix:** Implement the minimal after-sales state machine, SLA jobs, and shared order-funds lock before any settlement logic.
9. **High: The later production milestone still lacks the required financial ledger.** “Basic difference checks” do not cover external/manual refunds, profit-sharing rollback, channel fees, bank-arrival states, daily reconciliation, or invoice adjustments. **Fix:** Define immutable payment, refund-attempt, split, commission-adjustment, channel-ledger, reconciliation, settlement-batch, and invoice records before production work.
10. **High: The tenant and permission model is underspecified and conflicts with the PRD.** One-store UI and three roles cannot enforce the PRD’s multi-store operational and financial separation, while duplicating both tenant IDs on every table permits contradictory ownership. **Fix:** Define a permission matrix, derive scope from the authenticated principal, and enforce organization/store membership through composite foreign keys at aggregate boundaries.
11. **High: Venue changes bypass mandatory approval.** Immediate publication directly violates the PRD’s reviewed-copy model and `VEN-01`, allowing unreviewed addresses and contact details to reach users. **Fix:** Add draft, pending-review, rejected, and published versions for core venue data.
12. **High: Manual occupancy is not a substitute for offline orders.** It loses receivable, payment, refund, customer, and audit states required by `OFF-01`. **Fix:** Model offline orders separately while sharing the same atomic inventory reservation mechanism.
13. **High: The verification credential is brute-forceable.** A six-digit code has only one million possibilities, and neither TTL, rotation, rate limits, hashing, nor attempt lockout is specified. **Fix:** Use rotating high-entropy hashed QR tokens and scope short codes by store and order with strict TTL, throttling, and lockout.
14. **High: Authentication controls are not production-ready.** Initial password change and account lockout omit password hashing, session revocation, CSRF protection, administrator MFA, and lockout-abuse controls. **Fix:** Specify Argon2id/bcrypt storage, secure session cookies, CSRF defenses, audited reset/revocation, per-account and per-source throttling, and MFA for platform administrators.
15. **Medium: Cancellation and venue blocking are not atomic.** Releasing inventory after an asynchronous refund and separately creating an occupancy can either suppress inventory indefinitely or briefly resell an unavailable court. **Fix:** Atomically transition reserved atoms to either `AVAILABLE` or `INTERNAL_BLOCKED` when cancellation is accepted, independently of refund completion.
16. **Medium: Observability lists counters but no operational detection contract.** There are no alert thresholds, stuck-state ages, scheduler freshness checks, reconciliation lag targets, or provider latency/error objectives. **Fix:** Define SLOs and alerts for aged payment/refund/settlement states, failed jobs, reconciliation lag, inventory invariants, and tenant-access denials.

VERDICT: REVISE

### Primary agent response

- Accepted findings 5, 6, 9, 10, 13, 14, 15 and 16. The plan now defines persisted expiring holds, unknown payment recovery, durable provider inboxes, provider-neutral immutable ledgers, constrained tenant ownership, hardened credentials and sessions, atomic cancel/block transitions, and explicit alert ages.
- Accepted finding 1 as a documentation-authority problem. `PLAN.md` is now explicitly the grilled MVP scope overlay and maps every PRD acceptance group to the functional milestone, payment milestone, conditional profit-sharing work, or deliberate deferral.
- Rejected findings 2, 3, 4, 7, 11 and 12 as requests to undo explicit user decisions. The user deliberately selected monthly direct billing, full/half composite inventory, 30-minute fixed slots, simple full refunds, no venue approval workflow, and manual occupancy instead of complete offline orders. These changes are now recorded as formal PRD overrides rather than accidental contradictions.
- Partially accepted finding 8. The user explicitly excluded after-sales, so no after-sales state machine was added. Instead, fee accrual and platform manual refund now serialize on an order funds lock, which closes the actual race without restoring excluded product scope.
- Partially accepted finding 9. Production payment/refund and reconciliation ledgers are now mandatory; split, rollback and settlement-batch records are conditional on selecting profit sharing, and invoice workflow remains explicitly out of scope.

## Round 2 — Codex

1. **Critical: Cancellation can commit without a durable refund obligation.** The transaction guarantees the inventory transition but does not require order cancellation, refund-business record, and refund command/outbox to commit atomically; a crash can release the slot without ever scheduling the refund. **Fix:** Persist the cancellation, inventory transition, refund obligation, and provider-command outbox in one transaction, then call the provider asynchronously.
2. **Critical: Callback verification stops at signature validation.** A legitimately signed event must also be matched against the configured merchant/sub-merchant, provider, internal order, amount, and currency before it can confirm an order. **Fix:** Specify exact callback field validation and quarantine any signed event whose financial identity differs from the stored payment order.
3. **High: Schedule edits can overwrite active payment holds.** Template rebuilding excludes booked and manually occupied inventory but not `PAYMENT_LOCKED`, `PROCESSING`, or `UNKNOWN` atoms introduced later in the plan. **Fix:** Treat every active hold as immutable during schedule changes and defer affected rebuilds until the payment reaches a safe terminal state.
4. **High: The six-digit fallback code has no unambiguous lookup key.** Staff enter only a short code, yet the plan requires lookup by store, order, and code even though the order is unknown; collisions among active orders can therefore produce ambiguity. **Fix:** Enforce active-code uniqueness per store and window with collision retries, or require a second order identifier, and store the code using a keyed hash.
5. **High: `PAY-03` is promised but not designed.** The tracking matrix commits insufficient-funds handling, while the real-payment milestone defines neither `PENDING_FUNDING`, venue suspension, replenishment retry, nor recovery conditions. **Fix:** Add the provider-neutral blocked-refund state machine and venue pause/recovery workflow, or explicitly defer `PAY-03`.
6. **High: PII obligations are absent despite not being overridden.** The plan collects phone numbers but does not require consent evidence, masking rules for platform access, complete-number access audit, export restrictions, or retention/deletion behavior. **Fix:** Add versioned consent records, role-specific masking, access auditing, export restrictions, and a documented retention/deletion workflow before production.
7. **High: The acceptance matrix overclaims `PER-01`.** It marks the finance-view masking scenario implemented even though the finance role is explicitly excluded and the test plan contains no masking or export assertion. **Fix:** Replace `PER-01` with an approved platform-admin masking scenario or move it to the deferred row.
8. **High: Tests do not prove the newly added failure paths.** The suite omits payment-expiry versus callback races, query/close uncertainty, late-payment refunds, schedule changes during holds, short-code collisions, and crash recovery of refund commands. **Fix:** Add a fault-injectable asynchronous mock provider and deterministic integration tests for every new transition and concurrency boundary.

VERDICT: REVISE

### Primary agent response

- Accepted all eight findings as implementation-closure issues rather than scope changes.
- Cancellation, inventory transition, refund obligation and provider-command outbox now commit atomically; callback processing verifies provider, merchant identity, order, amount and currency after signature validation and quarantines mismatches.
- Versioned schedule publication now treats every active payment hold as immutable. The six-digit fallback is unique among active credentials within a store, collision-retried and stored as a keyed hash, so an authenticated employee can resolve exactly one order without knowing its ID.
- `PAY-03` now has provider-neutral `PENDING_FUNDING`, transaction suspension, retry and evidence-based recovery semantics. PII consent, masking, full-number access audit, export restrictions and retention/deletion gates are explicit; the original finance-role `PER-01` is deferred and replaced by an MVP masking scenario.
- The test baseline now uses a fault-injectable asynchronous payment provider and explicitly covers timeout/callback races, uncertainty, late payment, schedule-edit races, short-code collisions, refund-command crash recovery, callback identity mismatches and PII access boundaries.

## Round 3 — Codex

1. **Critical: Suspension is not serialized with checkout.** Booking creation does not atomically lock an `accepting_orders` state, so checkout can commit after a manual or `PENDING_FUNDING` suspension. **Fix:** Lock and validate the organization/store trading gate in the hold transaction, define treatment of existing unpaid holds, and test checkout racing suspension.
2. **High: Verification and cancellation can both succeed.** Verification begins 30 minutes before the booking, while users retain full-refund cancellation until start; without a shared order lock, a user can check in and then cancel or race both operations. **Fix:** Decide whether verified orders may be user-cancelled, serialize both transitions on the order row, and add concurrent verification/cancellation tests.
3. **High: Sellable-unit resource composition has no lifecycle rule.** Changing or deleting `full -> [A,B]` can invalidate existing schedules and cause later cancellation or availability calculations to use different resources. **Fix:** Make resource compositions immutable/versioned, forbid deletion while referenced, and rebuild only unoccupied future inventory against a new version.
4. **High: Security throttling has no Redis failure policy.** A Redis outage could either disable check-in or fail open and expose sensitive endpoints. **Fix:** Specify fail-closed behavior or a persistent fallback for sensitive endpoints and test Redis loss during authentication and short-code verification.
5. **High: `REC-01` and `REC-02` remain underdesigned.** The plan lacks matching keys, unmatched-record quarantine behavior, resolution states, and reconciliation tests. **Fix:** Define the channel-record matching hierarchy, immutable discrepancy workflow, prohibited automatic mutations, resolution outcomes, and provider contract tests before production approval.
6. **High: Mini-program authentication and employee binding are only named, not secured.** The production gate does not define server-side code exchange, token rotation/revocation, binding-code entropy and storage, or identity uniqueness. **Fix:** Specify server-verified `wx.login`, short-lived sessions with revocation, hashed high-entropy binding codes with TTL, and unique audited identity bindings.

VERDICT: REVISE

### Primary agent response

- Accepted all six findings. The store/organization trading gate now serializes suspension with checkout and explicitly honors only holds committed before suspension.
- User cancellation and verification share an order lock; verified users cannot self-cancel, while venue operational cancellation remains possible with preserved verification and refund audit. Sellable-unit compositions are immutable versions referenced by schedules, inventory and orders.
- Redis-backed sensitive controls fail closed; high-entropy QR verification can use durable session state. Mini-program login now uses server-side code exchange, rotating hashed sessions, high-entropy one-time employee binding and uniqueness/revocation rules.
- Reconciliation now has exact matching keys, immutable discrepancy states and resolution outcomes, no silent financial mutation, and provider contract tests for duplicates, missing records, mismatches, external refunds and idempotent reruns.

## Round 4 — Codex

1. **Critical: Not every refund origin is atomically durable.** Late-payment and platform-manual refunds leave a crash window not present in cancellation. **Fix:** Route cancellation, late-payment, and manual refunds through one transactional refund-intent primitive and inject crashes at every origin boundary.
2. **High: Releasing inventory can expose an obsolete schedule version.** Timeout and cancellation can release atoms before the latest schedule-version task runs. **Fix:** Release into `RECONCILE_PENDING` and expose inventory only after it matches the active schedule version, or reject stale versions in all availability queries.
3. **High: A single gate state cannot safely represent overlapping suspension causes.** Clearing one reason could reopen trading while another remains active. **Fix:** Store independent active gate blockers by cause and reopen trading only when no blocker remains, with an overlapping-cause concurrency test.
4. **High: Payment holds have no abuse controls.** One user can repeatedly create unique unpaid orders and monopolize scarce slots. **Fix:** Enforce database-backed per-user concurrent-hold limits, creation throttles, and cooldowns, then test repeated unique idempotency keys and multi-account pressure.
5. **High: The recovery production gate has no measurable success criteria.** Backup and restore drill lacks acceptable data loss, recovery time, integrity and restored-state verification. **Fix:** Define RPO, RTO, retention, encryption, point-in-time recovery, and a clean-environment restore drill that verifies business invariants and record counts.

VERDICT: REVISE

### Primary agent response

- Accepted all five findings. Every refund origin now uses one idempotent transactional refund-intent/outbox primitive, with crash-injection tests for cancellation, late payment and manual refund.
- Inventory release compares the active schedule version and uses `RECONCILE_PENDING`; stale inventory is excluded from availability until a versioned rebuild completes.
- Trading suspension uses independent cause blockers behind the same serialized aggregate gate. Reopening requires no active blocker at organization or store scope and has an overlapping-cause race test.
- Database-backed concurrent hold, rolling creation, expiry cooldown and hashed device/source pressure limits close unique-idempotency and multi-account slot abuse.
- The production gate now sets encrypted 30-day backups, binlog point-in-time recovery, `RPO ≤ 5 minutes`, `RTO ≤ 4 hours`, quarterly clean-environment restore drills and explicit business invariant verification.

## Round 5 — Codex

All Round 4 findings are closed:

- Refunds from cancellation, late payment, and manual action now share one transactional intent/outbox primitive.
- Stale schedule inventory enters `RECONCILE_PENDING` and cannot become visible prematurely.
- Concurrent suspension reasons use independent blockers and cannot reopen trading incorrectly.
- Database-backed hold quotas prevent idempotency-key and multi-account inventory abuse.
- Backup recovery now has measurable RPO/RTO, integrity checks, and a blocking production drill.

No remaining material blockers were found. The locked plan is internally implementable and has sufficient safety, recovery, and production verification boundaries.

VERDICT: APPROVED
