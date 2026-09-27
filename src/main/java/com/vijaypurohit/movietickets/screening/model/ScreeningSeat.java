package com.vijaypurohit.movietickets.screening.model;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class ScreeningSeat {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "screening_id") private Screening screening;
    @Column(name = "seat_id", nullable = false) private UUID seatId;
    @Column(name = "reservation_id") private UUID reservationId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ScreeningSeatState state;

    protected ScreeningSeat() { }
    ScreeningSeat(UUID id, Screening screening, UUID seatId) {
        this.id = Objects.requireNonNull(id); this.screening = Objects.requireNonNull(screening);
        this.seatId = Objects.requireNonNull(seatId); state = ScreeningSeatState.AVAILABLE;
    }
    public UUID getId() { return id; } public UUID getSeatId() { return seatId; }
    public UUID getScreeningId() { return screening.getId(); }
    public UUID getReservationId() { return reservationId; }
    public ScreeningSeatState getState() { return state; }
    public void reserve(UUID reservationId) { if (state != ScreeningSeatState.AVAILABLE) throw new IllegalStateException("seat is unavailable"); this.reservationId = Objects.requireNonNull(reservationId); state = ScreeningSeatState.RESERVED; }
    public void beginCheckout(UUID expectedReservationId) { if (state != ScreeningSeatState.RESERVED || !Objects.equals(reservationId, expectedReservationId)) throw new IllegalStateException("seat is not held by reservation"); state = ScreeningSeatState.PAYMENT_IN_PROGRESS; }
    public void book(UUID expectedReservationId) { if (state != ScreeningSeatState.PAYMENT_IN_PROGRESS || !Objects.equals(reservationId, expectedReservationId)) throw new IllegalStateException("seat is not checking out"); reservationId = null; state = ScreeningSeatState.BOOKED; }
    public void cancelBooking() { if (state != ScreeningSeatState.BOOKED) throw new IllegalStateException("seat is not booked"); state = ScreeningSeatState.AVAILABLE; }
    public void release(UUID expectedReservationId) { if (Objects.equals(reservationId, expectedReservationId) && (state == ScreeningSeatState.RESERVED || state == ScreeningSeatState.PAYMENT_IN_PROGRESS)) { reservationId = null; state = ScreeningSeatState.AVAILABLE; } }
}
