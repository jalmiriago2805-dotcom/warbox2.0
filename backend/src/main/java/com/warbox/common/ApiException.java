package com.warbox.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Business error rendered as {@link ApiErrorBody} by {@link GlobalExceptionHandler}.
 * {@code code} is a stable machine-readable identifier; {@code message} is user-facing pt-BR text.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fields;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = fields == null ? Map.of() : fields;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
