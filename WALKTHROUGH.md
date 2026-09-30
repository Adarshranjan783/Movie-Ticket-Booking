# How the Movie Ticket Booking Backend Works

A step-by-step walkthrough of the code: how a request travels through the layers, what each module does,
and exactly what changes in the database at every stage of a booking. Read this after the README; it goes
one level deeper.

---

## 1. The big picture

```
         HTTP request (curl / Postman)
                    │
                    ▼
   ┌─────────────────────────────────┐
   │ Spring Security filter chain     │  who are you? (HTTP Basic) / are you allowed? (role)
   └─────────────────────────────────┘
                    │
                    ▼
   ┌─────────────────────────────────┐
   │ Controller  (@RestController)    │  parse JSON -> DTO, validate, call one service method
   └─────────────────────────────────┘
                    │
                    ▼
   ┌─────────────────────────────────┐
   │ Service  (@Service)              │  business rules, state transitions, ONE transaction
   └─────────────────────────────────┘
                    │
                    ▼
   ┌─────────────────────────────────┐
   │ Repository (Spring Data JPA)     │  SQL, generated from method names or @Query
   └─────────────────────────────────┘
                    │
                    ▼
              Database (H2 / PostgreSQL)
```

Rules that every module follows:

| Rule | Where you see it |
|------|------------------|
| Controllers never contain logic | every `*Controller.java` is a few lines per endpoint |
| Entities never leave the service layer | every response is a `record` in a `*Dtos.java` file |
| One service method = one transaction | `@Transactional` on the method, or a `TransactionTemplate` |
| Business errors are exceptions with an HTTP status | `ApiException` subclasses, mapped in `GlobalExceptionHandler` |
| Correctness lives in the database, not in Java locks | unique constraints and `SELECT ... FOR UPDATE` |

---

## 2. Package by package

### `config`

| Class | What it does |
|-------|--------------|
| `SecurityConfig` | Defines which URLs are public, which need login, which need ADMIN. Uses HTTP Basic. Also formats 401/403 as the same JSON error shape the rest of the API uses. |
| `BookingProperties` | Type-safe view of the `booking.*` keys in `application.yml` (hold duration, weekend surcharge, time zone, etc.). |
| `DemoDataSeeder` | On startup, if the `users` table is empty, inserts an admin, a customer, one theater with 40 seats, one movie, two shows, a coupon, and a refund policy. Turned off in tests. |

### `common`

| Class | What it does |
|-------|--------------|
| `ApiException` | Base class carrying an HTTP status and an error code. Nested subclasses: `NotFound` (404), `BadRequest` (400), `Forbidden` (403), `Conflict` (409), `SeatUnavailable`, `HoldExpired`, `InvalidState` (all 409). |
| `ApiError` | The JSON body every error returns: `status, code, message, timestamp, path, fieldErrors`. |
| `GlobalExceptionHandler` | Catches exceptions from any controller and turns them into `ApiError`. Bean-validation failures become `VALIDATION_FAILED` with a per-field map. Anything unexpected becomes a 500 with the stack trace logged, never returned. |

### `user`

| Class | What it does |
|-------|--------------|
| `User`, `Role` | Entity with email (unique), BCrypt password hash, and role ADMIN or CUSTOMER. |
| `UserService` | Implements Spring Security's `UserDetailsService` (how Basic auth looks up a user) and `register()`. |
| `AppUserDetails` | Adapter so controllers can inject the logged-in user with `@AuthenticationPrincipal` and read its id. |
| `AuthController` | `POST /api/auth/register` (always creates a CUSTOMER) and `GET /api/auth/me`. |

### `catalog`

The static world: where and when movies play.

```
City ──1:N──► Theater ──1:N──► Screen ──1:N──► Seat (row, number, tier REGULAR|PREMIUM)
                                  ▲
Movie ──1:N──► Show ─────────────┘   (start/end time, regular price, premium price, refund policy)
```

| Class | What it does |
|-------|--------------|
| `CatalogAdminService` | Admin writes. Enforces: theater belongs to a real city, screen has seats before a show is scheduled, show start is in the future, premium >= regular price, **no two shows overlap on one screen**. `createScreen` generates the whole seat grid from `rows` x `seatsPerRow` and a list of premium rows. |
| `DiscoveryService` | Customer reads: cities, theaters in a city, movies playing in a city, shows by city/movie/date. Uses `join fetch` queries so one request is one SQL statement, not N+1. |
| `RefundPolicy` | Three numbers: full refund if cancelled >= X hours before, partial % if >= Y hours before, else nothing. One policy is marked default. |

### `inventory`

The live world: which seats are taken right now. This is the most important module.

| Class | What it does |
|-------|--------------|
| `SeatAllocation` | One row per (show, seat) **only while the seat is taken**. Status HELD (with `expires_at`) or BOOKED (with `booking_id`). The unique constraint on `(show_id, seat_id)` is the whole concurrency guarantee. |
| `SeatHoldService` | `hold()`, `release()`, `seatMap()`, plus helpers the booking module calls inside its own transaction. |
| `HoldExpiryJob` | Every 30 seconds deletes HELD rows whose `expires_at` has passed. |

### `booking`

| Class | What it does |
|-------|--------------|
| `Booking`, `BookingStatus` | The order. `transitionTo()` is the only way to change status and it rejects invalid moves. |
| `BookingSeat` | Historical record of which seats were in the booking and what each cost (kept even after cancellation). |
| `PricingService` | Pure function: tier price + weekend surcharge - coupon = total. |
| `Coupon`, `CouponService` | PERCENT (with optional cap) or FLAT discounts with a validity window. |
| `PaymentGateway` | Interface: `charge()` and `refund()`. |
| `SimulatedPaymentGateway` | The only implementation. Succeeds unless the payment method starts with `FAIL`. |
| `Payment` | One row per payment attempt; `idempotency_key` is unique. |
| `Refund`, `RefundCalculator` | Refund record and the pure arithmetic that decides the percentage. |
| `BookingService` | `create()`, `pay()`, `cancel()`, `get()`, `listMine()`. |
| `BookingExpiryJob` | Every 60 seconds marks PENDING_PAYMENT bookings whose hold ran out as EXPIRED. |

### `notification`

| Class | What it does |
|-------|--------------|
| `Notification` | Stored record: user, booking, type, message, status PENDING/SENT/FAILED, attempts. |
| `NotificationService` | Listens for `BookingConfirmed` / `BookingCancelled` events **after the booking transaction commits**, on a background thread. "Delivery" is a log line; swap `deliver()` for email/SMS. Retries up to 3 times. |
| `ReminderJob` | Every 10 minutes: sends one SHOW_REMINDER per confirmed booking whose show starts within 24 h, and retries pending notifications. |
| `NotificationController` | `GET /api/notifications` shows the caller's own feed, so you can watch async delivery without an email server. |

---

## 3. The complete flow, step by step

We follow one customer through a real run against the demo data. Show 1 is Interstellar tomorrow,
regular seats 200, premium 350. In the demo run below the show fell on a Sunday, so the 20 % weekend
surcharge applied.

### Step 0. Admin sets up the catalog (already done by the seeder)

```
POST /api/admin/cities            {"name":"Bengaluru"}
POST /api/admin/theaters          {"cityId":1,"name":"PVR Forum Mall"}
POST /api/admin/screens           {"theaterId":1,"name":"Audi 1","rows":5,"seatsPerRow":8,"premiumRows":["D","E"]}
POST /api/admin/movies            {"title":"Interstellar","durationMinutes":169}
POST /api/admin/refund-policies   {"name":"Standard","fullRefundHoursBefore":24,"partialRefundHoursBefore":4,"partialRefundPercent":50,"defaultPolicy":true}
POST /api/admin/shows             {"movieId":1,"screenId":1,"startTime":"...","regularPrice":200,"premiumPrice":350,"refundPolicyId":1}
POST /api/admin/coupons           {"code":"WELCOME10","type":"PERCENT","value":10,"maxDiscount":100,...}
```

What happens inside `createShow`: end time = start + movie duration; then
`ShowRepository.existsOverlapping(screenId, start, end)` runs
`start < other.end AND end > other.start` and rejects the show with `409 SHOW_OVERLAP` if anything matches.

Database after this step: `cities`, `theaters`, `screens`, `seats` (40 rows), `movies`, `shows`,
`refund_policies`, `coupons` populated. `seat_allocations` is **empty**, which means every seat is free.

### Step 1. Customer browses (no login)

```
GET /api/cities
GET /api/shows?cityId=1
GET /api/shows/1/seats
```

`SeatHoldService.seatMap()`:

1. Load all 40 seats of the show's screen.
2. Load all `seat_allocations` rows for the show (currently none).
3. For each seat: no row → AVAILABLE; row BOOKED → BOOKED; row HELD and not expired → HELD;
   row HELD but expired → AVAILABLE (expiry is judged by time, not by whether the sweep job ran yet).

Response includes each seat's id, label ("A1"), tier and base price, plus `availableSeats: 40`.

### Step 2. Customer holds seats

```
POST /api/shows/1/holds   (Basic auth: customer@example.com)
{"seatIds":[1,2,25]}                       ← A1, A2, D1
```

`SeatInventoryController.hold()` → `SeatHoldService.hold()`. Everything below runs in **one transaction**
via `TransactionTemplate`:

1. Load the show; reject if it already started.
2. Load the three seats **and check they belong to this show's screen** (otherwise 400).
3. `DELETE FROM seat_allocations WHERE status='HELD' AND expires_at < now AND seat_id IN (...)`
   so an abandoned hold from someone else never blocks us.
4. `SELECT` any remaining rows for these seats. If found → `409 SEAT_UNAVAILABLE: Seats not available: A1`.
   This is only a friendly pre-check; it is not the guarantee.
5. `INSERT` three rows: `(show 1, seat 1, HELD, hold_id=<uuid>, user_id=2, expires_at=now+5min)` etc.
   and **flush**.
6. Commit.

**The race.** If two customers do step 5 for seat 1 at the same moment, both pass the pre-check in step 4,
both INSERT, and the database's unique index on `(show_id, seat_id)` lets exactly one commit. The other
gets a constraint violation, which Spring surfaces as `DataIntegrityViolationException` *after* the
transaction has rolled back. `hold()` catches it outside the transaction and converts it to
`409 SEAT_UNAVAILABLE`. Because all three seats are inserted in the same transaction, a partial hold
(A1 succeeded, D1 failed) is impossible: the rollback removes all of them.

Response:

```json
{"holdId":"03be17c7-...","showId":1,"seatLabels":["A1","A2","D1"],"expiresAt":"...+5 min"}
```

Database now: three `seat_allocations` rows, status HELD. The seat map shows those three as HELD and
`availableSeats: 37`.

### Step 3. Customer creates the booking (price is calculated here)

```
POST /api/bookings
{"holdId":"03be17c7-...","couponCode":"WELCOME10"}
```

`BookingService.create()` (`@Transactional`):

1. `SeatHoldService.requireActiveHoldForUpdate(holdId, userId)`:
   `SELECT ... FROM seat_allocations WHERE hold_id=? FOR UPDATE`. Throws `HOLD_EXPIRED` if no rows or any
   row is expired, `FORBIDDEN` if the hold belongs to another user. The row lock means two simultaneous
   `create` calls for the same hold are serialized.
2. If a booking already exists for this hold, return it (idempotent).
3. `CouponService.requireUsable("WELCOME10")`: exists, active, inside its validity window, else 400.
4. `PricingService.quote(show, seats, coupon)`:

   | Seat | Tier | Base | Weekend +20 % | Line price |
   |------|------|------|---------------|------------|
   | A1 | REGULAR | 200 | 40 | 240 |
   | A2 | REGULAR | 200 | 40 | 240 |
   | D1 | PREMIUM | 350 | 70 | 420 |
   | | | | **subtotal** | **900** |
   | | | | WELCOME10 = 10 %, cap 100 | −90 |
   | | | | **total** | **810** |

5. Insert a `bookings` row: status `PENDING_PAYMENT`, hold_id, hold_expires_at copied from the hold,
   subtotal 900, discount 90, total 810, coupon code. Insert three `booking_seats` rows with each line price.
6. Commit.

Database now: 1 booking (PENDING_PAYMENT), 3 booking_seats, and the 3 seat_allocations are **still HELD**.
The seats are not yet booked because no money has moved.

### Step 4. Customer pays

```
POST /api/bookings/1/pay
{"idempotencyKey":"demo-1","paymentMethod":"FAIL_CARD"}     ← first attempt, declined on purpose
```

`BookingService.pay()` runs `doPay()` inside a `TransactionTemplate`:

1. `SELECT ... FROM bookings WHERE id=1 FOR UPDATE` and check the booking belongs to the caller
   (if not, 404, so we don't leak that the id exists).
2. Look up `payments` by `idempotency_key`. Not found, so this is a fresh attempt.
3. Status must be PENDING_PAYMENT.
4. Re-check the hold with `requireActiveHoldForUpdate` so we never charge for seats that expired.
5. `gateway.charge("demo-1", 810, "FAIL_CARD")` → declined.
6. Insert `payments` row `(demo-1, 810, FAILED, "Card declined by simulated provider")`.
7. Booking **stays PENDING_PAYMENT** and keeps its hold. Commit.

Response shows `status: PENDING_PAYMENT` and `lastPayment.status: FAILED`. The customer can retry
while the 5-minute hold is alive.

```
POST /api/bookings/1/pay
{"idempotencyKey":"demo-2","paymentMethod":"CARD"}         ← second attempt, succeeds
```

Same steps 1–5, but the gateway returns success. Then:

6. Insert `payments` row `(demo-2, 810, SUCCESS, provider_ref="sim_pay_...")`.
7. `UPDATE seat_allocations SET status='BOOKED', expires_at=NULL, booking_id=1 WHERE hold_id=? AND status='HELD'`.
8. `booking.transitionTo(CONFIRMED)` (the state machine allows PENDING_PAYMENT → CONFIRMED).
9. `events.publishEvent(new BookingConfirmed(...))`. Nothing happens yet; the event is queued until commit.
10. Commit.

Right after commit, Spring hands the event to `NotificationService.onBookingConfirmed` on a
**background thread** (`@Async` + `@TransactionalEventListener(AFTER_COMMIT)`). It writes a
`notifications` row and logs the message. The HTTP response was already sent; if notification delivery
threw, the booking would still be CONFIRMED.

```
POST /api/bookings/1/pay
{"idempotencyKey":"demo-2","paymentMethod":"CARD"}         ← network retry with the SAME key
```

Step 2 finds the existing `demo-2` payment row, so `doPay()` returns the current booking immediately.
No gateway call, no second charge. If two identical retries raced, the loser would hit the unique
constraint on `idempotency_key`; `pay()` catches that and simply re-reads the booking.

Database now: booking CONFIRMED; 3 seat_allocations BOOKED with booking_id=1; 2 payments (1 FAILED,
1 SUCCESS); 1 notification. Seat map: A1, A2, D1 are BOOKED, `availableSeats: 37`.

### Step 5. Someone else tries the same seat

```
POST /api/shows/1/holds   (as another user)
{"seatIds":[1]}
→ 409 {"code":"SEAT_UNAVAILABLE","message":"Seats not available: A1"}
```

The BOOKED row has no expiry, so step 3 of the hold flow leaves it alone and step 4 rejects.

### Step 6. Customer cancels

```
POST /api/bookings/1/cancel
```

`BookingService.cancel()` (`@Transactional`):

1. Lock the booking row, check ownership.
2. Already CANCELLED → return as is (idempotent). PENDING_PAYMENT → just release the hold and mark
   EXPIRED (nothing was paid). Otherwise must be CONFIRMED and the show must not have started.
3. Pick the refund policy: the show's own, else the default one.
4. `RefundCalculator.calculate(policy, showStart, now, 810)`:
   hours until show = 19 → not >= 24 (full) but >= 4 (partial) → **50 % = 405**.
5. `booking.transitionTo(CANCELLED)`.
6. `DELETE FROM seat_allocations WHERE booking_id=1` → the three seats are free again.
7. Find the SUCCESS payment, call `gateway.refund(provider_ref, 405)`, insert a `refunds` row
   `(405, 50 %, SUCCESS)`.
8. Publish `BookingCancelled`. Commit.

Because 5, 6 and 7 are in one transaction, it is impossible to end up with a CANCELLED booking whose seats
are still BOOKED, or a refund recorded against a booking that is still CONFIRMED.

After commit the notification listener writes two more rows: BOOKING_CANCELLED and REFUND_PROCESSED.

Database now: booking CANCELLED; `seat_allocations` for those seats **gone**; `booking_seats` still
present as history; 1 refund; 3 notifications. Seat map: `availableSeats: 40`.

### Step 7. Customer checks their feed

```
GET /api/bookings              → own bookings only
GET /api/bookings/1            → 404 if it belongs to someone else
GET /api/notifications         → BOOKING_CONFIRMED, BOOKING_CANCELLED, REFUND_PROCESSED, all SENT
```

---

## 4. What happens when things go wrong

| Situation | What the code does |
|-----------|--------------------|
| Hold expires before the customer pays | `requireActiveHoldForUpdate` throws `HOLD_EXPIRED`; `pay()` catches it, marks the booking EXPIRED in a separate transaction, returns 409. The seats were already free by time. |
| Customer never pays and never comes back | `HoldExpiryJob` deletes the HELD rows after 5 min; `BookingExpiryJob` flips the booking to EXPIRED after its `hold_expires_at`. |
| Two customers race for one seat | Unique index on `(show_id, seat_id)`: exactly one INSERT commits. |
| Multi-seat hold where one seat is taken | Whole transaction rolls back; the customer gets none of the seats and a 409 naming the taken one. |
| Same payment request sent twice | Unique `idempotency_key`: second call returns the first outcome, gateway is not called again. |
| Payment declined | Payment row FAILED, booking stays PENDING_PAYMENT with its hold; retry with a new key. |
| Cancel called twice | Second call returns the CANCELLED booking unchanged. |
| Notification delivery throws | Booking is already committed; the notification row stays PENDING and `ReminderJob` retries up to 3 times. |
| Customer reads another customer's booking | 404 (not 403) so the id's existence is not confirmed. |
| Customer calls an admin endpoint | 403 from the security filter, in the same JSON error shape. |
| Invalid JSON field | 400 `VALIDATION_FAILED` with `fieldErrors: {"name": "must not be blank"}`. |

---

## 5. Background jobs

| Job | Interval | Purpose |
|-----|----------|---------|
| `HoldExpiryJob` | 30 s | Delete expired HELD allocations (housekeeping; availability is already correct without it). |
| `BookingExpiryJob` | 60 s | PENDING_PAYMENT → EXPIRED once `hold_expires_at` passes. |
| `ReminderJob` | 10 min | SHOW_REMINDER for confirmed bookings starting within 24 h; retry PENDING notifications. |

All are enabled by `@EnableScheduling` on `MovieBookingApplication`; the async listeners by `@EnableAsync`.

---

## 6. Database tables at a glance

| Table | Key constraint | Why it matters |
|-------|----------------|----------------|
| `users` | unique `email` | login identity |
| `cities` / `theaters` / `screens` / `seats` | unique names within parent; unique `(screen_id, row_label, seat_number)` | no duplicate seat positions |
| `shows` | index `(screen_id, start_time)` | fast overlap check and listings |
| `seat_allocations` | **unique `(show_id, seat_id)`** | the double-booking guarantee |
| `bookings` | unique `hold_id`, unique `booking_ref` | one hold → at most one booking |
| `booking_seats` | unique `(booking_id, seat_id)` | price history per seat |
| `payments` | **unique `idempotency_key`** | retries cannot double-charge |
| `refunds` | index `booking_id` | audit trail |
| `notifications` | index `(booking_id, type)` | one reminder per booking |
| `coupons` | unique `code` | lookup by code |
| `refund_policies` | unique `name` | one is `default_policy = true` |

Inspect any of them live at http://localhost:8080/h2-console while the app runs.

---

## 7. Reading order if you want to modify the code

1. `SeatHoldService.hold()` – the concurrency model, 40 lines.
2. `BookingService.pay()` and `doPay()` – idempotency and the hold re-check.
3. `BookingService.cancel()` – transaction boundary for state + seats + refund.
4. `Booking.transitionTo()` and `BookingStatus.canTransitionTo()` – the state machine.
5. `PricingService.quote()` and `RefundCalculator.calculate()` – the money, both pure and unit-tested.
6. `NotificationService` – how events cross the transaction boundary asynchronously.
7. `SecurityConfig` – which URLs need which role.

The tests mirror this order: `SeatHoldConcurrencyTest`, `BookingFlowIntegrationTest`,
`PricingServiceTest`, `RefundCalculatorTest`.
