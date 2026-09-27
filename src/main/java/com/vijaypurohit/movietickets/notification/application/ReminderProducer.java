package com.vijaypurohit.movietickets.notification.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.vijaypurohit.movietickets.booking.persistence.BookingRepository;
import com.vijaypurohit.movietickets.notification.persistence.OutboxEventRepository;
import com.vijaypurohit.movietickets.shared.identifier.IdGenerator;

@Component
@ConditionalOnProperty(prefix = "app.notification", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReminderProducer {
    private final BookingRepository bookings; private final OutboxEventRepository events;
    private final IdGenerator ids; private final Clock clock; private final Duration lead; private final Duration window;
    public ReminderProducer(BookingRepository bookings, OutboxEventRepository events, IdGenerator ids, Clock clock,
            @Value("${app.notification.reminder-lead:PT24H}") Duration lead,
            @Value("${app.notification.reminder-window:PT5M}") Duration window) {
        this.bookings=bookings; this.events=events; this.ids=ids; this.clock=clock; this.lead=lead; this.window=window;
    }

    @Scheduled(fixedDelayString = "${app.notification.reminder-scan-delay:PT1M}")
    @Transactional
    public int produce() {
        Instant now = clock.instant(); int inserted = 0;
        for (var candidate : bookings.findReminderCandidates(now.plus(lead), now.plus(lead).plus(window))) {
            String key = "booking-reminder:" + candidate.getBookingId() + ":" + lead;
            String payload = "{\"bookingId\":\"" + candidate.getBookingId() + "\",\"screeningStart\":\""
                    + candidate.getScreeningStart() + "\"}";
            inserted += events.insertReminder(ids.nextId(), candidate.getBookingId(), key, payload, now);
        }
        return inserted;
    }
}
