package com.moviebooking.booking;

import com.moviebooking.booking.BookingDtos.*;
import com.moviebooking.booking.BookingEvents.BookingCancelled;
import com.moviebooking.booking.BookingEvents.BookingConfirmed;
import com.moviebooking.catalog.RefundPolicy;
import com.moviebooking.catalog.RefundPolicyRepository;
import com.moviebooking.catalog.Seat;
import com.moviebooking.catalog.Show;
import com.moviebooking.common.ApiException;
import com.moviebooking.inventory.SeatAllocation;
import com.moviebooking.inventory.SeatHoldService;
import com.moviebooking.user.User;
import com.moviebooking.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Booking lifecycle: hold -> booking (PENDING_PAYMENT) -> pay -> CONFIRMED -> cancel -> CANCELLED.
 * <p>
 * Transaction boundaries: each public operation is one database transaction. The (simulated) payment
 * provider is called inside it, which is acceptable here because the gateway is in-process; with a real
 * provider you would record a PENDING payment, call out, then finalize in a second transaction.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final RefundPolicyRepository refundPolicies;
    private final SeatHoldService holds;
    private final PricingService pricing;
    private final CouponService coupons;
    private final PaymentGateway gateway;
    private final UserService users;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    public BookingService(BookingRepository bookings, PaymentRepository payments, RefundRepository refunds,
                          RefundPolicyRepository refundPolicies, SeatHoldService holds, PricingService pricing,
                          CouponService coupons, PaymentGateway gateway, UserService users,
                          ApplicationEventPublisher events, PlatformTransactionManager txManager) {
        this.bookings = bookings;
        this.payments = payments;
        this.refunds = refunds;
        this.refundPolicies = refundPolicies;
        this.holds = holds;
        this.pricing = pricing;
        this.coupons = coupons;
        this.gateway = gateway;
        this.users = users;
        this.events = events;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---- Create ------------------------------------------------------------

    @Transactional
    public BookingResponse create(CreateBookingRequest req, Long userId) {
        // Locking the hold rows first serializes concurrent create() calls for the same hold.
        List<SeatAllocation> rows = holds.requireActiveHoldForUpdate(req.holdId(), userId);

        Optional<Booking> existing = bookings.findByHoldId(req.holdId());
        if (existing.isPresent()) {
            return toResponse(existing.get()); // idempotent: same hold -> same booking
        }

        Show show = rows.get(0).getShow();
        List<Seat> seats = rows.stream().map(SeatAllocation::getSeat).toList();
        Coupon coupon = (req.couponCode() == null || req.couponCode().isBlank())
                ? null : coupons.requireUsable(req.couponCode());
        PricingService.Quote quote = pricing.quote(show, seats, coupon);

        User user = users.getById(userId);
        Booking booking = new Booking(user, show, req.holdId(), rows.get(0).getExpiresAt(),
                quote.subtotal(), quote.discount(), quote.total(), quote.couponCode());
        for (PricingService.PriceLine line : quote.lines()) {
            Seat seat = seats.stream().filter(s -> s.getId().equals(line.seatId())).findFirst().orElseThrow();
            booking.addSeat(new BookingSeat(seat, line.price()));
        }
        bookings.save(booking);
        log.info("Booking {} created for user {} ({} seats, total {})", booking.getBookingRef(), userId,
                seats.size(), quote.total());
        return toResponse(booking);
    }

    // ---- Pay ---------------------------------------------------------------

    public BookingResponse pay(Long bookingId, PayRequest req, Long userId) {
        try {
            return tx.execute(status -> doPay(bookingId, req, userId));
        } catch (DataIntegrityViolationException e) {
            // Two requests with the same idempotency key raced; the loser simply reads the winner's outcome.
            log.info("Duplicate payment key {} raced for booking {}", req.idempotencyKey(), bookingId);
            return tx.execute(status -> get(bookingId, userId));
        } catch (ApiException.HoldExpired e) {
            // The hold vanished under a pending booking: record that in its own transaction, then report it.
            tx.executeWithoutResult(status -> bookings.findByIdForUpdate(bookingId)
                    .filter(b -> b.getStatus() == BookingStatus.PENDING_PAYMENT)
                    .ifPresent(b -> b.transitionTo(BookingStatus.EXPIRED)));
            throw e;
        }
    }

    private BookingResponse doPay(Long bookingId, PayRequest req, Long userId) {
        Booking booking = bookings.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ApiException.NotFound("Booking", bookingId));
        requireOwner(booking, userId);

        // Idempotent replay: same key -> same answer, no second charge.
        Optional<Payment> replay = payments.findByIdempotencyKey(req.idempotencyKey());
        if (replay.isPresent()) {
            if (!replay.get().getBooking().getId().equals(bookingId)) {
                throw new ApiException.BadRequest("Idempotency key was already used for a different booking");
            }
            return toResponse(booking);
        }
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return toResponse(booking); // already paid with another key; nothing more to charge
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ApiException.InvalidState("Booking is " + booking.getStatus() + " and cannot be paid");
        }

        // Verify the seats are still ours before taking money.
        holds.requireActiveHoldForUpdate(booking.getHoldId(), userId);

        PaymentGateway.ChargeResult result = gateway.charge(req.idempotencyKey(), booking.getTotal(), req.paymentMethod());
        payments.save(new Payment(booking, req.idempotencyKey(), booking.getTotal(),
                result.success() ? Payment.Status.SUCCESS : Payment.Status.FAILED,
                result.providerRef(), result.failureReason()));

        if (result.success()) {
            holds.convertHoldToBooking(booking.getHoldId(), booking.getId());
            booking.transitionTo(BookingStatus.CONFIRMED);
            events.publishEvent(new BookingConfirmed(booking.getId(), userId, booking.getBookingRef(),
                    booking.getShow().getMovie().getTitle(), seatLabels(booking), booking.getTotal()));
            log.info("Booking {} confirmed", booking.getBookingRef());
        } else {
            // Booking stays PENDING_PAYMENT and keeps its hold; the customer may retry with a new key.
            log.info("Payment failed for booking {}: {}", booking.getBookingRef(), result.failureReason());
        }
        return toResponse(booking);
    }

    // ---- Cancel ------------------------------------------------------------

    @Transactional
    public BookingResponse cancel(Long bookingId, Long userId) {
        Booking booking = bookings.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ApiException.NotFound("Booking", bookingId));
        requireOwner(booking, userId);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return toResponse(booking); // idempotent
        }
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            // Nothing was paid: just give the seats back.
            holds.releaseHoldUnchecked(booking.getHoldId(), userId);
            booking.transitionTo(BookingStatus.EXPIRED);
            return toResponse(booking);
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ApiException.InvalidState("Booking is " + booking.getStatus() + " and cannot be cancelled");
        }
        Instant now = Instant.now();
        Show show = booking.getShow();
        if (!show.getStartTime().isAfter(now)) {
            throw new ApiException.InvalidState("Cannot cancel after the show has started");
        }

        RefundPolicy policy = show.getRefundPolicy() != null
                ? show.getRefundPolicy()
                : refundPolicies.findFirstByDefaultPolicyTrue().orElse(null);
        RefundCalculator.Result calc = RefundCalculator.calculate(policy, show.getStartTime(), now, booking.getTotal());

        // State change + seat release + refund record happen in this one transaction, so we can never end up
        // with a CANCELLED booking whose seats are still BOOKED, or a refund without a cancellation.
        booking.transitionTo(BookingStatus.CANCELLED);
        holds.releaseBookingSeats(booking.getId());

        boolean refundOk = true;
        if (calc.amount().signum() > 0) {
            Payment paid = payments.findFirstByBookingIdAndStatus(booking.getId(), Payment.Status.SUCCESS)
                    .orElseThrow(() -> new ApiException.InvalidState("Confirmed booking has no successful payment"));
            PaymentGateway.RefundResult r = gateway.refund(paid.getProviderRef(), calc.amount());
            refundOk = r.success();
            refunds.save(new Refund(booking, calc.amount(), calc.percent(),
                    refundOk ? Refund.Status.SUCCESS : Refund.Status.FAILED, r.providerRef()));
        } else {
            refunds.save(new Refund(booking, BigDecimal.ZERO.setScale(2), 0, Refund.Status.SUCCESS, null));
        }

        events.publishEvent(new BookingCancelled(booking.getId(), userId, booking.getBookingRef(),
                show.getMovie().getTitle(), calc.amount(), refundOk));
        log.info("Booking {} cancelled, refund {} ({}%)", booking.getBookingRef(), calc.amount(), calc.percent());
        return toResponse(booking);
    }

    // ---- Read --------------------------------------------------------------

    @Transactional(readOnly = true)
    public BookingResponse get(Long bookingId, Long userId) {
        Booking booking = bookings.findByIdWithShow(bookingId)
                .orElseThrow(() -> new ApiException.NotFound("Booking", bookingId));
        requireOwner(booking, userId);
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listMine(Long userId) {
        return bookings.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    // ---- Maintenance -------------------------------------------------------

    @Transactional
    public int expireAbandonedBookings() {
        return bookings.expirePending(Instant.now(), BookingStatus.PENDING_PAYMENT, BookingStatus.EXPIRED);
    }

    // ---- Helpers -----------------------------------------------------------

    private static void requireOwner(Booking booking, Long userId) {
        if (!booking.getUser().getId().equals(userId)) {
            // 404 rather than 403 so we don't confirm that someone else's booking id exists
            throw new ApiException.NotFound("Booking", booking.getId());
        }
    }

    private static String seatLabels(Booking b) {
        return b.getSeats().stream().map(BookingSeat::getSeatLabel).sorted().collect(Collectors.joining(", "));
    }

    private BookingResponse toResponse(Booking b) {
        List<SeatLine> seatLines = b.getSeats().stream()
                .map(s -> new SeatLine(s.getSeat().getId(), s.getSeatLabel(), s.getTier().name(), s.getPrice()))
                .toList();
        PaymentInfo lastPayment = payments.findByBookingIdOrderByCreatedAtDesc(b.getId()).stream()
                .findFirst().map(PaymentInfo::from).orElse(null);
        RefundInfo refund = refunds.findFirstByBookingIdOrderByCreatedAtDesc(b.getId())
                .map(RefundInfo::from).orElse(null);
        return new BookingResponse(b.getId(), b.getBookingRef(), b.getStatus(),
                b.getShow().getId(), b.getShow().getMovie().getTitle(), b.getShow().getStartTime(),
                seatLines, b.getSubtotal(), b.getDiscount(), b.getTotal(), b.getCouponCode(),
                b.getHoldExpiresAt(), lastPayment, refund, b.getCreatedAt());
    }
}
