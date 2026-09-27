package com.vijaypurohit.movietickets.screening.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.vijaypurohit.movietickets.booking.application.ScreeningCancellationService;
import com.vijaypurohit.movietickets.catalog.application.CatalogLookupService;
import com.vijaypurohit.movietickets.catalog.model.SeatCategory;
import com.vijaypurohit.movietickets.pricing.application.PricingCalculator;
import com.vijaypurohit.movietickets.pricing.application.PricingLookupService;
import com.vijaypurohit.movietickets.screening.model.Screening;
import com.vijaypurohit.movietickets.screening.model.ScreeningStatus;
import com.vijaypurohit.movietickets.screening.persistence.ScreeningRepository;
import com.vijaypurohit.movietickets.generated.model.CreateScreeningRequest;
import com.vijaypurohit.movietickets.generated.model.ScreeningPriceResponse;
import com.vijaypurohit.movietickets.generated.model.ScreeningResponse;
import com.vijaypurohit.movietickets.shared.error.BusinessRuleViolationException;
import com.vijaypurohit.movietickets.shared.error.ConflictException;
import com.vijaypurohit.movietickets.shared.error.ResourceNotFoundException;
import com.vijaypurohit.movietickets.shared.identifier.IdGenerator;
import com.vijaypurohit.movietickets.shared.pagination.PageLimits;
import com.vijaypurohit.movietickets.shared.pagination.PageResponse;

@Service
@ConditionalOnProperty(prefix = "app.screening", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScreeningAdminService {
    private final ScreeningRepository screenings;
    private final CatalogLookupService catalog;
    private final PricingLookupService pricing;
    private final PricingCalculator calculator;
    private final IdGenerator ids;
    private final Clock clock;
    private final ObjectProvider<ScreeningCancellationService> cancellations;
    /** Self proxy, so the status change really opens its own transaction. */
    private final ScreeningAdminService self;

    public ScreeningAdminService(ScreeningRepository screenings, CatalogLookupService catalog,
            PricingLookupService pricing, PricingCalculator calculator, IdGenerator ids, Clock clock,
            ObjectProvider<ScreeningCancellationService> cancellations,
            @Lazy ScreeningAdminService self) {
        this.screenings = screenings; this.catalog = catalog; this.pricing = pricing;
        this.calculator = calculator; this.ids = ids; this.clock = clock;
        this.cancellations = cancellations; this.self = self;
    }

    @Transactional
    public ScreeningResponse create(CreateScreeningRequest request) {
        validateInterval(request);
        var auditorium = catalog.lockActiveAuditorium(request.getAuditoriumId());
        if (auditorium.seats().isEmpty()) throw violation("EMPTY_SEAT_LAYOUT", "The auditorium has no active seats.");
        catalog.requireActiveMovie(request.getMovieId());
        var plan = pricing.requireActivePricingPlan(request.getPricingPlanId());
        pricing.requireActiveRefundPolicy(request.getRefundPolicyId());
        if (screenings.existsOverlap(request.getAuditoriumId(), request.getStartTime(), request.getEndTime(), ScreeningStatus.ACTIVE)) {
            throw new ConflictException("screening-overlap", "Screening overlap", "SCREENING_OVERLAP", "The auditorium already has a screening in this time range.");
        }
        Screening screening = new Screening(ids.nextId(), request.getMovieId(), request.getAuditoriumId(),
                request.getPricingPlanId(), request.getRefundPolicyId(), request.getStartTime(), request.getEndTime());
        screening.addPrice(ids.nextId(), SeatCategory.REGULAR, price(plan.regularPrice(), plan.weekendAdjustment(), request, auditorium));
        screening.addPrice(ids.nextId(), SeatCategory.PREMIUM, price(plan.premiumPrice(), plan.weekendAdjustment(), request, auditorium));
        auditorium.seats().forEach(seat -> screening.addSeat(ids.nextId(), seat.id()));
        return response(screenings.save(screening));
    }

    @Transactional(readOnly = true)
    public ScreeningResponse get(UUID id) { return response(require(id)); }

    @Transactional(readOnly = true)
    public PageResponse<ScreeningResponse> list(UUID auditoriumId, UUID movieId, Integer page, Integer size) {
        PageRequest request = PageRequest.of(PageLimits.resolvePage(page), PageLimits.resolve(size),
                Sort.by("startTime").ascending().and(Sort.by("id")));
        return PageResponse.from(screenings.findAdminPage(auditoriumId, movieId, request).map(this::response));
    }

    /**
     * Cancels the screening, then settles every booking it already sold. The status change and the
     * settlement are deliberately separate: once the screening is {@code CANCELLED} no new
     * reservation or checkout can succeed, so the bounded per-booking drain that follows cannot
     * race new sales. An interrupted drain is finished by the cancellation sweeper.
     */
    /**
     * Stops sales first, then drains the bookings. The two phases are separate transactions on
     * purpose: the drain runs in bounded batches and may be resumed by the sweeper, so it must
     * not be held open by the status change. The call goes through {@code self} because a plain
     * {@code this.markCancelled(id)} would bypass the transactional proxy and never be flushed.
     */
    public void cancel(UUID id) {
        self.markCancelled(id);
        cancellations.ifAvailable(service -> service.settleBookings(id));
    }

    @Transactional
    public void markCancelled(UUID id) {
        Screening screening = require(id);
        if (!screening.getStartTime().isAfter(clock.instant())) throw violation("SCREENING_STARTED", "A started screening cannot be cancelled.");
        if (screening.getStatus() == ScreeningStatus.CANCELLED) return;
        screening.cancel();
    }

    private void validateInterval(CreateScreeningRequest request) {
        if (!request.getStartTime().isAfter(clock.instant())) throw violation("SCREENING_IN_PAST", "The screening must start in the future.");
        if (!request.getEndTime().isAfter(request.getStartTime())) throw violation("INVALID_SCREENING_INTERVAL", "endTime must be after startTime.");
    }
    private BigDecimal price(BigDecimal base, BigDecimal adjustment, CreateScreeningRequest request,
            CatalogLookupService.AuditoriumSnapshot auditorium) {
        return calculator.calculate(base, adjustment, request.getStartTime(), auditorium.timeZone());
    }
    private Screening require(UUID id) { return screenings.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("not-found", "Screening not found", "RESOURCE_NOT_FOUND", "The requested resource was not found.")); }
    private BusinessRuleViolationException violation(String code, String detail) { return new BusinessRuleViolationException("screening-rule", "Screening rule violation", code, detail); }
    private ScreeningResponse response(Screening screening) {
        List<ScreeningPriceResponse> prices = screening.getPrices().stream()
                .sorted(Comparator.comparing(price -> price.getSeatCategory().name()))
                .map(price -> new ScreeningPriceResponse()
                .seatCategory(price.getSeatCategory())
                .amount(price.getAmount())
                .currency(price.getCurrency())).toList();
        return new ScreeningResponse()
                .id(screening.getId())
                .movieId(screening.getMovieId())
                .auditoriumId(screening.getAuditoriumId())
                .pricingPlanId(screening.getPricingPlanId())
                .refundPolicyId(screening.getRefundPolicyId())
                .startTime(screening.getStartTime())
                .endTime(screening.getEndTime())
                .status(screening.getStatus())
                .prices(prices)
                .inventorySize(screening.getSeats().size())
                .createdAt(screening.getCreatedAt())
                .updatedAt(screening.getUpdatedAt())
                .version(screening.getVersion());
    }
}
