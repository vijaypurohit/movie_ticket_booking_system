package com.vijaypurohit.movietickets.booking.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vijaypurohit.movietickets.booking.model.BookingRefundRule;
import com.vijaypurohit.movietickets.booking.model.BookingState;
import com.vijaypurohit.movietickets.booking.persistence.BookingRepository;
import com.vijaypurohit.movietickets.generated.model.CancellationResponse;
import com.vijaypurohit.movietickets.identity.application.CurrentUserProvider;
import com.vijaypurohit.movietickets.notification.model.OutboxEvent;
import com.vijaypurohit.movietickets.notification.persistence.OutboxEventRepository;
import com.vijaypurohit.movietickets.payment.model.PaymentStatus;
import com.vijaypurohit.movietickets.payment.model.Refund;
import com.vijaypurohit.movietickets.payment.model.RefundReason;
import com.vijaypurohit.movietickets.payment.persistence.PaymentRepository;
import com.vijaypurohit.movietickets.payment.persistence.RefundRepository;
import com.vijaypurohit.movietickets.generated.model.RefundResponse;
import com.vijaypurohit.movietickets.screening.persistence.ScreeningRepository;
import com.vijaypurohit.movietickets.screening.persistence.ScreeningSeatRepository;
import com.vijaypurohit.movietickets.shared.error.ConflictException;
import com.vijaypurohit.movietickets.shared.error.ResourceNotFoundException;
import com.vijaypurohit.movietickets.shared.identifier.IdGenerator;

@Service
@ConditionalOnProperty(prefix = "app.booking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BookingCancellationService {
    private final BookingRepository bookings; private final PaymentRepository payments;
    private final RefundRepository refunds; private final ScreeningRepository screenings;
    private final ScreeningSeatRepository seats; private final OutboxEventRepository outbox;
    private final CurrentUserProvider users; private final IdGenerator ids; private final Clock clock;

    public BookingCancellationService(BookingRepository bookings, PaymentRepository payments,
            RefundRepository refunds, ScreeningRepository screenings, ScreeningSeatRepository seats,
            OutboxEventRepository outbox, CurrentUserProvider users, IdGenerator ids, Clock clock) {
        this.bookings=bookings; this.payments=payments; this.refunds=refunds; this.screenings=screenings;
        this.seats=seats; this.outbox=outbox; this.users=users; this.ids=ids; this.clock=clock;
    }

    @Transactional
    public CancellationResponse cancel(UUID bookingId, String idempotencyKey) {
        UUID customerId = users.requireCustomerId();
        bookings.lockCustomer(customerId);
        var keyedBooking = bookings.findByCustomerIdAndCancellationIdempotencyKey(customerId, idempotencyKey);
        if (keyedBooking.isPresent() && !keyedBooking.get().getId().equals(bookingId)) {
            throw new ConflictException("booking-conflict", "Booking conflict", "IDEMPOTENCY_KEY_REUSED",
                    "The idempotency key was already used for a different cancellation.");
        }
        var booking = bookings.findOwnedForUpdate(bookingId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("not-found", "Booking not found",
                        "RESOURCE_NOT_FOUND", "The requested booking was not found."));
        var payment = payments.findByBookingIdForUpdate(bookingId).orElseThrow();
        var existing = refunds.findByPaymentIdAndReason(payment.getId(), RefundReason.CANCELLATION);
        if (booking.getState() == BookingState.CANCELLED && existing.isPresent()) return response(bookingId, existing.get());
        if (booking.getState() != BookingState.CONFIRMED || payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new ConflictException("booking-conflict", "Booking conflict", "BOOKING_NOT_CANCELLABLE",
                    "Only a confirmed, paid booking can be cancelled.");
        }
        Instant now = clock.instant();
        var screening = screenings.findById(booking.getScreeningId()).orElseThrow();
        if (!screening.getStartTime().isAfter(now)) {
            throw new ConflictException("booking-conflict", "Booking conflict", "SCREENING_STARTED",
                    "A booking cannot be cancelled after its screening starts.");
        }
        long remainingMinutes = Duration.between(now, screening.getStartTime()).toMinutes();
        BigDecimal percentage = booking.getRefundRules().stream()
                .filter(rule -> remainingMinutes >= rule.getCutoffMinutes())
                .max(Comparator.comparingLong(BookingRefundRule::getCutoffMinutes))
                .map(BookingRefundRule::getRefundPercentage).orElse(BigDecimal.ZERO);
        BigDecimal amount = payment.getAmount().multiply(percentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP).min(payment.getAmount());
        var seatIds = booking.getItems().stream().map(item -> item.getScreeningSeatId()).sorted().toList();
        var lockedSeats = seats.findAllByIdForUpdate(seatIds);
        if (lockedSeats.size() != seatIds.size()) throw new IllegalStateException("booking seat snapshot is incomplete");
        lockedSeats.forEach(seat -> seat.cancelBooking());
        booking.cancel(idempotencyKey);
        Refund refund = refunds.save(new Refund(ids.nextId(), bookingId, payment.getId(), RefundReason.CANCELLATION,
                amount, "cancellation:" + payment.getId(), now));
        outbox.save(new OutboxEvent(ids.nextId(), "BOOKING_CANCELLED", "BOOKING", bookingId,
                "booking-cancelled:" + bookingId, "{\"bookingId\":\"" + bookingId + "\"}", now));
        outbox.save(new OutboxEvent(ids.nextId(), "REFUND_REQUESTED", "REFUND", refund.getId(),
                "refund-requested:" + refund.getId(), "{\"refundId\":\"" + refund.getId() + "\"}", now));
        return response(bookingId, refund);
    }

    private CancellationResponse response(UUID bookingId, Refund refund) {
        return new CancellationResponse()
                .bookingId(bookingId)
                .bookingState(BookingState.CANCELLED)
                .refund(new RefundResponse()
                .id(refund.getId())
                .bookingId(bookingId)
                .reason(refund.getReason())
                .status(refund.getStatus())
                .amount(refund.getAmount())
                .currency(refund.getCurrency()));
    }
}
