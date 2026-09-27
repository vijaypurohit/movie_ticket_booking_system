package com.vijaypurohit.movietickets.shared.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class HmacCursorCodecTest {
    private final HmacCursorCodec codec = new HmacCursorCodec("test-signing-key-with-at-least-32-bytes");

    @Test void roundTripsResourceTimeAndId() {
        var cursor = new PageCursor(CursorResource.BOOKING, Instant.parse("2026-09-27T00:00:00Z"), UUID.randomUUID());
        assertThat(codec.decode(codec.encode(cursor), CursorResource.BOOKING)).isEqualTo(cursor);
    }

    @Test void rejectsTamperingAndWrongResource() {
        var cursor = new PageCursor(CursorResource.BOOKING, Instant.parse("2026-09-27T00:00:00Z"), UUID.randomUUID());
        String encoded = codec.encode(cursor);
        assertThatThrownBy(() -> codec.decode(encoded + "x", CursorResource.BOOKING)).isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> codec.decode(encoded, CursorResource.SCREENING)).isInstanceOf(InvalidCursorException.class);
    }

    @Test void rejectsShortSigningKeys() {
        assertThatThrownBy(() -> new HmacCursorCodec("too-short")).isInstanceOf(IllegalArgumentException.class);
    }
}
