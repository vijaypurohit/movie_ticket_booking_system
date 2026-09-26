package com.vijaypurohit.movietickets.shared.pagination;

import com.vijaypurohit.movietickets.shared.error.BadRequestException;

public class InvalidPageRequestException extends BadRequestException {

    public InvalidPageRequestException(String detail) {
        super("invalid-page", "Invalid page", "INVALID_PAGE", detail);
    }
}
