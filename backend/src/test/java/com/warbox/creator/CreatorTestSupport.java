package com.warbox.creator;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.warbox.mail.RecordingEmailSender;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Helpers shared by the creator integration tests. */
final class CreatorTestSupport {

    private CreatorTestSupport() {}

    /** Signs up (which logs in) and optionally verifies the e-mail; returns the session. */
    static MockHttpSession signUp(MockMvc mvc, RecordingEmailSender emails, String email, boolean verified)
            throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Davi","email":"%s","password":"senha-forte-123","birthDate":"1990-01-01","acceptedTerms":true}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getRequest().getSession(false);
        if (verified) {
            mvc.perform(post("/api/auth/verify-email").with(csrf()).session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"%s\"}".formatted(RecordingEmailSender.extractToken(emails.last()))))
                    .andExpect(status().is2xxSuccessful());
        }
        return session;
    }

    static String body(String handle, String displayName, String bio) {
        return "{\"handle\":%s,\"displayName\":%s,\"bio\":%s}".formatted(json(handle), json(displayName), json(bio));
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value.replace("\"", "\\\"") + "\"";
    }

    static ResultActions activate(MockMvc mvc, MockHttpSession session, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/creators/me").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(session == null ? request : request.session(session));
    }

    static ResultActions update(MockMvc mvc, MockHttpSession session, String body) throws Exception {
        MockHttpServletRequestBuilder request = put("/api/creators/me").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(session == null ? request : request.session(session));
    }
}
