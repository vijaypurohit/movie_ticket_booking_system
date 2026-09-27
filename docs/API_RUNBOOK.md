# API Runbook

**Contents**

| § | Section |
|---|---|
| [0](#0--setup) | Setup |
| [1](#1--public-discovery) | Public discovery |
| [2](#2--hold-pay-confirm) | Hold, pay, confirm |
| [3](#3--concurrency) | Concurrency |
| [4](#4--discounts) | Discounts |
| [5](#5--cancellation-and-refunds) | Cancellation and refunds |
| [6](#6--failure-and-safety-paths) | Failure and safety paths |
| [7](#7--admin-demo-subset) | Admin (demo subset) |
| [8](#8--admin-full-crud-surface) | Admin — full CRUD surface |
| [9](#9--tests) | Tests |
| [10](#10--reset) | Reset |

---

## §0 — Setup

### §0.1 Start the application

```bash
export JAVA_HOME=/path/to/jdk-21          # once per shell
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

### §0.2 Shell variables

Paste once into a second terminal. The six UUIDs are deterministic — the seeder
derives them from fixed names, so they are identical on every machine and every
run.

```bash
API=localhost:8080

CITY=4b33ef60-74fa-367e-8b1d-12fc21925a98        # Demo Pune
THEATER=ae81b337-117c-35c2-ba5e-8ec7d6551d16     # Demo Cinema
AUDITORIUM=2b7c76c1-fc3f-3925-9180-2f153c7765b5  # Screen 1
MOVIE=52a31bb5-df24-39e7-bd9c-8cc933ab5f40       # The Last Commit
PLAN=5fc46422-236a-3616-a8b6-12f2eadac911        # Demo Pricing
POLICY=eb06636e-a2f0-3fe2-91b3-d54eae026680      # Demo Refund Policy

CUST1='-u customer1@movietickets.local:Customer@123'
CUST2='-u customer2@movietickets.local:Customer@123'
ADMIN='-u admin@movietickets.local:Admin@123'
JSON='Content-Type: application/json'

DAY1=$(date -v+1d +%F 2>/dev/null || date -d '+1 day' +%F)
DAY2=$(date -v+2d +%F 2>/dev/null || date -d '+2 day' +%F)
DAY3=$(date -v+3d +%F 2>/dev/null || date -d '+3 day' +%F)
DAY5=$(date -v+5d +%F 2>/dev/null || date -d '+5 day' +%F)
echo "$DAY1 / $DAY2 / $DAY3 / $DAY5"
```

Screening and seat IDs derive from the date, so fetch those as you go.

### §0.3 Confirm the dataset loaded

```bash
curl -s "$API/api/v1/cities" | jq
```

```json
{"items":[{"id":"4b33ef60-74fa-367e-8b1d-12fc21925a98","name":"Demo Pune",
           "country":"India","timeZone":"Asia/Kolkata"}],
 "page":0,"size":20,"totalElements":1,"totalPages":1}
```

No credentials sent — discovery is public.

---

## §1 — Public discovery

### §1.1 Cities, offset pagination

```bash
curl -s "$API/api/v1/cities?page=0&size=20" | jq
```

### §1.2 Theaters by city

```bash
curl -s "$API/api/v1/theaters?cityId=$CITY&page=0&size=20" | jq -c '.items' | jq
```

```json
[{"address":"Baner, Pune","cityId":"4b33ef60-74fa-367e-8b1d-12fc21925a98",
  "id":"ae81b337-117c-35c2-ba5e-8ec7d6551d16","name":"Demo Cinema"}]
```

The public view carries `id`, `cityId`, `name` and `address` only — `active`,
`createdAt`, `updatedAt` and `version` are admin-only fields and never leak to
an anonymous caller. Compare with §8.2, where the same theater read as admin
returns all eight.

### §1.3 `cityId` is required

```bash
curl -s "$API/api/v1/theaters" | jq -c '{status,code,detail}'
```

```json
{"status":400,"code":"INVALID_REQUEST","detail":"The request is invalid."}
```

Drop the filter and the request is rejected — theaters are never listed
unscoped.

### §1.4 Movies playing in a city on a date

```bash
curl -s "$API/api/v1/movies?cityId=$CITY&date=$DAY1&page=0&size=20" | jq -c '.items' | jq
```
```bash
curl -s "$API/api/v1/movies/$MOVIE" | jq -c
```

```json
[{"id":"52a31bb5-df24-39e7-bd9c-8cc933ab5f40","title":"The Last Commit",
  "durationMinutes":120,"language":"English"}]
```

A join across screenings, not a movie-table dump.

### §1.5 Screenings, every filter applied

```bash
curl -s "$API/api/v1/screenings?cityId=$CITY&movieId=$MOVIE&theaterId=$THEATER&date=$DAY1&limit=20" | jq
```

```json
{"items":[{"id":"0e8a7c76-b592-3993-a1b8-d5480ddc8ccf",
           "movieId":"52a31bb5-df24-39e7-bd9c-8cc933ab5f40",
           "auditoriumId":"2b7c76c1-fc3f-3925-9180-2f153c7765b5",
           "startTime":"2026-09-28T13:00:00Z","endTime":"2026-09-28T15:00:00Z"}],
 "nextCursor":null}
```

`cityId` + `date` required; `movieId` + `theaterId` optional. `13:00Z` = the
18:30 IST show.

### §1.6 A filter matching nothing

```bash
curl -s "$API/api/v1/screenings?cityId=$CITY&theaterId=00000000-0000-4000-8000-000000000999&date=$DAY1" | jq -c
```

```json
{"items":[],"nextCursor":null}
```

Empty result, not `404`.

### §1.7 Capture the screening, read its prices

```bash
SCREENING=$(curl -s "$API/api/v1/screenings?cityId=$CITY&date=$DAY1" | jq -r '.items[0].id')
curl -s "$API/api/v1/screenings/$SCREENING" | jq
```

```json
{"id":"0e8a7c76-…","inventorySize":4,
 "prices":[{"category":"PREMIUM","amount":400.00,"currency":"INR"},
           {"category":"REGULAR","amount":250.00,"currency":"INR"}],
 "startTime":"2026-09-28T13:00:00Z","endTime":"2026-09-28T15:00:00Z"}
```

Saturday/Sunday shows read ₹300 / ₹450 — weekend surcharge baked in.

### §1.8 Seat availability

```bash
curl -s "$API/api/v1/screenings/$SCREENING/seats" \
  | jq -c '.[]|{seat:"\(.rowLabel)\(.seatNumber)",category,state,screeningSeatId}'
```

```json
{"seat":"A1","category":"REGULAR","state":"AVAILABLE","screeningSeatId":"b0d3c2c8-…"}
{"seat":"A2","category":"REGULAR","state":"AVAILABLE","screeningSeatId":"cbfa67d1-…"}
{"seat":"B1","category":"PREMIUM","state":"AVAILABLE","screeningSeatId":"99344215-…"}
{"seat":"B2","category":"PREMIUM","state":"AVAILABLE","screeningSeatId":"67e110f0-…"}
```

### §1.9 Rejected pagination inputs

```bash
curl -s "$API/api/v1/cities?page=0&size=500" | jq -c '{status,code,detail}'
curl -s "$API/api/v1/screenings?cityId=$CITY&date=$DAY1&cursor=bm90LWEtcmVhbC1jdXJzb3I" | jq -c '{status,code,detail}'
```

```json
{"status":400,"code":"INVALID_PAGE","detail":"Page size must be between 1 and 100."}
{"status":400,"code":"INVALID_CURSOR","detail":"The pagination cursor is invalid."}
```

Cursors are HMAC-signed.

---

## §2 — Hold, pay, confirm

### §2.1 Hold two seats

```bash
A1=$(curl -s "$API/api/v1/screenings/$SCREENING/seats" | jq -r '.[]|select(.rowLabel=="A" and .seatNumber==1)|.screeningSeatId')
B1=$(curl -s "$API/api/v1/screenings/$SCREENING/seats" | jq -r '.[]|select(.rowLabel=="B" and .seatNumber==1)|.screeningSeatId')

RESERVATION=$(curl -s $CUST1 -H "$JSON" -H 'Idempotency-Key: demo-res-1' \
  -d "{\"screeningSeatIds\":[\"$A1\",\"$B1\"]}" \
  "$API/api/v1/seat-reservations" | tee /tmp/res.json | jq -r .id)
jq . /tmp/res.json
```

```json
{"id":"3093e4b4-…","screeningId":"0e8a7c76-…",
 "screeningSeatIds":["99344215-…","b0d3c2c8-…"],
 "state":"ACTIVE","expiresAt":"2026-09-27T14:43:35Z","createdAt":"2026-09-27T14:39:35Z"}
```

`screeningSeatIds` comes back **sorted** — lock ordering.

### §2.2 Seats flip to RESERVED

```bash
curl -s "$API/api/v1/screenings/$SCREENING/seats" | jq -c '[.[]|{seat:"\(.rowLabel)\(.seatNumber)",state}]' | jq
```

```json
[{"seat":"A1","state":"RESERVED"},{"seat":"A2","state":"AVAILABLE"},
 {"seat":"B1","state":"RESERVED"},{"seat":"B2","state":"AVAILABLE"}]
```

### §2.3 Pay

```bash
BOOKING=$(curl -s $CUST1 -H "$JSON" -H 'Idempotency-Key: demo-book-1' \
  -d "{\"reservationId\":\"$RESERVATION\",\"paymentToken\":\"tok_success\"}" \
  "$API/api/v1/bookings" | tee /tmp/book.json | jq -r .id)
jq . /tmp/book.json
```

```json
{"id":"9130dc04-…","reference":"MTB-9130DC04943D4770","state":"CONFIRMED",
 "paymentStatus":"SUCCEEDED","subtotal":650.00,"discountAmount":0.00,
 "totalAmount":650.00,"currency":"INR",
 "items":[{"rowLabel":"A","seatNumber":1,"category":"REGULAR","unitPrice":250.00},
          {"rowLabel":"B","seatNumber":1,"category":"PREMIUM","unitPrice":400.00}]}
```

₹250 + ₹400 = **₹650**. `items[].unitPrice` is a checkout snapshot.

### §2.4 Read the booking back

```bash
curl -s $CUST1 "$API/api/v1/bookings/$BOOKING" | jq
```

### §2.5 Reservation read and release

```bash
curl -s $CUST1 "$API/api/v1/seat-reservations/$RESERVATION" | jq -c '{state,expiresAt}'

# release path, on a throwaway hold
A2=$(curl -s "$API/api/v1/screenings/$SCREENING/seats" | jq -r '.[]|select(.rowLabel=="A" and .seatNumber==2)|.screeningSeatId')
TMPRES=$(curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: rel-$RANDOM" \
  -d "{\"screeningSeatIds\":[\"$A2\"]}" "$API/api/v1/seat-reservations" | jq -r .id)
curl -s $CUST1 -X DELETE -o /dev/null -w 'release → %{http_code}\n' "$API/api/v1/seat-reservations/$TMPRES"
curl -s $CUST1 -X DELETE -o /dev/null -w 'release again → %{http_code}\n' "$API/api/v1/seat-reservations/$TMPRES"
```

```
release → 204
release again → 204
```

Release is idempotent.

### §2.6 Booking history, cursor pagination

Make a second, cheaper booking first:

```bash
A2=$(curl -s "$API/api/v1/screenings/$SCREENING/seats" | jq -r '.[]|select(.rowLabel=="A" and .seatNumber==2)|.screeningSeatId')
RES_A2=$(curl -s $CUST1 -H "$JSON" -H 'Idempotency-Key: demo-res-2' \
  -d "{\"screeningSeatIds\":[\"$A2\"]}" "$API/api/v1/seat-reservations" | jq -r .id)
curl -s $CUST1 -H "$JSON" -H 'Idempotency-Key: demo-book-2' \
  -d "{\"reservationId\":\"$RES_A2\",\"paymentToken\":\"tok_success\"}" \
  "$API/api/v1/bookings" | jq -c '{state,totalAmount}'
```

```json
{"state":"CONFIRMED","totalAmount":250.00}
```

Walk it one page at a time:

```bash
curl -s $CUST1 "$API/api/v1/bookings?limit=1" | jq -c '{count:(.items|length), total:.items[0].totalAmount, nextCursor}'

CURSOR=$(curl -s $CUST1 "$API/api/v1/bookings?limit=1" | jq -r .nextCursor)
curl -s $CUST1 "$API/api/v1/bookings?limit=1&cursor=$CURSOR" | jq -c '{count:(.items|length), total:.items[0].totalAmount, nextCursor}'
```

```json
{"count":1,"total":250.00,"nextCursor":"eyJ2IjoxLCJrIjo…"}
{"count":1,"total":650.00,"nextCursor":null}
```

Newest first. Mutable list → opaque signed cursor, not `page`/`size`.

---

## §3 — Concurrency

### §3.1 Two customers, one seat, same instant

```bash
SCR3=$(curl -s "$API/api/v1/screenings?cityId=$CITY&date=$DAY3" | jq -r '.items[0].id')
SEAT=$(curl -s "$API/api/v1/screenings/$SCR3/seats" | jq -r '[.[]|select(.state=="AVAILABLE")|.screeningSeatId][0]')

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

Exactly one `201`, one `409 SEAT_UNAVAILABLE`. **The winner changes between
runs** — run it two or three times.

---

## §4 — Discounts

Both codes are seeded. **`DEMO50` has a global limit of one use per database** —
once §4.2 spends it, §4.2 returns the same `422` as §4.3. Reset (§10) before a
recorded take.

### §4.1 Percentage discount — DEMO10

```bash
SCR2=$(curl -s "$API/api/v1/screenings?cityId=$CITY&date=$DAY2" | jq -r '.items[0].id')
SEAT2=$(curl -s "$API/api/v1/screenings/$SCR2/seats" | jq -r '[.[]|select(.state=="AVAILABLE")|.screeningSeatId][0]')
RES2=$(curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: d10-r-$RANDOM" \
  -d "{\"screeningSeatIds\":[\"$SEAT2\"]}" "$API/api/v1/seat-reservations" | jq -r .id)

BOOKING2=$(curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: d10-b-$RANDOM" \
  -d "{\"reservationId\":\"$RES2\",\"discountCode\":\"DEMO10\",\"paymentToken\":\"tok_success\"}" \
  "$API/api/v1/bookings" | tee /tmp/d10.json | jq -r .id)
jq -c '{state,subtotal,discountAmount,totalAmount}' /tmp/d10.json
```

```json
{"state":"CONFIRMED","subtotal":250.00,"discountAmount":25.00,"totalAmount":225.00}
```

### §4.2 Single-use code, first use — DEMO50

```bash
SEAT3=$(curl -s "$API/api/v1/screenings/$SCR2/seats" | jq -r '[.[]|select(.state=="AVAILABLE")|.screeningSeatId][0]')
RES3=$(curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: d50-r-$RANDOM" \
  -d "{\"screeningSeatIds\":[\"$SEAT3\"]}" "$API/api/v1/seat-reservations" | jq -r .id)

curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: d50-b-$RANDOM" \
  -d "{\"reservationId\":\"$RES3\",\"discountCode\":\"DEMO50\",\"paymentToken\":\"tok_success\"}" \
  "$API/api/v1/bookings" | jq -c '{state,subtotal,discountAmount,totalAmount}'
```

```json
{"state":"CONFIRMED","subtotal":250.00,"discountAmount":50.00,"totalAmount":200.00}
```

### §4.3 Second use rejected

```bash
SEAT4=$(curl -s "$API/api/v1/screenings/$SCR2/seats" | jq -r '[.[]|select(.state=="AVAILABLE")|.screeningSeatId][0]')
RES4=$(curl -s $CUST2 -H "$JSON" -H "Idempotency-Key: d50b-r-$RANDOM" \
  -d "{\"screeningSeatIds\":[\"$SEAT4\"]}" "$API/api/v1/seat-reservations" | jq -r .id)

curl -s $CUST2 -H "$JSON" -H "Idempotency-Key: d50b-b-$RANDOM" \
  -d "{\"reservationId\":\"$RES4\",\"discountCode\":\"DEMO50\",\"paymentToken\":\"tok_success\"}" \
  "$API/api/v1/bookings" | jq -c '{status,code,detail}'
```

```json
{"status":422,"code":"DISCOUNT_LIMIT_REACHED",
 "detail":"The discount code usage limit has been reached."}
```

Limit enforced under a row lock on the code, inside the checkout transaction.

---

## §5 — Cancellation and refunds

Seeded policy: **≥ 24 h → 100%, ≥ 2 h → 50%, otherwise 0%.**

### §5.1 Which tier each day lands in — run before recording

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

Two real runs, same seeded data, different times of day:

```
day+1  2026-09-29  starts in   2522 min  ->  100%      # run at 00:30 local
day+2  2026-09-30  starts in   3962 min  ->  100%
day+3  2026-10-01  starts in   5402 min  ->  100%
```

```
day+1  2026-09-28  starts in   1339 min  ->   50%      # run at 20:10 local
day+2  2026-09-29  starts in   2779 min  ->  100%
day+3  2026-09-30  starts in   4219 min  ->  100%
```

**Read this before planning Scene 5.** The seeder creates seven screenings, all
at **18:30 local, starting tomorrow** — there is no screening today. So the
day+1 show sits in the 50% tier only while the clock is between:

| From | To | day+1 tier |
|---|---|---|
| — | today 18:30 | **100%** (show is >24 h out) |
| today 18:30 | tomorrow 16:30 | **50%** |
| tomorrow 16:30 | tomorrow 18:30 | 0% |

Day+2 onward is always 100%.

**Consequence: if you record before 18:30 local, §5.3 refunds ₹650, not ₹325,
and §5.2 and §5.3 both show the 100% tier — the two-tier demo collapses.** Either
record after 18:30, or use §5.3b, which builds a 50% screening on demand and
works at any hour.

### §5.2 Cancel in the 100% tier

`BOOKING2` from §4.1 is on day+2:

```bash
curl -s $CUST1 -X POST -H "Idempotency-Key: cancel-100-$RANDOM" \
  "$API/api/v1/bookings/$BOOKING2/cancellations" | jq
```

```json
{"bookingId":"95e8c9b3-…","bookingState":"CANCELLED",
 "refund":{"id":"…","reason":"CANCELLATION","status":"PENDING","amount":225.00,"currency":"INR"}}
```

Paid ₹225 → refunded ₹225.

### §5.3 Cancel in the 50% tier — only after 18:30 local

`BOOKING` from §2.3 is on day+1. **Check §5.1 first:** this gives 50% only if
day+1 printed `50%`. Before 18:30 local it refunds the full ₹650 instead, which
is correct behaviour but makes the same point as §5.2. Use §5.3b in that case.

```bash
REFUND=$(curl -s $CUST1 -X POST -H 'Idempotency-Key: demo-cancel-1' \
  "$API/api/v1/bookings/$BOOKING/cancellations" | tee /tmp/cancel.json | jq -r .refund.id)
jq -c '{bookingState, amount:.refund.amount}' /tmp/cancel.json
```

```json
{"bookingState":"CANCELLED","amount":325.00}
```

Paid ₹650 → refunded ₹325.

### §5.3b The 50% tier at any hour

Same idea as §5.6, one tier up: create a screening **3 hours out** — more than
the 2 h cutoff, less than the 24 h one — so it lands in the 50% band whatever
the wall clock says.

```bash
H3=$(python3 -c "import datetime;print((datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(hours=3)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
H5=$(python3 -c "import datetime;print((datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(hours=5)).strftime('%Y-%m-%dT%H:%M:%SZ'))")

HALF=$(curl -s $ADMIN -H "$JSON" -d "{\"movieId\":\"$MOVIE\",\"auditoriumId\":\"$AUDITORIUM\",
  \"pricingPlanId\":\"$PLAN\",\"refundPolicyId\":\"$POLICY\",
  \"startTime\":\"$H3\",\"endTime\":\"$H5\"}" \
  "$API/admin/api/v1/screenings" | newid)

SEATH=$(curl -s "$API/api/v1/screenings/$HALF/seats" | jq -r '[.[]|select(.state=="AVAILABLE")|.screeningSeatId][0]')
RESH=$(curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: h-r-$RANDOM" \
  -d "{\"screeningSeatIds\":[\"$SEATH\"]}" "$API/api/v1/seat-reservations" | jq -r .id)
BOOKH=$(curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: h-b-$RANDOM" \
  -d "{\"reservationId\":\"$RESH\",\"paymentToken\":\"tok_success\"}" "$API/api/v1/bookings" | jq -r .id)

curl -s $CUST1 -X POST -H "Idempotency-Key: h-c-$RANDOM" \
  "$API/api/v1/bookings/$BOOKH/cancellations" | jq -c '{bookingState, amount:.refund.amount}'
```

Expect `125.00` on a ₹250 regular seat — half. Two caveats:

- **Overlap.** The seeded 18:30 show occupies Screen 1 from 18:30 to 20:30
  local. If `now + 3 h` falls in that window — i.e. you are recording between
  roughly 15:30 and 17:30 — creation returns a `409` and you should shift to
  `+4 h` / `+6 h`, or just use §5.3, which is in its 50% window by then anyway.
- **This screening survives the soft reset** (§10.1). Hard-reset (§10.2) before
  the take you keep, or it shows up in the catalog during Part 1.

### §5.4 Refund settles; seats return

```bash
sleep 6                      # refund worker runs every 5s
curl -s $CUST1 "$API/api/v1/refunds/$REFUND" | jq -c '{status,amount,reason}'
curl -s "$API/api/v1/screenings/$SCREENING/seats" | jq -c '[.[]|{seat:"\(.rowLabel)\(.seatNumber)",state}]'
```

```json
{"status":"SUCCEEDED","amount":325.00,"reason":"CANCELLATION"}
[{"seat":"A1","state":"AVAILABLE"},{"seat":"A2","state":"BOOKED"},
 {"seat":"B1","state":"AVAILABLE"},{"seat":"B2","state":"AVAILABLE"}]
```

**A2 is still `BOOKED`, and that is correct** — it belongs to the second, ₹250
booking made in §2.6, which nothing has cancelled. Only A1 and B1, the two seats
on the booking cancelled in §5.3, came back. If you skipped §2.6, all four read
`AVAILABLE`.

Seats return **immediately** at cancellation; the refund settles
**asynchronously**, one worker tick later.

To release A2 as well, cancel that booking too:

```bash
BOOKING_A2=$(curl -s $CUST1 "$API/api/v1/bookings?limit=5" | jq -r '.items[]|select(.totalAmount==250.00 and .state=="CONFIRMED")|.id' | head -1)
curl -s $CUST1 -X POST -H "Idempotency-Key: cancel-a2-$RANDOM" \
  "$API/api/v1/bookings/$BOOKING_A2/cancellations" | jq -c '{bookingState, amount:.refund.amount}'
curl -s "$API/api/v1/screenings/$SCREENING/seats" | jq -c '[.[].state]'
```

### §5.5 Cancelling again is free

```bash
curl -s $CUST1 -X POST -H 'Idempotency-Key: demo-cancel-1' \
  "$API/api/v1/bookings/$BOOKING/cancellations" | jq -r .refund.id
```

Same refund id. No second refund created.

### §5.6 The 0% tier — needs a show inside 2 hours

The seeder does not create one. Build it as admin:

```bash
START=$(python3 -c "import datetime;print((datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(hours=1)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
END=$(python3   -c "import datetime;print((datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(hours=3)).strftime('%Y-%m-%dT%H:%M:%SZ'))")

SOON=$(curl -s $ADMIN -H "$JSON" -d "{\"movieId\":\"$MOVIE\",\"auditoriumId\":\"$AUDITORIUM\",
  \"pricingPlanId\":\"$PLAN\",\"refundPolicyId\":\"$POLICY\",
  \"startTime\":\"$START\",\"endTime\":\"$END\"}" \
  "$API/admin/api/v1/screenings" | jq -r .id)

SEAT0=$(curl -s "$API/api/v1/screenings/$SOON/seats" | jq -r '[.[]|select(.state=="AVAILABLE")|.screeningSeatId][0]')
RES0=$(curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: z-r-$RANDOM" \
  -d "{\"screeningSeatIds\":[\"$SEAT0\"]}" "$API/api/v1/seat-reservations" | jq -r .id)
BOOK0=$(curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: z-b-$RANDOM" \
  -d "{\"reservationId\":\"$RES0\",\"paymentToken\":\"tok_success\"}" "$API/api/v1/bookings" | jq -r .id)

curl -s $CUST1 -X POST -H "Idempotency-Key: z-c-$RANDOM" \
  "$API/api/v1/bookings/$BOOK0/cancellations" | jq -c '{bookingState, amount:.refund.amount}'
```

```json
{"bookingState":"CANCELLED","amount":0.00}
```

Booking still cancels; refund is `0.00`.

---

## §6 — Failure and safety paths

### §6.1 Declined payment

```bash
SCR4=$(curl -s "$API/api/v1/screenings?cityId=$CITY&date=$DAY3" | jq -r '.items[0].id')
SEATD=$(curl -s "$API/api/v1/screenings/$SCR4/seats" | jq -r '[.[]|select(.state=="AVAILABLE")|.screeningSeatId][0]')
RESD=$(curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: dec-r-$RANDOM" \
  -d "{\"screeningSeatIds\":[\"$SEATD\"]}" "$API/api/v1/seat-reservations" | jq -r .id)

curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: dec-b-$RANDOM" \
  -d "{\"reservationId\":\"$RESD\",\"paymentToken\":\"tok_decline\"}" \
  "$API/api/v1/bookings" | jq -c '{state,paymentStatus,totalAmount}'

curl -s "$API/api/v1/screenings/$SCR4/seats" | jq -r --arg s "$SEATD" '.[]|select(.screeningSeatId==$s)|"seat → \(.state)"'
```

```json
{"state":"FAILED","paymentStatus":"DECLINED","totalAmount":250.00}
"seat → AVAILABLE"
```

`FAILED` is a queryable record, not a thrown-away error. Seat back on sale.

### §6.2 A retry is safe

```bash
BODY="{\"screeningSeatIds\":[\"$SEATD\"]}"
curl -s $CUST1 -H "$JSON" -H 'Idempotency-Key: demo-idem' -d "$BODY" "$API/api/v1/seat-reservations" | jq -r .id
curl -s $CUST1 -H "$JSON" -H 'Idempotency-Key: demo-idem' -d "$BODY" "$API/api/v1/seat-reservations" | jq -r .id
```

```
3df07737-9bf2-4616-8d90-2dfaf49b867b
3df07737-9bf2-4616-8d90-2dfaf49b867b
```

### §6.3 Reusing a key for a different body is not

```bash
curl -s $CUST1 -H "$JSON" -H 'Idempotency-Key: demo-idem' \
  -d '{"screeningSeatIds":["00000000-0000-4000-8000-000000000001"]}' \
  "$API/api/v1/seat-reservations" | jq -c '{status,code}'
```

```json
{"status":409,"code":"IDEMPOTENCY_KEY_REUSED"}
```

### §6.4 Error shape

```bash
curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: bad-$RANDOM" \
  -d '{"screeningSeatIds":[]}' "$API/api/v1/seat-reservations" | jq
```

```json
{"detail":"Request validation failed.",
 "instance":"/api/v1/seat-reservations","status":400,
 "title":"Invalid request","type":"/problems/invalid-request",
 "code":"INVALID_REQUEST","requestId":"a8e277e3-…",
 "fieldErrors":[{"field":"screeningSeatIds","code":"Size","message":"size must be between 1 and 10"}]}
```

RFC 9457 `application/problem+json`, stable `code`, per-field errors,
`requestId` also returned as `X-Request-Id`. No stack trace, SQL, payment token
or provider detail.

### §6.5 Authorization matrix

```bash
printf 'anonymous → public catalog  %s\n' "$(curl -s -o /dev/null -w '%{http_code}' "$API/api/v1/cities")"
printf 'anonymous → bookings        %s\n' "$(curl -s -o /dev/null -w '%{http_code}' "$API/api/v1/bookings")"
printf 'customer  → admin endpoint  %s\n' "$(curl -s -o /dev/null -w '%{http_code}' $CUST1 "$API/admin/api/v1/cities")"
printf 'admin     → customer view   %s\n' "$(curl -s -o /dev/null -w '%{http_code}' $ADMIN "$API/api/v1/bookings")"
```

```
anonymous → public catalog  200
anonymous → bookings        401
customer  → admin endpoint  403
admin     → customer view   403
```

### §6.6 Ownership is concealed, not refused

```bash
curl -s -o /dev/null -w 'customer2 reads customer1 booking → %{http_code}\n' $CUST2 "$API/api/v1/bookings/$BOOKING"
```

```
customer2 reads customer1 booking → 404
```

`403` would confirm the booking exists.

---

## §7 — Admin (demo subset)

### §7.1 Screenings, admin filters + offset pagination

```bash
curl -s $ADMIN "$API/admin/api/v1/screenings?auditoriumId=$AUDITORIUM&movieId=$MOVIE&page=0&size=3" \
  | jq -c '{returned:(.items|length),page,size,totalElements,totalPages}'
```

```json
{"returned":3,"page":0,"size":3,"totalElements":7,"totalPages":3}
```

### §7.2 Seeded discount codes

```bash
curl -s $ADMIN "$API/admin/api/v1/discount-codes?page=0&size=10" \
  | jq -c '.items[]|{code,type,value,globalUsageLimit,perCustomerUsageLimit,active}'
```

```json
{"code":"DEMO10","type":"PERCENTAGE","value":10.00,"globalUsageLimit":null,"perCustomerUsageLimit":5,"active":true}
{"code":"DEMO50","type":"FIXED","value":50.00,"globalUsageLimit":1,"perCustomerUsageLimit":1,"active":true}
```

### §7.3 The refund policy driving §5

```bash
curl -s $ADMIN "$API/admin/api/v1/refund-policies?page=0&size=5" \
  | jq -c '.items[]|{name,rules:[.rules[]|{cutoffMinutes,refundPercentage}]}'
```

```json
{"name":"Demo Refund Policy","rules":[{"cutoffMinutes":1440,"refundPercentage":100.00},
                                      {"cutoffMinutes":120,"refundPercentage":50.00},
                                      {"cutoffMinutes":0,"refundPercentage":0.00}]}
```

### §7.4 Cancel a whole show

Uses day+5, so the screenings you demoed on stay intact.

```bash
SCR5=$(curl -s "$API/api/v1/screenings?cityId=$CITY&date=$DAY5" | jq -r '.items[0].id')
SEAT5=$(curl -s "$API/api/v1/screenings/$SCR5/seats" | jq -r '.[0].screeningSeatId')
RES5=$(curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: sc-r-$RANDOM" \
  -d "{\"screeningSeatIds\":[\"$SEAT5\"]}" "$API/api/v1/seat-reservations" | jq -r .id)
BOOK5=$(curl -s $CUST1 -H "$JSON" -H "Idempotency-Key: sc-b-$RANDOM" \
  -d "{\"reservationId\":\"$RES5\",\"paymentToken\":\"tok_success\"}" "$API/api/v1/bookings" | jq -r .id)

curl -s $ADMIN -X DELETE -o /dev/null -w 'admin DELETE screening → %{http_code}\n' \
  "$API/admin/api/v1/screenings/$SCR5"

sleep 6
curl -s $CUST1 "$API/api/v1/bookings/$BOOK5" | jq -c '{state, refunds:[.refunds[]|{reason,status,amount}]}'
curl -s "$API/api/v1/screenings?cityId=$CITY&date=$DAY5" | jq -c '.items|length'
```

```
admin DELETE screening → 204
{"state":"CANCELLED","refunds":[{"reason":"SHOW_CANCELLED","status":"SUCCEEDED","amount":250.00}]}
0
```

`SHOW_CANCELLED`, **full** amount regardless of cutoff tier — the customer is
not penalised for a cancellation that is not their fault. Runs in bounded
batches with a sweeper.

### §7.5 Screening prices

```bash
curl -s $ADMIN "$API/admin/api/v1/screenings/$SCREENING" | jq -c
curl -s $ADMIN "$API/admin/api/v1/screenings/$SCREENING/prices" | jq -c
```

---

## §8 — Admin: full CRUD surface

All require `$ADMIN`. Every `DELETE` here is a **deactivate** (soft) — records
referenced by screenings are never hard-deleted, and browse queries exclude
inactive rows. Create these against a scratch database.

### §8.0 Run this first — names must be unique per run

Almost every catalog record has a uniqueness constraint, so **re-running a §8
block with the same literal name returns `409 DATA_CONFLICT`**. The 409 body has
no `.id`, so `jq -r .id` yields the string `null`, every following URL becomes
`…/cities/null`, and you get a cascade of `400`s that looks like a broken
endpoint but is just a name collision:

```json
{"status":400,"code":"INVALID_REQUEST","instance":"/admin/api/v1/cities/null"}
```

| Record | Unique on | Re-runnable? |
|---|---|---|
| City | `(name, country)` | no |
| Theater | `(cityId, name)` | no |
| Auditorium | `(theaterId, name)` | no |
| Seat | `(auditoriumId, rowLabel, seatNumber)` | no |
| Pricing plan | `name` | no |
| Refund policy | `name` | no |
| Discount code | `code` | no |
| Movie | — | yes |

Two lines make every block below re-runnable and loud on failure:

```bash
SUF=$RANDOM        # fresh suffix per run; re-run this line before re-running a block

newid() { local body id; body=$(cat); id=$(jq -r '.id // empty' <<<"$body")
  if [ -z "$id" ]; then printf 'CREATE FAILED: %s\n' "$body" >&2; return 1; fi
  printf '%s\n' "$id"; }
```

`newid` prints the server's actual error instead of silently handing you `null`.

**Finish every block you start.** A record you create and do not deactivate is
`active:true`, and active records *are* public — an abandoned §8.1 leaves an
extra city in `GET /api/v1/cities`, which is §0.3's and §1.1's expected output
and the first thing on camera in Scene 4:

```bash
# sanity check before recording — must be exactly Demo Pune
curl -s "$API/api/v1/cities" | jq -c '{totalElements, names:[.items[].name]}'
# {"totalElements":1,"names":["Demo Pune"]}
```

If it is not, deactivate the extras (`DELETE /admin/api/v1/cities/{id}`) or hard
reset. Rows already reading `active:false` are harmless — they stay in the admin
list by design and never reach public browse. §10.2 clears everything.

### §8.1 Cities

```bash
NEWCITY=$(curl -s $ADMIN -H "$JSON" \
  -d "{\"name\":\"Test Mumbai $SUF\",\"country\":\"India\",\"timeZone\":\"Asia/Kolkata\"}" \
  "$API/admin/api/v1/cities" | newid)

curl -s $ADMIN "$API/admin/api/v1/cities?page=0&size=20" | jq -c '.items[]|{id,name,active}'
curl -s $ADMIN "$API/admin/api/v1/cities/$NEWCITY" | jq -c

curl -s $ADMIN -X PUT -H "$JSON" \
  -d "{\"name\":\"Test Mumbai Central $SUF\",\"country\":\"India\",\"timeZone\":\"Asia/Kolkata\"}" \
  "$API/admin/api/v1/cities/$NEWCITY" | jq -c '{name,version}'

curl -s $ADMIN -X DELETE -o /dev/null -w 'deactivate city → %{http_code}\n' \
  "$API/admin/api/v1/cities/$NEWCITY"
```

The create returns `201` with the full record — this is the response `$NEWCITY`
is read from, and it is also §8.10's proof that system-managed fields are
server-set:

```json
{"id":"7e41e97c-…","name":"Probe City","country":"India","timeZone":"Asia/Kolkata",
 "active":true,"createdAt":"2026-09-27T19:07:07.236909Z",
 "updatedAt":"2026-09-27T19:07:07.236909Z","version":0}
```

A new city is `active:true`, `version:0`. Rows reading `active:false` in the
list are ones a previous run's `DELETE` deactivated.

**`version` in a `PUT` response body is the pre-update value.** Verified on a
live instance:

```
PUT response:  {"name":"VerProbe b","version":0}
GET after PUT: {"name":"VerProbe b","version":1}
GET after DEL: {"name":"VerProbe b","active":false,"version":2}
```

The write lands correctly and the stored version does increment — the returned
representation is just rendered before the JPA flush. Nothing in this API
consumes `version` (there is no `If-Match` or version parameter on any
endpoint), so it affects nothing you can demo, but do not read the `version` in
a `PUT` response as the current one. Note also that a soft delete is a normal
update, so deactivating bumps the version too.

`CityRequest`: `name`, `country`, `timeZone` — all required. Unique on
`(name, country)`, so the same name with a different country is accepted.

### §8.2 Theaters

```bash
NEWTHEATER=$(curl -s $ADMIN -H "$JSON" \
  -d "{\"cityId\":\"$CITY\",\"name\":\"Test Cinema $SUF\",\"address\":\"Kothrud, Pune\"}" \
  "$API/admin/api/v1/theaters" | newid)

curl -s $ADMIN "$API/admin/api/v1/theaters?cityId=$CITY&page=0&size=20" | jq -c '.items[]|{id,name,active}'
curl -s $ADMIN "$API/admin/api/v1/theaters/$NEWTHEATER" | jq -c

curl -s $ADMIN -X PUT -H "$JSON" \
  -d "{\"cityId\":\"$CITY\",\"name\":\"Test Cinema Annexe $SUF\",\"address\":\"Kothrud, Pune\"}" \
  "$API/admin/api/v1/theaters/$NEWTHEATER" | jq -c '{name,version}'

curl -s $ADMIN -X DELETE -o /dev/null -w 'deactivate theater → %{http_code}\n' \
  "$API/admin/api/v1/theaters/$NEWTHEATER"
```

`TheaterRequest`: `cityId`, `name`, `address` — all required.

### §8.3 Auditoriums

```bash
NEWAUD=$(curl -s $ADMIN -H "$JSON" -d "{\"name\":\"Screen $SUF\"}" \
  "$API/admin/api/v1/theaters/$THEATER/auditoriums" | newid)

curl -s $ADMIN "$API/admin/api/v1/theaters/$THEATER/auditoriums?page=0&size=20" | jq -c '.items[]|{id,name,active}'
curl -s $ADMIN "$API/admin/api/v1/auditoriums/$NEWAUD" | jq -c

curl -s $ADMIN -X PUT -H "$JSON" -d "{\"name\":\"Screen $SUF IMAX\"}" \
  "$API/admin/api/v1/auditoriums/$NEWAUD" | jq -c '{name,version}'

curl -s $ADMIN -X DELETE -o /dev/null -w 'deactivate auditorium → %{http_code}\n' \
  "$API/admin/api/v1/auditoriums/$NEWAUD"
```

`AuditoriumRequest`: `name` only. The theater comes from the path.

### §8.4 Seats (the physical layout)

```bash
SEATNO=$((RANDOM % 900 + 100))
NEWSEAT=$(curl -s $ADMIN -H "$JSON" \
  -d "{\"rowLabel\":\"Z\",\"seatNumber\":$SEATNO,\"category\":\"PREMIUM\"}" \
  "$API/admin/api/v1/auditoriums/$AUDITORIUM/seats" | newid)

curl -s $ADMIN "$API/admin/api/v1/auditoriums/$AUDITORIUM/seats?page=0&size=50" \
  | jq -c '.items[]|{rowLabel,seatNumber,category,active}'
curl -s $ADMIN "$API/admin/api/v1/seats/$NEWSEAT" | jq -c

curl -s $ADMIN -X PUT -H "$JSON" \
  -d "{\"rowLabel\":\"Z\",\"seatNumber\":$SEATNO,\"category\":\"REGULAR\"}" \
  "$API/admin/api/v1/seats/$NEWSEAT" | jq -c '{rowLabel,seatNumber,category,version}'

curl -s $ADMIN -X DELETE -o /dev/null -w 'deactivate seat → %{http_code}\n' \
  "$API/admin/api/v1/seats/$NEWSEAT"
```

`SeatRequest`: `rowLabel` (≤10 chars), `seatNumber` (1–1000),
`category` (`REGULAR` | `PREMIUM`).

Layout changes are **refused while future screenings depend on that layout** —
expect a `409`/`422` if you edit a seat the seeded screenings materialise.

### §8.5 Movies

```bash
NEWMOVIE=$(curl -s $ADMIN -H "$JSON" \
  -d '{"title":"The Rollback","durationMinutes":95,"language":"Hindi"}' \
  "$API/admin/api/v1/movies" | newid)

curl -s $ADMIN "$API/admin/api/v1/movies?page=0&size=20" | jq -c '.items[]|{id,title,active}'
curl -s $ADMIN "$API/admin/api/v1/movies/$NEWMOVIE" | jq -c

curl -s $ADMIN -X PUT -H "$JSON" \
  -d '{"title":"The Rollback: Directors Cut","durationMinutes":130,"language":"Hindi"}' \
  "$API/admin/api/v1/movies/$NEWMOVIE" | jq -c '{title,durationMinutes,version}'

curl -s $ADMIN -X DELETE -o /dev/null -w 'deactivate movie → %{http_code}\n' \
  "$API/admin/api/v1/movies/$NEWMOVIE"
```

`MovieRequest`: `title`, `durationMinutes` (1–1440), `language`.

### §8.6 Pricing plans

```bash
NEWPLAN=$(curl -s $ADMIN -H "$JSON" \
  -d "{\"name\":\"Test Pricing $SUF\",\"regularPrice\":300.00,\"premiumPrice\":500.00,\"weekendAdjustment\":75.00}" \
  "$API/admin/api/v1/pricing-plans" | newid)

curl -s $ADMIN "$API/admin/api/v1/pricing-plans?page=0&size=20" \
  | jq -c '.items[]|{id,name,regularPrice,premiumPrice,weekendAdjustment,active}'
curl -s $ADMIN "$API/admin/api/v1/pricing-plans/$NEWPLAN" | jq -c

curl -s $ADMIN -X PUT -H "$JSON" \
  -d "{\"name\":\"Test Pricing $SUF\",\"regularPrice\":320.00,\"premiumPrice\":520.00,\"weekendAdjustment\":80.00}" \
  "$API/admin/api/v1/pricing-plans/$NEWPLAN" | jq -c '{regularPrice,version}'

curl -s $ADMIN -X DELETE -o /dev/null -w 'deactivate pricing plan → %{http_code}\n' \
  "$API/admin/api/v1/pricing-plans/$NEWPLAN"
```

`PricingPlanRequest`: `name`, `regularPrice`, `premiumPrice`,
`weekendAdjustment` — all required. Prices are snapshotted onto a screening at
creation, so editing a plan never changes an existing screening or booking.

### §8.7 Refund policies

```bash
NEWPOLICY=$(curl -s $ADMIN -H "$JSON" -d "{
  \"name\":\"Strict Policy $SUF\",
  \"rules\":[{\"cutoffMinutes\":2880,\"refundPercentage\":100.00},
            {\"cutoffMinutes\":720,\"refundPercentage\":25.00},
            {\"cutoffMinutes\":0,\"refundPercentage\":0.00}]}" \
  "$API/admin/api/v1/refund-policies" | newid)

curl -s $ADMIN "$API/admin/api/v1/refund-policies?page=0&size=20" | jq -c '.items[]|{id,name,active}'
curl -s $ADMIN "$API/admin/api/v1/refund-policies/$NEWPOLICY" | jq -c

curl -s $ADMIN -X PUT -H "$JSON" -d "{
  \"name\":\"Strict Policy $SUF v2\",
  \"rules\":[{\"cutoffMinutes\":2880,\"refundPercentage\":90.00},
            {\"cutoffMinutes\":0,\"refundPercentage\":0.00}]}" \
  "$API/admin/api/v1/refund-policies/$NEWPOLICY" | jq -c '{name,rules:[.rules[]|{cutoffMinutes,refundPercentage}]}'

curl -s $ADMIN -X DELETE -o /dev/null -w 'deactivate refund policy → %{http_code}\n' \
  "$API/admin/api/v1/refund-policies/$NEWPOLICY"
```

`RefundPolicyRequest`: `name`, `rules` (1–20). Each rule:
`cutoffMinutes` (≥0), `refundPercentage` (0–100). Semantics: *cancel at least N
minutes before start → P%*; the most generous matching rule wins, no match means
no refund.

### §8.8 Discount codes

```bash
FROM=$(python3 -c "import datetime;print(datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'))")
UNTIL=$(python3 -c "import datetime;print((datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(days=30)).strftime('%Y-%m-%dT%H:%M:%SZ'))")

NEWCODE=$(curl -s $ADMIN -H "$JSON" -d "{
  \"code\":\"TEST$SUF\",\"type\":\"PERCENTAGE\",\"value\":20.00,
  \"validFrom\":\"$FROM\",\"validUntil\":\"$UNTIL\",
  \"minimumSpend\":200.00,\"maximumDiscount\":150.00,
  \"globalUsageLimit\":50,\"perCustomerUsageLimit\":2}" \
  "$API/admin/api/v1/discount-codes" | newid)

curl -s $ADMIN "$API/admin/api/v1/discount-codes?page=0&size=20" | jq -c '.items[]|{code,type,value,active}'
curl -s $ADMIN "$API/admin/api/v1/discount-codes/$NEWCODE" | jq -c

curl -s $ADMIN -X PUT -H "$JSON" -d "{
  \"code\":\"TEST$SUF\",\"type\":\"PERCENTAGE\",\"value\":25.00,
  \"validFrom\":\"$FROM\",\"validUntil\":\"$UNTIL\",
  \"minimumSpend\":200.00,\"maximumDiscount\":150.00,
  \"globalUsageLimit\":50,\"perCustomerUsageLimit\":2}" \
  "$API/admin/api/v1/discount-codes/$NEWCODE" | jq -c '{code,value,version}'

curl -s $ADMIN -X DELETE -o /dev/null -w 'deactivate discount code → %{http_code}\n' \
  "$API/admin/api/v1/discount-codes/$NEWCODE"
```

`DiscountCodeRequest` required: `code`, `type` (`PERCENTAGE` | `FIXED`), `value`,
`validFrom`, `validUntil`, `minimumSpend`. Optional: `maximumDiscount`,
`globalUsageLimit`, `perCustomerUsageLimit`.

### §8.9 Screenings

```bash
ST=$(python3 -c "import datetime;print((datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(days=9)).strftime('%Y-%m-%dT10:00:00Z'))")
EN=$(python3 -c "import datetime;print((datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(days=9)).strftime('%Y-%m-%dT12:00:00Z'))")

NEWSCR=$(curl -s $ADMIN -H "$JSON" -d "{
  \"movieId\":\"$MOVIE\",\"auditoriumId\":\"$AUDITORIUM\",
  \"pricingPlanId\":\"$PLAN\",\"refundPolicyId\":\"$POLICY\",
  \"startTime\":\"$ST\",\"endTime\":\"$EN\"}" \
  "$API/admin/api/v1/screenings" | jq -r .id)

curl -s $ADMIN "$API/admin/api/v1/screenings/$NEWSCR" | jq -c
curl -s $ADMIN "$API/admin/api/v1/screenings/$NEWSCR/prices" | jq -c
curl -s $ADMIN "$API/admin/api/v1/screenings?auditoriumId=$AUDITORIUM&page=0&size=10" \
  | jq -c '{totalElements,totalPages}'

# cancel it (refunds every confirmed booking in full, takes it off sale)
curl -s $ADMIN -X DELETE -o /dev/null -w 'cancel screening → %{http_code}\n' \
  "$API/admin/api/v1/screenings/$NEWSCR"
```

`CreateScreeningRequest`: `movieId`, `auditoriumId`, `pricingPlanId`,
`refundPolicyId`, `startTime`, `endTime` — all required, times in UTC.
Creation materialises one `screening_seat` row per active seat in the
auditorium. There is no screening `PUT`: a screening is created or cancelled,
never edited, so its price snapshot cannot move under a booking.

### §8.10 Overlap and validation rejections

```bash
# same auditorium, overlapping window
curl -s $ADMIN -H "$JSON" -d "{
  \"movieId\":\"$MOVIE\",\"auditoriumId\":\"$AUDITORIUM\",
  \"pricingPlanId\":\"$PLAN\",\"refundPolicyId\":\"$POLICY\",
  \"startTime\":\"${DAY1}T13:00:00Z\",\"endTime\":\"${DAY1}T15:00:00Z\"}" \
  "$API/admin/api/v1/screenings" | jq -c '{status,code,detail}'

# end before start
curl -s $ADMIN -H "$JSON" -d "{
  \"movieId\":\"$MOVIE\",\"auditoriumId\":\"$AUDITORIUM\",
  \"pricingPlanId\":\"$PLAN\",\"refundPolicyId\":\"$POLICY\",
  \"startTime\":\"${DAY3}T15:00:00Z\",\"endTime\":\"${DAY3}T13:00:00Z\"}" \
  "$API/admin/api/v1/screenings" | jq -c '{status,code,detail}'

# unknown reference
curl -s $ADMIN -H "$JSON" \
  -d '{"cityId":"00000000-0000-4000-8000-000000000999","name":"Ghost","address":"Nowhere"}' \
  "$API/admin/api/v1/theaters" | jq -c '{status,code,detail}'

# client-supplied system field is ignored, not accepted
curl -s $ADMIN -H "$JSON" \
  -d '{"name":"Injected","country":"India","timeZone":"Asia/Kolkata","id":"00000000-0000-4000-8000-000000000001","active":false,"version":99}' \
  "$API/admin/api/v1/cities" | jq -c '{id,active,version}'
```

The last one returns a server-generated `id`, `active:true` and `version:0` —
internal IDs, ownership, timestamps, versions and deletion state are never
accepted from a client.

---

## §9 — Tests

Unit tests run first each time (~2 s). Integration suites need Docker.

### §9.1 The concurrency guarantee — §3

```bash
./mvnw verify -Dit.test=ReservationConcurrencyIT -DfailIfNoTests=false
```

25 customers on one seat, exactly one winner; reversed lock order; disjoint
seats proceeding in parallel.

### §9.2 The demo dataset — §1 and §4

```bash
./mvnw verify -Dit.test=DemoDatasetIT -DfailIfNoTests=false
```

Seeded catalog and week of screenings, `DEMO10`, the `DEMO50` limit, seeder
idempotency.

### §9.3 Races you cannot show by hand

```bash
./mvnw verify -Dit.test=WorkflowRaceIT -DfailIfNoTests=false
```

Payment succeeding *after* the lease expired and the seat was resold, the
last-discount race, cleanup versus reclaim, two workers claiming one job.

### §9.4 End to end — §2, §5, §6

```bash
./mvnw verify -Dit.test=BookingJourneysIT,ShowCancellationIT,RefundWorkerIT -DfailIfNoTests=false
```

### §9.5 Contracts

```bash
./mvnw verify -Dit.test=ApiContractIT,DatabaseContractIT -DfailIfNoTests=false
```

Roles, concealed `404`, Problem Details shape, request IDs, the exact OpenAPI
path/method surface; migrations from empty, Hibernate validation, unique
constraints, UTC auditing, optimistic versions.

### §9.6 Capacity

```bash
./mvnw verify -Dit.test=CapacityDatasetIT -DfailIfNoTests=false
```

840 screenings, ~126,000 screening-seat rows, 10,000 bookings paged through.
A **functional** check that queries stay bounded — not a throughput benchmark.

### §9.7 Unit tests only, no Docker

```bash
./mvnw clean test
```

### §9.8 Everything

```bash
./mvnw clean verify
```

```
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0     (unit)
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0     (integration)
BUILD SUCCESS
```

**53 tests, 0 failures.** This is the terminal to have open while recording.

### §9.9 Against an existing PostgreSQL instead of Testcontainers

```bash
./mvnw clean verify \
  -Dtest.database.url=jdbc:postgresql://localhost:5432/movie_tickets_test \
  -Dtest.database.username=movie_tickets \
  -Dtest.database.password=your-password
```

If Testcontainers fails with a Docker Hub `401`, the usual cause is a stale
saved credential — `docker logout` restores anonymous pulls.

---

## §10 — Reset

Rehearsing dirties the data: seats stay `BOOKED`, `DEMO50` is spent, and the
fixed keys in this runbook (`demo-res-1`, `demo-book-1`, `demo-cancel-1`) are
already used.

### §10.1 Soft reset — between takes, no restart, ~40 ms

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

Leave the app running. Every seat returns to `AVAILABLE`, history empties,
`DEMO50` gets its single use back, and every `Idempotency-Key` in this document
is replayable. The six fixed IDs in §0.2 do not change.

Anything created as admin mid-run (extra screenings from §5.6 or §8, extra
discount codes) **survives this**.

### §10.2 Hard reset — before the take you keep

```sql
DROP DATABASE movie_tickets;
CREATE DATABASE movie_tickets OWNER movie_tickets;
```

Restart the app. Flyway and the seeder rebuild everything in a few seconds. The
seeders run at startup, so a truncate alone is not enough — the app has to boot
for the data to return.
