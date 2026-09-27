package com.vijaypurohit.movietickets.booking.model;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;

import com.vijaypurohit.movietickets.catalog.model.SeatCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class BookingItem {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "booking_id") private Booking booking;
    @Column(name = "screening_seat_id", nullable = false) private UUID screeningSeatId;
    @Column(name = "row_label", nullable = false, length = 10) private String rowLabel;
    @Column(name = "seat_number", nullable = false) private int seatNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SeatCategory category;
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2) private BigDecimal unitPrice;
    @JdbcTypeCode(Types.CHAR) @Column(nullable = false, length = 3) private String currency;

    protected BookingItem() { }
    BookingItem(UUID id, Booking booking, UUID screeningSeatId, String rowLabel, int seatNumber,
            SeatCategory category, BigDecimal unitPrice) {
        this.id = Objects.requireNonNull(id); this.booking = Objects.requireNonNull(booking);
        this.screeningSeatId = Objects.requireNonNull(screeningSeatId); this.rowLabel = Objects.requireNonNull(rowLabel);
        this.seatNumber = seatNumber; this.category = Objects.requireNonNull(category);
        this.unitPrice = Objects.requireNonNull(unitPrice).setScale(2); this.currency = "INR";
    }
    public UUID getScreeningSeatId() { return screeningSeatId; }
    public String getRowLabel() { return rowLabel; }
    public int getSeatNumber() { return seatNumber; }
    public SeatCategory getCategory() { return category; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
