package com.vijaypurohit.movietickets.shared.error;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String problemType, String title, String code, String detail) {
        super(HttpStatus.NOT_FOUND, problemType, title, code, detail);
    }
}
