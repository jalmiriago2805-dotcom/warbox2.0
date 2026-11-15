package com.warbox.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
class LoginLogoutFlowTest {

    private static final String PASSWORD = "senha-forte-123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingEmailSender emails;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("TRUNCATE TABLE account_tokens, users");
        emails.clear();
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Ana","email":"ana@example.com","password":"%s","birthDate":"1990-01-01","acceptedTerms":true}
                        """.formatted(PASSWORD)))
                .andExpect(status().isCreated());
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andReturn();
    }

    @Test
    void loginReturnsMeAndEstablishesSession() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@example.com"));
    }

    @Test
    void loginChangesTheSessionId() throws Exception {
        MockHttpSession existing = new MockHttpSession();
        String before = existing.getId();

        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf()).session(existing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(before);
    }

    @Test
    void wrongPasswordAndUnknownEmailGetIdenticalResponses() throws Exception {
        MvcResult wrongPassword = login("ana@example.com", "senha-errada-999");
        MvcResult unknownEmail = login("ninguem@example.com", PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        String body = wrongPassword.getResponse().getContentAsString();
        assertThat(body).contains("\"code\":\"INVALID_CREDENTIALS\"").contains("\"fields\":{}");
        assertThat(unknownEmail.getResponse().getContentAsString()).isEqualTo(body);
        assertThat(wrongPassword.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        assertThat(wrongPassword.getRequest().getSession(false)).isNull();
    }

    @Test
    void loginIsCaseInsensitiveOnEmail() throws Exception {
        MvcResult result = login("  ANA@Example.COM ", PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"email\":\"ana@example.com\"");
    }

    @Test
    void blankFieldsAreValidationErrors() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.email").isString())
                .andExpect(jsonPath("$.fields.password").isString());
    }

    @Test
    void anonymousMeIsJson401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.fields").isEmpty());
    }

    @Test
    void unknownProtectedApiRouteIs401ForAnonymous() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        MockHttpSession session = (MockHttpSession) login("ana@example.com", PASSWORD).getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

        MvcResult logout = mvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(logout.getResponse().getCookie("JSESSIONID")).isNotNull();
        assertThat(logout.getResponse().getCookie("JSESSIONID").getMaxAge()).isZero();
        assertThat(session.isInvalid()).isTrue();

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void anonymousLogoutIsNoContent() throws Exception {
        mvc.perform(post("/api/auth/logout").with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void logoutRequiresCsrf() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }
}
