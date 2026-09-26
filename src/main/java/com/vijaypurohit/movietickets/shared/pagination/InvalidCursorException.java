package com.vijaypurohit.movietickets.shared.pagination;

import com.vijaypurohit.movietickets.shared.error.BadRequestException;

public class InvalidCursorException extends BadRequestException {

    public InvalidCursorException() {
        super(
                "invalid-cursor",
                "Invalid cursor",
                "INVALID_CURSOR",
                "The pagination cursor is invalid.");
    }
}
