package com.vijaypurohit.movietickets.booking.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import com.vijaypurohit.movietickets.booking.model.Booking;
import com.vijaypurohit.movietickets.booking.model.BookingState;
import com.vijaypurohit.movietickets.booking.model.DiscountRedemption;
import com.vijaypurohit.movietickets.booking.persistence.BookingRepository;
import com.vijaypurohit.movietickets.booking.persistence.DiscountRedemptionRepository;
import com.vijaypurohit.movietickets.catalog.model.SeatCategory;
import com.vijaypurohit.movietickets.notification.model.OutboxEvent;
import com.vijaypurohit.movietickets.notification.persistence.OutboxEventRepository;
import com.vijaypurohit.movietickets.payment.PaymentGateway.ChargeResult;
import com.vijaypurohit.movietickets.payment.model.Payment;
import com.vijaypurohit.movietickets.payment.model.PaymentStatus;
import com.vijaypurohit.movietickets.payment.model.Refund;
import com.vijaypurohit.movietickets.payment.model.RefundReason;
import com.vijaypurohit.movietickets.payment.persistence.PaymentRepository;
import com.vijaypurohit.movietickets.payment.persistence.RefundRepository;
import com.vijaypurohit.movietickets.pricing.application.PricingLookupService;
import com.vijaypurohit.movietickets.reservation.model.ReservationState;
import com.vijaypurohit.movietickets.reservation.persistence.SeatReservationRepository;
import com.vijaypurohit.movietickets.screening.model.ScreeningSeatState;
import com.vijaypurohit.movietickets.screening.model.ScreeningStatus;
import com.vijaypurohit.movietickets.screening.persistence.ScreeningRepository;
import com.vijaypurohit.movietickets.screening.persistence.ScreeningSeatRepository;
import com.vijaypurohit.movietickets.shared.error.BusinessRuleViolationException;
import com.vijaypurohit.movietickets.shared.error.ConflictException;
import com.vijaypurohit.movietickets.shared.error.ResourceNotFoundException;
import com.vijaypurohit.movietickets.shared.identifier.IdGenerator;

@Service
@ConditionalOnProperty(prefix = "app.booking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BookingTransactionService {
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final DiscountRedemptionRepository redemptions;
    private final OutboxEventRepository outbox;
    private final SeatReservationRepository reservations;
    private final ScreeningSeatRepository seats;
    private final ScreeningRepository screenings;
    private final PricingLookupService pricing;
    private final IdGenerator ids;
    private final Clock clock;
    private final Duration checkoutDuration;
    private final Duration minimumRemaining;

    public BookingTransactionService(BookingRepository bookings, PaymentRepository payments,
            RefundRepository refunds, DiscountRedemptionRepository redemptions,
            OutboxEventRepository outbox, SeatReservationRepository reservations,
            ScreeningSeatRepository seats, ScreeningRepository screenings,
            PricingLookupService pricing, IdGenerator ids, Clock clock,
            @Value("${app.booking.checkout-duration:PT1M}") Duration checkoutDuration,
            @Value("${app.booking.minimum-remaining:PT30S}") Duration minimumRemaining) {
        this.bookings = bookings; this.payments = payments; this.refunds = refunds;
        this.redemptions = redemptions; this.outbox = outbox; this.reservations = reservations;
        this.seats = seats; this.screenings = screenings; this.pricing = pricing;
        this.ids = ids; this.clock = clock; this.checkoutDuration = checkoutDuration;
        this.minimumRemaining = minimumRemaining;
    }

    @Transactional
    public PreparedCheckout prepare(UUID customerId, String idempotencyKey, UUID reservationId, String discountCode) {
        String normalizedDiscount = normalize(discountCode);
        String requestFingerprint = fingerprint(reservationId + "|" + Objects.toString(normalizedDiscount, ""));
        bookings.lockCustomer(customerId);
        var earlyExisting = bookings.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        if (earlyExisting.isPresent()) return existing(earlyExisting.get(), requestFingerprint);

        var reservation = reservations.findOwnedForUpdate(reservationId, customerId)
                .orElseThrow(() -> missing("Reservation", "The requested reservation was not found."));
        var existing = bookings.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        if (existing.isPresent()) return existing(existing.get(), requestFingerprint);

        Instant now = clock.instant();
        if (reservation.getState() != ReservationState.ACTIVE || !reservation.getExpiresAt().isAfter(now)) {
            throw conflict("RESERVATION_NOT_ACTIVE", "The reservation is no longer active.");
        }
        if (Duration.between(now, reservation.getExpiresAt()).compareTo(minimumRemaining) < 0) {
            throw conflict("INSUFFICIENT_CHECKOUT_TIME", "At least 30 seconds must remain on the reservation.");
        }
        var screening = screenings.findById(reservation.getScreeningId())
                .filter(value -> value.getStatus() == ScreeningStatus.ACTIVE && value.getStartTime().isAfter(now))
                .orElseThrow(() -> conflict("SCREENING_CLOSED", "The screening is not open for checkout."));
        var lockedSeats = seats.findByReservationIdForUpdate(reservationId);
        if (lockedSeats.isEmpty() || lockedSeats.stream().anyMatch(seat -> seat.getState() != ScreeningSeatState.RESERVED
                || !reservationId.equals(seat.getReservationId()))) {
            throw conflict("RESERVATION_SEATS_CHANGED", "The reservation no longer owns every selected seat.");
        }
        var snapshots = seats.findCheckoutSnapshots(reservationId);
        if (snapshots.size() != lockedSeats.size()) throw new IllegalStateException("seat price snapshot is incomplete");
        BigDecimal subtotal = snapshots.stream().map(ScreeningSeatRepository.CheckoutSeatProjection::getUnitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        UUID discountId = null;
        BigDecimal discountAmount = new BigDecimal("0.00");
        BigDecimal total = subtotal;
        if (normalizedDiscount != null) {
            var quote = pricing.quoteDiscount(normalizedDiscount, subtotal);
            enforceDiscountLimit(quote.discountCodeId(), customerId, quote.globalLimit(), quote.perCustomerLimit());
            discountId = quote.discountCodeId(); discountAmount = quote.discount(); total = quote.total();
        }

        Instant checkoutExpiresAt = now.plus(checkoutDuration);
        UUID bookingId = ids.nextId();
        Booking booking = new Booking(bookingId, reference(bookingId), customerId, reservation.getScreeningId(),
                reservationId, discountId, subtotal, discountAmount, total, idempotencyKey,
                requestFingerprint, checkoutExpiresAt);
        snapshots.forEach(value -> booking.addItem(ids.nextId(), value.getScreeningSeatId(), value.getRowLabel(),
                value.getSeatNumber(), SeatCategory.valueOf(value.getCategory()), value.getUnitPrice()));
        pricing.refundRules(screening.getRefundPolicyId()).forEach(rule ->
                booking.addRefundRule(ids.nextId(), rule.cutoffMinutes(), rule.percentage()));
        bookings.save(booking);
        String gatewayKey = customerId + ":" + idempotencyKey;
        Payment payment = payments.save(new Payment(ids.nextId(), bookingId, total, gatewayKey));
        if (discountId != null && discountAmount.signum() > 0) {
            redemptions.save(new DiscountRedemption(ids.nextId(), discountId, customerId, bookingId, discountAmount));
        }
        reservation.beginCheckout(checkoutExpiresAt);
        lockedSeats.forEach(seat -> seat.beginCheckout(reservationId));
        return new PreparedCheckout(bookingId, payment.getGatewayIdempotencyKey(), total, true);
    }

    @Transactional
    public void finalizePayment(UUID bookingId, ChargeResult result) {
        Booking booking = bookings.findByIdForUpdate(bookingId)
                .orElseThrow(() -> missing("Booking", "The requested booking was not found."));
        Payment payment = payments.findByBookingIdForUpdate(bookingId).orElseThrow();
        if (payment.getStatus() == PaymentStatus.SUCCEEDED
                || (payment.getStatus() == PaymentStatus.DECLINED && !payment.isExpiredAttempt())) return;
        var reservation = reservations.findByIdForUpdate(booking.getReservationId()).orElseThrow();
        var lockedSeats = seats.findByReservationIdForUpdate(reservation.getId());
        Instant now = clock.instant();

        if (!result.succeeded()) {
            payment.decline(result.providerReference()); booking.fail(); reservation.failCheckout();
            lockedSeats.forEach(seat -> seat.release(reservation.getId()));
            redemptions.deleteByBookingId(bookingId);
            return;
        }

        payment.succeed(result.providerReference());
        boolean validLease = reservation.getState() == ReservationState.PAYMENT_IN_PROGRESS
                && !reservation.isCheckoutExpired(now);
        boolean ownsEverySeat = !lockedSeats.isEmpty() && lockedSeats.size() == booking.getItems().size()
                && lockedSeats.stream().allMatch(seat -> seat.getState() == ScreeningSeatState.PAYMENT_IN_PROGRESS
                        && reservation.getId().equals(seat.getReservationId()));
        if (validLease && ownsEverySeat) {
            lockedSeats.forEach(seat -> seat.book(reservation.getId()));
            reservation.convert(); booking.confirm();
            outbox.save(new OutboxEvent(ids.nextId(), "BOOKING_CONFIRMED", "BOOKING", bookingId,
                    "booking-confirmed:" + bookingId, "{\"bookingId\":\"" + bookingId + "\"}", now));
            return;
        }

        booking.fail(); reservation.failCheckout();
        lockedSeats.forEach(seat -> seat.release(reservation.getId()));
        redemptions.deleteByBookingId(bookingId);
        refunds.findByPaymentIdAndReason(payment.getId(), RefundReason.LATE_PAYMENT)
                .orElseGet(() -> refunds.save(new Refund(ids.nextId(), bookingId, payment.getId(),
                        RefundReason.LATE_PAYMENT, payment.getAmount(), "late-payment:" + payment.getId(), now)));
    }

    @Transactional(readOnly = true)
    public java.util.List<RecoveryCandidate> expiredCandidates() {
        return bookings.findExpiredPending(clock.instant(), org.springframework.data.domain.PageRequest.of(0, 100)).stream()
                .map(booking -> {
                    Payment payment = payments.findByBookingId(booking.getId()).orElseThrow();
                    return new RecoveryCandidate(booking.getId(), payment.getGatewayIdempotencyKey());
                }).toList();
    }

    @Transactional
    public void expireCheckout(UUID bookingId) {
        Booking booking = bookings.findByIdForUpdate(bookingId).orElseThrow();
        if (booking.getState() != BookingState.PENDING_PAYMENT || booking.getCheckoutExpiresAt().isAfter(clock.instant())) return;
        Payment payment = payments.findByBookingIdForUpdate(bookingId).orElseThrow();
        var reservation = reservations.findByIdForUpdate(booking.getReservationId()).orElseThrow();
        var lockedSeats = seats.findByReservationIdForUpdate(reservation.getId());
        booking.fail(); reservation.failCheckout();
        lockedSeats.forEach(seat -> seat.release(reservation.getId()));
        redemptions.deleteByBookingId(bookingId);
        payment.decline("checkout-expired");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireCheckoutForReservation(UUID reservationId) {
        var booking = bookings.findByReservationIdForUpdate(reservationId).orElse(null);
        if (booking == null || booking.getState() != BookingState.PENDING_PAYMENT) return;
        var reservation = reservations.findByIdForUpdate(reservationId).orElseThrow();
        if (!reservation.isCheckoutExpired(clock.instant())) return;
        Payment payment = payments.findByBookingIdForUpdate(booking.getId()).orElseThrow();
        var lockedSeats = seats.findByReservationIdForUpdate(reservationId);
        booking.fail(); reservation.failCheckout();
        lockedSeats.forEach(seat -> seat.release(reservationId));
        redemptions.deleteByBookingId(booking.getId());
        payment.decline("checkout-expired");
    }

    private PreparedCheckout existing(Booking booking, String fingerprint) {
        if (!booking.getRequestFingerprint().equals(fingerprint)) {
            throw conflict("IDEMPOTENCY_KEY_REUSED", "The idempotency key was already used for a different request.");
        }
        Payment payment = payments.findByBookingId(booking.getId()).orElseThrow();
        return new PreparedCheckout(booking.getId(), payment.getGatewayIdempotencyKey(), payment.getAmount(),
                payment.getStatus() == PaymentStatus.INITIATED && booking.getState() == BookingState.PENDING_PAYMENT);
    }

    private void enforceDiscountLimit(UUID discountId, UUID customerId, Integer globalLimit, Integer customerLimit) {
        if (globalLimit != null && redemptions.countByDiscountCodeId(discountId) >= globalLimit) {
            throw rule("DISCOUNT_LIMIT_REACHED", "The discount code usage limit has been reached.");
        }
        if (customerLimit != null && redemptions.countByDiscountCodeIdAndCustomerId(discountId, customerId) >= customerLimit) {
            throw rule("CUSTOMER_DISCOUNT_LIMIT_REACHED", "The discount code usage limit for this customer has been reached.");
        }
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT); }
    private String reference(UUID id) { return "MTB-" + id.toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT); }
    private String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private ConflictException conflict(String code, String detail) { return new ConflictException("booking-conflict", "Booking conflict", code, detail); }
    private BusinessRuleViolationException rule(String code, String detail) { return new BusinessRuleViolationException("booking-rule", "Booking rule violation", code, detail); }
    private ResourceNotFoundException missing(String title, String detail) { return new ResourceNotFoundException("not-found", title + " not found", "RESOURCE_NOT_FOUND", detail); }

    public record PreparedCheckout(UUID bookingId, String gatewayKey, BigDecimal amount, boolean requiresCharge) { }
    public record RecoveryCandidate(UUID bookingId, String gatewayKey) { }
}
