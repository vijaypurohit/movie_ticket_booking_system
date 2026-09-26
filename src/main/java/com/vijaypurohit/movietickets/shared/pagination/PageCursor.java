package com.vijaypurohit.movietickets.shared.pagination;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PageCursor(CursorResource resource, Instant sortValue, UUID id) {

    public PageCursor {
        Objects.requireNonNull(resource, "resource is required");
        Objects.requireNonNull(sortValue, "sortValue is required");
        Objects.requireNonNull(id, "id is required");
    }
}
