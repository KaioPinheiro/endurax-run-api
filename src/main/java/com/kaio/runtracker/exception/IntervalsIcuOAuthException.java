package com.kaio.runtracker.exception;

import org.springframework.http.HttpStatus;

public class IntervalsIcuOAuthException extends RuntimeException {
    private final HttpStatus status;

    public IntervalsIcuOAuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public IntervalsIcuOAuthException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
