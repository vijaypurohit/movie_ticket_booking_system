package com.vijaypurohit.movietickets.shared.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PageLimitsTest {
    @Test void appliesBoundsAndDefaults() {
        assertThat(PageLimits.resolve(null)).isEqualTo(20);
        assertThat(PageLimits.resolve(1)).isEqualTo(1);
        assertThat(PageLimits.resolve(100)).isEqualTo(100);
        assertThatThrownBy(() -> PageLimits.resolve(0)).isInstanceOf(InvalidPageRequestException.class);
        assertThatThrownBy(() -> PageLimits.resolve(101)).isInstanceOf(InvalidPageRequestException.class);
        assertThat(PageLimits.resolvePage(null)).isZero();
        assertThatThrownBy(() -> PageLimits.resolvePage(-1)).isInstanceOf(InvalidPageRequestException.class);
    }
}
