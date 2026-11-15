package com.warbox.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
class PasswordResetFlowTest {

    private static final String OLD_PASSWORD = "senha-antiga-123";
    private static final String NEW_PASSWORD = "senha-nova-456";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingEmailSender emails;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("TRUNCATE TABLE creator_profiles, account_tokens, users");
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Carla","email":"carla@example.com","password":"%s","birthDate":"1988-07-01","acceptedTerms":true}
                        """.formatted(OLD_PASSWORD)))
                .andExpect(status().isCreated());
        emails.clear();
    }

    private ResultActions requestReset(String email) throws Exception {
        return mvc.perform(post("/api/auth/password-reset/request").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions confirm(String token, String newPassword) throws Exception {
        return mvc.perform(post("/api/auth/password-reset/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword)));
    }

    private int loginStatus(String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"carla@example.com\",\"password\":\"%s\"}".formatted(password)))
                .andReturn().getResponse().getStatus();
    }

    private String requestTokenFromEmail() throws Exception {
        requestReset("carla@example.com").andExpect(status().isNoContent());
        return RecordingEmailSender.extractToken(emails.last());
    }

    private void backdateResetTokens(int seconds) {
        jdbc.update("UPDATE account_tokens SET created_at = created_at - make_interval(secs => ?)"
                + " WHERE purpose = 'PASSWORD_RESET'", seconds);
    }

    @Test
    void requestForExistingEmailSendsResetLink() throws Exception {
        requestReset("Carla@Example.com").andExpect(status().isNoContent());

        assertThat(emails.sent()).hasSize(1);
        RecordingEmailSender.SentEmail email = emails.last();
        assertThat(email.to()).isEqualTo("carla@example.com");
        assertThat(email.subject()).isEqualTo("Redefinição de senha do Warbox");
        assertThat(email.body()).contains("http://localhost:5173/redefinir-senha?token=").contains("1 hora");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM account_tokens WHERE purpose = 'PASSWORD_RESET'",
                String.class)).matches("[0-9a-f]{64}");
    }

    @Test
    void requestForUnknownEmailIsSilent() throws Exception {
        requestReset("ninguem@example.com").andExpect(status().isNoContent());

        assertThat(emails.sent()).isEmpty();
    }

    @Test
    void malformedEmailIsValidationError() throws Exception {
        requestReset("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.email").isString());
    }

    @Test
    void secondRequestWithinCooldownSendsNothing() throws Exception {
        requestReset("carla@example.com").andExpect(status().isNoContent());
        requestReset("carla@example.com").andExpect(status().isNoContent());

        assertThat(emails.sent()).hasSize(1);
    }

    @Test
    void confirmChangesPasswordAndVerifiesEmail() throws Exception {
        String token = requestTokenFromEmail();
        assertThat(jdbc.queryForObject("SELECT email_verified_at FROM users", Object.class)).isNull();

        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(loginStatus(NEW_PASSWORD)).isEqualTo(200);
        assertThat(loginStatus(OLD_PASSWORD)).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT email_verified_at FROM users", Object.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users", String.class)).startsWith("{bcrypt}");
    }

    @Test
    void confirmDoesNotLogIn() throws Exception {
        String token = requestTokenFromEmail();

        var result = confirm(token, NEW_PASSWORD).andExpect(status().isNoContent()).andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void reusedTokenIsRejected() throws Exception {
        String token = requestTokenFromEmail();
        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

        confirm(token, "outra-senha-789")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
        assertThat(loginStatus(NEW_PASSWORD)).isEqualTo(200);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = requestTokenFromEmail();
        jdbc.update("UPDATE account_tokens SET expires_at = now() - interval '1 second'"
                + " WHERE purpose = 'PASSWORD_RESET'");

        confirm(token, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
        assertThat(loginStatus(OLD_PASSWORD)).isEqualTo(200);
    }

    @Test
    void newRequestAfterCooldownInvalidatesPreviousToken() throws Exception {
        String first = requestTokenFromEmail();
        backdateResetTokens(61);
        String second = requestTokenFromEmail();

        assertThat(emails.sent()).hasSize(2);
        assertThat(second).isNotEqualTo(first);
        confirm(first, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
        confirm(second, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void verificationTokenCannotBeUsedForReset() throws Exception {
        jdbc.execute("TRUNCATE TABLE creator_profiles, account_tokens, users");
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Carla","email":"carla@example.com","password":"%s","birthDate":"1988-07-01","acceptedTerms":true}
                        """.formatted(OLD_PASSWORD)))
                .andExpect(status().isCreated());
        String verificationToken = RecordingEmailSender.extractToken(emails.last());

        confirm(verificationToken, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
    }

    @Test
    void shortNewPasswordIsValidationError() throws Exception {
        String token = requestTokenFromEmail();

        confirm(token, "curta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.newPassword").isString());

        // The token was not consumed by the rejected request.
        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }
}
