package com.vijaypurohit.movietickets.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vijaypurohit.movietickets.catalog.model.City;
import com.vijaypurohit.movietickets.catalog.persistence.CityRepository;
import com.vijaypurohit.movietickets.identity.model.Role;
import com.vijaypurohit.movietickets.identity.model.UserAccount;
import com.vijaypurohit.movietickets.identity.persistence.UserAccountRepository;
import com.vijaypurohit.movietickets.support.ApiIntegrationTest;

class DatabaseContractIT extends ApiIntegrationTest {
    @Autowired private CityRepository cities;
    @Autowired private UserAccountRepository users;
    @Autowired private PasswordEncoder passwords;

    @Test
    void migrationsApplyAndHibernateValidatesTheCompleteSchema() {
        Integer migrations = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);
        List<String> constraints = jdbc.queryForList("""
                select constraint_name from information_schema.table_constraints
                where table_schema = 'public' and constraint_name in
                    ('uq_reservation_customer_key', 'uq_booking_customer_key',
                     'uq_refund_payment_reason', 'outbox_event_business_key_key')
                order by constraint_name
                """, String.class);

        assertThat(migrations).isEqualTo(8);
        assertThat(constraints).contains("uq_reservation_customer_key", "uq_booking_customer_key",
                "uq_refund_payment_reason", "outbox_event_business_key_key");
    }

    @Test
    void normalizedEmailIsUniqueAndSeededPasswordsAreBcryptOnly() {
        UserAccount seeded = users.findByEmail(CUSTOMER_ONE_EMAIL).orElseThrow();
        assertThat(seeded.getPasswordHash()).startsWith("$2");
        assertThat(seeded.getPasswordHash()).doesNotContain(CUSTOMER_PASSWORD);
        assertThat(passwords.matches(CUSTOMER_PASSWORD, seeded.getPasswordHash())).isTrue();

        UserAccount duplicate = new UserAccount(UUID.randomUUID(), " CUSTOMER1@MOVIETICKETS.LOCAL ",
                passwords.encode("different"), Role.CUSTOMER);
        assertThatThrownBy(() -> users.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditedEntitiesUseInjectedUtcClockAndIncrementOptimisticVersion() {
        City city = cities.saveAndFlush(new City(UUID.randomUUID(), "Pune", "India", "Asia/Kolkata"));
        assertThat(city.getCreatedAt()).isEqualTo(INITIAL_TIME);
        assertThat(city.getUpdatedAt()).isEqualTo(INITIAL_TIME);
        assertThat(city.getVersion()).isZero();

        clock.advance(java.time.Duration.ofSeconds(1));
        city.update("Pune City", "India", "Asia/Kolkata");
        City updated = cities.saveAndFlush(city);
        assertThat(updated.getVersion()).isEqualTo(1);
        assertThat(updated.getUpdatedAt()).isEqualTo(INITIAL_TIME.plusSeconds(1));
    }
}
