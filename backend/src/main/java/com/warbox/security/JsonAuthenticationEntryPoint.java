package com.warbox.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warbox.common.ApiErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** 401 with the API error body instead of a redirect or a Basic challenge. */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        writeError(objectMapper, response, HttpServletResponse.SC_UNAUTHORIZED,
                ApiErrorBody.of("UNAUTHENTICATED", "Faça login para continuar."));
    }

    static void writeError(ObjectMapper objectMapper, HttpServletResponse response, int status, ApiErrorBody body)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
