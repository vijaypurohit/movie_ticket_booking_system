package com.vijaypurohit.movietickets.identity.model;

import java.util.Locale;

public final class EmailAddress {

    private EmailAddress() {
    }

    public static String normalize(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
