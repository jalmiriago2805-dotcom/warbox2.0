package com.warbox.common;

import org.springframework.http.HttpStatus;

/**
 * Business error rendered as {@link ApiErrorBody} by {@link GlobalExceptionHandler}.
 * {@code code} is a stable machine-readable identifier; {@code message} is user-facing pt-BR text.
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
}
