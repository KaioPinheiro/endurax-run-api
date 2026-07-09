package com.kaio.runtracker.service;

import org.springframework.http.HttpStatus;

public class GerarTreinoIAException extends RuntimeException {

    private final HttpStatus status;

    public GerarTreinoIAException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public GerarTreinoIAException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
