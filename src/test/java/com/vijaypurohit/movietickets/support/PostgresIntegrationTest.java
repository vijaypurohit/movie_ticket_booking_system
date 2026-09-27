package com.vijaypurohit.movietickets.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.vijaypurohit.movietickets.identity.application.DemoIdentitySeeder;

import java.time.Instant;

@ActiveProfiles("test")
@Import(TestTimeConfiguration.class)
public abstract class PostgresIntegrationTest {
    public static final Instant INITIAL_TIME = Instant.parse("2026-09-27T10:00:00Z");
    private static final String EXTERNAL_URL = System.getProperty("test.database.url");

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (EXTERNAL_URL == null) {
            POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("movie_tickets_test")
                    .withUsername("movie_tickets")
                    .withPassword("movie_tickets");
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES == null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> System.getProperty("test.database.username", "movie_tickets"));
            registry.add("spring.datasource.password", () -> System.getProperty("test.database.password", "movie_tickets"));
        } else {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MutableClock clock;
    @Autowired private DemoIdentitySeeder identities;

    @BeforeEach
    void resetDatabaseAndClock() throws Exception {
        clock.set(INITIAL_TIME);
        jdbc.execute("""
                TRUNCATE TABLE
                    outbox_event, refund, payment, discount_redemption,
                    booking_refund_rule, booking_item, booking,
                    seat_reservation, screening_seat, screening_price, screening,
                    refund_policy_rule, refund_policy, discount_code, pricing_plan,
                    seat, auditorium, theater, movie, city, app_user
                RESTART IDENTITY CASCADE
                """);
        identities.run(new DefaultApplicationArguments(new String[0]));
    }

}
