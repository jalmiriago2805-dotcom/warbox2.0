package com.warbox.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String VALIDATION_MESSAGE = "Verifique os campos informados.";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorBody> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiErrorBody.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorBody> handleInvalid(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(),
                    error.getDefaultMessage() == null ? "Valor inválido." : error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ApiErrorBody(VALIDATION_ERROR, VALIDATION_MESSAGE, fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorBody> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorBody.of(VALIDATION_ERROR, "Não foi possível ler os dados enviados."));
    }
}
