package com.vijaypurohit.movietickets.demo.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(prefix = "app.demo", name = "generator-enabled", havingValue = "true", matchIfMissing = true)
public class LargeDemoDataGenerator {
    public static final int CITY_COUNT = 3;
    public static final int THEATER_COUNT = 10;
    public static final int AUDITORIUM_COUNT = 30;
    public static final int SEATS_PER_AUDITORIUM = 150;
    public static final int MOVIE_COUNT = 20;
    public static final int DAYS = 7;
    public static final int SCREENINGS_PER_AUDITORIUM_PER_DAY = 4;
    public static final int SCREENING_COUNT = AUDITORIUM_COUNT * DAYS * SCREENINGS_PER_AUDITORIUM_PER_DAY;
    public static final int SCREENING_SEAT_COUNT = SCREENING_COUNT * SEATS_PER_AUDITORIUM;
    public static final int CUSTOMER_COUNT = 1_000;
    public static final int BOOKING_COUNT = 10_000;
    public static final String CUSTOMER_PASSWORD = "Capacity@123";

    private static final int BATCH_SIZE = 1_000;
    private static final int SEEDED_DEMO_CUSTOMERS = 2;
    private static final int CAPACITY_CUSTOMER_COUNT = CUSTOMER_COUNT - SEEDED_DEMO_CUSTOMERS;
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalTime[] SHOW_TIMES = {
            LocalTime.of(9, 0), LocalTime.of(12, 0), LocalTime.of(15, 0), LocalTime.of(18, 0)
    };
    private static final UUID PRICING_PLAN_ID = id("pricing-plan");
    private static final UUID REFUND_POLICY_ID = id("refund-policy");
    private static final BigDecimal REGULAR_PRICE = new BigDecimal("250.00");
    private static final BigDecimal PREMIUM_PRICE = new BigDecimal("400.00");
    private static final BigDecimal WEEKEND_ADJUSTMENT = new BigDecimal("50.00");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final LocalDate anchorDate;
    private final TransactionTemplate transactions;

    public LargeDemoDataGenerator(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, Clock clock,
            PlatformTransactionManager transactionManager,
            @Value("${app.demo.anchor-date:2030-01-07}") LocalDate anchorDate) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.anchorDate = anchorDate;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public GenerationReport generate(boolean includeHistory) {
        Instant generatedAt = clock.instant();
        CatalogData catalog = catalogData();
        transactions.executeWithoutResult(status -> insertCatalog(catalog, generatedAt));
        for (int day = 0; day < DAYS; day++) {
            int dayIndex = day;
            transactions.executeWithoutResult(status -> insertScreeningDay(catalog, dayIndex, generatedAt));
        }
        if (includeHistory) {
            transactions.executeWithoutResult(status -> insertCustomers(generatedAt));
            for (int offset = 0; offset < BOOKING_COUNT; offset += BATCH_SIZE) {
                int chunkStart = offset;
                int chunkEnd = Math.min(offset + BATCH_SIZE, BOOKING_COUNT);
                transactions.executeWithoutResult(status -> insertHistoryChunk(catalog, chunkStart, chunkEnd, generatedAt));
            }
        }
        return new GenerationReport(CITY_COUNT, THEATER_COUNT, AUDITORIUM_COUNT,
                AUDITORIUM_COUNT * SEATS_PER_AUDITORIUM, MOVIE_COUNT, SCREENING_COUNT,
                SCREENING_SEAT_COUNT, includeHistory ? CUSTOMER_COUNT : 0,
                includeHistory ? BOOKING_COUNT : 0, anchorDate);
    }

    private CatalogData catalogData() {
        List<UUID> cityIds = ids("city", CITY_COUNT);
        List<UUID> theaterIds = ids("theater", THEATER_COUNT);
        List<UUID> auditoriumIds = ids("auditorium", AUDITORIUM_COUNT);
        List<UUID> movieIds = ids("movie", MOVIE_COUNT);
        return new CatalogData(cityIds, theaterIds, auditoriumIds, movieIds);
    }

    private void insertCatalog(CatalogData data, Instant now) {
        batch("""
                INSERT INTO city (id, name, country, time_zone, active, created_at, updated_at, version)
                VALUES (?, ?, 'India', 'Asia/Kolkata', TRUE, ?, ?, 0)
                ON CONFLICT DO NOTHING
                """, indexes(CITY_COUNT), (statement, index) -> {
            uuid(statement, 1, data.cityIds().get(index));
            statement.setString(2, "Capacity City " + (index + 1));
            instant(statement, 3, now); instant(statement, 4, now);
        });
        batch("""
                INSERT INTO theater (id, city_id, name, address, active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, TRUE, ?, ?, 0)
                ON CONFLICT DO NOTHING
                """, indexes(THEATER_COUNT), (statement, index) -> {
            uuid(statement, 1, data.theaterIds().get(index));
            uuid(statement, 2, data.cityIds().get(index % CITY_COUNT));
            statement.setString(3, "Capacity Theater " + (index + 1));
            statement.setString(4, "Capacity address " + (index + 1));
            instant(statement, 5, now); instant(statement, 6, now);
        });
        batch("""
                INSERT INTO auditorium (id, theater_id, name, active, created_at, updated_at, version)
                VALUES (?, ?, ?, TRUE, ?, ?, 0)
                ON CONFLICT DO NOTHING
                """, indexes(AUDITORIUM_COUNT), (statement, index) -> {
            uuid(statement, 1, data.auditoriumIds().get(index));
            uuid(statement, 2, data.theaterIds().get(index % THEATER_COUNT));
            statement.setString(3, "Capacity Screen " + (index / THEATER_COUNT + 1));
            instant(statement, 4, now); instant(statement, 5, now);
        });

        List<SeatRow> seats = new ArrayList<>(AUDITORIUM_COUNT * SEATS_PER_AUDITORIUM);
        for (int auditorium = 0; auditorium < AUDITORIUM_COUNT; auditorium++) {
            for (int seat = 0; seat < SEATS_PER_AUDITORIUM; seat++) {
                seats.add(new SeatRow(physicalSeatId(auditorium, seat), data.auditoriumIds().get(auditorium),
                        rowLabel(seat), seatNumber(seat), category(seat)));
            }
        }
        batch("""
                INSERT INTO seat (id, auditorium_id, row_label, seat_number, category, active,
                                  created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, TRUE, ?, ?, 0)
                ON CONFLICT DO NOTHING
                """, seats, (statement, seat) -> {
            uuid(statement, 1, seat.id()); uuid(statement, 2, seat.auditoriumId());
            statement.setString(3, seat.rowLabel()); statement.setInt(4, seat.number());
            statement.setString(5, seat.category()); instant(statement, 6, now); instant(statement, 7, now);
        });

        batch("""
                INSERT INTO movie (id, title, duration_minutes, language, active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'English', TRUE, ?, ?, 0)
                ON CONFLICT DO NOTHING
                """, indexes(MOVIE_COUNT), (statement, index) -> {
            uuid(statement, 1, data.movieIds().get(index));
            statement.setString(2, "Capacity Movie %02d".formatted(index + 1));
            statement.setInt(3, 90 + index * 3);
            instant(statement, 4, now); instant(statement, 5, now);
        });
        jdbc.update("""
                INSERT INTO pricing_plan (id, name, regular_price, premium_price, weekend_adjustment,
                                          active, created_at, updated_at, version)
                VALUES (?, 'Capacity Pricing', ?, ?, ?, TRUE, ?, ?, 0)
                ON CONFLICT DO NOTHING
                """, PRICING_PLAN_ID, REGULAR_PRICE, PREMIUM_PRICE, WEEKEND_ADJUSTMENT,
                Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO refund_policy (id, name, active, created_at, updated_at, version)
                VALUES (?, 'Capacity Refund Policy', TRUE, ?, ?, 0)
                ON CONFLICT DO NOTHING
                """, REFUND_POLICY_ID, Timestamp.from(now), Timestamp.from(now));
        List<RefundRuleRow> rules = List.of(
                new RefundRuleRow(id("refund-rule-1440"), 1440, new BigDecimal("100.00")),
                new RefundRuleRow(id("refund-rule-120"), 120, new BigDecimal("50.00")),
                new RefundRuleRow(id("refund-rule-0"), 0, BigDecimal.ZERO.setScale(2)));
        batch("""
                INSERT INTO refund_policy_rule (id, refund_policy_id, cutoff_minutes, refund_percentage)
                VALUES (?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, rules, (statement, rule) -> {
            uuid(statement, 1, rule.id()); uuid(statement, 2, REFUND_POLICY_ID);
            statement.setLong(3, rule.cutoffMinutes()); statement.setBigDecimal(4, rule.percentage());
        });
    }

    private void insertScreeningDay(CatalogData data, int day, Instant now) {
        List<ScreeningRow> screenings = new ArrayList<>(AUDITORIUM_COUNT * SCREENINGS_PER_AUDITORIUM_PER_DAY);
        LocalDate date = anchorDate.plusDays(day);
        for (int auditorium = 0; auditorium < AUDITORIUM_COUNT; auditorium++) {
            for (int slot = 0; slot < SCREENINGS_PER_AUDITORIUM_PER_DAY; slot++) {
                int index = screeningIndex(day, auditorium, slot);
                Instant start = date.atTime(SHOW_TIMES[slot]).atZone(ZONE).toInstant();
                screenings.add(new ScreeningRow(index, screeningId(index),
                        data.movieIds().get(index % MOVIE_COUNT), data.auditoriumIds().get(auditorium),
                        start, start.plusSeconds(2 * 60 * 60L), auditorium, date));
            }
        }
        batch("""
                INSERT INTO screening (id, movie_id, auditorium_id, pricing_plan_id, refund_policy_id,
                                       start_time, end_time, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, 0)
                ON CONFLICT DO NOTHING
                """, screenings, (statement, screening) -> {
            uuid(statement, 1, screening.id()); uuid(statement, 2, screening.movieId());
            uuid(statement, 3, screening.auditoriumId()); uuid(statement, 4, PRICING_PLAN_ID);
            uuid(statement, 5, REFUND_POLICY_ID); instant(statement, 6, screening.start());
            instant(statement, 7, screening.end()); instant(statement, 8, now); instant(statement, 9, now);
        });

        List<PriceRow> prices = new ArrayList<>(screenings.size() * 2);
        List<ScreeningSeatRow> inventory = new ArrayList<>(screenings.size() * SEATS_PER_AUDITORIUM);
        for (ScreeningRow screening : screenings) {
            boolean weekend = screening.date().getDayOfWeek() == DayOfWeek.SATURDAY
                    || screening.date().getDayOfWeek() == DayOfWeek.SUNDAY;
            BigDecimal adjustment = weekend ? WEEKEND_ADJUSTMENT : BigDecimal.ZERO;
            prices.add(new PriceRow(id("screening-price:" + screening.index() + ":REGULAR"),
                    screening.id(), "REGULAR", REGULAR_PRICE.add(adjustment)));
            prices.add(new PriceRow(id("screening-price:" + screening.index() + ":PREMIUM"),
                    screening.id(), "PREMIUM", PREMIUM_PRICE.add(adjustment)));
            for (int seat = 0; seat < SEATS_PER_AUDITORIUM; seat++) {
                inventory.add(new ScreeningSeatRow(screeningSeatId(screening.index(), seat), screening.id(),
                        physicalSeatId(screening.auditoriumIndex(), seat)));
            }
        }
        batch("""
                INSERT INTO screening_price (id, screening_id, seat_category, amount, currency)
                VALUES (?, ?, ?, ?, 'INR')
                ON CONFLICT DO NOTHING
                """, prices, (statement, price) -> {
            uuid(statement, 1, price.id()); uuid(statement, 2, price.screeningId());
            statement.setString(3, price.category()); statement.setBigDecimal(4, price.amount());
        });
        batch("""
                INSERT INTO screening_seat (id, screening_id, seat_id, state)
                VALUES (?, ?, ?, 'AVAILABLE')
                ON CONFLICT DO NOTHING
                """, inventory, (statement, seat) -> {
            uuid(statement, 1, seat.id()); uuid(statement, 2, seat.screeningId()); uuid(statement, 3, seat.seatId());
        });
    }

    private void insertCustomers(Instant now) {
        String passwordHash = passwordEncoder.encode(CUSTOMER_PASSWORD);
        batch("""
                INSERT INTO app_user (id, email, password_hash, role, active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'CUSTOMER', TRUE, ?, ?, 0)
                ON CONFLICT DO NOTHING
                """, indexes(CAPACITY_CUSTOMER_COUNT), (statement, index) -> {
            uuid(statement, 1, customerId(index));
            statement.setString(2, customerEmail(index)); statement.setString(3, passwordHash);
            instant(statement, 4, now); instant(statement, 5, now);
        });
    }

    private void insertHistoryChunk(CatalogData data, int start, int end, Instant generatedAt) {
        List<HistoryRow> rows = new ArrayList<>(end - start);
        for (int index = start; index < end; index++) {
            int screeningIndex = index / SEATS_PER_AUDITORIUM;
            int seatIndex = index % SEATS_PER_AUDITORIUM;
            LocalDate screeningDate = anchorDate.plusDays(screeningIndex / (AUDITORIUM_COUNT * SCREENINGS_PER_AUDITORIUM_PER_DAY));
            boolean weekend = screeningDate.getDayOfWeek() == DayOfWeek.SATURDAY
                    || screeningDate.getDayOfWeek() == DayOfWeek.SUNDAY;
            BigDecimal basePrice = "PREMIUM".equals(category(seatIndex)) ? PREMIUM_PRICE : REGULAR_PRICE;
            BigDecimal price = weekend ? basePrice.add(WEEKEND_ADJUSTMENT) : basePrice;
            Instant createdAt = generatedAt.minusSeconds((BOOKING_COUNT - index) * 60L);
            rows.add(new HistoryRow(index, reservationId(index), bookingId(index), paymentId(index),
                    screeningId(screeningIndex), screeningSeatId(screeningIndex, seatIndex), seatIndex,
                    price, createdAt));
        }
        batch("""
                INSERT INTO seat_reservation (id, customer_id, screening_id, state, expires_at,
                                              checkout_expires_at, idempotency_key, request_fingerprint,
                                              created_at, updated_at, version)
                VALUES (?, ?, ?, 'CONVERTED', ?, ?, ?, ?, ?, ?, 0)
                ON CONFLICT DO NOTHING
                """, rows, (statement, row) -> {
            uuid(statement, 1, row.reservationId()); uuid(statement, 2, customerId(0));
            uuid(statement, 3, row.screeningId()); instant(statement, 4, row.createdAt().plusSeconds(240));
            instant(statement, 5, row.createdAt().plusSeconds(60));
            statement.setString(6, "capacity-history-reservation-" + row.index());
            statement.setString(7, "0".repeat(64)); instant(statement, 8, row.createdAt());
            instant(statement, 9, row.createdAt());
        });
        batch("""
                INSERT INTO booking (id, reference, customer_id, screening_id, reservation_id, state,
                                     subtotal, discount_amount, total_amount, currency, idempotency_key,
                                     request_fingerprint, checkout_expires_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'CONFIRMED', ?, 0.00, ?, 'INR', ?, ?, ?, ?, ?, 0)
                ON CONFLICT DO NOTHING
                """, rows, (statement, row) -> {
            uuid(statement, 1, row.bookingId()); statement.setString(2, "CAP%020d".formatted(row.index()));
            uuid(statement, 3, customerId(0)); uuid(statement, 4, row.screeningId());
            uuid(statement, 5, row.reservationId()); statement.setBigDecimal(6, row.price());
            statement.setBigDecimal(7, row.price()); statement.setString(8, "capacity-history-booking-" + row.index());
            statement.setString(9, "0".repeat(64)); instant(statement, 10, row.createdAt().plusSeconds(60));
            instant(statement, 11, row.createdAt()); instant(statement, 12, row.createdAt());
        });
        batch("""
                INSERT INTO booking_item (id, booking_id, screening_seat_id, row_label, seat_number,
                                          category, unit_price, currency)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'INR')
                ON CONFLICT DO NOTHING
                """, rows, (statement, row) -> {
            uuid(statement, 1, id("history-item:" + row.index())); uuid(statement, 2, row.bookingId());
            uuid(statement, 3, row.screeningSeatId()); statement.setString(4, rowLabel(row.seatIndex()));
            statement.setInt(5, seatNumber(row.seatIndex())); statement.setString(6, category(row.seatIndex()));
            statement.setBigDecimal(7, row.price());
        });
        List<BookingRuleRow> rules = new ArrayList<>(rows.size() * 3);
        for (HistoryRow row : rows) {
            rules.add(new BookingRuleRow(id("history-rule:" + row.index() + ":1440"), row.bookingId(), 1440, new BigDecimal("100.00")));
            rules.add(new BookingRuleRow(id("history-rule:" + row.index() + ":120"), row.bookingId(), 120, new BigDecimal("50.00")));
            rules.add(new BookingRuleRow(id("history-rule:" + row.index() + ":0"), row.bookingId(), 0, BigDecimal.ZERO.setScale(2)));
        }
        batch("""
                INSERT INTO booking_refund_rule (id, booking_id, cutoff_minutes, refund_percentage)
                VALUES (?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, rules, (statement, rule) -> {
            uuid(statement, 1, rule.id()); uuid(statement, 2, rule.bookingId());
            statement.setLong(3, rule.cutoff()); statement.setBigDecimal(4, rule.percentage());
        });
        batch("""
                INSERT INTO payment (id, booking_id, status, amount, currency, gateway_idempotency_key,
                                     provider_reference, created_at, updated_at, version)
                VALUES (?, ?, 'SUCCEEDED', ?, 'INR', ?, ?, ?, ?, 0)
                ON CONFLICT DO NOTHING
                """, rows, (statement, row) -> {
            uuid(statement, 1, row.paymentId()); uuid(statement, 2, row.bookingId());
            statement.setBigDecimal(3, row.price()); statement.setString(4, "capacity-charge-" + row.index());
            statement.setString(5, "pay_capacity_" + row.index()); instant(statement, 6, row.createdAt());
            instant(statement, 7, row.createdAt());
        });
        batch("UPDATE screening_seat SET state = 'BOOKED', reservation_id = NULL WHERE id = ?",
                rows, (statement, row) -> uuid(statement, 1, row.screeningSeatId()));
    }

    private <T> void batch(String sql, List<T> values, StatementSetter<T> setter) {
        jdbc.batchUpdate(sql, values, BATCH_SIZE, (statement, value) -> setter.set(statement, value));
    }

    private static List<Integer> indexes(int size) {
        List<Integer> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) values.add(index);
        return values;
    }

    private static List<UUID> ids(String resource, int count) {
        List<UUID> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) values.add(id(resource + ':' + index));
        return values;
    }

    private static int screeningIndex(int day, int auditorium, int slot) {
        return day * AUDITORIUM_COUNT * SCREENINGS_PER_AUDITORIUM_PER_DAY
                + auditorium * SCREENINGS_PER_AUDITORIUM_PER_DAY + slot;
    }

    private static UUID physicalSeatId(int auditorium, int seat) { return id("seat:" + auditorium + ':' + seat); }
    private static UUID screeningId(int index) { return id("screening:" + index); }
    private static UUID screeningSeatId(int screening, int seat) { return id("screening-seat:" + screening + ':' + seat); }
    private static UUID customerId(int index) { return id("customer:" + index); }
    private static UUID reservationId(int index) { return id("history-reservation:" + index); }
    private static UUID bookingId(int index) { return id("history-booking:" + index); }
    private static UUID paymentId(int index) { return id("history-payment:" + index); }
    private static String customerEmail(int index) { return "capacity.customer.%04d@movietickets.local".formatted(index); }
    private static String rowLabel(int seat) { return String.valueOf((char) ('A' + seat / 15)); }
    private static int seatNumber(int seat) { return seat % 15 + 1; }
    private static String category(int seat) { return seat / 15 >= 7 ? "PREMIUM" : "REGULAR"; }
    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(("movie-tickets-capacity:" + value).getBytes(StandardCharsets.UTF_8));
    }
    private static void uuid(PreparedStatement statement, int index, UUID value) throws SQLException { statement.setObject(index, value); }
    private static void instant(PreparedStatement statement, int index, Instant value) throws SQLException { statement.setTimestamp(index, Timestamp.from(value)); }

    @FunctionalInterface
    private interface StatementSetter<T> { void set(PreparedStatement statement, T value) throws SQLException; }

    public record GenerationReport(int cities, int theaters, int auditoriums, int seats, int movies,
            int screenings, int screeningSeats, int customers, int bookings, LocalDate anchorDate) { }
    private record CatalogData(List<UUID> cityIds, List<UUID> theaterIds, List<UUID> auditoriumIds, List<UUID> movieIds) { }
    private record SeatRow(UUID id, UUID auditoriumId, String rowLabel, int number, String category) { }
    private record RefundRuleRow(UUID id, long cutoffMinutes, BigDecimal percentage) { }
    private record ScreeningRow(int index, UUID id, UUID movieId, UUID auditoriumId, Instant start,
            Instant end, int auditoriumIndex, LocalDate date) { }
    private record PriceRow(UUID id, UUID screeningId, String category, BigDecimal amount) { }
    private record ScreeningSeatRow(UUID id, UUID screeningId, UUID seatId) { }
    private record HistoryRow(int index, UUID reservationId, UUID bookingId, UUID paymentId,
            UUID screeningId, UUID screeningSeatId, int seatIndex, BigDecimal price, Instant createdAt) { }
    private record BookingRuleRow(UUID id, UUID bookingId, long cutoff, BigDecimal percentage) { }
}
