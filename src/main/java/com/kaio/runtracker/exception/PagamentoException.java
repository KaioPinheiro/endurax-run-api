package com.kaio.runtracker.exception;

import org.springframework.http.HttpStatus;

public class PagamentoException extends RuntimeException {
    private final HttpStatus status;

    public PagamentoException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public PagamentoException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
