# Movie Ticket Booking Platform

A Spring Boot backend for booking movie seats: cities, theaters, screens, seat maps, shows, time-bound seat
holds that survive concurrent requests, tiered pricing with coupons, a payment abstraction with idempotency,
configurable refunds, and asynchronous notifications.

For a step-by-step explanation of every layer and the full booking flow with database state at each stage,
see [WALKTHROUGH.md](WALKTHROUGH.md).

## Run it

Requires Java 21. The Maven wrapper is included.

```bash
./mvnw spring-boot:run
```

The app starts on http://localhost:8080 with an embedded H2 database and demo data:

| Account              | Password      | Role     |
|----------------------|---------------|----------|
| admin@example.com    | admin123      | ADMIN    |
| customer@example.com | customer123   | CUSTOMER |

Demo data also includes one city, one theater with a 5x8 screen (rows D and E premium), one movie, two shows
tomorrow, a `WELCOME10` coupon and a default refund policy. H2 console: http://localhost:8080/h2-console
(JDBC URL `jdbc:h2:mem:moviebooking`, user `sa`, empty password).

To use PostgreSQL instead:

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

Run the tests:

```bash
./mvnw test
```

## Try the booking flow

```bash
# 1. Browse (no login needed)
curl localhost:8080/api/cities
curl "localhost:8080/api/shows?cityId=1"
curl localhost:8080/api/shows/1/seats

# 2. Hold two seats (5 minute hold)
curl -u customer@example.com:customer123 -H 'Content-Type: application/json' \
  -d '{"seatIds":[1,2]}' localhost:8080/api/shows/1/holds
# -> {"holdId":"...","seatLabels":["A1","A2"],"expiresAt":"..."}

# 3. Create the booking (price is calculated here)
curl -u customer@example.com:customer123 -H 'Content-Type: application/json' \
  -d '{"holdId":"<holdId>","couponCode":"WELCOME10"}' localhost:8080/api/bookings

# 4. Pay. Same idempotencyKey twice = one charge. paymentMethod starting with FAIL simulates a decline.
curl -u customer@example.com:customer123 -H 'Content-Type: application/json' \
  -d '{"idempotencyKey":"any-unique-string","paymentMethod":"CARD"}' localhost:8080/api/bookings/1/pay

# 5. Cancel (refund follows the show's refund policy)
curl -u customer@example.com:customer123 -X POST localhost:8080/api/bookings/1/cancel

# 6. See the async notifications that were generated
curl -u customer@example.com:customer123 localhost:8080/api/notifications
```

## API overview

| Method | Path                            | Who      | Purpose                                   |
|--------|---------------------------------|----------|-------------------------------------------|
| POST   | /api/auth/register              | public   | Sign up as a customer                     |
| GET    | /api/auth/me                    | any user | Current user                              |
| GET    | /api/cities                     | public   | List cities                               |
| GET    | /api/theaters?cityId=           | public   | Theaters in a city                        |
| GET    | /api/movies?cityId=             | public   | Movies (playing in a city if given)       |
| GET    | /api/shows?cityId=&movieId=&date= | public | Upcoming shows                            |
| GET    | /api/shows/{id}                 | public   | Show details                              |
| GET    | /api/shows/{id}/seats           | public   | Seat map with AVAILABLE / HELD / BOOKED   |
| POST   | /api/shows/{id}/holds           | customer | Hold seats                                |
| DELETE | /api/holds/{holdId}             | customer | Release a hold early                      |
| POST   | /api/bookings                   | customer | Create booking from a hold (+ coupon)     |
| POST   | /api/bookings/{id}/pay          | customer | Pay (idempotent)                          |
| POST   | /api/bookings/{id}/cancel       | customer | Cancel and refund                         |
| GET    | /api/bookings, /{id}            | customer | Own bookings only                         |
| GET    | /api/notifications              | customer | Own notification feed                     |
| POST   | /api/admin/cities, theaters, screens, movies, shows, refund-policies, coupons | admin | Catalog setup |

Errors always look like:

```json
{"status":409,"code":"SEAT_UNAVAILABLE","message":"Seats not available: A1","timestamp":"...","path":"/api/shows/1/holds","fieldErrors":null}
```

## How it is built

**Modular monolith**, one Maven module, packages by domain:

```
com.moviebooking
├── config        security (HTTP Basic + roles), properties, demo data seeder
├── common        ApiException hierarchy, ApiError body, global exception handler
├── user          User, Role, registration, Spring Security UserDetails adapter
├── catalog       City → Theater → Screen → Seat, Movie → Show, RefundPolicy; admin + discovery APIs
├── inventory     SeatAllocation (the concurrency guarantee), SeatHoldService, expiry job
├── booking       Booking state machine, pricing, coupons, PaymentGateway + simulated impl, refunds
└── notification  async event listeners, notification records, reminder job
```

Controllers are thin, services own business logic and transactions, entities never leave the service layer
(DTOs are records nested in `*Dtos` classes).

### How double booking is prevented

The `seat_allocations` table has a **unique constraint on (show_id, seat_id)**. A row exists only while a seat
is taken (status HELD or BOOKED). Holding seats is an INSERT of all requested seats in one transaction:

- Two users race for A10: both INSERT the same key, the database commits exactly one and the other gets a
  constraint violation, which the API returns as `409 SEAT_UNAVAILABLE`.
- Multi-seat holds are all-or-nothing because they share a transaction.
- Expired HELD rows are treated as free, deleted lazily on the next hold attempt for those seats, and swept
  every 30 seconds. So the guarantee never depends on the scheduler being alive.
- Paying and cancelling lock the relevant rows (`SELECT ... FOR UPDATE`) so a payment cannot confirm a hold
  that is being expired underneath it.

No Java-level `synchronized` is involved; correctness lives in the database.

### Booking lifecycle

```
hold  ──►  PENDING_PAYMENT  ──pay ok──►  CONFIRMED  ──cancel──►  CANCELLED
                │
                └── hold expires ──►  EXPIRED
```

`Booking.transitionTo` rejects every other transition. A failed payment leaves the booking in
PENDING_PAYMENT with its hold intact so the customer can retry with a new idempotency key.

### Payment idempotency

Every payment attempt is a row in `payments` with a unique `idempotency_key`. A retried request with the same
key finds the existing row and returns the booking's current state without calling the gateway again. If two
identical requests race, the loser hits the unique constraint and simply re-reads the result.
`PaymentGateway` is an interface; `SimulatedPaymentGateway` is the only implementation for now.

### Pricing and refunds

Price = show's tier price (REGULAR / PREMIUM) + weekend surcharge (configurable %, weekend judged in the
configured time zone) − coupon (PERCENT with optional cap, or FLAT, never below zero). `PricingService` is a
pure function and unit-tested.

Refund percent comes from the show's `RefundPolicy` (or the default one): full refund if cancelled at least
`fullRefundHoursBefore` hours before the show, `partialRefundPercent` if at least `partialRefundHoursBefore`,
otherwise nothing. Cancellation changes the booking state, releases seats and records the refund in **one**
transaction so they can never disagree.

### Notifications

`BookingService` publishes `BookingConfirmed` / `BookingCancelled` events. `NotificationService` listens with
`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`, so the booking API returns before any notification
work happens and a notification failure can never roll back a booking. Delivery is a log line plus a row in
`notifications` (visible at `GET /api/notifications`); failed deliveries are retried up to 3 times by the
reminder job, which also emits a `SHOW_REMINDER` 24h before each confirmed show.

## Configuration

All in `src/main/resources/application.yml`:

| Key                                | Default       | Meaning                                  |
|------------------------------------|---------------|------------------------------------------|
| booking.hold-duration-minutes      | 5             | Seat hold lifetime                        |
| booking.weekend-surcharge-percent  | 20            | Added to Saturday/Sunday shows            |
| booking.reminder-hours-before-show | 24            | Reminder window                           |
| booking.time-zone                  | Asia/Kolkata  | Zone used for the weekend check           |
| booking.seed-data                  | true          | Insert demo data on an empty database     |

## Tests

- `PricingServiceTest`, `RefundCalculatorTest`: pure unit tests of the money logic and state machine.
- `SeatHoldConcurrencyTest`: 20 real threads race for one seat; exactly one wins. Also overlapping seat sets,
  expiry, and paying against an expired hold.
- `BookingFlowIntegrationTest`: the whole product over HTTP, including RBAC, a declined payment, an idempotent
  retry, cancellation with refund, seat release and async notifications.

## What was simplified


- **Schema is generated by Hibernate** (`ddl-auto`) instead of Flyway/Liquibase migrations.
- **H2 by default**; PostgreSQL is a profile away but not the default.
- **HTTP Basic auth** with BCrypt passwords in the database; no JWT, sessions, or refresh tokens.
- **Payment gateway is simulated** and called inside the booking transaction. A real provider needs a
  PENDING payment row, an out-of-transaction call, and a finalize step (plus webhooks).
- **Notifications are log-only**; swap `NotificationService.deliver()` for email/SMS/push.
- **Admin CRUD is create + list only**; no update/delete endpoints yet.
- **No pagination** on list endpoints.
- **One hold per request, max 10 seats**, no per-user limit on concurrent holds.
- Shows are scheduled in UTC; the weekend check uses one global time zone rather than per-theater zones.
