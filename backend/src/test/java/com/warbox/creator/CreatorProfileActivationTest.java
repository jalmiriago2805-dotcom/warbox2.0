package com.warbox.creator;

import static com.warbox.creator.CreatorTestSupport.activate;
import static com.warbox.creator.CreatorTestSupport.body;
import static com.warbox.creator.CreatorTestSupport.signUp;
import static org.assertj.core.api.Assertions.assertThat;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
class CreatorProfileActivationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingEmailSender emails;

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE TABLE videos, creator_profiles, account_tokens, users");
        emails.clear();
    }

    private int profileCount() {
        return jdbc.queryForObject("SELECT count(*) FROM creator_profiles", Integer.class);
    }

    @Test
    void withoutSessionReturns401() throws Exception {
        activate(mvc, null, body("davi", "Davi", null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void withoutCsrfReturns403() throws Exception {
        MockHttpSession session = signUp(mvc, emails, "davi@example.com", true);
        mvc.perform(post("/api/creators/me").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(body("davi", "Davi", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void unverifiedEmailIsRejectedAndNothingIsCreated() throws Exception {
        MockHttpSession session = signUp(mvc, emails, "davi@example.com", false);

        activate(mvc, session, body("davi", "Davi", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        assertThat(profileCount()).isZero();
    }

    @Test
    void verifiedUserActivatesWithNormalizedHandleAndNullBio() throws Exception {
        MockHttpSession session = signUp(mvc, emails, "davi@example.com", true);

        activate(mvc, session, body("  Davi_Silva ", " Davi Silva ", "   "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.handle").value("davi_silva"))
                .andExpect(jsonPath("$.displayName").value("Davi Silva"))
                .andExpect(jsonPath("$.bio").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        assertThat(profileCount()).isEqualTo(1);
    }

    @Test
    void secondActivationReturnsProfileExists() throws Exception {
        MockHttpSession session = signUp(mvc, emails, "davi@example.com", true);
        activate(mvc, session, body("davi", "Davi", null)).andExpect(status().isCreated());

        activate(mvc, session, body("outro", "Davi", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_PROFILE_EXISTS"));
    }

    @Test
    void handleUsedByAnotherUserIsTakenEvenWithDifferentCase() throws Exception {
        MockHttpSession first = signUp(mvc, emails, "a@example.com", true);
        activate(mvc, first, body("davi", "Davi", null)).andExpect(status().isCreated());
        emails.clear();
        MockHttpSession second = signUp(mvc, emails, "b@example.com", true);

        activate(mvc, second, body("DAVI", "Outro", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDLE_TAKEN"))
                .andExpect(jsonPath("$.fields.handle").value("Este nome de usuário já está em uso."));
        assertThat(profileCount()).isEqualTo(1);
    }

    @Test
    void invalidInputsReturnValidationErrorsOnTheMatchingField() throws Exception {
        MockHttpSession session = signUp(mvc, emails, "davi@example.com", true);

        for (String handle : new String[] {"ab", "-abc", "abc_", "joão", "a b", "x".repeat(31)}) {
            activate(mvc, session, body(handle, "Davi", null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fields.handle").isNotEmpty());
        }
        activate(mvc, session, body("   ", "Davi", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.handle").value("Informe um nome de usuário."));
        activate(mvc, session, body("admin", "Davi", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.handle").value("Este nome de usuário não está disponível."));
        activate(mvc, session, body("me", "Davi", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.handle").isNotEmpty());
        activate(mvc, session, body("davi", "  ", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.displayName").value("Informe o nome de exibição."));
        activate(mvc, session, body("davi", "x".repeat(61), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.displayName").isNotEmpty());
        activate(mvc, session, body("davi", "Davi", "x".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.bio").value("A bio deve ter no máximo 500 caracteres."));

        assertThat(profileCount()).isZero();
    }

    @Test
    void getMineIs404BeforeAndOkAfterActivation() throws Exception {
        MockHttpSession session = signUp(mvc, emails, "davi@example.com", true);

        mvc.perform(get("/api/creators/me").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_PROFILE_NOT_FOUND"));

        activate(mvc, session, body("davi", "Davi", "Olá")).andExpect(status().isCreated());

        mvc.perform(get("/api/creators/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("davi"))
                .andExpect(jsonPath("$.bio").value("Olá"));
    }
}
