package com.vijaypurohit.movietickets.reservation.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vijaypurohit.movietickets.identity.application.CurrentUserProvider;
import com.vijaypurohit.movietickets.booking.application.BookingTransactionService;
import com.vijaypurohit.movietickets.reservation.model.ReservationState;
import com.vijaypurohit.movietickets.reservation.model.SeatReservation;
import com.vijaypurohit.movietickets.reservation.persistence.SeatReservationRepository;
import com.vijaypurohit.movietickets.reservation.web.ReservationRequests.CreateReservationRequest;
import com.vijaypurohit.movietickets.reservation.web.ReservationResponses.ReservationResponse;
import com.vijaypurohit.movietickets.screening.model.ScreeningSeat;
import com.vijaypurohit.movietickets.screening.model.ScreeningSeatState;
import com.vijaypurohit.movietickets.screening.model.ScreeningStatus;
import com.vijaypurohit.movietickets.screening.persistence.ScreeningRepository;
import com.vijaypurohit.movietickets.screening.persistence.ScreeningSeatRepository;
import com.vijaypurohit.movietickets.shared.error.ConflictException;
import com.vijaypurohit.movietickets.shared.error.ResourceNotFoundException;
import com.vijaypurohit.movietickets.shared.identifier.IdGenerator;

@Service
@ConditionalOnProperty(prefix = "app.reservation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeatReservationService {
    private final SeatReservationRepository reservations;
    private final ScreeningSeatRepository seats;
    private final ScreeningRepository screenings;
    private final CurrentUserProvider users;
    private final IdGenerator ids;
    private final Clock clock;
    private final Duration duration;
    private final ObjectProvider<BookingTransactionService> bookingTransactions;

    public SeatReservationService(SeatReservationRepository reservations, ScreeningSeatRepository seats,
            ScreeningRepository screenings, CurrentUserProvider users, IdGenerator ids, Clock clock,
            @Value("${app.booking.reservation-duration:PT4M}") Duration duration,
            ObjectProvider<BookingTransactionService> bookingTransactions) {
        this.reservations = reservations; this.seats = seats; this.screenings = screenings;
        this.users = users; this.ids = ids; this.clock = clock; this.duration = duration;
        this.bookingTransactions = bookingTransactions;
    }

    @Transactional
    public ReservationResponse create(String idempotencyKey, CreateReservationRequest request) {
        UUID customerId = users.requireCustomerId();
        List<UUID> requestedIds = request.screeningSeatIds().stream().sorted().toList();
        if (new HashSet<>(requestedIds).size() != requestedIds.size()) throw conflict("DUPLICATE_SEAT", "The request contains duplicate seats.");
        String fingerprint = fingerprint(requestedIds);
        var existing = reservations.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getRequestFingerprint().equals(fingerprint)) throw conflict("IDEMPOTENCY_KEY_REUSED", "The idempotency key was already used for a different request.");
            return response(existing.get(), seats.findByReservationIdOrderById(existing.get().getId()));
        }

        seats.findReservationIds(requestedIds).forEach(reservationId ->
                bookingTransactions.ifAvailable(service -> service.expireCheckoutForReservation(reservationId)));
        List<ScreeningSeat> locked = seats.findAllByIdForUpdate(requestedIds);
        if (locked.size() != requestedIds.size()) throw conflict("SEAT_UNAVAILABLE", "One or more seats are unavailable.");
        Instant now = clock.instant();
        reclaimExpired(locked, now);
        UUID screeningId = locked.getFirst().getScreeningId();
        if (locked.stream().anyMatch(seat -> !seat.getScreeningId().equals(screeningId))) throw conflict("MULTIPLE_SCREENINGS", "All seats must belong to one screening.");
        if (locked.stream().anyMatch(seat -> seat.getState() != ScreeningSeatState.AVAILABLE)) throw conflict("SEAT_UNAVAILABLE", "One or more seats are unavailable.");
        var screening = screenings.findById(screeningId)
                .filter(value -> value.getStatus() == ScreeningStatus.ACTIVE && value.getStartTime().isAfter(now))
                .orElseThrow(() -> conflict("SCREENING_CLOSED", "The screening is not open for reservations."));

        SeatReservation reservation = new SeatReservation(ids.nextId(), customerId, screening.getId(),
                now.plus(duration), idempotencyKey, fingerprint);
        SeatReservation savedReservation = reservations.saveAndFlush(reservation);
        locked.forEach(seat -> seat.reserve(savedReservation.getId()));
        return response(savedReservation, locked);
    }

    @Transactional
    public ReservationResponse get(UUID id) {
        SeatReservation reservation = requireOwned(id);
        List<ScreeningSeat> locked = seats.findByReservationIdForUpdate(id);
        expireIfNeeded(reservation, locked, clock.instant());
        return response(reservation, locked);
    }

    @Transactional
    public void release(UUID id) {
        SeatReservation reservation = requireOwned(id);
        List<ScreeningSeat> locked = seats.findByReservationIdForUpdate(id);
        locked.forEach(seat -> seat.release(id));
        reservation.release();
    }

    @Transactional
    public int releaseExpiredBatch() {
        Instant now = clock.instant();
        List<SeatReservation> expired = reservations.claimExpired(now);
        for (SeatReservation reservation : expired) {
            List<ScreeningSeat> locked = seats.findByReservationIdForUpdate(reservation.getId());
            expireIfNeeded(reservation, locked, now);
        }
        return expired.size();
    }

    private void reclaimExpired(List<ScreeningSeat> locked, Instant now) {
        locked.stream().map(ScreeningSeat::getReservationId).filter(java.util.Objects::nonNull).distinct().forEach(id -> {
            reservations.findById(id).ifPresent(reservation -> expireIfNeeded(reservation, locked, now));
        });
    }
    private void expireIfNeeded(SeatReservation reservation, List<ScreeningSeat> locked, Instant now) {
        if (reservation.isExpired(now)) {
            reservation.expire();
            locked.stream().filter(seat -> java.util.Objects.equals(seat.getReservationId(), reservation.getId()))
                    .forEach(seat -> seat.release(reservation.getId()));
        }
    }
    private SeatReservation requireOwned(UUID id) { UUID customerId = users.requireCustomerId(); return reservations.findByIdAndCustomerId(id, customerId).orElseThrow(() -> new ResourceNotFoundException("not-found", "Reservation not found", "RESOURCE_NOT_FOUND", "The requested reservation was not found.")); }
    private ConflictException conflict(String code, String detail) { return new ConflictException("reservation-conflict", "Reservation conflict", code, detail); }
    private ReservationResponse response(SeatReservation reservation, List<ScreeningSeat> values) { return new ReservationResponse(reservation.getId(), reservation.getScreeningId(), values.stream().map(ScreeningSeat::getId).sorted().toList(), reservation.getState(), reservation.getExpiresAt(), reservation.getCreatedAt()); }
    private String fingerprint(List<UUID> ids) { try { MessageDigest digest = MessageDigest.getInstance("SHA-256"); return HexFormat.of().formatHex(digest.digest(ids.toString().getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
}
