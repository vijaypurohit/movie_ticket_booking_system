# Movie Ticket Booking System

A Spring Boot REST API for multi-city cinema ticketing. It covers the full customer journey — browse a city's shows, hold exact seats for four minutes, pay, confirm, cancel under a configurable refund policy — and the administrative side that defines cities, theaters, seat layouts, pricing tiers, discounts and screenings.

The central guarantee the design is built around:

> A seat in a screening belongs to at most one active reservation or confirmed booking, no matter how many customers attempt it simultaneously.

Everything else — the four-minute hold, the separate checkout lease, the compensating refund for a late payment — exists to keep that true while still allowing a real payment call that takes unpredictable time.

---

## Table of contents

1. [Technology and why](#1-technology-and-why)
2. [Architecture](#2-architecture)
3. [Navigating the codebase](#3-navigating-the-codebase)
4. [First-time setup](#4-first-time-setup)
5. [Demo data reference](#5-demo-data-reference)
6. [Guided demo walkthrough](#6-guided-demo-walkthrough)
7. [Exercising edge cases by hand](#7-exercising-edge-cases-by-hand)
8. [Configuration](#8-configuration)
9. [API surface](#9-api-surface)
10. [Running the tests](#10-running-the-tests)
11. [Scope, assumptions and limitations](#11-scope-assumptions-and-limitations)
12. [AI-assisted workflow](#12-ai-assisted-workflow)

---

## 1. Technology and why

| Concern | Choice | Why this one |
|---|---|---|
| Runtime | Java 21 | Current LTS; virtual threads make the concurrency tests cheap to write |
| Framework | Spring Boot 4.1 | REST, validation, security, transactions and scheduling in one dependency set |
| Build | Maven Wrapper | No local Maven install; one reproducible command |
| Database | PostgreSQL 15+ | `SELECT … FOR UPDATE`, `SKIP LOCKED`, check constraints and real transaction semantics *are* the booking contract |
| Migrations | Flyway | Reviewable, ordered schema changes; Hibernate is set to `validate`, never `create` |
| Persistence | Spring Data JPA + explicit locking queries | Fast CRUD, with hand-written queries wherever a lock or a lock order matters |
| API docs | springdoc-openapi | The contract is generated from the code, so it cannot drift |
| Errors | RFC 9457 Problem Details | One machine-readable error shape across every endpoint |
| Tests | JUnit 5, AssertJ, Testcontainers PostgreSQL | Tests run against the same engine as production |

Two choices are worth defending explicitly:

**PostgreSQL rather than a document or key-value store.** Reserving three seats must atomically succeed or fail across three rows. That is a transaction with row locks, which is exactly what a relational engine is for.

**Testcontainers PostgreSQL rather than H2.** H2 emulates neither `SKIP LOCKED` nor PostgreSQL's deadlock detection and error codes. Since the system's core guarantee depends on both, testing against H2 would test something that is not the production behaviour. H2 is deliberately absent from the dependency list.

---

## 2. Architecture

A modular monolith. Every module follows the same one-way dependency:

```
Controller  →  Service  →  Repository / Gateway  →  Model / Database
  (HTTP)      (rules,        (queries, locks)        (entities)
             transactions)
```

- **Controllers** validate request shape and map responses. No business logic, no repository access.
- **Services** own authorization, business rules, state transitions and transaction boundaries.
- **Repositories** own filtering, sorting, pagination and locking. Nothing is filtered or sorted in memory.
- **Gateways** isolate payment and notification providers so vendor concepts never reach the domain.

A module may call another module's *application service*, never another module's repository.

### The three mechanisms that matter

**Seat allocation.** Creating a screening materialises one `screening_seat` row per active physical seat. That row is the lock target and the single source of availability. A reservation sorts the requested seat IDs ascending, locks them in that order with `FOR UPDATE`, validates, and updates all or none. Because every code path uses the same order, `[10, 11]` and `[11, 10]` cannot deadlock against each other. A PostgreSQL `40P01` deadlock is retried outside the failed transaction, at most three times.

**Time-bounded holds.** A reservation lives four minutes. Checkout requires at least 30 seconds remaining and replaces the deadline with a separate one-minute lease. The payment gateway is called **outside** any database transaction; afterwards the booking, reservation and seats are locked again and the lease re-checked. If the gateway succeeds after the lease expired, the payment is recorded, the booking stays failed, and one idempotent compensating refund is queued — a seat already taken by someone else is never reclaimed. Expiry is *logical*: a hold whose deadline has passed is treated as free immediately, so the cleanup worker is housekeeping, not a correctness dependency.

**Asynchronous notifications.** Confirmation, cancellation, refund and reminder messages are written to a transactional outbox in the same transaction as the business change. Workers claim at most 100 rows with `FOR UPDATE SKIP LOCKED`, call the gateway after the claim commits, and use bounded retries with processing leases. A failing notification provider can never roll back a booking.

---

## 3. Navigating the codebase

```
src/main/java/com/vijaypurohit/movietickets/
├── identity/       users, BCrypt credentials, ADMIN/CUSTOMER roles          (8 files)
├── catalog/        cities, theaters, auditoriums, seats, movies            (20 files)
├── pricing/        pricing plans, discount codes, refund policies          (16 files)
├── screening/      scheduling, screening-seat inventory, public browse     (16 files)
├── reservation/    four-minute holds, expiry, deadlock retry                (9 files)
├── booking/        checkout, confirmation, history, cancellation           (17 files)
├── payment/        payments, refunds, local payment adapter                (13 files)
├── notification/   outbox, delivery worker, reminders, local adapter        (8 files)
├── demo/           deterministic seed and capacity dataset                  (3 files)
└── shared/         errors, pagination, clock, IDs, security, OpenAPI       (30 files)

src/main/resources/db/migration/   V1 … V9, applied in order by Flyway
```

Each domain module is split the same way: `web/` → `application/` → `persistence/` → `model/`.

**If you only read five files**, read these — they contain the logic the whole design turns on:

| File | What it decides |
|---|---|
| [SeatReservationService.java](src/main/java/com/vijaypurohit/movietickets/reservation/application/SeatReservationService.java) | Sorted lock acquisition, idempotency, logical expiry |
| [BookingTransactionService.java](src/main/java/com/vijaypurohit/movietickets/booking/application/BookingTransactionService.java) | Checkout preparation, lease re-check, late-payment compensation |
| [BookingCancellationService.java](src/main/java/com/vijaypurohit/movietickets/booking/application/BookingCancellationService.java) | Refund-rule selection against the booking's own snapshot |
| [ScreeningCancellationService.java](src/main/java/com/vijaypurohit/movietickets/booking/application/ScreeningCancellationService.java) | Admin cancels a show; bounded full-refund drain |
| [ScreeningSeat.java](src/main/java/com/vijaypurohit/movietickets/screening/model/ScreeningSeat.java) | The seat state machine; every transition is guarded here |

**To trace one request end to end**, follow `POST /api/v1/bookings`:
`BookingController` → `BookingService.create` → `BookingTransactionService.prepare` (transaction 1, commits) → `PaymentGateway.charge` (no transaction) → `BookingTransactionService.finalizePayment` (transaction 2).

---

## 4. First-time setup

### Prerequisites

- **JDK 21** — `java -version` must report 21
- **PostgreSQL 15+** running locally
- **Docker** — only for the integration test suites, not for running the app

### Step 1 — create the database

```sql
CREATE ROLE movie_tickets LOGIN PASSWORD 'choose-a-local-password';
CREATE DATABASE movie_tickets OWNER movie_tickets;
```

### Step 2 — configure

```bash
cp .env.example .env
```

Edit `.env` and set two values:

```properties
DB_PASSWORD=the-password-you-just-chose
CURSOR_SIGNING_KEY=at-least-32-random-characters-goes-here
APP_DEMO_ENABLED=true
```

`CURSOR_SIGNING_KEY` must be at least 32 UTF-8 bytes — the application refuses to start otherwise, because pagination cursors are HMAC-signed to stop clients forging them. `.env` is gitignored; never commit it.

### Step 3 — run

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Flyway applies `V1`–`V9` on startup, Hibernate then validates that the mapped entities match the migrated schema, and the demo seeder loads the dataset below.

### Step 4 — confirm it is up

```bash
curl -s localhost:8080/api/v1/cities | head -c 200
```

You should see `Codex Demo Pune`. Then open **http://localhost:8080/swagger-ui.html** — every endpoint below is executable from there.

---

## 5. Demo data reference

With `APP_DEMO_ENABLED=true`, the seeder creates this exact dataset. It uses deterministic IDs, so restarting the app never duplicates it.

**Accounts**

| Role | Username | Password |
|---|---|---|
| Admin | `admin@movietickets.local` | `Admin@123` |
| Customer | `customer1@movietickets.local` | `Customer@123` |
| Customer | `customer2@movietickets.local` | `Customer@123` |

**Catalog**

| Thing | Value |
|---|---|
| City | Codex Demo Pune (India, `Asia/Kolkata`) |
| Theater | Codex Demo Cinema, Baner, Pune |
| Auditorium | Screen 1 |
| Seats | `A1`, `A2` (REGULAR) · `B1`, `B2` (PREMIUM) — **4 seats total** |
| Movie | The Last Commit, 120 min, English |
| Screenings | 18:30 IST, on each of the **next 7 days** starting tomorrow |

**Prices** (regular ₹250, premium ₹400, weekend surcharge ₹50, snapshotted per screening at creation)

| Day | REGULAR | PREMIUM |
|---|---|---|
| Weekday | ₹250 | ₹400 |
| Sat / Sun | ₹300 | ₹450 |

**Refund policy** — cancel at least *N* minutes before the show starts:

| Minutes before start | Refund |
|---|---|
| ≥ 1440 (24 h) | 100% |
| ≥ 120 (2 h) | 50% |
| ≥ 0 | 0% |

**Payment tokens** — `tok_success` succeeds; `tok_decline` and anything else declines. Tokens are never stored or logged.

> **Note:** the demo seeder does **not** create a discount code. To demo discounts, create one first — see [§7](#7-exercising-edge-cases-by-hand).

### Larger dataset

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw spring-boot:run -Dspring-boot.run.profiles=capacity
```

Generates 3 cities, 10 theaters, 30 auditoriums, 4,500 physical seats, 20 movies, 840 screenings and ~126,000 screening-seat rows. Add `APP_DEMO_HISTORY_ENABLED=true` for 1,000 customers and 10,000 bookings (`capacity.customer.0000@movietickets.local` … `0997`, password `Capacity@123`). Use this to see that pagination, indexes and worker batches stay bounded on a realistic dataset.

---

## 6. Guided demo walkthrough

Roughly ten minutes end to end, entirely through Swagger UI or `curl`. Every creation endpoint needs a **fresh** `Idempotency-Key`.

### 6.1 Browse anonymously

No authentication required:

1. `GET /api/v1/cities` → copy the city `id`
2. `GET /api/v1/movies?cityId={cityId}&date=YYYY-MM-DD` (use tomorrow's date) → copy the movie `id`
3. `GET /api/v1/screenings?cityId={cityId}&movieId={movieId}&date=YYYY-MM-DD` → copy a screening `id`
4. `GET /api/v1/screenings/{screeningId}/seats` → note the four seats, all `AVAILABLE`, and copy two `screeningSeatId` values

### 6.2 Hold two seats

Authorize as `customer1`. `POST /api/v1/seat-reservations`, key `demo-res-1`:

```json
{ "screeningSeatIds": ["<seat-A1-id>", "<seat-B1-id>"] }
```

Returns a reservation with `expiresAt` four minutes out. Re-run `GET /screenings/{id}/seats` in another tab — those two seats now read `RESERVED`, the other two are still `AVAILABLE`.

### 6.3 Pay and confirm

`POST /api/v1/bookings`, key `demo-book-1`:

```json
{ "reservationId": "<reservation-id>", "paymentToken": "tok_success" }
```

Check the response: `state: CONFIRMED`, `paymentStatus: SUCCEEDED`, and `items[]` carrying the seat labels and per-seat prices captured at checkout. A weekday screening gives ₹250 + ₹400 = **₹650**; a weekend one ₹300 + ₹450 = **₹750**.

Then `GET /api/v1/bookings?limit=20` — the booking appears in `customer1`'s history.

### 6.4 Cancel and refund

`POST /api/v1/bookings/{bookingId}/cancellations`, key `demo-cancel-1`. Because the show is more than 24 hours away, the policy returns **100%**. The response carries the refund `id`.

Refunds are processed asynchronously. Wait ~5 seconds for the worker, then `GET /api/v1/refunds/{refundId}` → `SUCCEEDED`.

Re-run `GET /screenings/{id}/seats` — both seats are `AVAILABLE` again and immediately re-bookable.

### 6.5 Show that it is idempotent

Repeat the cancellation with the **same** key `demo-cancel-1`: the same refund comes back, and no second refund is created.

---

## 7. Exercising edge cases by hand

Each recipe uses only the demo dataset. These are the behaviours worth showing a reviewer.

### Concurrency — two customers, one seat

The headline guarantee. Both requests, same seat, at once:

```bash
SEAT=<a-screening-seat-id>
curl -s -u customer1@movietickets.local:Customer@123 -H 'Idempotency-Key: race-1' \
  -H 'Content-Type: application/json' -d "{\"screeningSeatIds\":[\"$SEAT\"]}" \
  localhost:8080/api/v1/seat-reservations -o /tmp/a.json -w '%{http_code}\n' &
curl -s -u customer2@movietickets.local:Customer@123 -H 'Idempotency-Key: race-2' \
  -H 'Content-Type: application/json' -d "{\"screeningSeatIds\":[\"$SEAT\"]}" \
  localhost:8080/api/v1/seat-reservations -o /tmp/b.json -w '%{http_code}\n' &
wait
```

Exactly one `201`, one `409 SEAT_UNAVAILABLE`. The automated version of this runs 25 customers at once — see `ReservationConcurrencyIT`.

### Hold expiry without any worker

Reserve a seat, then wait four minutes and ask for it as `customer2`. It is granted, because expiry is evaluated logically rather than by the cleanup worker. Then try to pay with `customer1`'s stale reservation → `409 RESERVATION_NOT_ACTIVE`.

To avoid waiting, set `APP_BOOKING_RESERVATION_DURATION=PT20S` and restart.

### Payment decline releases the seat

Reserve, then book with `"paymentToken": "tok_decline"`. The booking comes back `FAILED` / `DECLINED`, and the seat is `AVAILABLE` again immediately. No confirmation notification is sent.

### Too little time left to pay

Set `APP_BOOKING_RESERVATION_DURATION=PT35S`, restart, reserve, wait ~10 seconds, then book → `409 INSUFFICIENT_CHECKOUT_TIME`. Checkout is refused unless 30 seconds remain, so a payment cannot start against a hold that is about to lapse.

### Refund tiers

**Every seeded screening is more than 24 hours away, so the stock demo data only ever
produces a 100% refund.** To show the lower tiers, create a screening a few hours out as
`admin` first — `POST /admin/api/v1/screenings`:

```json
{
  "movieId": "<from GET /admin/api/v1/movies>",
  "auditoriumId": "<from GET /admin/api/v1/theaters/{id}/auditoriums>",
  "pricingPlanId": "<from GET /admin/api/v1/pricing-plans>",
  "refundPolicyId": "<from GET /admin/api/v1/refund-policies>",
  "startTime": "<now + 3 hours, ISO-8601 UTC>",
  "endTime": "<now + 5 hours>"
}
```

Book a seat on it, cancel, and compare:

| Screening starts in | Refund | Verified |
|---|---|---|
| > 24 h (any seeded screening) | 100% | ₹650 paid → ₹650 back |
| 3 h (created as above) | 50% | ₹300 paid → ₹150 back |
| 1 h (`startTime` = now + 1 h) | 0% | ₹300 paid → ₹0, booking still `CANCELLED` |
| already started | — | `409 SCREENING_STARTED` |

Creating extra screenings in the same auditorium is fine as long as their time ranges do
not overlap; adjacent ranges are accepted.

### Discount codes

Not seeded — create one as `admin` first. `POST /admin/api/v1/discount-codes`:

```json
{
  "code": "DEMO10",
  "type": "PERCENTAGE",
  "value": "10.00",
  "validFrom": "2026-01-01T00:00:00Z",
  "validUntil": "2027-01-01T00:00:00Z",
  "minimumSpend": "100.00",
  "maximumDiscount": "100.00",
  "globalUsageLimit": 1,
  "perCustomerUsageLimit": 1
}
```

Then pass `"discountCode": "DEMO10"` in the booking body. With `globalUsageLimit: 1`, a second customer's booking is rejected `422 DISCOUNT_LIMIT_REACHED` — the limit is enforced under a row lock on the code, so it holds even under concurrent checkout.

### Admin cancels a show

Confirm a booking, then as `admin` call `DELETE /admin/api/v1/screenings/{screeningId}`. Every confirmed booking is cancelled, its seats released, and a **full** refund issued — the customer's cutoff tier is deliberately bypassed because the cancellation is not their fault. Check `GET /api/v1/bookings/{id}` as the customer: `CANCELLED`, with a `SHOW_CANCELLED` refund.

### Ownership is concealed, not denied

As `customer2`, fetch `customer1`'s booking → `404`, not `403`. Leaking "this exists but is not yours" is itself a disclosure.

### Validation and error shape

```bash
curl -s -u customer1@movietickets.local:Customer@123 -H 'Idempotency-Key: bad-1' \
  -H 'Content-Type: application/json' -d '{"screeningSeatIds":[]}' \
  localhost:8080/api/v1/seat-reservations | jq
```

Returns `application/problem+json` with `type`, `title`, `status`, `detail`, `instance`, `code`, `requestId` and `fieldErrors[]`. Every response also carries a server-generated `X-Request-Id`.

### Idempotency

| Try | Result |
|---|---|
| Same key, same body | Original result returned; nothing new created |
| Same key, different body | `409 IDEMPOTENCY_KEY_REUSED` |
| New key, same body | A genuinely new operation |

---

## 8. Configuration

Supplied through `.env`, environment variables, or any Spring configuration source.

| Setting | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/movie_tickets` | JDBC URL |
| `DB_USERNAME` | `movie_tickets` | Database role |
| `DB_PASSWORD` | none | Required locally |
| `CURSOR_SIGNING_KEY` | none | ≥ 32 UTF-8 bytes; signs opaque cursors |
| `APP_DEMO_ENABLED` | `false` | Load demo identities and the small dataset |
| `APP_DEMO_HISTORY_ENABLED` | `false` | Add 1,000 customers and 10,000 bookings |
| `APP_BOOKING_RESERVATION_DURATION` | `PT4M` | Seat-hold lifetime |
| `APP_BOOKING_MINIMUM_REMAINING` | `PT30S` | Minimum hold time left to start checkout |
| `APP_BOOKING_CHECKOUT_DURATION` | `PT1M` | Payment-in-progress lease |
| `APP_BOOKING_CLEANUP_DELAY` | `PT30S` | Expiry and recovery worker interval |
| `APP_REFUND_WORKER_DELAY` | `PT5S` | Refund worker interval |
| `APP_REFUND_MAX_ATTEMPTS` | `3` | Refund provider attempts before terminal failure |
| `APP_NOTIFICATION_WORKER_DELAY` | `PT5S` | Outbox delivery interval |
| `APP_NOTIFICATION_MAX_ATTEMPTS` | `5` | Delivery attempts before terminal failure |
| `APP_NOTIFICATION_REMINDER_LEAD` | `PT24H` | How far ahead reminders are sent |
| `APP_NOTIFICATION_REMINDER_WINDOW` | `PT5M` | Reminder eligibility window |

Shortening the durations is the intended way to demonstrate expiry without waiting.

---

## 9. API surface

- **Swagger UI** — http://localhost:8080/swagger-ui.html
- **OpenAPI JSON** — http://localhost:8080/v3/api-docs

| Access | Endpoints |
|---|---|
| Public | `GET /api/v1/cities`, `/theaters`, `/movies`, `/movies/{id}`, `/screenings`, `/screenings/{id}`, `/screenings/{id}/seats` |
| Customer | `POST`/`GET`/`DELETE` `/api/v1/seat-reservations`, `POST`/`GET` `/api/v1/bookings`, `POST /api/v1/bookings/{id}/cancellations`, `GET /api/v1/refunds/{id}` |
| Admin | `/admin/api/v1/` — `cities`, `theaters`, `theaters/{id}/auditoriums`, `auditoriums/{id}/seats`, `movies`, `pricing-plans`, `discount-codes`, `refund-policies`, `screenings` (incl. `DELETE` to cancel a show and refund its tickets) |

Conventions:

- Reservation, booking and cancellation creation **require** an `Idempotency-Key` header.
- Offset pages (`page`, `size`) for stable lists; signed opaque cursors (`cursor`, `limit`) for screening browse and booking history, which are time-ordered and mutable.
- Page size defaults to 20 and is capped at 100.
- Errors are RFC 9457 `application/problem+json`: `400` malformed, `401` unauthenticated, `403` unauthorized, `404` missing or concealed, `409` conflict, `422` business rule, `500` unexpected. No stack traces, SQL, tokens or provider internals are ever exposed.

---

## 10. Running the tests

Fast unit tests only (no Docker):

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw clean test
```

Everything, including the PostgreSQL-backed suites:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw clean verify
```

`verify` uses a shared **PostgreSQL 17 Testcontainer** by default, so Docker must be running. If you cannot pull images, point the suites at an existing database instead:

```bash
./mvnw clean verify \
  -Dtest.database.url=jdbc:postgresql://localhost:5432/movie_tickets_test \
  -Dtest.database.username=movie_tickets \
  -Dtest.database.password=your-password
```

> If Testcontainers fails with a Docker Hub `401`, the usual cause is a stale saved credential rather than a missing one. `docker logout` restores anonymous pulls.

### What the suites cover

| Suite | Tests | Covers |
|---|---:|---|
| Unit | 21 | Pricing, discounts, refund cutoffs, state machines, cursor signing, page bounds, deadlock-retry classification |
| `DatabaseContractIT` | 3 | Migrations apply from empty, Hibernate validates, unique constraints, UTC auditing, optimistic versions |
| `ApiContractIT` | 5 | Roles, concealed `404`, Problem Details shape, request IDs, exact OpenAPI path/method surface |
| `ReservationConcurrencyIT` | 2 | 25 customers on one seat; reversed lock order; disjoint seats proceed in parallel |
| `WorkflowRaceIT` | 4 | Payment vs. lease expiry vs. resale; last-discount race; cleanup vs. reclaim; two-worker claim and lease recovery |
| `RefundWorkerIT` | 4 | Refund success, retryable-then-success, terminal stop, attempt-limit exhaustion |
| `ShowCancellationIT` | 5 | Admin show cancellation, full refund, idempotency, sweeper recovery, started-show rejection |
| `BookingJourneysIT` | 4 | End-to-end: discounted booking → history → cancellation → refund → seat reuse; expiry; decline; reminders |
| `CapacityDatasetIT` | 1 | 126,000 seat rows, cursor paging through 10,000 bookings, bounded worker batches |

**Latest run: 49 tests — 21 unit/context, 28 integration — 0 failures, 0 errors, 0 skips**, on the default Testcontainer.

---

## 11. Scope, assumptions and limitations

**Assumptions**

- Single-tenant modular monolith, one application against one PostgreSQL database.
- A screening materialises inventory for every active seat in its auditorium. Layout changes are refused while future screenings depend on that layout.
- Instants are stored in UTC; the theater's time zone decides the local date and whether the weekend surcharge applies.
- INR only. Prices, discounts, payments, refund rules and refunds are immutable booking-time snapshots, so later admin edits never alter a past booking.
- Holds expire logically at their deadline; the cleanup worker is housekeeping, not correctness.
- Checkout requires 30 seconds remaining and takes a separate one-minute lease. A charge that succeeds after the lease expires produces a compensating refund rather than reclaiming a resold seat.
- Cancellation is whole-booking. A refund rule means "cancel at least *N* minutes before the start and receive *P*%"; the most generous matching rule wins and no match means no refund.
- An admin cancelling a screening refunds every confirmed booking in full, bypassing the cutoff tiers, in bounded batches with a sweeper for interrupted runs.
- Referenced catalog records are deactivated, never deleted; browse queries exclude inactive records.
- The reminder key includes the configured lead time, so changing it intentionally re-sends.

**Out of scope**

- UI, deployment, containerisation, CI/CD.
- OAuth/SSO/MFA — HTTP Basic with BCrypt is an assignment-scope choice.
- Real payment, email or SMS providers — the local adapters are deterministic stand-ins.
- Partial cancellation, rescheduling, loyalty schemes, demand-based pricing, multi-currency.
- Microservices and distributed infrastructure.

**Known limitations**

- The capacity dataset is a local *functional* check. No throughput or load claim is made anywhere.
- The notification adapter records deliveries in memory; durable delivery state lives in the outbox table.
- Discount usage limits are enforced by a row lock on the code inside the checkout transaction, verified by a concurrent last-redemption test.

---

## 12. AI-assisted workflow

The repository keeps the original [requirements](docs/requirements.md), the [design](docs/DESIGN.md), and the [AGENTS.md](AGENTS.md) instructions that governed development. The implementation plan, testing strategy and recording script are kept as local working notes.

AI assistance was used milestone by milestone: inspect the requirement and the existing code, propose a bounded change set, wait for approval, implement, review the diff, run focused and then full verification. The main areas it was applied to were transaction boundaries, deterministic lock ordering, deadlock-retry placement, idempotency, late-payment compensation, worker bounds, cursor pagination, OpenAPI coverage, capacity data and log safety.

No external skill package or app connector was used; this was repository-native Java development with the Maven wrapper, Git and a local PostgreSQL instance. That is the complete tooling disclosure.

---

**Further reading:** [docs/DESIGN.md](docs/DESIGN.md) for the full design and trade-offs · [docs/DEMO_WORKFLOW.md](docs/DEMO_WORKFLOW.md) for the Swagger-only script.
