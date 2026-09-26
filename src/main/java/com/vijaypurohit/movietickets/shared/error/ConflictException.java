package com.vijaypurohit.movietickets.shared.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String problemType, String title, String code, String detail) {
        super(HttpStatus.CONFLICT, problemType, title, code, detail);
    }
}
