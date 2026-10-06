# Interview Prep — Movie Ticket Booking System
## Part A — The 5 things you must truly understand

If you can whiteboard these five, most follow-up questions will come from one of them.

1. **No double booking.**
   - Each seat in each show is its own row in `screening_seat`.
   - A reservation sorts the seat IDs, locks those rows with `SELECT … FOR UPDATE`, and validates them.
   - It then updates all the seats or none of them.
   - Code: `ScreeningSeatRepository.findAllByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`) and `SeatReservationService`.
2. **Sorted lock order prevents deadlock.**
   - Requests for [10, 11] and [11, 10] both lock seat 10 first, so they cannot wait on each other in a circle.
   - `DeadlockRetryExecutor` covers anything left over: on PostgreSQL deadlock code `40P01` it retries the whole transaction, up to 3 times, with 5–20 ms jitter.
3. **Payment happens outside the database transaction.**
   - The flow is `prepare` (a transaction that starts a 1-minute lease), then `charge` (no transaction), then `finalizePayment` (a new transaction).
   - `finalizePayment` checks again that the lease is still valid and that the customer still owns every seat.
   - If payment succeeds but the lease has expired or the seats are no longer held, the booking fails and a `LATE_PAYMENT` refund is created. Whoever holds the seat now keeps it.
   - The two transactional steps live in a separate bean, `BookingTransactionService`, because `@Transactional` is ignored when a class calls its own method.
4. **Idempotency.**
   - Every create request carries an `Idempotency-Key`. The server stores it, unique per customer, together with a SHA-256 fingerprint of the fields that matter: the sorted seat IDs for a reservation, or the reservation ID plus the normalized discount code for a booking.
   - Same key and same fingerprint returns the original result.
   - Same key and a different fingerprint returns `409 IDEMPOTENCY_KEY_REUSED`.
   - A second key, `customerId:key`, goes to the payment gateway, so a retried request can never charge twice.
5. **Outbox for notifications.**
   - The booking transaction also writes a notification row, so both commit together.
   - A worker picks up those rows using `FOR UPDATE SKIP LOCKED` and a 1-minute lease, and retries failures with exponential backoff (2^n seconds, up to 5 attempts, then `FAILED`).
   - Delivery is at least once: no notification is lost, but a duplicate is possible, so receivers must ignore repeats by event ID. Sending never slows down a booking.

---

## Part B — Deep dives (whiteboard these)

### B1. What happens when a hold expires?

**Nothing changes in the database at the moment a hold expires.** Expiry is not checked in every query. Three mechanisms deal with it instead.

| Table | Column(s) | Role |
|---|---|---|
| `seat_reservation` | `state`, `expires_at`, `checkout_expires_at` | **The clock.** The only place that knows a hold has expired. |
| `screening_seat` | `state`, `reservation_id` | **The inventory.** Still says `RESERVED` after the hold expires, until something releases it. |
| `booking` | `state`, `checkout_expires_at` | The separate 1-minute payment lease. |

```
T+0        POST /reservations → seat_reservation ACTIVE, expires_at = T+4m
                                screening_seat RESERVED (reservation_id = R)
T+0..3m30  POST /bookings allowed (needs ≥ 30s left on the hold)
           → reservation PAYMENT_IN_PROGRESS, checkout_expires_at = now+1m
T+4m       hold expires. NOTHING is written at this instant.
T+4m..4m30 the sweeper (every 30s) or another request notices and fixes it.
```

1. **Reads compute the real state without writing.** The seat-map query (`ScreeningSeatRepository.findAvailability`) joins `seat_reservation` and reports `AVAILABLE` when `expires_at <= now` or `checkout_expires_at <= now`. Customers never see a seat that looks taken when it isn't.
2. **Writes on those seats clean up first.** When someone tries to reserve seats, `SeatReservationService.create` does three things in order:
   - It calls `expireCheckoutForReservation` (`REQUIRES_NEW`), which ends any payment lease that has already expired on those seats.
   - It locks the seat rows.
   - It calls `reclaimExpired`, which marks an expired reservation `EXPIRED` and frees its seats, before checking `AVAILABLE`.

   `GET /reservations/{id}` also expires the hold on demand. Checkout rejects a hold that has expired or has less than 30 seconds left.
3. **A background sweeper handles the rest.** `ReservationExpiryWorker` runs every 30 seconds with `WHERE state='ACTIVE' AND expires_at <= now ORDER BY expires_at, id FOR UPDATE SKIP LOCKED LIMIT 100`. The partial index `idx_reservation_active_expiry … WHERE state='ACTIVE'` keeps that query cheap. `CheckoutRecoveryWorker` asks the gateway about each expired checkout. If a charge went through, it finalizes the booking (a late payment gets a refund). If not, it expires the checkout.

**Why all three:** with only the sweeper, freed seats could look taken for up to 30 seconds. With only on-demand cleanup, abandoned rows would never be tidied. Correctness never depends on when the sweeper runs.

### B2. Idempotency: two layers

```
Client ──Idempotency-Key: K──▶ API
                               │
          Layer 1 (API): UNIQUE(customer_id, idempotency_key) + request_fingerprint
                         in seat_reservation and booking
                               │
          Layer 2 (provider): payment.gateway_idempotency_key = "customerId:K" (UNIQUE)
                               ▼
                         PaymentGateway.charge(key, …)
```

```
Request with key K
      │
      ▼
 Row exists for (customer, K)? ──no──▶ do the work, store K + fingerprint
      │yes
      ▼
 Same fingerprint? ──no──▶ 409 IDEMPOTENCY_KEY_REUSED
      │yes
      ▼
 Return the stored result (no new side effects)
```

**Crash in the middle of checkout:**

```
 TX1 prepare()            charge() (outside any TX)     TX2 finalizePayment()
 booking PENDING_PAYMENT ─▶ gateway.charge("cust:K") ──▶ booking CONFIRMED
 payment INITIATED                    │                  + outbox row
 COMMIT                               ✗ crash here
                                      │
     ┌────────────────────────────────┴──────────────────────────┐
     ▼                                                           ▼
 Client retries with K                            CheckoutRecoveryWorker (after lease)
 → existing booking, requiresCharge=true          → gateway.findChargeResult("cust:K")
 → charge("cust:K") again → the gateway           → found: finalizePayment
   returns the SAME result (no double charge)     → not found: expireCheckout
```

**Concurrent duplicates:**
- Booking: `lockCustomer` makes requests from the same customer run one after the other, so the second sees the first one's row and replays it.
- Reservation: both requests lock the same seats. The second waits, then finds them `RESERVED` and gets `409 SEAT_UNAVAILABLE`, not a replay. A later retry does get the replay. The unique constraint is the final safety net.

### B3. Transactional outbox

**The problem it solves (dual write):**
```
  commit booking ✓ ──▶ send email ✗ (crash)   → confirmed booking, no email
  send email ✓ ──▶ commit booking ✗           → email sent for a booking that doesn't exist
```

**The flow:**
```
┌──────────── TX finalizePayment (one atomic commit) ────────────┐
│ booking → CONFIRMED,  screening_seat → BOOKED                  │
│ outbox_event INSERT (PENDING, business_key="booking-confirmed:ID")│
└────────────────────────────────────────────────────────────────┘
                    ▼
OutboxWorker every 5s
 ┌ TX claimReady ───────────────────────────────────────────┐
 │ (PENDING AND available_at<=now) OR (PROCESSING AND lease expired)
 │ FOR UPDATE SKIP LOCKED LIMIT 100   ← many workers, no double claim
 │ → PROCESSING, lease = now+1m, attempt_count++            │
 └ COMMIT ──────────────────────────────────────────────────┘
                    ▼
       NotificationGateway.deliver()   (outside any TX)
                    ▼
 ┌ TX complete ───────────────────────────────────────┐
 │ success           → DELIVERED                       │
 │ retryable & < 5   → PENDING, available_at = now+2^n │
 │ otherwise         → FAILED (terminal, needs ops)    │
 └─────────────────────────────────────────────────────┘
```

- **At least once:** if the worker crashes after sending but before marking `DELIVERED`, the lease expires and the event is sent again.
- `business_key UNIQUE` (reminders use `ON CONFLICT DO NOTHING`) prevents duplicate events from being created.
- The claim commits before the network call, so no lock or connection is held while sending.

### B4. Known gaps (good "what would you improve?" material)

- **Releasing a reservation during checkout.** `ScreeningSeat.release()` frees seats in `PAYMENT_IN_PROGRESS`, but `SeatReservation.release()` ignores that state. `DELETE /reservations/{id}` during checkout frees the seats while the reservation stays `PAYMENT_IN_PROGRESS`. A successful charge then ends `FAILED` with a `LATE_PAYMENT` refund. No seat is sold twice, but the delete should be rejected with a 409.
- **Outbox backoff has no jitter.** It uses a plain 2^n seconds delay, while the project guidelines ask for bounded exponential backoff with jitter.

---

## Part C — Tech choices: what we picked and what we passed on

Each row gives the choice, the main alternatives, and the one-line reason. Interviewers often ask "why not X?", so know the third column.

| Concern | Chosen | Considered / ignored | Why |
|---|---|---|---|
| Language | **Java 21 (LTS)** | Java 17, Kotlin | Latest LTS. Virtual threads and records are available (the concurrency test uses virtual threads). Kotlin added no value for this brief. |
| Framework | **Spring Boot 4** | Quarkus, Micronaut | Required by the brief. One ecosystem covers web, validation, security, JPA, transactions and scheduling. |
| Build | **Maven** | Gradle | Conventional and reproducible, with a declarative plugin setup for OpenAPI generation and failsafe integration tests. |
| Architecture | **Modular monolith** | Microservices | The brief puts distributed systems out of scope. A booking must be atomic across seats, payment and discount, which one database gives. Clear module boundaries keep a later split possible. |
| Database | **PostgreSQL** | MySQL, MongoDB, Redis | Needs multi-row ACID, `FOR UPDATE`, `SKIP LOCKED`, `ON CONFLICT` and unique constraints. MySQL 8 could also work. MongoDB and Redis lack relational constraints and an easy multi-row atomic update. |
| Schema migrations | **Flyway** (`ddl-auto=validate`) | Liquibase, Hibernate `ddl-auto=update` | Plain, reviewable versioned SQL (V1–V9). Letting Hibernate change the schema is unsafe and hard to review. |
| Data access | **Spring Data JPA + explicit lock queries** | JdbcTemplate, jOOQ, MyBatis | Fast CRUD for the catalog, with native/JPQL queries plus `@Lock` where it matters. jOOQ or plain JDBC would mean more code for little gain. |
| Seat concurrency | **Pessimistic row locks, sorted order** | Optimistic `@Version`, Redis lock, PostgreSQL advisory locks, `SERIALIZABLE` isolation | High contention on popular seats means optimistic locking produces many failed retries. A Redis lock adds a second source of truth. Advisory locks are not tied to the rows. `SERIALIZABLE` causes broad aborts. Row locks give one clean winner. |
| Inventory model | **One `screening_seat` row per seat per show** | Bitmap or JSON column per screening, counting booked seats | Gives a precise row to lock and a single source of availability. A per-screening column would lock the whole show on every booking. |
| Async work | **Transactional outbox + `@Scheduled` workers** | `@Async`, Spring application events, Kafka, RabbitMQ | `@Async` work and in-memory events are lost on a crash. Kafka and RabbitMQ are extra infrastructure the brief rules out. The outbox commits together with the booking. |
| Job claiming | **`FOR UPDATE SKIP LOCKED` + lease** | Quartz, ShedLock | Safe with several app instances and no extra library. A crashed worker's lease expires, so another worker reclaims the job. |
| Auth | **HTTP Basic + BCrypt, stateless** | JWT, OAuth2/OIDC, session cookies | Advanced auth is out of scope. Basic is stateless and easy to test with curl. JWT/OAuth2 is the production path. |
| API definition | **Contract-first OpenAPI 3.1 + openapi-generator** | Code-first springdoc annotations | Controllers implement interfaces generated from the spec, so any drift from the contract fails to compile. The spec is the single source of truth. |
| Errors | **RFC 9457 `ProblemDetail`** | Custom error JSON | A standard format, built into Spring. It adds a stable `code`, a `requestId` and per-field errors. |
| Pagination | **Offset for admin/catalog, HMAC-signed keyset cursor for bookings/screenings** | Offset everywhere | Offset skips or duplicates rows when the data changes underneath it, and deep pages get slow. Cursors are signed so they cannot be tampered with. |
| Money | **`BigDecimal` + `NUMERIC(12,2)`, INR only** | `double`, integer paise, multi-currency | Exact decimals, and the code reads like the domain. Multi-currency was out of scope. |
| Time | **`Instant` (UTC) + injected `Clock`** | `LocalDateTime`, calling `now()` directly | Avoids time-zone ambiguity, and tests can move the clock forward deterministically. |
| External providers | **Gateway interfaces with local fake adapters** | Real Stripe or email providers | Deterministic, needs no credentials, and keeps vendor code out of the business layer. |
| Tests | **JUnit 5 + Testcontainers PostgreSQL** | H2, Mockito-heavy mocks | H2 does not behave like PostgreSQL for locking, `SKIP LOCKED` or `ON CONFLICT`. Fakes such as `ScriptedPaymentGateway` stay closer to real behaviour than mocks. |
| Boilerplate | **Plain Java (no Lombok/MapStruct)** | Lombok, MapStruct | Fewer dependencies and no annotation-processing surprises. The generated OpenAPI models cover the DTOs. |

---

## Part D — Likely project questions (short answers)

| Question | Answer |
|---|---|
| How do you stop two users booking the same seat? | A `FOR UPDATE` lock on the seat rows, taken in sorted order, all-or-nothing. `UNIQUE (screening_id, seat_id)` guarantees one inventory row per seat per show. Every losing request gets a 409. |
| Why pessimistic locking, not optimistic? | Contention on popular seats is high, so optimistic locking would mean many failed commits. Pessimistic locking gives one clean winner. The locks are short because no network call happens inside the transaction. |
| What happens when a hold expires? | Nothing is written at that moment. Reads work out the real state by comparing `seat_reservation.expires_at` with now. Any write on those seats releases an expired hold first. A sweeper every 30 seconds (`SKIP LOCKED`, batches of 100) tidies the rest. Correctness doesn't depend on the sweeper. See B1. |
| Do you check expiry in every query? | No. Only the seat-map read, reservation `GET`, reserve and checkout check it. Everything else relies on `seat_reservation` being the only clock, plus the sweeper. |
| Payment succeeds but the app crashes before confirming? | Either the client retries with the same key (the gateway returns the stored result, so there's no second charge), or `CheckoutRecoveryWorker` asks the gateway after the lease expires and confirms or expires the booking. See B2. |
| How are prices and refunds handled? | Amounts are `BigDecimal`. Prices are copied onto the screening, and refund rules are copied onto each booking, so a later admin edit never changes an existing booking. When cancelling, the most generous rule that applies wins. |
| Why return 404, not 403, for another customer's booking? | A 403 would confirm the booking exists. Queries are scoped to the owner, so someone else's booking simply isn't found. |
| How did you test concurrency? | 25 virtual threads wait on a `CountDownLatch`, then all fire together. The test asserts exactly one 201 and 24 409s. |
| How would you scale this? | A waiting room in front of popular shows, read replicas or a cache for browsing, partitioning `screening_seat`, and moving the outbox onto Kafka through change data capture. |
| What would you improve? | Move from HTTP Basic to JWT/OAuth2, replace error-code strings with an enum, fix the `PUT` response that returns the version from before the flush, and close the gaps in B4. |
| How did you use AI? | AGENTS.md rules first, then the design doc, then build and review one module at a time, then verify every flow with the runbook. *Be clear about which decisions were yours.* |

---

## Part E — Spring Boot questions tied to the project

1. **How does `@Transactional` work?**
   - It runs through a proxy, so a call from one method to another in the same class bypasses it. That's why `BookingTransactionService` is a separate bean, and why `SeatReservationService` uses `TransactionTemplate`: the deadlock retry has to wrap the whole transaction from the outside.
   - By default it rolls back only on unchecked exceptions and `Error`s. Checked exceptions need `rollbackFor`.
   - Propagation is `REQUIRED` by default. `REQUIRES_NEW` starts a separate transaction: `expireCheckoutForReservation` uses it so its cleanup commits even if the reservation that triggered it fails.
   - PostgreSQL defaults to `READ COMMITTED` isolation, which is why the explicit row locks are needed.
   - `readOnly = true` skips Hibernate dirty checking and flushing.
2. **Why not call the payment provider inside a transaction?** It would hold row locks and a database connection for the whole network call, so a slow provider would use up the connection pool.
3. **`@Lock` and `@Version`.** `@Lock(PESSIMISTIC_WRITE)` turns the query into `SELECT … FOR UPDATE`. `@Version` (on `AuditableEntity`) gives optimistic locking, which the project uses for admin edits.
4. **Spring Security flow.**
   - A request passes through the `SecurityFilterChain`. `UserDetailsService` loads the user and BCrypt checks the password, then the role rules apply.
   - Failed authentication returns 401 from the entry point.
   - A missing role returns 403 from the access-denied handler. Both happen in filters, before `@RestControllerAdvice` is reached.
   - CSRF protection is off because the API is stateless and uses no cookies.
5. **Error handling.**
   - `@RestControllerAdvice` turns exceptions into a `ProblemDetail`.
   - `@Valid` rejects malformed input with a 400.
   - The service layer enforces business rules and returns 409 or 422.
6. **JPA pitfalls.**
   - N+1 queries: fix with fetch joins, `@EntityGraph` or batch loading. `BookingService.history` loads details, payments and refunds in one query each.
   - Dirty checking: changes to loaded entities are saved without an explicit call.
   - Flush timing: explains the `PUT` version quirk.
   - Flyway owns the schema; Hibernate only validates it.
7. **Scheduling.**
   - `@Scheduled(fixedDelay)` workers poll the outbox, rather than `@Async`, because `@Async` work is lost on a crash while outbox rows survive.
   - `fixedDelay` waits for the previous run to finish; `fixedRate` doesn't.
   - All scheduled jobs share one thread by default.
   - With several instances, every instance runs every job, which is why claiming uses `SKIP LOCKED`. ShedLock is the alternative.
8. **Auto-configuration and conditional beans.** Auto-configuration works through `@Conditional*` annotations. The project uses `@ConditionalOnProperty(app.booking.enabled)` and similar flags to turn modules on or off.
9. **Constructor injection and `ObjectProvider`.** Constructor injection makes dependencies immutable and easy to test. `ObjectProvider` (`bookingTransactions.ifAvailable`) handles a dependency that may be missing or needs to be resolved lazily.
10. **Config.** `@Value` vs `@ConfigurationProperties`, profiles (`demo`, `capacity`), and the order in which property sources override each other.
11. **Testable time.** Inject a `Clock` bean instead of calling `Instant.now()`. `MutableClock` in the tests moves time past the expiry deterministically.
12. **Testing approaches.** `@SpringBootTest` vs slice tests (`@WebMvcTest`, `@DataJpaTest`), and Testcontainers because H2 can't test `SKIP LOCKED` or partial indexes.
13. **Production readiness.** Actuator health/readiness probes, and graceful shutdown: an outbox event in the middle of delivery has its lease expire and is sent again.

## Part F — Java questions tied to the project

1. **Why `BigDecimal` for money?**
   - `double` is imprecise: `0.1 + 0.2` is not `0.3`.
   - Compare with `compareTo`, not `equals`, because `2.0` and `2.00` are not `equals`.
2. **Why not Java `synchronized` for seats?** It only works inside one JVM. With several app instances, the lock has to live in the database.
3. **Deadlock.** There are four conditions for a deadlock: mutual exclusion, hold and wait, no preemption, and circular wait. Sorting the IDs removes circular wait. Deadlocks can still happen across tables, so `40P01` is retried.
4. **Check-then-act race.** "If the key doesn't exist, insert it" lets two threads both see "doesn't exist". Fix it with an atomic step: a unique constraint, `ON CONFLICT`, or a lock.
5. **`volatile` vs `synchronized` vs `Atomic*`.**
   - `volatile` makes changes visible to other threads but doesn't make compound actions atomic.
   - `synchronized` gives visibility plus mutual exclusion.
   - `Atomic*` classes use compare-and-swap (CAS). Know the ABA problem.
6. **Happens-before.** The rules that guarantee one thread sees another's writes: an unlock before a later lock, a `volatile` write before a later read, `Thread.start()`/`join()`, and submitting work to an executor.
7. **HashMap vs ConcurrentHashMap.**
   - HashMap stores entries in buckets. A bucket turns into a tree once it holds 8 or more entries and the table has at least 64 buckets (otherwise the map resizes instead). The map also resizes at a 0.75 load factor.
   - ConcurrentHashMap uses CAS to fill an empty bucket and locks only the first node of a bucket when writing to it. Reads never lock.
   - `computeIfAbsent` is atomic per key. `LocalPaymentGateway` relies on this so the same key always gets the same charge result. Keep the function short, and don't change the map from inside it.
8. **`InterruptedException`.** Restore the flag with `Thread.currentThread().interrupt()` instead of swallowing it, as `DeadlockRetryExecutor.pause` does.
9. **`ThreadLocal` in thread pools.** A value stays on the thread and leaks into the next task unless it is cleared. Spring keeps the security context and the current transaction in `ThreadLocal`s, which is why `@Async` threads don't inherit them.
10. **Java 21 virtual threads.**
    - They are lightweight and release their carrier thread while blocked on I/O. The concurrency test uses them.
    - `synchronized` "pins" the carrier thread (fixed in JDK 24).
    - They don't enlarge the database connection pool; the pool is still the bottleneck.
11. **`ExecutorService` / `CompletableFuture`.**
    - Size pools to the work: about one thread per core for CPU-bound work, more for I/O-bound work.
    - Prefer bounded queues and pick a rejection policy.
    - `supplyAsync` uses the common pool unless you pass an executor.
12. **`CountDownLatch`, `CyclicBarrier`, `Semaphore`.**
    - A latch makes threads wait until a count reaches zero. The test uses one to release 25 threads at the same moment.
    - A barrier can be reused.
    - A semaphore limits how many threads can do something at once.
13. **Database isolation.** `READ COMMITTED` allows lost updates without locks. `SKIP LOCKED` lets several workers pull from a queue table without blocking each other.
14. **Time handling.**
    - Store `Instant` values in UTC.
    - Decide whether a show falls on a weekend using the theater's `ZoneId`.
    - Inject a `Clock` so tests control time.
