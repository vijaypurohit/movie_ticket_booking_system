package com.vijaypurohit.movietickets.shared.pagination;

import java.util.List;

public record CursorPageResponse<T>(List<T> items, String nextCursor) {
    public CursorPageResponse {
        items = List.copyOf(items);
    }
}
