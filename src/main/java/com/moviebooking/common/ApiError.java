package com.moviebooking.common;

import java.time.Instant;
import java.util.Map;

/** Uniform error body returned by every failing endpoint. */
public record ApiError(
        int status,
        String code,
        String message,
        Instant timestamp,
        String path,
        Map<String, String> fieldErrors
) {
    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(status, code, message, Instant.now(), path, null);
    }
}
