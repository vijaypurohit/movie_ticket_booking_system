# Movie Ticket Booking System

Spring Boot REST API for administering movie catalog data and completing the customer journey from browse, seat reservation, and payment through cancellation, refund, and asynchronous notifications.

## Technology

- Java 21 and Spring Boot 4.1
- PostgreSQL 15+ with Flyway migrations and Hibernate schema validation
- Spring Security with database-backed BCrypt users and `ADMIN`/`CUSTOMER` roles
- springdoc OpenAPI and RFC 9457 Problem Details
- Maven Wrapper, JUnit 5, AssertJ, Mockito, and Testcontainers PostgreSQL

PostgreSQL is used in development and integration tests because row locks, `SKIP LOCKED`, constraints, and transaction behavior are part of the booking contract. H2 is intentionally not used.

## Architecture

The application follows `web -> application -> persistence -> model` boundaries. Business transactions live in application services; controllers only validate and translate HTTP requests. Payment and notification providers are isolated behind gateways.

Seat allocation uses materialized `ScreeningSeat` rows and sorted pessimistic locks. A reservation lasts four minutes. Checkout replaces that deadline with a one-minute lease. Payment is called outside the database transaction, then booking, reservation, and seat rows are locked again before confirmation. Cancellation uses the refund rules copied into the booking at checkout.

Confirmation, cancellation, refund, and reminder messages use a transactional outbox. Refund and notification workers claim at most 100 rows with `FOR UPDATE SKIP LOCKED`, call their gateways after the claim transaction, and use bounded retries and processing leases.

## Scope and assumptions

- This is a single-tenant modular monolith intended to run as one application against one PostgreSQL database.
- A screening owns materialized inventory for every active seat in its auditorium. Seat-layout changes are rejected while future screenings depend on that layout.
- PostgreSQL stores instants in UTC. Theater time zones determine local screening dates and whether weekend pricing applies.
- INR is the only currency. Prices, booking items, discount amounts, payments, refund rules, and refunds are immutable booking-time snapshots.
- Reservations expire logically at their deadline, so seats can be reclaimed even when the cleanup worker has not run. Cleanup remains bounded housekeeping rather than a correctness dependency.
- Checkout is allowed only when at least 30 seconds remain. Starting checkout creates a separate one-minute lease; a late successful charge produces an idempotent compensating refund instead of reclaiming a resold seat.
- Cancellation applies to the complete booking. Partial cancellation, rescheduling, loyalty points, dynamic demand pricing, and multi-currency settlement are outside scope.
- Referenced catalog records are deactivated instead of deleted. Normal browse queries exclude inactive records.
- Offset pagination is used for stable administrative lists; mutable time-ordered screening and booking-history collections use signed opaque cursors.
- HTTP Basic, demo credentials, and the local payment/refund/notification adapters are development choices, not production integrations.

## Local setup

Prerequisites:

- JDK 21
- PostgreSQL 15 or newer
- Docker only when running the Testcontainers integration suites

Create the local database and role:

```sql
CREATE ROLE movie_tickets LOGIN PASSWORD 'choose-a-local-password';
CREATE DATABASE movie_tickets OWNER movie_tickets;
```

Copy `.env.example` to `.env`, set the database password, and replace the cursor key with at least 32 random characters. To load the repeatable small demo dataset, add:

```properties
APP_DEMO_ENABLED=true
```

Start the API:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw spring-boot:run
```

Flyway creates or upgrades the schema at startup. Hibernate then validates the mapped schema.

## Configuration

Spring Boot accepts these values through `.env`, environment variables, or equivalent configuration:

| Setting | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/movie_tickets` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `movie_tickets` | Database role |
| `DB_PASSWORD` | none | Database password; required locally |
| `CURSOR_SIGNING_KEY` | none | At least 32 UTF-8 bytes used to sign opaque cursors |
| `APP_DEMO_ENABLED` | `false` | Load deterministic local identities and the small demo |
| `APP_DEMO_HISTORY_ENABLED` | `false` | Add 1,000 customers and 10,000 bookings with the capacity profile |
| `APP_BOOKING_RESERVATION_DURATION` | `PT4M` | Seat-reservation lifetime |
| `APP_BOOKING_MINIMUM_REMAINING` | `PT30S` | Minimum reservation time required to begin checkout |
| `APP_BOOKING_CHECKOUT_DURATION` | `PT1M` | Payment-in-progress lease |
| `APP_BOOKING_CLEANUP_DELAY` | `PT30S` | Expired-reservation cleanup interval |
| `APP_REFUND_WORKER_DELAY` | `PT5S` | Refund worker interval |
| `APP_NOTIFICATION_WORKER_DELAY` | `PT5S` | Outbox delivery interval |
| `APP_NOTIFICATION_REMINDER_LEAD` | `PT24H` | Reminder lead time |
| `APP_NOTIFICATION_REMINDER_WINDOW` | `PT5M` | Reminder eligibility window |

The `demo` profile enables the small dataset. The explicit `capacity` profile enables the larger functional-capacity dataset. Raw Hibernate JDBC error logging is disabled so database constraint details and customer data are not written to application logs.

## Local accounts and adapters

When demo data is enabled:

| Role | Username | Password |
|---|---|---|
| Admin | `admin@movietickets.local` | `Admin@123` |
| Customer | `customer1@movietickets.local` | `Customer@123` |
| Customer | `customer2@movietickets.local` | `Customer@123` |

The local payment adapter accepts `tok_success`; `tok_decline` and every other token decline. Tokens are never stored or logged. The local refund adapter succeeds deterministically and the local notification adapter records idempotent deliveries in memory while durable delivery state remains in the outbox.

The demo loader creates a small catalog and seven upcoming evening screenings. It uses deterministic IDs and can run repeatedly without duplicating data.

For the explicit capacity dataset, run with the `capacity` profile:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw spring-boot:run -Dspring-boot.run.profiles=capacity
```

This deterministically generates 3 cities, 10 theaters, 30 auditoriums, 4,500 physical seats, 840 screenings, and 126,000 screening-seat rows. Set `APP_DEMO_HISTORY_ENABLED=true` to additionally bring the dataset to 1,000 customers and generate 10,000 booking-history rows. Capacity customers use `capacity.customer.0000@movietickets.local` through `capacity.customer.0997@movietickets.local` with the local-only password `Capacity@123`; the two standard demo customers complete the 1,000-customer target. The generator uses stable IDs and conflict-safe bounded batches, so rerunning it does not duplicate data.

## API

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Public browse APIs: `/api/v1/cities`, `/api/v1/theaters`, `/api/v1/movies`, and `/api/v1/screenings`
- Customer APIs: `/api/v1/seat-reservations`, `/api/v1/bookings`, and `/api/v1/refunds`
- Administration APIs: `/admin/api/v1/**`

Reservation, booking, and cancellation creation require an `Idempotency-Key` header. Movie and administration lists use offset pages. Screening browse and booking history use signed opaque cursors. Page sizes are limited to 100; clients request subsequent pages instead of raising that bound.

The complete public-to-cancellation walkthrough is documented in [docs/DEMO_WORKFLOW.md](docs/DEMO_WORKFLOW.md). It can be executed entirely through Swagger UI with the local demo accounts.

## Verification

Run the local unit and context suite:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw clean test
```

Run the Maven verification lifecycle:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw clean verify
```

The required scenarios and concurrency checks are listed in [docs/TESTING.md](docs/TESTING.md). The design and implementation sequence are in [docs/DESIGN.md](docs/DESIGN.md) and [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md).

`verify` separates fast unit tests from PostgreSQL-backed repository, API, concurrency, and end-to-end suites. The database suites use a shared PostgreSQL 17 Testcontainer by default. A pre-existing PostgreSQL instance can be used for constrained environments by supplying the `test.database.url`, `test.database.username`, and `test.database.password` Maven system properties.

The latest clean run passed 36 tests: 21 unit/context tests and 15 PostgreSQL schema, API contract, concurrency, capacity, and end-to-end tests. It started from an empty PostgreSQL 15 database with no failures, errors, or skips. The capacity check generated 126,000 screening-seat rows and 10,000 booking-history rows; it is a functional local check, not a production throughput benchmark.

## Scope and limitations

- Payment, refund, and notification integrations are deterministic local adapters. No real money or message provider is called.
- Authentication uses HTTP Basic for the assignment; production federation and token issuance are outside scope.
- The default demo is deliberately small; the capacity profile is explicit and is intended for local functional checks rather than production benchmarking.
- Payment/checkout expiry races, discount-limit races, and competing cleanup-worker recovery remain specialized integration-test gaps; the implemented suite covers the primary API journeys, ownership and validation contracts, schema validation, same-seat contention, reversed lock order, disjoint reservations, exact reservation expiry, worker delivery, and idempotent reminders.

## AI-assisted workflow

The repository retains the original [requirements](docs/requirements.md), [design](docs/DESIGN.md), [implementation plan](docs/IMPLEMENTATION_PLAN.md), [testing strategy](docs/TESTING.md), and [AGENTS.md](AGENTS.md) instructions used during development. AI assistance was used milestone by milestone to inspect requirements and existing code, propose a bounded change set, wait for approval, implement it, review the diff, and run focused and full verification.

The main AI-assisted review areas were transaction boundaries, deterministic lock ordering, deadlock retry placement, idempotency, late-payment compensation, worker bounds, cursor pagination, OpenAPI coverage, capacity data, and log safety. No named Codex skill package or external app connector was used; the work was repository-native Java development using the Maven wrapper, Git, and a local PostgreSQL instance. This is the complete skills/tools disclosure for the project.

## Submission guide

- Swagger demonstration: [docs/DEMO_WORKFLOW.md](docs/DEMO_WORKFLOW.md)
- Timed recording script: [docs/VIDEO_OUTLINE.md](docs/VIDEO_OUTLINE.md)
- Detailed design and tradeoffs: [docs/DESIGN.md](docs/DESIGN.md)
- Verification evidence and remaining specialized test gaps: [docs/TESTING.md](docs/TESTING.md)
