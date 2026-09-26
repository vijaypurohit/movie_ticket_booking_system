package com.vijaypurohit.movietickets.shared.request;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestIdContext {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String ATTRIBUTE_NAME = RequestIdContext.class.getName() + ".requestId";
    public static final String MDC_KEY = "requestId";

    private RequestIdContext() {
    }

    public static String from(HttpServletRequest request) {
        Object requestId = request.getAttribute(ATTRIBUTE_NAME);
        return requestId instanceof String value ? value : null;
    }
}
