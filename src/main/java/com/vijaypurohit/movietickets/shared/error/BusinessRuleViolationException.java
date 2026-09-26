package com.vijaypurohit.movietickets.shared.error;

import org.springframework.http.HttpStatus;

public class BusinessRuleViolationException extends ApiException {

    public BusinessRuleViolationException(String problemType, String title, String code, String detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, problemType, title, code, detail);
    }
}
