package com.project.aura.exception;

import org.springframework.http.HttpStatus;

/**
 * Error de negocio con un status HTTP explícito.
 * getMessage() conserva el texto original para no romper a los llamadores.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
