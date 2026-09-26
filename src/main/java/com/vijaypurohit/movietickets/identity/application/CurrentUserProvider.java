package com.vijaypurohit.movietickets.identity.application;

import java.util.UUID;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.vijaypurohit.movietickets.identity.model.EmailAddress;
import com.vijaypurohit.movietickets.identity.model.Role;
import com.vijaypurohit.movietickets.identity.model.UserAccount;
import com.vijaypurohit.movietickets.identity.persistence.UserAccountRepository;
import com.vijaypurohit.movietickets.shared.error.ResourceNotFoundException;

@Component
@ConditionalOnProperty(prefix = "app.identity", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CurrentUserProvider {

    private final UserAccountRepository userAccountRepository;

    public CurrentUserProvider(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResourceNotFoundException(
                    "authenticated-user-not-found",
                    "Authenticated user not found",
                    "AUTHENTICATED_USER_NOT_FOUND",
                    "The authenticated user is unavailable.");
        }
        UserAccount account = userAccountRepository.findByEmail(EmailAddress.normalize(authentication.getName()))
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "authenticated-user-not-found",
                        "Authenticated user not found",
                        "AUTHENTICATED_USER_NOT_FOUND",
                        "The authenticated user is unavailable."));
        return new AuthenticatedUser(account.getId(), account.getEmail(), account.getRole());
    }

    public UUID requireCustomerId() {
        AuthenticatedUser user = requireCurrentUser();
        if (user.role() != Role.CUSTOMER) {
            throw new ResourceNotFoundException(
                    "customer-not-found",
                    "Customer not found",
                    "CUSTOMER_NOT_FOUND",
                    "The requested customer resource was not found.");
        }
        return user.id();
    }
}
