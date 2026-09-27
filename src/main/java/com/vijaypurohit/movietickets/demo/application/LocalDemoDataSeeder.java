package com.vijaypurohit.movietickets.demo.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.vijaypurohit.movietickets.catalog.model.Auditorium;
import com.vijaypurohit.movietickets.catalog.model.City;
import com.vijaypurohit.movietickets.catalog.model.Movie;
import com.vijaypurohit.movietickets.catalog.model.Seat;
import com.vijaypurohit.movietickets.catalog.model.SeatCategory;
import com.vijaypurohit.movietickets.catalog.model.Theater;
import com.vijaypurohit.movietickets.catalog.persistence.AuditoriumRepository;
import com.vijaypurohit.movietickets.catalog.persistence.CityRepository;
import com.vijaypurohit.movietickets.catalog.persistence.MovieRepository;
import com.vijaypurohit.movietickets.catalog.persistence.SeatRepository;
import com.vijaypurohit.movietickets.catalog.persistence.TheaterRepository;
import com.vijaypurohit.movietickets.pricing.model.PricingPlan;
import com.vijaypurohit.movietickets.pricing.model.RefundPolicy;
import com.vijaypurohit.movietickets.pricing.persistence.PricingPlanRepository;
import com.vijaypurohit.movietickets.pricing.persistence.RefundPolicyRepository;
import com.vijaypurohit.movietickets.screening.model.Screening;
import com.vijaypurohit.movietickets.screening.persistence.ScreeningRepository;

@Component
@ConditionalOnProperty(prefix = "app.demo", name = "enabled", havingValue = "true")
public class LocalDemoDataSeeder implements ApplicationRunner {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private final CityRepository cities; private final TheaterRepository theaters;
    private final AuditoriumRepository auditoriums; private final SeatRepository seats;
    private final MovieRepository movies; private final PricingPlanRepository pricingPlans;
    private final RefundPolicyRepository refundPolicies; private final ScreeningRepository screenings;
    private final Clock clock;

    public LocalDemoDataSeeder(CityRepository cities, TheaterRepository theaters,
            AuditoriumRepository auditoriums, SeatRepository seats, MovieRepository movies,
            PricingPlanRepository pricingPlans, RefundPolicyRepository refundPolicies,
            ScreeningRepository screenings, Clock clock) {
        this.cities=cities; this.theaters=theaters; this.auditoriums=auditoriums; this.seats=seats;
        this.movies=movies; this.pricingPlans=pricingPlans; this.refundPolicies=refundPolicies;
        this.screenings=screenings; this.clock=clock;
    }

    @Override @Transactional
    public void run(ApplicationArguments arguments) {
        City city = cities.findById(id("city")).orElseGet(() -> cities.save(new City(id("city"), "Codex Demo Pune", "India", ZONE.getId())));
        Theater theater = theaters.findById(id("theater")).orElseGet(() -> theaters.save(new Theater(id("theater"), city, "Codex Demo Cinema", "Baner, Pune")));
        Auditorium auditorium = auditoriums.findById(id("auditorium")).orElseGet(() -> auditoriums.save(new Auditorium(id("auditorium"), theater, "Screen 1")));
        List<Seat> demoSeats = List.of(
                seat(auditorium, "A", 1, SeatCategory.REGULAR), seat(auditorium, "A", 2, SeatCategory.REGULAR),
                seat(auditorium, "B", 1, SeatCategory.PREMIUM), seat(auditorium, "B", 2, SeatCategory.PREMIUM));
        Movie movie = movies.findById(id("movie")).orElseGet(() -> movies.save(new Movie(id("movie"), "The Last Commit", 120, "English")));
        PricingPlan plan = pricingPlans.findById(id("pricing")).orElseGet(() -> pricingPlans.save(
                new PricingPlan(id("pricing"), "Codex Demo Pricing", new BigDecimal("250.00"),
                        new BigDecimal("400.00"), new BigDecimal("50.00"))));
        RefundPolicy policy = refundPolicies.findById(id("refund-policy")).orElseGet(() -> {
            RefundPolicy value = new RefundPolicy(id("refund-policy"), "Codex Demo Refund Policy");
            value.replace(value.getName(), List.of(
                    new RefundPolicy.RuleDefinition(id("refund-rule-1440"), 1440, new BigDecimal("100.00")),
                    new RefundPolicy.RuleDefinition(id("refund-rule-120"), 120, new BigDecimal("50.00")),
                    new RefundPolicy.RuleDefinition(id("refund-rule-0"), 0, new BigDecimal("0.00"))));
            return refundPolicies.save(value);
        });
        LocalDate firstDay = LocalDate.now(clock.withZone(ZONE)).plusDays(1);
        for (int day = 0; day < 7; day++) createScreening(firstDay.plusDays(day), movie, auditorium, demoSeats, plan, policy);
    }

    private Seat seat(Auditorium auditorium, String row, int number, SeatCategory category) {
        UUID id = id("seat-" + row + '-' + number);
        return seats.findById(id).orElseGet(() -> seats.save(new Seat(id, auditorium, row, number, category)));
    }
    private void createScreening(LocalDate date, Movie movie, Auditorium auditorium, List<Seat> demoSeats,
            PricingPlan plan, RefundPolicy policy) {
        UUID screeningId = id("screening-" + date);
        if (screenings.existsById(screeningId)) return;
        var start = date.atTime(LocalTime.of(18, 30)).atZone(ZONE).toInstant();
        Screening screening = new Screening(screeningId, movie.getId(), auditorium.getId(), plan.getId(), policy.getId(), start, start.plusSeconds(7200));
        boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        BigDecimal adjustment = weekend ? plan.getWeekendAdjustment() : BigDecimal.ZERO;
        screening.addPrice(id("price-regular-" + date), SeatCategory.REGULAR, plan.getRegularPrice().add(adjustment));
        screening.addPrice(id("price-premium-" + date), SeatCategory.PREMIUM, plan.getPremiumPrice().add(adjustment));
        demoSeats.forEach(seat -> screening.addSeat(id("screening-seat-" + date + '-' + seat.getId()), seat.getId()));
        screenings.save(screening);
    }
    private static UUID id(String value) { return UUID.nameUUIDFromBytes(("movie-tickets-demo:" + value).getBytes(StandardCharsets.UTF_8)); }
}
