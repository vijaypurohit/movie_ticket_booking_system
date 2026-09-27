package com.vijaypurohit.movietickets.booking.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class BookingRefundRule {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "booking_id") private Booking booking;
    @Column(name = "cutoff_minutes", nullable = false) private long cutoffMinutes;
    @Column(name = "refund_percentage", nullable = false, precision = 5, scale = 2) private BigDecimal refundPercentage;

    protected BookingRefundRule() { }
    BookingRefundRule(UUID id, Booking booking, long cutoffMinutes, BigDecimal refundPercentage) {
        this.id = Objects.requireNonNull(id); this.booking = Objects.requireNonNull(booking);
        this.cutoffMinutes = cutoffMinutes; this.refundPercentage = Objects.requireNonNull(refundPercentage).setScale(2);
    }
    public long getCutoffMinutes() { return cutoffMinutes; }
    public BigDecimal getRefundPercentage() { return refundPercentage; }
}
