package com.vijaypurohit.movietickets.identity.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.vijaypurohit.movietickets.identity.model.EmailAddress;
import com.vijaypurohit.movietickets.identity.model.Role;
import com.vijaypurohit.movietickets.identity.model.UserAccount;
import com.vijaypurohit.movietickets.identity.persistence.UserAccountRepository;
import com.vijaypurohit.movietickets.shared.identifier.IdGenerator;

@Component
@ConditionalOnProperty(prefix = "app.demo", name = "enabled", havingValue = "true")
public class DemoIdentitySeeder implements ApplicationRunner {

    public static final String ADMIN_EMAIL = "admin@movietickets.local";
    public static final String ADMIN_PASSWORD = "Admin@123";
    public static final String CUSTOMER_ONE_EMAIL = "customer1@movietickets.local";
    public static final String CUSTOMER_TWO_EMAIL = "customer2@movietickets.local";
    public static final String CUSTOMER_PASSWORD = "Customer@123";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdGenerator idGenerator;

    public DemoIdentitySeeder(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            IdGenerator idGenerator) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        createIfMissing(ADMIN_EMAIL, ADMIN_PASSWORD, Role.ADMIN);
        createIfMissing(CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, Role.CUSTOMER);
        createIfMissing(CUSTOMER_TWO_EMAIL, CUSTOMER_PASSWORD, Role.CUSTOMER);
    }

    private void createIfMissing(String email, String password, Role role) {
        String normalizedEmail = EmailAddress.normalize(email);
        if (!userAccountRepository.existsByEmail(normalizedEmail)) {
            userAccountRepository.save(new UserAccount(
                    idGenerator.nextId(),
                    normalizedEmail,
                    passwordEncoder.encode(password),
                    role));
        }
    }
}
