package com.moviebooking.common;

import org.springframework.http.HttpStatus;

/**
 * Base class for all business exceptions. Each carries an HTTP status and a stable error code
 * so the API returns a consistent error body (see {@link GlobalExceptionHandler}).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    // ---- Common subclasses -------------------------------------------------

    public static class NotFound extends ApiException {
        public NotFound(String entity, Object id) {
            super(HttpStatus.NOT_FOUND, "NOT_FOUND", entity + " not found: " + id);
        }
    }

    public static class BadRequest extends ApiException {
        public BadRequest(String message) {
            super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
        }
    }

    public static class Forbidden extends ApiException {
        public Forbidden(String message) {
            super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
        }
    }

    public static class Conflict extends ApiException {
        public Conflict(String code, String message) {
            super(HttpStatus.CONFLICT, code, message);
        }
    }

    public static class SeatUnavailable extends Conflict {
        public SeatUnavailable(String message) {
            super("SEAT_UNAVAILABLE", message);
        }
    }

    public static class HoldExpired extends Conflict {
        public HoldExpired(String message) {
            super("HOLD_EXPIRED", message);
        }
    }

    public static class InvalidState extends Conflict {
        public InvalidState(String message) {
            super("INVALID_STATE", message);
        }
    }
}
