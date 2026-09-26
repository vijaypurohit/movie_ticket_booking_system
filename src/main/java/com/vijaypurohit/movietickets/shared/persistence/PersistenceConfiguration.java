package com.vijaypurohit.movietickets.shared.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;

@Configuration(proxyBeanMethods = false)
public class PersistenceConfiguration {

    @Bean
    DateTimeProvider utcDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
