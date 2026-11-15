package com.warbox.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warbox.common.ApiErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

/** 403 with the API error body; CSRF failures get their own code so the SPA can refresh the token and retry. */
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ApiErrorBody body = accessDeniedException instanceof CsrfException
                ? ApiErrorBody.of("CSRF_INVALID", "Sua sessão de segurança expirou. Tente novamente.")
                : ApiErrorBody.of("FORBIDDEN", "Você não tem permissão para fazer isso.");
        JsonAuthenticationEntryPoint.writeError(objectMapper, response, HttpServletResponse.SC_FORBIDDEN, body);
    }
}
