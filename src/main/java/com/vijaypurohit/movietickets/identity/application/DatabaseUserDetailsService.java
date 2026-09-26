package com.vijaypurohit.movietickets.identity.application;

import org.springframework.security.core.userdetails.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vijaypurohit.movietickets.identity.model.EmailAddress;
import com.vijaypurohit.movietickets.identity.model.UserAccount;
import com.vijaypurohit.movietickets.identity.persistence.UserAccountRepository;

@Service
@ConditionalOnProperty(prefix = "app.identity", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public DatabaseUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount account = userAccountRepository.findByEmail(EmailAddress.normalize(username))
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials."));
        return User.withUsername(account.getEmail())
                .password(account.getPasswordHash())
                .authorities(account.getRole().authority())
                .build();
    }
}
