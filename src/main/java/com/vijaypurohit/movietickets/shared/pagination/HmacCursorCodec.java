package com.vijaypurohit.movietickets.shared.pagination;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacCursorCodec implements CursorCodec {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String VERSION = "v1";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec signingKey;

    public HmacCursorCodec(String signingKey) {
        Objects.requireNonNull(signingKey, "signingKey is required");
        byte[] keyBytes = signingKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("Cursor signing key must contain at least 32 UTF-8 bytes.");
        }
        this.signingKey = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }

    @Override
    public String encode(PageCursor cursor) {
        String payload = VERSION + '|' + cursor.resource() + '|' + cursor.sortValue() + '|' + cursor.id();
        String encodedPayload = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String encodedSignature = ENCODER.encodeToString(sign(encodedPayload));
        return encodedPayload + '.' + encodedSignature;
    }

    @Override
    public PageCursor decode(String encodedCursor, CursorResource expectedResource) {
        try {
            Objects.requireNonNull(expectedResource, "expectedResource is required");
            if (encodedCursor == null || encodedCursor.isBlank()) {
                throw new InvalidCursorException();
            }
            String[] parts = encodedCursor.split("\\.", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new InvalidCursorException();
            }

            byte[] actualSignature = DECODER.decode(parts[1]);
            byte[] expectedSignature = sign(parts[0]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                throw new InvalidCursorException();
            }

            String payload = new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            String[] values = payload.split("\\|", -1);
            if (values.length != 4 || !VERSION.equals(values[0])) {
                throw new InvalidCursorException();
            }
            CursorResource resource = CursorResource.valueOf(values[1]);
            if (resource != expectedResource) {
                throw new InvalidCursorException();
            }
            return new PageCursor(resource, Instant.parse(values[2]), UUID.fromString(values[3]));
        } catch (InvalidCursorException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new InvalidCursorException();
        }
    }

    private byte[] sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            return mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.", exception);
        }
    }
}
