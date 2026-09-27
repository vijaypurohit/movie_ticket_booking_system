package com.vijaypurohit.movietickets.booking.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.vijaypurohit.movietickets.booking.model.Booking;
import com.vijaypurohit.movietickets.booking.model.BookingState;
import com.vijaypurohit.movietickets.booking.persistence.BookingRepository;
import com.vijaypurohit.movietickets.notification.model.OutboxEvent;
import com.vijaypurohit.movietickets.notification.persistence.OutboxEventRepository;
import com.vijaypurohit.movietickets.payment.model.PaymentStatus;
import com.vijaypurohit.movietickets.payment.model.Refund;
import com.vijaypurohit.movietickets.payment.model.RefundReason;
import com.vijaypurohit.movietickets.payment.persistence.PaymentRepository;
import com.vijaypurohit.movietickets.payment.persistence.RefundRepository;
import com.vijaypurohit.movietickets.screening.persistence.ScreeningSeatRepository;
import com.vijaypurohit.movietickets.shared.identifier.IdGenerator;

/**
 * Settles the bookings of an administratively cancelled screening.
 *
 * <p>Marking the screening {@code CANCELLED} stops new sales, because both the reservation and the
 * checkout paths require an {@code ACTIVE} screening. This service drains the bookings that were
 * already confirmed, in bounded batches, each batch in its own transaction so an interrupted drain
 * can be resumed by {@link ScreeningCancellationSweeper}. A cancelled show is not the customer's
 * fault, so the refund is the full captured amount and the booking's cutoff rules are not applied.
 */
@Service
@ConditionalOnProperty(prefix = "app.booking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScreeningCancellationService {
    private static final int BATCH_SIZE = 100;

    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final ScreeningSeatRepository seats;
    private final OutboxEventRepository outbox;
    private final IdGenerator ids;
    private final Clock clock;
    /** Self proxy, so each batch entry really opens its own transaction. */
    private final ScreeningCancellationService self;

    public ScreeningCancellationService(BookingRepository bookings, PaymentRepository payments,
            RefundRepository refunds, ScreeningSeatRepository seats, OutboxEventRepository outbox,
            IdGenerator ids, Clock clock, @Lazy ScreeningCancellationService self) {
        this.bookings = bookings; this.payments = payments; this.refunds = refunds;
        this.seats = seats; this.outbox = outbox; this.ids = ids; this.clock = clock; this.self = self;
    }

    /** Drains every confirmed booking of the screening and returns how many were settled. */
    public int settleBookings(UUID screeningId) {
        int settled = 0;
        while (true) {
            List<UUID> batch = self.nextBatch(screeningId);
            if (batch.isEmpty()) return settled;
            int batchSettled = 0;
            for (UUID bookingId : batch) batchSettled += self.settleBooking(bookingId);
            // A batch that settles nothing means another actor is holding those rows; stop rather
            // than spin, and let the sweeper retry on its next pass.
            if (batchSettled == 0) return settled;
            settled += batchSettled;
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> nextBatch(UUID screeningId) {
        return bookings.findConfirmedIdsByScreening(screeningId, PageRequest.of(0, BATCH_SIZE));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int settleBooking(UUID bookingId) {
        Booking booking = bookings.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getState() != BookingState.CONFIRMED) return 0;
        var payment = payments.findByBookingIdForUpdate(bookingId).orElseThrow();
        Instant now = clock.instant();

        var seatIds = booking.getItems().stream().map(item -> item.getScreeningSeatId()).sorted().toList();
        var lockedSeats = seats.findAllByIdForUpdate(seatIds);
        if (lockedSeats.size() != seatIds.size()) throw new IllegalStateException("booking seat snapshot is incomplete");
        lockedSeats.forEach(seat -> seat.cancelBooking());
        booking.cancelForCancelledScreening();

        outbox.save(new OutboxEvent(ids.nextId(), "SCREENING_CANCELLED", "BOOKING", bookingId,
                "screening-cancelled:" + bookingId, "{\"bookingId\":\"" + bookingId + "\"}", now));
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) return 1;

        Refund refund = refunds.findByPaymentIdAndReason(payment.getId(), RefundReason.SHOW_CANCELLED)
                .orElseGet(() -> refunds.save(new Refund(ids.nextId(), bookingId, payment.getId(),
                        RefundReason.SHOW_CANCELLED, payment.getAmount(),
                        "show-cancelled:" + payment.getId(), now)));
        outbox.save(new OutboxEvent(ids.nextId(), "REFUND_REQUESTED", "REFUND", refund.getId(),
                "refund-requested:" + refund.getId(), "{\"refundId\":\"" + refund.getId() + "\"}", now));
        return 1;
    }
}
