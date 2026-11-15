package com.warbox.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.warbox.TestcontainersConfiguration;
import com.warbox.mail.RecordingEmailSender;
import com.warbox.mail.TestMailConfiguration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
class CsrfAndSessionCookieTest {

    private static final String SIGNUP_JSON = """
            {"name":"Eva","email":"eva@example.com","password":"senha-forte-123","birthDate":"1992-09-09","acceptedTerms":true}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingEmailSender emails;

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE TABLE account_tokens, users");
        emails.clear();
    }

    private static Optional<String> setCookie(ResponseEntity<?> response, String name) {
        List<String> cookies = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        // The last Set-Cookie for a name wins in the browser.
        return cookies.stream().filter(c -> c.startsWith(name + "=")).reduce((first, second) -> second);
    }

    private static String cookieValue(String setCookieHeader) {
        return setCookieHeader.substring(setCookieHeader.indexOf('=') + 1, setCookieHeader.indexOf(';'));
    }

    @Test
    void postWithoutCsrfIsRejectedAsJson() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"eva@example.com\",\"password\":\"senha-forte-123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andExpect(jsonPath("$.fields").isEmpty());
    }

    @Test
    void wrongCsrfHeaderIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=cookie-value");
        headers.add("X-XSRF-TOKEN", "different-value");

        ResponseEntity<String> response = rest.exchange("/api/auth/signup", HttpMethod.POST,
                new HttpEntity<>(SIGNUP_JSON, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("\"code\":\"CSRF_INVALID\"");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
    }

    @Test
    void spaCsrfFlowWithRealCookies() {
        // 1) Bootstrap the CSRF cookie.
        ResponseEntity<Void> csrf = rest.getForEntity("/api/auth/csrf", Void.class);
        assertThat(csrf.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String xsrfCookie = setCookie(csrf, "XSRF-TOKEN").orElseThrow();
        assertThat(xsrfCookie).doesNotContainIgnoringCase("HttpOnly").contains("Path=/").containsIgnoringCase("SameSite=Lax");
        String xsrf = cookieValue(xsrfCookie);
        assertThat(xsrf).isNotBlank();

        // 2) Signup echoing the raw cookie value in the header.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrf);
        headers.add("X-XSRF-TOKEN", xsrf);
        ResponseEntity<String> signup = rest.exchange("/api/auth/signup", HttpMethod.POST,
                new HttpEntity<>(SIGNUP_JSON, headers), String.class);

        assertThat(signup.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(signup.getBody()).contains("\"emailVerified\":false");
        String sessionCookie = setCookie(signup, "JSESSIONID").orElseThrow();
        assertThat(sessionCookie).contains("HttpOnly").containsIgnoringCase("SameSite=Lax").contains("Path=/")
                .doesNotContain("Secure");
        // Signup rotates the CSRF token.
        String rotated = setCookie(signup, "XSRF-TOKEN").map(CsrfAndSessionCookieTest::cookieValue).orElseThrow();
        assertThat(rotated).isNotBlank().isNotEqualTo(xsrf);

        // 3) The session cookie authenticates /me.
        HttpHeaders meHeaders = new HttpHeaders();
        meHeaders.add(HttpHeaders.COOKIE, "JSESSIONID=" + cookieValue(sessionCookie));
        ResponseEntity<String> me = rest.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(meHeaders),
                String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).contains("\"email\":\"eva@example.com\"");

        // 4) Logout with the rotated token expires both cookies; the session is gone.
        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.add(HttpHeaders.COOKIE, "JSESSIONID=" + cookieValue(sessionCookie) + "; XSRF-TOKEN=" + rotated);
        logoutHeaders.add("X-XSRF-TOKEN", rotated);
        ResponseEntity<String> logout = rest.exchange("/api/auth/logout", HttpMethod.POST,
                new HttpEntity<>(logoutHeaders), String.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(setCookie(logout, "JSESSIONID").orElseThrow()).contains("Max-Age=0");
        assertThat(setCookie(logout, "XSRF-TOKEN").orElseThrow()).contains("Max-Age=0");

        ResponseEntity<String> meAfter = rest.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(meHeaders),
                String.class);
        assertThat(meAfter.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(meAfter.getBody()).contains("\"code\":\"UNAUTHENTICATED\"");
    }

    @Test
    void anyResponseIssuesTheCsrfCookieWhenMissing() {
        ResponseEntity<String> me = rest.getForEntity("/api/auth/me", String.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(setCookie(me, "XSRF-TOKEN")).isPresent();
    }

    @Test
    void errorMessagesAreUtf8() {
        ResponseEntity<String> me = rest.getForEntity("/api/auth/me", String.class);

        assertThat(me.getBody()).contains("Faça login para continuar.");
    }

    @Test
    void healthStaysPublicWithoutSession() {
        ResponseEntity<String> health = rest.getForEntity("/api/health", String.class);

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).isEqualTo("{\"status\":\"ok\"}");
        assertThat(setCookie(health, "JSESSIONID")).isEmpty();
    }
}
