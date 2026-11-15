package com.warbox.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The single error format of the API: {@code {code, message, fields}}. */
public record ApiErrorBody(String code, String message, Map<String, String> fields) {

    public ApiErrorBody {
        fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public static ApiErrorBody of(String code, String message) {
        return new ApiErrorBody(code, message, Map.of());
    }
}
