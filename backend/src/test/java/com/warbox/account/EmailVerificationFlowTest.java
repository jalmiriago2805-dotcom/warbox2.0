package com.warbox.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.warbox.TestcontainersConfiguration;
import com.warbox.mail.RecordingEmailSender;
import com.warbox.mail.TestMailConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
class EmailVerificationFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingEmailSender emails;

    private MockHttpSession session;
    private String signupToken;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("TRUNCATE TABLE creator_profiles, account_tokens, users");
        emails.clear();
        session = (MockHttpSession) mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Bia","email":"bia@example.com","password":"senha-forte-123","birthDate":"1995-03-10","acceptedTerms":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getRequest().getSession(false);
        signupToken = RecordingEmailSender.extractToken(emails.last());
    }

    private ResultActions verify(String token) throws Exception {
        return mvc.perform(post("/api/auth/verify-email").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(token)));
    }

    private void backdateVerificationTokens(int seconds) {
        jdbc.update("UPDATE account_tokens SET created_at = created_at - make_interval(secs => ?)"
                + " WHERE purpose = 'EMAIL_VERIFICATION'", seconds);
    }

    @Test
    void tokenFromEmailVerifiesAndIsSingleUse() throws Exception {
        verify(signupToken).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
        assertThat(jdbc.queryForObject("SELECT email_verified_at FROM users", Object.class)).isNotNull();

        verify(signupToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        jdbc.update("UPDATE account_tokens SET expires_at = now() - interval '1 minute'");

        verify(signupToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
        assertThat(jdbc.queryForObject("SELECT email_verified_at FROM users", Object.class)).isNull();
    }

    @Test
    void unknownOrBlankTokenIsRejected() throws Exception {
        verify("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
        verify("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
        mvc.perform(post("/api/auth/verify-email").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
    }

    @Test
    void verificationWorksWithoutSession() throws Exception {
        // verify() never sends the session.
        verify(signupToken).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void resendRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/auth/verify-email/resend").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void resendRightAfterSignupIsRateLimited() throws Exception {
        mvc.perform(post("/api/auth/verify-email/resend").with(csrf()).session(session))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        assertThat(emails.sent()).hasSize(1);
    }

    @Test
    void resendAfterCooldownSendsNewEmailAndInvalidatesOldToken() throws Exception {
        backdateVerificationTokens(61);

        mvc.perform(post("/api/auth/verify-email/resend").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        assertThat(emails.sent()).hasSize(2);
        RecordingEmailSender.SentEmail resent = emails.last();
        assertThat(resent.to()).isEqualTo("bia@example.com");
        assertThat(resent.body()).contains("http://localhost:5173/verificar-email?token=");
        String newToken = RecordingEmailSender.extractToken(resent);
        assertThat(newToken).isNotEqualTo(signupToken);

        verify(signupToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
        verify(newToken).andExpect(status().isNoContent());
    }

    @Test
    void resendWhenAlreadyVerifiedIsConflict() throws Exception {
        verify(signupToken).andExpect(status().isNoContent());
        backdateVerificationTokens(61);

        mvc.perform(post("/api/auth/verify-email/resend").with(csrf()).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_VERIFIED"));
    }
}
