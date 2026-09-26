package com.vijaypurohit.movietickets.shared.error;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

    public BadRequestException(String problemType, String title, String code, String detail) {
        super(HttpStatus.BAD_REQUEST, problemType, title, code, detail);
    }
}
