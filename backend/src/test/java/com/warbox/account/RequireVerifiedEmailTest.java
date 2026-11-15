package com.warbox.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.warbox.TestcontainersConfiguration;
import com.warbox.account.dto.AuthDtos.SignupRequest;
import com.warbox.common.ApiException;
import com.warbox.mail.RecordingEmailSender;
import com.warbox.mail.TestMailConfiguration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

// @AutoConfigureMockMvc keeps the same cached context as the MockMvc flow tests.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
class RequireVerifiedEmailTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingEmailSender emails;

    private UUID userId;

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE TABLE account_tokens, users");
        emails.clear();
        userId = accountService.signup(new SignupRequest("Davi", "davi@example.com", "senha-forte-123",
                LocalDate.of(1990, 1, 1), true)).getId();
    }

    @Test
    void unverifiedUserIsRejected() {
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> accountService.requireVerifiedEmail(userId));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(ex.getMessage()).isNotBlank();
    }

    @Test
    void verifiedUserPasses() {
        accountService.verifyEmail(RecordingEmailSender.extractToken(emails.last()));

        assertThatCode(() -> accountService.requireVerifiedEmail(userId)).doesNotThrowAnyException();
    }
}
