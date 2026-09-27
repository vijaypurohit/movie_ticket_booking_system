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

## Local accounts and adapters

When demo data is enabled:

| Role | Username | Password |
|---|---|---|
| Admin | `admin@movietickets.local` | `Admin@123` |
| Customer | `customer1@movietickets.local` | `Customer@123` |
| Customer | `customer2@movietickets.local` | `Customer@123` |

The local payment adapter accepts `tok_success`; `tok_decline` and every other token decline. Tokens are never stored or logged. The local refund adapter succeeds deterministically and the local notification adapter records idempotent deliveries in memory while durable delivery state remains in the outbox.

The demo loader creates a small catalog and seven upcoming evening screenings. It uses deterministic IDs and can run repeatedly without duplicating data.

## API

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Public browse APIs: `/api/v1/cities`, `/api/v1/theaters`, `/api/v1/movies`, and `/api/v1/screenings`
- Customer APIs: `/api/v1/seat-reservations`, `/api/v1/bookings`, and `/api/v1/refunds`
- Administration APIs: `/admin/api/v1/**`

Reservation, booking, and cancellation creation require an `Idempotency-Key` header. Movie and administration lists use offset pages. Screening browse and booking history use signed opaque cursors. Page sizes are limited to 100; clients request subsequent pages instead of raising that bound.

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

## Scope and limitations

- Payment, refund, and notification integrations are deterministic local adapters. No real money or message provider is called.
- Authentication uses HTTP Basic for the assignment; production federation and token issuance are outside scope.
- The default demo is deliberately small. The optional 126,000-seat capacity fixture and its measurements remain to be added.
- The full Testcontainers integration, concurrency, and API suites in `docs/TESTING.md` remain to be implemented. Docker was unavailable during the latest local run. PostgreSQL migrations and the main booking, cancellation, refund, ownership, history, and outbox flows were verified manually against the configured local PostgreSQL instance.

## AI-assisted workflow

The repository keeps the original requirements, design, implementation plan, testing strategy, and `AGENTS.md` instructions. AI assistance was used to review those documents, implement milestones, inspect diffs, run local checks, and identify concurrency and idempotency gaps. No additional project skill package was used.

## Video outline

For a recording under ten minutes:

1. Explain the requirements, layered architecture, PostgreSQL choice, and immutable snapshots.
2. Show admin setup and public browse in Swagger UI.
3. Reserve seats, pay, inspect booking history, cancel, and show the refund.
4. Explain row locking, checkout expiry, late-payment compensation, and the outbox workers.
5. Show the test strategy, clean test result, migrations, and repository history.
