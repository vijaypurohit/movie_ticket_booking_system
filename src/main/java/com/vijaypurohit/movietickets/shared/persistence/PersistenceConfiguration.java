package com.vijaypurohit.movietickets.shared.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
@ConditionalOnProperty(
        prefix = "app.persistence.auditing",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PersistenceConfiguration {

    @Bean
    DateTimeProvider utcDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
