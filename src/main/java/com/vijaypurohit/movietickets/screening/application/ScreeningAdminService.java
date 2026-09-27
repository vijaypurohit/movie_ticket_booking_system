package com.vijaypurohit.movietickets.screening.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import com.vijaypurohit.movietickets.screening.web.ScreeningRequests.CreateScreeningRequest;
import com.vijaypurohit.movietickets.screening.web.ScreeningResponses.ScreeningPriceResponse;
import com.vijaypurohit.movietickets.screening.web.ScreeningResponses.ScreeningResponse;
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

    public ScreeningAdminService(ScreeningRepository screenings, CatalogLookupService catalog,
            PricingLookupService pricing, PricingCalculator calculator, IdGenerator ids, Clock clock,
            ObjectProvider<ScreeningCancellationService> cancellations) {
        this.screenings = screenings; this.catalog = catalog; this.pricing = pricing;
        this.calculator = calculator; this.ids = ids; this.clock = clock; this.cancellations = cancellations;
    }

    @Transactional
    public ScreeningResponse create(CreateScreeningRequest request) {
        validateInterval(request);
        var auditorium = catalog.lockActiveAuditorium(request.auditoriumId());
        if (auditorium.seats().isEmpty()) throw violation("EMPTY_SEAT_LAYOUT", "The auditorium has no active seats.");
        catalog.requireActiveMovie(request.movieId());
        var plan = pricing.requireActivePricingPlan(request.pricingPlanId());
        pricing.requireActiveRefundPolicy(request.refundPolicyId());
        if (screenings.existsOverlap(request.auditoriumId(), request.startTime(), request.endTime(), ScreeningStatus.ACTIVE)) {
            throw new ConflictException("screening-overlap", "Screening overlap", "SCREENING_OVERLAP", "The auditorium already has a screening in this time range.");
        }
        Screening screening = new Screening(ids.nextId(), request.movieId(), request.auditoriumId(),
                request.pricingPlanId(), request.refundPolicyId(), request.startTime(), request.endTime());
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
    public void cancel(UUID id) {
        markCancelled(id);
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
        if (!request.startTime().isAfter(clock.instant())) throw violation("SCREENING_IN_PAST", "The screening must start in the future.");
        if (!request.endTime().isAfter(request.startTime())) throw violation("INVALID_SCREENING_INTERVAL", "endTime must be after startTime.");
    }
    private BigDecimal price(BigDecimal base, BigDecimal adjustment, CreateScreeningRequest request,
            CatalogLookupService.AuditoriumSnapshot auditorium) {
        return calculator.calculate(base, adjustment, request.startTime(), auditorium.timeZone());
    }
    private Screening require(UUID id) { return screenings.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("not-found", "Screening not found", "RESOURCE_NOT_FOUND", "The requested resource was not found.")); }
    private BusinessRuleViolationException violation(String code, String detail) { return new BusinessRuleViolationException("screening-rule", "Screening rule violation", code, detail); }
    private ScreeningResponse response(Screening screening) {
        List<ScreeningPriceResponse> prices = screening.getPrices().stream()
                .sorted(Comparator.comparing(price -> price.getSeatCategory().name()))
                .map(price -> new ScreeningPriceResponse(price.getSeatCategory(), price.getAmount(), price.getCurrency())).toList();
        return new ScreeningResponse(screening.getId(), screening.getMovieId(), screening.getAuditoriumId(),
                screening.getPricingPlanId(), screening.getRefundPolicyId(), screening.getStartTime(), screening.getEndTime(),
                screening.getStatus(), prices, screening.getSeats().size(), screening.getCreatedAt(), screening.getUpdatedAt(), screening.getVersion());
    }
}
