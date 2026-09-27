# Swagger UI Demo Workflow

This workflow uses only the small, repeatable demo dataset and Swagger UI. It exercises public discovery, customer reservation and payment, booking history, cancellation, and refund lookup without requiring an additional API client.

## 1. Start the local application

Set the values from `.env.example`, enable the demo profile, and start the application:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Open `http://localhost:8080/swagger-ui.html`. The demo creates one movie with an 18:30 IST screening on each of the seven consecutive dates starting tomorrow.

## 2. Discover a screening without authentication

Execute these operations under **Public catalog** and **Public screenings**:

1. `GET /api/v1/cities`; copy the first city `id`.
2. `GET /api/v1/movies` with that `cityId` and tomorrow's date as `date`; copy the movie `id`.
3. `GET /api/v1/screenings` with the city, movie, and date; copy the first screening `id`.
4. `GET /api/v1/screenings/{id}/seats`; copy one or two `AVAILABLE` screening-seat IDs.

Use `limit=20` for cursor lists and `size=20` for offset lists. Pass the returned `nextCursor` unchanged to retrieve another cursor page.

## 3. Reserve seats as a customer

Select **Authorize** and enter `customer1@movietickets.local` / `Customer@123`. Execute `POST /api/v1/seat-reservations` with a new idempotency key such as `demo-reservation-001`:

```json
{
  "screeningSeatIds": [
    "replace-with-an-available-screening-seat-id"
  ]
}
```

Copy the returned reservation `id`. Repeating the identical request with the same key returns the original result; reusing the key with a different body returns `409`.

## 4. Complete payment and inspect history

Execute `POST /api/v1/bookings` with a different idempotency key, such as `demo-booking-001`:

```json
{
  "reservationId": "replace-with-the-reservation-id",
  "paymentToken": "tok_success"
}
```

Copy the returned booking `id`, then execute:

1. `GET /api/v1/bookings/{id}` to inspect the immutable seat and price snapshots.
2. `GET /api/v1/bookings?limit=20` to verify that the booking appears in customer-scoped history.

The local adapter accepts only `tok_success`. Use `tok_decline` in a separate reservation to demonstrate the declined-payment path.

## 5. Cancel and inspect the refund

Execute `POST /api/v1/bookings/{id}/cancellations` with another new key, such as `demo-cancellation-001`. The response contains the cancellation result and any refund. If a refund ID is present, execute `GET /api/v1/refunds/{id}`.

Repeat the cancellation with the same key to demonstrate idempotency, then call the screening-seat endpoint again to verify that the cancelled booking's seat is available.

## 6. Verify role boundaries

Use **Authorize** to switch to `admin@movietickets.local` / `Admin@123` and execute any operation under an **Admin** tag. Admin credentials receive `403` on customer-owned booking endpoints, while customer credentials receive `403` on `/admin/api/v1/**`. Public discovery remains available after clearing authorization.

Every response includes `X-Request-Id`. Error responses use `application/problem+json` and do not expose stack traces, SQL, credentials, payment tokens, or provider details.
