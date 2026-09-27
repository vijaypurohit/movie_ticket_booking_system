package com.vijaypurohit.movietickets.booking.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vijaypurohit.movietickets.booking.model.Booking;
import com.vijaypurohit.movietickets.booking.model.BookingItem;
import com.vijaypurohit.movietickets.booking.persistence.BookingRepository;
import com.vijaypurohit.movietickets.generated.model.CreateBookingRequest;
import com.vijaypurohit.movietickets.generated.model.BookingResponse;
import com.vijaypurohit.movietickets.generated.model.ItemResponse;
import com.vijaypurohit.movietickets.generated.model.RefundSummary;
import com.vijaypurohit.movietickets.identity.application.CurrentUserProvider;
import com.vijaypurohit.movietickets.payment.PaymentGateway;
import com.vijaypurohit.movietickets.payment.model.Payment;
import com.vijaypurohit.movietickets.payment.model.Refund;
import com.vijaypurohit.movietickets.payment.persistence.PaymentRepository;
import com.vijaypurohit.movietickets.payment.persistence.RefundRepository;
import com.vijaypurohit.movietickets.shared.error.ResourceNotFoundException;
import com.vijaypurohit.movietickets.shared.pagination.CursorCodec;
import com.vijaypurohit.movietickets.shared.pagination.CursorPageResponse;
import com.vijaypurohit.movietickets.shared.pagination.CursorResource;
import com.vijaypurohit.movietickets.shared.pagination.PageCursor;
import com.vijaypurohit.movietickets.shared.pagination.PageLimits;

@Service
@ConditionalOnProperty(prefix = "app.booking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BookingService {
    private static final UUID MAX_UUID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private final BookingTransactionService transactions;
    private final PaymentGateway gateway;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final CurrentUserProvider users;
    private final CursorCodec cursors;

    public BookingService(BookingTransactionService transactions, PaymentGateway gateway,
            BookingRepository bookings, PaymentRepository payments, RefundRepository refunds,
            CurrentUserProvider users, CursorCodec cursors) {
        this.transactions = transactions; this.gateway = gateway; this.bookings = bookings;
        this.payments = payments; this.refunds = refunds; this.users = users; this.cursors = cursors;
    }

    public BookingResponse create(String idempotencyKey, CreateBookingRequest request) {
        UUID customerId = users.requireCustomerId();
        var prepared = transactions.prepare(customerId, idempotencyKey, request.getReservationId(), request.getDiscountCode());
        if (prepared.requiresCharge()) {
            var result = gateway.charge(prepared.gatewayKey(), prepared.amount(), request.getPaymentToken());
            transactions.finalizePayment(prepared.bookingId(), result);
        }
        return get(prepared.bookingId());
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID id) {
        UUID customerId = users.requireCustomerId();
        Booking booking = bookings.findDetailedOwned(id, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("not-found", "Booking not found",
                        "RESOURCE_NOT_FOUND", "The requested booking was not found."));
        Payment payment = payments.findByBookingId(id).orElseThrow();
        return response(booking, payment, refunds.findByBookingIdIn(List.of(id)));
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<BookingResponse> history(String encodedCursor, Integer requestedLimit) {
        UUID customerId = users.requireCustomerId();
        int limit = PageLimits.resolve(requestedLimit);
        PageCursor cursor = encodedCursor == null
                ? new PageCursor(CursorResource.BOOKING, Instant.parse("9999-12-31T23:59:59.999999Z"), MAX_UUID)
                : cursors.decode(encodedCursor, CursorResource.BOOKING);
        List<Booking> page = bookings.findHistory(customerId, cursor.sortValue(), cursor.id(), PageRequest.of(0, limit + 1));
        boolean hasMore = page.size() > limit;
        List<Booking> values = hasMore ? page.subList(0, limit) : page;
        List<UUID> ids = values.stream().map(Booking::getId).toList();
        if (ids.isEmpty()) return new CursorPageResponse<>(List.of(), null);
        Map<UUID, Booking> detailedById = bookings.findHistoryDetails(ids).stream()
                .collect(Collectors.toMap(Booking::getId, Function.identity()));
        Map<UUID, Payment> byBooking = payments.findByBookingIdIn(ids).stream()
                .collect(Collectors.toMap(Payment::getBookingId, Function.identity()));
        Map<UUID, List<Refund>> refundsByBooking = refunds.findByBookingIdIn(ids).stream()
                .collect(Collectors.groupingBy(Refund::getBookingId));
        List<BookingResponse> items = ids.stream()
                .map(id -> response(detailedById.get(id), byBooking.get(id),
                        refundsByBooking.getOrDefault(id, List.of())))
                .toList();
        String nextCursor = null;
        if (hasMore && !values.isEmpty()) {
            Booking last = values.getLast();
            nextCursor = cursors.encode(new PageCursor(CursorResource.BOOKING, last.getCreatedAt(), last.getId()));
        }
        return new CursorPageResponse<>(items, nextCursor);
    }

    private BookingResponse response(Booking booking, Payment payment, List<Refund> refundValues) {
        List<ItemResponse> items = booking.getItems().stream()
                .sorted(Comparator.comparing(BookingItem::getRowLabel).thenComparingInt(BookingItem::getSeatNumber))
                .map(item -> new ItemResponse()
                .screeningSeatId(item.getScreeningSeatId())
                .rowLabel(item.getRowLabel())
                .seatNumber(item.getSeatNumber())
                .category(item.getCategory())
                .unitPrice(item.getUnitPrice()))
                .toList();
        List<RefundSummary> refundSummaries = refundValues.stream()
                .map(refund -> new RefundSummary()
                .id(refund.getId())
                .reason(refund.getReason())
                .status(refund.getStatus())
                .amount(refund.getAmount()))
                .toList();
        return new BookingResponse()
                .id(booking.getId())
                .reference(booking.getReference())
                .screeningId(booking.getScreeningId())
                .state(booking.getState())
                .items(items)
                .subtotal(booking.getSubtotal())
                .discountAmount(booking.getDiscountAmount())
                .totalAmount(booking.getTotalAmount())
                .currency(booking.getCurrency())
                .paymentStatus(payment.getStatus())
                .refunds(refundSummaries)
                .createdAt(booking.getCreatedAt());
    }
}
