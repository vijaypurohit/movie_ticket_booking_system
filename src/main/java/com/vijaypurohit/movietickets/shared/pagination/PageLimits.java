package com.vijaypurohit.movietickets.shared.pagination;

public final class PageLimits {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT = 20;
    public static final int MAXIMUM = 100;

    private PageLimits() {
    }

    public static int resolve(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT;
        }
        if (requestedLimit < 1 || requestedLimit > MAXIMUM) {
            throw new InvalidPageRequestException("Page size must be between 1 and " + MAXIMUM + '.');
        }
        return requestedLimit;
    }

    public static int resolvePage(Integer requestedPage) {
        if (requestedPage == null) {
            return DEFAULT_PAGE;
        }
        if (requestedPage < 0) {
            throw new InvalidPageRequestException("Page index must be zero or greater.");
        }
        return requestedPage;
    }
}
