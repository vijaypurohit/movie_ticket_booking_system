package com.vijaypurohit.movietickets.shared.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }
}
