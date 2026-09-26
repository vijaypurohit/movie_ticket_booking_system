package com.vijaypurohit.movietickets.shared.identifier;

import java.util.UUID;

@FunctionalInterface
public interface IdGenerator {

    UUID nextId();
}
