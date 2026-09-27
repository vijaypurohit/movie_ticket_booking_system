package com.vijaypurohit.movietickets.notification.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vijaypurohit.movietickets.notification.NotificationGateway;

@Component
@ConditionalOnProperty(prefix = "app.notification", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxWorker {
    private final OutboxService outbox; private final NotificationGateway notifications;
    public OutboxWorker(OutboxService outbox, NotificationGateway notifications) { this.outbox=outbox; this.notifications=notifications; }
    @Scheduled(fixedDelayString = "${app.notification.worker-delay:PT5S}")
    public void deliver() {
        for (var work : outbox.claimReady()) {
            outbox.complete(work.id(), notifications.deliver(work.id(), work.eventType(), work.payload()));
        }
    }
}
