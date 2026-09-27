package com.vijaypurohit.movietickets.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestTimeConfiguration {
    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock(PostgresIntegrationTest.INITIAL_TIME);
    }
}
