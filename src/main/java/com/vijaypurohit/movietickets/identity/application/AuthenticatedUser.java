package com.vijaypurohit.movietickets.identity.application;

import java.util.UUID;

import com.vijaypurohit.movietickets.identity.model.Role;

public record AuthenticatedUser(UUID id, String email, Role role) {
}
