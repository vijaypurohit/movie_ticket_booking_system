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
13. [License](#13-license)

---

## 1. Technology and why

| Concern     | Choice                                      | Why this one                                                                                                      |
|-------------|---------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| Runtime     | Java 21                                     | Current LTS; virtual threads make the concurrency tests cheap to write                                            |
| Framework   | Spring Boot 4.1                             | REST, validation, security, transactions and scheduling in one dependency set                                     |
| Build       | Maven Wrapper                               | No local Maven install; one reproducible command                                                                  |
| Database    | PostgreSQL 15+                              | `SELECT … FOR UPDATE`, `SKIP LOCKED`, check constraints and real transaction semantics *are* the booking contract |
| Migrations  | Flyway                                      | Reviewable, ordered schema changes; Hibernate is set to `validate`, never `create`                                |
| Persistence | Spring Data JPA + explicit locking queries  | Fast CRUD, with hand-written queries wherever a lock or a lock order matters                                      |
| API contract| OpenAPI 3.1 spec + openapi-generator        | Contract-first: `openapi.yaml` is the source of truth and the models and controller interfaces are generated from it, so a mismatch is a compile error |
| Errors      | RFC 9457 Problem Details                    | One machine-readable error shape across every endpoint                                                            |
| Tests       | JUnit 5, AssertJ, Testcontainers PostgreSQL | Tests run against the same engine as production                                                                   |

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
psql -d postgres
CREATE ROLE movie_tickets LOGIN PASSWORD 'choose-a-local-password';
CREATE DATABASE movie_tickets OWNER movie_tickets;
```

### Step 2 — configure

```bash
cp .env.example .env
```

Edit `.env` and set the two required values:

```properties
DB_PASSWORD=the-password-you-just-chose
CURSOR_SIGNING_KEY=at-least-32-random-characters-goes-here
```

Those are the only two. The demo dataset is switched on by the `demo` profile in Step 3, so
`APP_DEMO_ENABLED` does not belong in `.env` — set it there only if you want demo data without
running under that profile.

`CURSOR_SIGNING_KEY` must be at least 32 UTF-8 bytes — the application refuses to start otherwise, because pagination cursors are HMAC-signed to stop clients forging them. `.env` is gitignored; never commit it.

### Step 3 — point the shell at JDK 21

Export it once. Every `./mvnw` command in this README assumes it is set:

```bash
export JAVA_HOME=/path/to/jdk-21
```

On macOS with Homebrew that is usually
`export JAVA_HOME=/opt/homebrew/opt/openjdk@21`; `java -version` must report 21.

### Step 4 — run

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Flyway applies `V1`–`V9` on startup, Hibernate then validates that the mapped entities match the migrated schema, and the demo seeder loads the dataset below.

### Step 5 — confirm it is up

```bash
curl -s localhost:8080/api/v1/cities | head -c 200
```

You should see `Demo Pune`. Then open **http://localhost:8080/swagger-ui.html** — every endpoint below is executable from there.

---

## 5. Demo data reference

### Which dataset you get

A Spring profile here is nothing but a named bundle of `app.demo.*` properties — there is no
behaviour in the profile itself, so setting the properties directly is exactly equivalent.

| To get | Run with | Which sets |
|---|---|---|
| Small demo dataset | `--spring.profiles.active=demo` | `app.demo.enabled=true` |
| Same, without a profile | `APP_DEMO_ENABLED=true` | the same single property |
| Large dataset | `--spring.profiles.active=capacity` | `enabled` + `large-enabled` + a fixed `anchor-date` |
| Large dataset **with** booking history | the `capacity` profile **plus** `APP_DEMO_HISTORY_ENABLED=true` | adds `history-enabled` |

`app.demo.enabled` seeds the three accounts and selects a data seeder;
`app.demo.large-enabled` decides which one runs. The two datasets are mutually exclusive:
with `large-enabled=true` the small seeder backs off.

The seeders are idempotent and use deterministic identifiers, so restarting never duplicates
anything. The small dataset is what the rest of this section describes.


**Accounts**

| Role | Username | Password |
|---|---|---|
| Admin | `admin@movietickets.local` | `Admin@123` |
| Customer | `customer1@movietickets.local` | `Customer@123` |
| Customer | `customer2@movietickets.local` | `Customer@123` |

**Catalog**

| Thing | Value |
|---|---|
| City | Demo Pune (India, `Asia/Kolkata`) |
| Theater | Demo Cinema, Baner, Pune |
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

**Discount codes** — both seeded, so discounts need no admin setup:

| Code | Effect | Limits | Demonstrates |
|---|---|---|---|
| `DEMO10` | 10% off, capped at ₹100 | min spend ₹100, 5 per customer | the happy path, reusable |
| `DEMO50` | flat ₹50 off | min spend ₹100, **one use in total** | `422 DISCOUNT_LIMIT_REACHED` on the second redemption |

Pass either as `"discountCode"` in the booking body. `DemoDatasetIT` covers both paths.

### Resetting between runs

Rehearsing the walkthrough dirties the data: seats stay `BOOKED`, history fills up, `DEMO50`
is spent, and the fixed idempotency keys in §6 are already used. Two ways back.

**Soft reset — no restart, ~40 ms.** Deletes transactional data only and puts every seat back
on sale. The catalog, screenings, pricing, refund policy, discount codes and accounts survive
untouched, so the IDs in §6.0 stay valid and the app keeps running:

```bash
psql -d movie_tickets <<'SQL'
BEGIN;
DELETE FROM outbox_event;
DELETE FROM refund;
DELETE FROM payment;
DELETE FROM discount_redemption;
DELETE FROM booking_refund_rule;
DELETE FROM booking_item;
DELETE FROM booking;
DELETE FROM seat_reservation;
UPDATE screening_seat SET state = 'AVAILABLE', reservation_id = NULL;
UPDATE screening SET status = 'ACTIVE' WHERE status <> 'ACTIVE';
COMMIT;
SQL
```

Afterwards every seat reads `AVAILABLE`, booking history is empty, `DEMO50` has its single
use back, and keys like `demo-res-1` can be replayed. Save it as an alias and run it between
takes:

```bash
alias demo-reset='psql -d movie_tickets -f ~/demo-reset.sql'
```

It deliberately leaves anything an admin created during the run — extra screenings, extra
discount codes — in place. Use the hard reset if you want those gone.

**Hard reset — restart required, a few seconds.** Truncate everything and let the seeder
rebuild it on startup:

```bash
psql -d movie_tickets -c 'TRUNCATE TABLE
  outbox_event, refund, payment, discount_redemption, booking_refund_rule, booking_item,
  booking, seat_reservation, screening_seat, screening_price, screening,
  refund_policy_rule, refund_policy, discount_code, pricing_plan,
  seat, auditorium, theater, movie, city, app_user RESTART IDENTITY CASCADE'
```

Then restart the application — the seeders run at startup, so the data only comes back on
boot. To also re-run the migrations, `DROP DATABASE movie_tickets;` and recreate it instead.

Seeders are idempotent with deterministic IDs, so no reset ever produces duplicates, and the
four fixed IDs in §6.0 are identical afterwards.

### Larger dataset

Catalog only — 3 cities, 10 theaters, 30 auditoriums, 4,500 physical seats, 20 movies,
840 screenings and ~126,000 screening-seat rows:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=capacity
```

Add booking history — 1,000 customers and 10,000 bookings on top:

```bash
APP_DEMO_HISTORY_ENABLED=true ./mvnw spring-boot:run -Dspring-boot.run.profiles=capacity
```

History logins are `capacity.customer.0000@movietickets.local` … `0997`, password
`Capacity@123`. The generator writes in batches and takes roughly 15 seconds; it is
idempotent, so a restart does not regenerate it.

Because the `capacity` profile pins `app.demo.anchor-date` (default `2030-01-07`), its
screenings sit at a fixed future date rather than relative to today. Browse them with that
date, not tomorrow's:

```bash
curl -s "localhost:8080/api/v1/cities" | jq -r '.items[0].id'
curl -s "localhost:8080/api/v1/screenings?cityId=<id>&date=2030-01-07&limit=5" | jq
```

Use this dataset to see that pagination, indexes and worker batches stay bounded. It is a
functional check, not a throughput benchmark.

---

## 6. Guided demo walkthrough

Ten minutes end to end with `curl`. The same calls are executable from
**http://localhost:8080/swagger-ui.html** if you prefer clicking — use **Authorize** with the
credentials from §5.

`jq` is the only extra tool (`brew install jq`). Every creation endpoint needs a **fresh**
`Idempotency-Key`.

### 6.0 Shell setup

The seeder uses deterministic UUIDs, so these four IDs are identical on every machine and
every run — paste this block once:

```bash
API=localhost:8080
CITY=4b33ef60-74fa-367e-8b1d-12fc21925a98        # Demo Pune
THEATER=ae81b337-117c-35c2-ba5e-8ec7d6551d16     # Demo Cinema
AUDITORIUM=2b7c76c1-fc3f-3925-9180-2f153c7765b5  # Screen 1
MOVIE=52a31bb5-df24-39e7-bd9c-8cc933ab5f40       # The Last Commit

CUST1='-u customer1@movietickets.local:Customer@123'
CUST2='-u customer2@movietickets.local:Customer@123'
ADMIN='-u admin@movietickets.local:Admin@123'
JSON='Content-Type: application/json'

DAY1=$(date -v+1d +%F 2>/dev/null || date -d '+1 day' +%F)
DAY2=$(date -v+2d +%F 2>/dev/null || date -d '+2 day' +%F)
DAY3=$(date -v+3d +%F 2>/dev/null || date -d '+3 day' +%F)
```

Screening and seat IDs are derived from the date, so each step below fetches them.

### 6.1 Browse anonymously, with filters

```bash
curl -s "$API/api/v1/cities?page=0&size=20" | jq -c '.items'
curl -s "$API/api/v1/theaters?cityId=$CITY&page=0&size=20" | jq -c '.items'
curl -s "$API/api/v1/movies?cityId=$CITY&date=$DAY1" | jq -c '.items'
curl -s "$API/api/v1/screenings?cityId=$CITY&movieId=$MOVIE&theaterId=$THEATER&date=$DAY1&limit=20" | jq
```

`cityId` and `date` are required on `/screenings` and `/movies`; `movieId` and `theaterId`
are optional narrowing filters. A filter that matches nothing returns an empty page, not a
`404`:

```bash
curl -s "$API/api/v1/screenings?cityId=$CITY&theaterId=00000000-0000-4000-8000-000000000999&date=$DAY1" | jq -c
# {"items":[],"nextCursor":null}
```

Capture the screening and look at its seats:

```bash
SCREENING=$(curl -s "$API/api/v1/screenings?cityId=$CITY&date=$DAY1" | jq -r '.items[0].id')
curl -s "$API/api/v1/screenings/$SCREENING" | jq -c '{inventorySize,prices}'
curl -s "$API/api/v1/screenings/$SCREENING/seats" \
  | jq -c '.[]|{seat:"\(.rowLabel)\(.seatNumber)",category,state,screeningSeatId}'
```

```json
{"inventorySize":4,"prices":[{"category":"PREMIUM","amount":400.00,"currency":"INR"},
                             {"category":"REGULAR","amount":250.00,"currency":"INR"}]}
{"seat":"A1","category":"REGULAR","state":"AVAILABLE","screeningSeatId":"b0d3c2c8-…"}
{"seat":"A2","category":"REGULAR","state":"AVAILABLE","screeningSeatId":"cbfa67d1-…"}
{"seat":"B1","category":"PREMIUM","state":"AVAILABLE","screeningSeatId":"99344215-…"}
{"seat":"B2","category":"PREMIUM","state":"AVAILABLE","screeningSeatId":"67e110f0-…"}
```

### 6.2 Hold two seats

Pick two seats:

```bash
A1=$(curl -s "$API/api/v1/screenings/$SCREENING/seats" | jq -r '.[]|select(.rowLabel=="A" and .seatNumber==1)|.screeningSeatId')
B1=$(curl -s "$API/api/v1/screenings/$SCREENING/seats" | jq -r '.[]|select(.rowLabel=="B" and .seatNumber==1)|.screeningSeatId')
```

Hold them:

```bash
curl -s $CUST1 -H "$JSON" -H 'Idempotency-Key: demo-res-1' \
  -d "{\"screeningSeatIds\":[\"$A1\",\"$B1\"]}" \
  "$API/api/v1/seat-reservations" | tee /tmp/res.json | jq
```

```json
{"id":"3093e4b4-…","screeningId":"0e8a7c76-…",
 "screeningSeatIds":["99344215-…","b0d3c2c8-…"],
 "state":"ACTIVE","expiresAt":"2026-09-27T14:43:35Z","createdAt":"2026-09-27T14:39:35Z"}
```

Keep the id for the next step:

```bash
RESERVATION=$(jq -r .id /tmp/res.json)
```

Check the seat map — exactly those two flipped:

```bash
curl -s "$API/api/v1/screenings/$SCREENING/seats" | jq -c '[.[]|{seat:"\(.rowLabel)\(.seatNumber)",state}]'
```

```json
[{"seat":"A1","state":"RESERVED"},{"seat":"A2","state":"AVAILABLE"},
 {"seat":"B1","state":"RESERVED"},{"seat":"B2","state":"AVAILABLE"}]
```

The hold lasts four minutes, and `screeningSeatIds` comes back **sorted** — that sort is what
makes `[A1,B1]` and `[B1,A1]` take their locks in the same order.

### 6.3 Pay and confirm

```bash
curl -s $CUST1 -H "$JSON" -H 'Idempotency-Key: demo-book-1' \
  -d "{\"reservationId\":\"$RESERVATION\",\"paymentToken\":\"tok_success\"}" \
  "$API/api/v1/bookings" | tee /tmp/book.json \
  | jq -c '{state,paymentStatus,subtotal,totalAmount,items:[.items[]|{rowLabel,seatNumber,unitPrice}]}'
```

```bash
BOOKING=$(jq -r .id /tmp/book.json)
```

```json
{"state":"CONFIRMED","paymentStatus":"SUCCEEDED","subtotal":650.00,"totalAmount":650.00,
 "items":[{"rowLabel":"A","seatNumber":1,"unitPrice":250.00},
          {"rowLabel":"B","seatNumber":1,"unitPrice":400.00}]}
```

₹250 + ₹400 = ₹650 on a weekday; ₹300 + ₹450 = ₹750 on a weekend show. `items[].unitPrice`
is a snapshot — later admin price edits cannot rewrite this booking.

### 6.4 Booking history, with cursor pagination

Make a second, cheaper booking so there are two pages to walk:

```bash
A2=$(curl -s "$API/api/v1/screenings/$SCREENING/seats" | jq -r '.[]|select(.rowLabel=="A" and .seatNumber==2)|.screeningSeatId')
RES2=$(curl -s $CUST1 -H "$JSON" -H 'Idempotency-Key: demo-res-2' \
  -d "{\"screeningSeatIds\":[\"$A2\"]}" "$API/api/v1/seat-reservations" | jq -r .id)
```

```bash
curl -s $CUST1 -H "$JSON" -H 'Idempotency-Key: demo-book-2' \
  -d "{\"reservationId\":\"$RES2\",\"paymentToken\":\"tok_success\"}" \
  "$API/api/v1/bookings" | jq -c '{state,totalAmount}'
```

```json
{"state":"CONFIRMED","totalAmount":250.00}
```

First page — one row and a cursor:

```bash
curl -s $CUST1 "$API/api/v1/bookings?limit=1" | jq -c '{count:(.items|length),total:.items[0].totalAmount,nextCursor}'
```

```json
{"count":1,"total":250.00,"nextCursor":"djF8Qk9PS0lOR3wyMDI2…"}
```

Follow the cursor to the second page:

```bash
CURSOR=$(curl -s $CUST1 "$API/api/v1/bookings?limit=1" | jq -r .nextCursor)
curl -s $CUST1 "$API/api/v1/bookings?limit=1&cursor=$CURSOR" | jq -c '{count:(.items|length),total:.items[0].totalAmount,nextCursor}'
```

```json
{"count":1,"total":650.00,"nextCursor":null}
```

History is time-ordered and mutable, so it pages by signed opaque cursor rather than
`page`/`size`: rows cannot shift onto a page you have already seen.

### 6.5 Cancel, and watch the refund settle

```bash
curl -s $CUST1 -X POST -H 'Idempotency-Key: demo-cancel-1' \
  "$API/api/v1/bookings/$BOOKING/cancellations" | tee /tmp/cancel.json \
  | jq -c '{bookingState,refund:{status:.refund.status,amount:.refund.amount}}'
```

```json
{"bookingState":"CANCELLED","refund":{"status":"PENDING","amount":325.00}}
```

The refund is `PENDING`. Wait for the worker, then read it back:

```bash
REFUND=$(jq -r .refund.id /tmp/cancel.json)
sleep 6                       # the refund worker runs every 5 seconds
curl -s $CUST1 "$API/api/v1/refunds/$REFUND" | jq -c '{status,amount,reason}'
```

```json
{"status":"SUCCEEDED","amount":325.00,"reason":"CANCELLATION"}
```

The seats, meanwhile, came back the moment you cancelled:

```bash
curl -s "$API/api/v1/screenings/$SCREENING/seats" | jq -c '[.[].state]'
```

```json
["AVAILABLE","BOOKED","AVAILABLE","AVAILABLE"]
```

A1 and B1 are free again; A2 stays `BOOKED` because that is the second booking from 6.4.

Two things happened at once: the seats came back **immediately**, and the refund settled
**asynchronously** five seconds later. The customer never waits on a payment provider.

The refunded amount depends on how far away the show is — see
[refund tiers](#refund-tiers-without-creating-anything) in §7.

### 6.6 Show that it is idempotent

```bash
curl -s $CUST1 -X POST -H 'Idempotency-Key: demo-cancel-1' \
  "$API/api/v1/bookings/$BOOKING/cancellations" | jq -r .refund.id
```

The same refund id comes back; no second refund is created.

---

## 7. Exercising edge cases by hand

Each recipe says how to trigger the behaviour and which test proves it. Run any single
suite with:

```bash
./mvnw verify -Dit.test=<Class> -DfailIfNoTests=false
```

### Index

| Edge case | How | Test that proves it |
|---|---|---|
| [Two customers, one seat](#concurrency--two-customers-one-seat) | curl, no config change | `ReservationConcurrencyIT` |
| [Refund tiers](#refund-tiers-without-creating-anything) | curl, no config change | `BookingJourneysIT` |
| [Discount limit](#discount-codes-both-seeded) | curl, no config change | `DemoDatasetIT`, `WorkflowRaceIT` |
| [Declined payment](#payment-decline-releases-the-seat) | curl, no config change | `BookingJourneysIT` |
| [Admin cancels a show](#admin-cancels-a-show) | curl, no config change | `ShowCancellationIT` |
| [Ownership concealed](#ownership-is-concealed-not-denied) | curl, no config change | `ApiContractIT` |
| [Validation and error shape](#validation-and-error-shape) | curl, no config change | `ApiContractIT` |
| [Idempotency](#idempotency) | curl, no config change | `ApiContractIT`, `BookingJourneysIT` |
| [Hold expiry](#config-dependent-edge-cases) | **needs config** | `BookingJourneysIT` |
| [Too little time to pay](#config-dependent-edge-cases) | **needs config** | `WorkflowRaceIT` |
| [Late payment compensation](#races-you-cannot-reproduce-by-hand) | not reproducible by hand | `WorkflowRaceIT` |
| [Refund provider failures](#races-you-cannot-reproduce-by-hand) | not reproducible by hand | `RefundWorkerIT` |

### Concurrency — two customers, one seat

The headline guarantee. Both requests, same seat, at once:

```bash
SCR3=$(curl -s "$API/api/v1/screenings?cityId=$CITY&date=$DAY3" | jq -r '.items[0].id')
SEAT=$(curl -s "$API/api/v1/screenings/$SCR3/seats" | jq -r '[.[]|select(.state=="AVAILABLE")|.screeningSeatId][0]')
```

```bash
for U in 1 2; do
  curl -s -u customer$U@movietickets.local:Customer@123 -H "$JSON" \
    -H "Idempotency-Key: race-$U-$RANDOM" \
    -d "{\"screeningSeatIds\":[\"$SEAT\"]}" \
    "$API/api/v1/seat-reservations" -o /tmp/race$U.json -w "customer$U → HTTP %{http_code}\n" &
done; wait
jq -rc '.state // "\(.status) \(.code)"' /tmp/race1.json /tmp/race2.json
```

```
customer2 → HTTP 201
customer1 → HTTP 409
409 SEAT_UNAVAILABLE
ACTIVE
```

Exactly one `201`, one `409 SEAT_UNAVAILABLE` — and **the winner changes between runs**,
which is what makes it a real race rather than request ordering.

```bash
./mvnw verify -Dit.test=ReservationConcurrencyIT -DfailIfNoTests=false
```

- `exactlyOneOfTwentyFiveCustomersCanReserveTheSameSeat` — 25 threads, one winner.
- `reversedRequestsFinishWithoutDeadlockWhileDisjointSeatsProceed` — reversed lock order.

### Refund tiers, without creating anything

The seeded policy is **≥ 24 h → 100%, ≥ 2 h → 50%, otherwise 0%**. Because every seeded
screening starts at 18:30 IST, *tomorrow's* show is **less than 24 hours away once it is past
18:30 today* — so the stock dataset gives you two tiers with no setup at all. Check before
demoing:

```bash
for i in 1 2 3; do
  D=$(date -v+${i}d +%F 2>/dev/null || date -d "+$i day" +%F)
  S=$(curl -s "$API/api/v1/screenings?cityId=$CITY&date=$D" | jq -r '.items[0].startTime')
  python3 -c "
import datetime
st=datetime.datetime.fromisoformat('$S'.replace('Z','+00:00'))
m=(st-datetime.datetime.now(datetime.timezone.utc)).total_seconds()/60
print(f'day+$i  $D  starts in {m:6.0f} min  ->  ' + ('100%' if m>=1440 else '50%' if m>=120 else '0%'))"
done
```

```
day+1  2026-09-28  starts in   1339 min  ->  50%
day+2  2026-09-29  starts in   2779 min  ->  100%
day+3  2026-09-30  starts in   4219 min  ->  100%
```

| Book on | Paid | Refunded | Tier |
|---|---|---|---|
| day+2 or later | ₹225 | ₹225 | **100%** — always |
| day+1, after 18:30 local | ₹650 | ₹325 | **50%** |
| a screening < 2 h out | ₹300 | ₹0 | **0%** — booking still `CANCELLED` |
| a screening already started | — | — | `409 SCREENING_STARTED` |

Only the last two rows need an admin-created screening, because the seeder never places one
that close. Create one with `startTime` = now + 1 h:

```bash
PP=$(curl -s $ADMIN "$API/admin/api/v1/pricing-plans" | jq -r '.items[0].id')
RP=$(curl -s $ADMIN "$API/admin/api/v1/refund-policies" | jq -r '.items[0].id')
ST=$(python3 -c "import datetime;print((datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(hours=1)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
ET=$(python3 -c "import datetime;print((datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(hours=3)).strftime('%Y-%m-%dT%H:%M:%SZ'))")

curl -s $ADMIN -H "$JSON" -d "{\"movieId\":\"$MOVIE\",\"auditoriumId\":\"$AUDITORIUM\",
  \"pricingPlanId\":\"$PP\",\"refundPolicyId\":\"$RP\",
  \"startTime\":\"$ST\",\"endTime\":\"$ET\"}" \
  "$API/admin/api/v1/screenings" | jq -c '{id,startTime,status}'
```

Book a seat on it and cancel: the booking becomes `CANCELLED` with a `0.00` refund. Extra
screenings in the same auditorium are fine as long as time ranges do not overlap.

```bash
./mvnw verify -Dit.test=BookingJourneysIT -DfailIfNoTests=false
```

- `adminSetupBrowseDiscountedBookingHistoryCancellationRefundAndSeatReuse` — the full
  journey with a fixed clock, so every cutoff boundary is exact.

### Discount codes, both seeded

`DEMO10` (10%, reusable) and `DEMO50` (flat ₹50, **one use per database**) are created by the
seeder. Pass either as `discountCode`:

```bash
SCR2=$(curl -s "$API/api/v1/screenings?cityId=$CITY&date=$DAY2" | jq -r '.items[0].id')
SEAT2=$(curl -s "$API/api/v1/screenings/$SCR2/seats" | jq -r '[.[]|select(.state=="AVAILABLE")|.screeningSeatId][0]')
RESD=$(curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: d10-r-$RANDOM" \
  -d "{\"screeningSeatIds\":[\"$SEAT2\"]}" "$API/api/v1/seat-reservations" | jq -r .id)
```

```bash
curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: d10-b-$RANDOM" \
  -d "{\"reservationId\":\"$RESD\",\"discountCode\":\"DEMO10\",\"paymentToken\":\"tok_success\"}" \
  "$API/api/v1/bookings" | jq -c '{state,subtotal,discountAmount,totalAmount}'
```

```json
{"state":"CONFIRMED","subtotal":250.00,"discountAmount":25.00,"totalAmount":225.00}
```

Redeem `DEMO50` twice and the second attempt is rejected:

```json
{"status":422,"code":"DISCOUNT_LIMIT_REACHED","detail":"The discount code usage limit has been reached."}
```

The limit is held under a row lock on the code *inside* the checkout transaction, so two
simultaneous checkouts cannot both take the last use.

```bash
./mvnw verify -Dit.test=DemoDatasetIT,WorkflowRaceIT -DfailIfNoTests=false
```

- `theSeededPercentageDiscountAppliesAtCheckout`, `theSeededSingleUseDiscountIsRejectedOnASecondRedemption`
- `concurrentBookingsCannotExceedTheLastDiscountRedemption` — the concurrent version.

### Payment decline releases the seat

```bash
SEATD=$(curl -s "$API/api/v1/screenings/$SCR3/seats" | jq -r '[.[]|select(.state=="AVAILABLE")|.screeningSeatId][0]')
RESX=$(curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: dec-r-$RANDOM" \
  -d "{\"screeningSeatIds\":[\"$SEATD\"]}" "$API/api/v1/seat-reservations" | jq -r .id)
```

```bash
curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: dec-b-$RANDOM" \
  -d "{\"reservationId\":\"$RESX\",\"paymentToken\":\"tok_decline\"}" \
  "$API/api/v1/bookings" | jq -c '{state,paymentStatus}'
```

```json
{"state":"FAILED","paymentStatus":"DECLINED"}
```

```bash
curl -s "$API/api/v1/screenings/$SCR3/seats" | jq -r --arg s "$SEATD" '.[]|select(.screeningSeatId==$s)|"seat → \(.state)"'
```

```
seat → AVAILABLE
```

`FAILED` is a queryable record, not an error, and the seat is instantly re-bookable. No
confirmation notification is written.

Test: `BookingJourneysIT#declinedPaymentReleasesSeatAndWritesNoConfirmationEvent`.

### Admin cancels a show

```bash
DAY5=$(date -v+5d +%F 2>/dev/null || date -d '+5 day' +%F)
SCR5=$(curl -s "$API/api/v1/screenings?cityId=$CITY&date=$DAY5" | jq -r '.items[0].id')
SEAT5=$(curl -s "$API/api/v1/screenings/$SCR5/seats" | jq -r '.[0].screeningSeatId')
RES5=$(curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: sc-r-$RANDOM" \
  -d "{\"screeningSeatIds\":[\"$SEAT5\"]}" "$API/api/v1/seat-reservations" | jq -r .id)
BOOK5=$(curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: sc-b-$RANDOM" \
  -d "{\"reservationId\":\"$RES5\",\"paymentToken\":\"tok_success\"}" \
  "$API/api/v1/bookings" | jq -r .id)
```

```bash
curl -s $ADMIN -X DELETE -o /dev/null -w 'DELETE → %{http_code}\n' "$API/admin/api/v1/screenings/$SCR5"
```

```bash
sleep 6
curl -s $CUST1 "$API/api/v1/bookings/$BOOK5" | jq -c '{state,refunds:[.refunds[]|{reason,status,amount}]}'
```

```
DELETE → 204
{"state":"CANCELLED","refunds":[{"reason":"SHOW_CANCELLED","status":"SUCCEEDED","amount":250.00}]}
```

The show also comes off sale — it disappears from browse and new holds are refused:

```bash
curl -s "$API/api/v1/screenings?cityId=$CITY&date=$DAY5" | jq -c '.items|length'   # 0
```

Every confirmed booking is cancelled, its seats released, and a **full** refund issued — the
cutoff tier is deliberately bypassed because the cancellation is not the customer's fault.

```bash
./mvnw verify -Dit.test=ShowCancellationIT -DfailIfNoTests=false
```

Covers the full refund, idempotent repeat, sweeper recovery after an interrupted run, and
`409` on an already-started show.

### Ownership is concealed, not denied

```bash
curl -s -o /dev/null -w 'customer2 reads customer1 booking → %{http_code}\n' $CUST2 "$API/api/v1/bookings/$BOOKING"
# 404
```

`403` would confirm the booking exists. Test:
`ApiContractIT#ownershipIsConcealedAndConflictsUseTheExpectedStatus`.

### Validation and error shape

```bash
curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: bad-$RANDOM" \
  -d '{"screeningSeatIds":[]}' "$API/api/v1/seat-reservations" | jq
```

```json
{"detail":"Request validation failed.","instance":"/api/v1/seat-reservations","status":400,
 "title":"Invalid request","type":"/problems/invalid-request","code":"INVALID_REQUEST",
 "requestId":"a8e277e3-…",
 "fieldErrors":[{"field":"screeningSeatIds","code":"Size","message":"size must be between 1 and 10"}]}
```

Every response also carries a server-generated `X-Request-Id`. Tests:
`ApiContractIT#validationAndMalformedContractsReturnSafeProblemDetails` and
`#rawSqlAndJdbcErrorLoggingRemainDisabled`, which asserts no SQL or JDBC detail reaches the
logs.

### Idempotency

| Try | Result |
|---|---|
| Same key, same body | Original result returned; nothing new created |
| Same key, different body | `409 IDEMPOTENCY_KEY_REUSED` |
| New key, same body | A genuinely new operation |

```bash
BODY="{\"screeningSeatIds\":[\"$SEATD\"]}"
```

Same key, same body — twice. Both print the **same** id, and nothing new is created:

```bash
curl -s $CUST1 -H "$JSON" -H 'Idempotency-Key: demo-idem' -d "$BODY" "$API/api/v1/seat-reservations" | jq -r .id
curl -s $CUST1 -H "$JSON" -H 'Idempotency-Key: demo-idem' -d "$BODY" "$API/api/v1/seat-reservations" | jq -r .id
```

Same key, *different* body — rejected:

```bash
curl -s $CUST1 -H "$JSON" -H 'Idempotency-Key: demo-idem' \
  -d '{"screeningSeatIds":["00000000-0000-4000-8000-000000000001"]}' \
  "$API/api/v1/seat-reservations" | jq -c '{status,code}'
```

```json
{"status":409,"code":"IDEMPOTENCY_KEY_REUSED"}
```

### Config-dependent edge cases

These depend on time passing. Rather than waiting, either restart the app with a shortened
duration, or run the test that already covers it against a controlled clock.

| Behaviour | Run the app with | Then | Test instead |
|---|---|---|---|
| Hold expires and the seat is reclaimed | `APP_BOOKING_RESERVATION_DURATION=PT20S` | reserve, wait 20 s, reserve the same seat as `customer2` → `201`; pay with the stale reservation → `409 RESERVATION_NOT_ACTIVE` | `BookingJourneysIT#reservationExpiresAtTheExactBoundaryAndIsReclaimedWithoutCleanup` |
| Too little time left to pay | `APP_BOOKING_RESERVATION_DURATION=PT35S` | reserve, wait ~10 s, book → `409 INSUFFICIENT_CHECKOUT_TIME` | `WorkflowRaceIT#lateGatewaySuccessCannotConfirmOrReclaimASeatResoldAfterTheLeaseExpired` |
| Cleanup worker sweeping expired holds | `APP_BOOKING_CLEANUP_DELAY=PT5S` | reserve, wait, watch the seat free itself | `WorkflowRaceIT#cleanupRacingAReclaimLeavesOneConsistentSeatOwner` |
| Refund retries and terminal failure | `APP_REFUND_MAX_ATTEMPTS=1` | cancel a booking and watch the refund give up after one attempt | `RefundWorkerIT` (all four) |

Example — the hold-expiry run:

```bash
APP_BOOKING_RESERVATION_DURATION=PT20S ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Expiry is **logical**: a hold past its deadline is treated as free the moment anybody asks,
so this works with the cleanup worker disabled entirely. The worker is housekeeping, not
correctness — which is exactly what the `…WithoutCleanup` test name asserts.

### Races you cannot reproduce by hand

Two behaviours need the payment gateway parked mid-flight, which no sequence of `curl`
commands can arrange. They are covered by tests that script the gateway through
`ScriptedPaymentGateway`:

```bash
./mvnw verify -Dit.test=WorkflowRaceIT,RefundWorkerIT -DfailIfNoTests=false
```

| Test | What it pins down |
|---|---|
| `lateGatewaySuccessCannotConfirmOrReclaimASeatResoldAfterTheLeaseExpired` | A charge that succeeds *after* the lease lapsed and the seat was resold: the payment is recorded, the booking stays failed, one compensating refund is queued, and the new owner keeps the seat |
| `cleanupRacingAReclaimLeavesOneConsistentSeatOwner` | The cleanup worker and a live reclaim touching the same seat |
| `twoWorkersNeverClaimTheSameEventAndAnExpiredLeaseIsReclaimable` | `FOR UPDATE SKIP LOCKED` outbox claiming |
| `retryableProviderFailureIsRetriedOnALaterBoundedAttemptAndThenSucceeds` | Bounded retry with backoff |
| `terminalProviderFailureStopsAtTheConfiguredAttemptLimit` | No infinite retry on a terminal provider error |

This is the honest reason the test suite exists: the most important guarantees in this system
are the ones a manual demo cannot show.

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

This API is **contract-first**. [`src/main/resources/openapi/openapi.yaml`](src/main/resources/openapi/openapi.yaml)
is the source of truth — 32 paths, 60 operations, 61 schemas — and is served verbatim
rather than reconstructed from the running code.

- **Swagger UI** — http://localhost:8080/swagger-ui.html
- **OpenAPI JSON** — http://localhost:8080/v3/api-docs
- **OpenAPI YAML** — http://localhost:8080/v3/api-docs.yaml

### Changing the API

The build generates the request/response models and one controller interface per tag from
the specification into `target/generated-sources/openapi`. Each controller implements its
interface, so **an implementation that drifts from the specification does not compile**.

```bash
# 1. edit src/main/resources/openapi/openapi.yaml
# 2. regenerate and let the compiler tell you what no longer matches
./mvnw generate-sources
./mvnw compile
```

Never edit anything under `target/generated-sources` — it is overwritten on every build.
[docs/DESIGN.md §9](docs/DESIGN.md) explains the generator configuration and the type
mappings it relies on.

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
./mvnw clean test
```

Everything, including the PostgreSQL-backed suites:

```bash
./mvnw clean verify
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
| `ShowCancellationIT` | 6 | Admin show cancellation takes the show off sale, full refund, idempotency, sweeper recovery, started-show rejection |
| `BookingJourneysIT` | 4 | End-to-end: discounted booking → history → cancellation → refund → seat reuse; expiry; decline; reminders |
| `DemoDatasetIT` | 3 | The seeded demo dataset: catalog and a week of screenings, the `DEMO10` discount, `DEMO50` single-use rejection, seeder idempotency |
| `CapacityDatasetIT` | 1 | 126,000 seat rows, cursor paging through 10,000 bookings, bounded worker batches |

**Latest run: 53 tests — 21 unit/context, 32 integration — 0 failures, 0 errors, 0 skips**, on the default Testcontainer.

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

---

## 13. License

Released under the [MIT License](LICENSE). Copyright © 2026 Vijay Purohit.

---

**Further reading:** [docs/DESIGN.md](docs/DESIGN.md) for the full design and trade-offs.
