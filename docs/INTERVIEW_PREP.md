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
   - `DeadlockRetryExecutor` covers anything left over: on PostgreSQL deadlock code `40P01` it retries the whole transaction, up to 3 times.
3. **Payment happens outside the database transaction.**
   - The flow is `prepare` (a transaction that starts a 1-minute lease), then `charge` (no transaction), then `finalizePayment` (a new transaction).
   - `finalizePayment` checks again that the lease is still valid and that the customer still owns every seat.
   - If payment succeeds after the lease expired and the seat was resold, the customer gets a refund. The new owner keeps the seat.
   - The two transactional steps live in a separate bean, `BookingTransactionService`, because `@Transactional` is ignored when a class calls its own method.
4. **Idempotency.**
   - Every create request carries an `Idempotency-Key`. The server stores it with a SHA-256 hash of the request body.
   - Same key and same body returns the original result.
   - Same key and a different body returns `409 IDEMPOTENCY_KEY_REUSED`.
   - So a retried request can never charge or book twice.
5. **Outbox for notifications.**
   - The booking transaction also writes a notification row, so both commit together.
   - A worker picks up those rows using `FOR UPDATE SKIP LOCKED` and retries failures with backoff.
   - A notification is never lost, and sending one never slows down a booking.

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
| How do you stop two users booking the same seat? | A `FOR UPDATE` lock on the seat rows, taken in sorted order, all-or-nothing, backed by a unique constraint. Every losing request gets a 409. |
| Why pessimistic locking, not optimistic? | Contention on popular seats is high, so optimistic locking would mean many failed commits. Pessimistic locking gives one clean winner. The locks are short because no network call happens inside the transaction. |
| What happens when a hold expires? | A hold counts as expired as soon as `expires_at <= now`, in every query. The cleanup worker is only housekeeping; correctness doesn't depend on it. |
| Payment succeeds but the app crashes before confirming? | `CheckoutRecoveryWorker` asks the gateway for the result using the same idempotency key, then either confirms the booking or expires it. |
| How are prices and refunds handled? | Amounts are `BigDecimal`. Prices are copied onto the screening, and refund rules are copied onto each booking, so a later admin edit never changes an existing booking. When cancelling, the most generous rule that applies wins. |
| Why return 404, not 403, for another customer's booking? | A 403 would confirm the booking exists. Queries are scoped to the owner, so someone else's booking simply isn't found. |
| How did you test concurrency? | 25 virtual threads wait on a `CountDownLatch`, then all fire together. The test asserts exactly one 201 and 24 409s. |
| How would you scale this? | A waiting room in front of popular shows, read replicas or a cache for browsing, partitioning `screening_seat`, and moving the outbox onto Kafka through change data capture. |
| What would you improve? | Move from HTTP Basic to JWT/OAuth2, replace error-code strings with an enum, and fix the `PUT` response that returns the version from before the flush. |
| How did you use AI? | AGENTS.md rules first, then the design doc, then build and review one module at a time, then verify every flow with the runbook. *Be clear about which decisions were yours.* |

---

## Part E — Spring Boot questions tied to the project

1. **How does `@Transactional` work?**
   - It runs through a proxy, so a call from one method to another in the same class bypasses it.
   - By default it rolls back only on unchecked exceptions.
   - Propagation is `REQUIRED` by default; `REQUIRES_NEW` starts a separate transaction.
   - PostgreSQL defaults to `READ COMMITTED` isolation, which is why the explicit row locks are needed.
2. **`@Lock` and `@Version`.** `@Lock(PESSIMISTIC_WRITE)` turns the query into `SELECT … FOR UPDATE`. `@Version` gives optimistic locking, which the project uses for admin edits.
3. **Spring Security flow.**
   - A request passes through the `SecurityFilterChain`. `UserDetailsService` loads the user and BCrypt checks the password, then the role rules apply.
   - Failed authentication returns 401 from the entry point.
   - A missing role returns 403 from the access-denied handler.
   - CSRF protection is off because the API is stateless and uses no cookies.
4. **Error handling.**
   - `@RestControllerAdvice` turns exceptions into a `ProblemDetail`.
   - `@Valid` rejects malformed input with a 400.
   - The service layer enforces business rules and returns 409 or 422.
5. **JPA pitfalls.**
   - N+1 queries: fix with fetch joins.
   - Dirty checking: changes to loaded entities are saved without an explicit call.
   - Flush timing: explains the `PUT` version quirk.
   - Flyway owns the schema; Hibernate only validates it.
6. **Scheduling.** `@Scheduled(fixedDelay)` workers poll the outbox, rather than `@Async`, because `@Async` work is lost on a crash while outbox rows survive.

## Part F — Java questions tied to the project

1. **Why `BigDecimal` for money?**
   - `double` is imprecise: `0.1 + 0.2` is not `0.3`.
   - Compare with `compareTo`, not `equals`, because `2.0` and `2.00` are not `equals`.
2. **Why not Java `synchronized` for seats?** It only works inside one JVM. With several app instances, the lock has to live in the database.
3. **Deadlock.** There are four conditions for a deadlock. Sorting the IDs removes one of them: circular wait.
4. **Java 21 virtual threads.** They are lightweight and release their carrier thread while blocked on I/O. The concurrency test uses them.
5. **`CountDownLatch`.** One or more threads wait until a count reaches zero. The test uses it to release 25 threads at the same moment.
6. **HashMap vs ConcurrentHashMap.**
   - HashMap stores entries in buckets. A bucket turns into a tree after 8 entries, and the map resizes at a 0.75 load factor.
   - ConcurrentHashMap uses CAS plus a lock per bucket, which makes it thread-safe.
7. **Time handling.**
   - Store `Instant` values in UTC.
   - Decide whether a show falls on a weekend using the theater's `ZoneId`.
   - Inject a `Clock` so tests control time.
