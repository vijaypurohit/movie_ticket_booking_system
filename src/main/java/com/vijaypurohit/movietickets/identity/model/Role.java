package com.vijaypurohit.movietickets.identity.model;

public enum Role {
    ADMIN,
    CUSTOMER;

    public String authority() {
        return "ROLE_" + name();
    }
}
