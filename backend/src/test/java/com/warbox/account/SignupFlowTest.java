package com.warbox.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.warbox.TestcontainersConfiguration;
import com.warbox.common.TimeConfiguration;
import com.warbox.mail.RecordingEmailSender;
import com.warbox.mail.TestMailConfiguration;
import java.time.LocalDate;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
class SignupFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingEmailSender emails;

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE TABLE account_tokens, users");
        emails.clear();
    }

    private static LocalDate today() {
        return LocalDate.now(TimeConfiguration.ZONE);
    }

    private static String signupJson(String name, String email, String password, String birthDate, boolean terms) {
        return """
                {"name":"%s","email":"%s","password":"%s","birthDate":"%s","acceptedTerms":%s}
                """.formatted(name, email, password, birthDate, terms);
    }

    private static String validSignup(String email) {
        return signupJson("Maria Silva", email, "senha-forte-123", "1990-05-20", true);
    }

    private int userCount() {
        return jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);
    }

    @Test
    void signupCreatesAccountAndLogsIn() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(validSignup("maria@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value("maria@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("maria@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(validSignup("maria@example.com")))
                .andExpect(status().isCreated());

        String hash = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
        assertThat(hash).startsWith("{bcrypt}").isNotEqualTo("senha-forte-123").doesNotContain("senha-forte-123");
    }

    @Test
    void termsTimestampAndLowercaseEmailAreStored() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(validSignup("  Maria.Silva@Example.COM ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("maria.silva@example.com"));

        Map<String, Object> row = jdbc.queryForMap("SELECT email, terms_accepted_at, email_verified_at FROM users");
        assertThat(row.get("email")).isEqualTo("maria.silva@example.com");
        assertThat(row.get("terms_accepted_at")).isNotNull();
        assertThat(row.get("email_verified_at")).isNull();
    }

    @Test
    void verificationEmailIsSentAndOnlyTheHashIsStored() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(validSignup("maria@example.com")))
                .andExpect(status().isCreated());

        assertThat(emails.sent()).hasSize(1);
        RecordingEmailSender.SentEmail email = emails.last();
        assertThat(email.to()).isEqualTo("maria@example.com");
        assertThat(email.subject()).isEqualTo("Confirme seu e-mail no Warbox");
        assertThat(email.body()).contains("http://localhost:5173/verificar-email?token=").contains("24 horas");

        String rawToken = RecordingEmailSender.extractToken(email);
        assertThat(rawToken).hasSize(43);
        String storedHash = jdbc.queryForObject("SELECT token_hash FROM account_tokens", String.class);
        assertThat(storedHash).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(AccountTokenService.sha256Hex(rawToken));
        assertThat(jdbc.queryForObject("SELECT purpose FROM account_tokens", String.class))
                .isEqualTo("EMAIL_VERIFICATION");
    }

    @Test
    void turningEighteenTomorrowIsUnderage() throws Exception {
        String birthDate = today().minusYears(18).plusDays(1).toString();

        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("Joao", "joao@example.com", "senha-forte-123", birthDate, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNDERAGE"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.fields").isEmpty());

        assertThat(userCount()).isZero();
        assertThat(emails.sent()).isEmpty();
    }

    @Test
    void turningEighteenTodayIsAccepted() throws Exception {
        String birthDate = today().minusYears(18).toString();

        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("Joao", "joao@example.com", "senha-forte-123", birthDate, true)))
                .andExpect(status().isCreated());

        assertThat(userCount()).isEqualTo(1);
    }

    @Test
    void termsNotAcceptedIsRejected() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("Joao", "joao@example.com", "senha-forte-123", "1990-01-01", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));

        assertThat(userCount()).isZero();
    }

    @Test
    void missingTermsFieldIsTreatedAsNotAccepted() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Joao","email":"joao@example.com","password":"senha-forte-123","birthDate":"1990-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));
    }

    @Test
    void termsAreCheckedBeforeAge() throws Exception {
        String birthDate = today().minusYears(10).toString();

        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("Joao", "joao@example.com", "senha-forte-123", birthDate, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));
    }

    @Test
    void duplicateEmailWithDifferentCaseIsTaken() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(validSignup("maria@example.com")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(validSignup("MARIA@Example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));

        assertThat(userCount()).isEqualTo(1);
        assertThat(emails.sent()).hasSize(1);
    }

    @Test
    void invalidFieldsReturnValidationErrorWithFields() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("  ", "not-an-email", "short", "1899-12-31", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.fields.name").isString())
                .andExpect(jsonPath("$.fields.email").isString())
                .andExpect(jsonPath("$.fields.password").isString())
                .andExpect(jsonPath("$.fields.birthDate").isString());

        assertThat(userCount()).isZero();
    }

    @Test
    void validationComesBeforeTermsAndAge() throws Exception {
        String birthDate = today().minusYears(10).toString();

        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("Joao", "bad", "senha-forte-123", birthDate, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.email").isString());
    }

    @Test
    void futureBirthDateAndMissingFieldsAreValidationErrors() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("Joao", "joao@example.com", "senha-forte-123",
                                today().plusDays(1).toString(), true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.birthDate").isString());

        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptedTerms\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").isString())
                .andExpect(jsonPath("$.fields.email").isString())
                .andExpect(jsonPath("$.fields.password").isString())
                .andExpect(jsonPath("$.fields.birthDate").isString());
    }

    @Test
    void passwordOverSeventyTwoUtf8BytesIsRejected() throws Exception {
        // 40 characters, 80 bytes in UTF-8.
        String password = "é".repeat(40);

        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("Joao", "joao@example.com", password, "1990-01-01", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.password").isString());
    }

    @Test
    void malformedJsonOrInvalidDateIsValidationErrorWithEmptyFields() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields").isEmpty());

        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("Joao", "joao@example.com", "senha-forte-123", "1990-02-31", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields").isEmpty());
    }
}
