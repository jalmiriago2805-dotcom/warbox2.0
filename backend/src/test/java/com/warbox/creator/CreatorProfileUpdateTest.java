package com.warbox.creator;

import static com.warbox.creator.CreatorTestSupport.activate;
import static com.warbox.creator.CreatorTestSupport.body;
import static com.warbox.creator.CreatorTestSupport.signUp;
import static com.warbox.creator.CreatorTestSupport.update;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
class CreatorProfileUpdateTest {

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

    @Test
    void withoutSessionReturns401() throws Exception {
        update(mvc, null, body("davi", "Davi", null)).andExpect(status().isUnauthorized());
    }

    @Test
    void withoutProfileReturns404() throws Exception {
        MockHttpSession session = signUp(mvc, emails, "davi@example.com", true);

        update(mvc, session, body("davi", "Davi", null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_PROFILE_NOT_FOUND"));
    }

    @Test
    void updatesDisplayNameAndBioKeepingHandle() throws Exception {
        MockHttpSession session = signUp(mvc, emails, "davi@example.com", true);
        activate(mvc, session, body("davi", "Davi", null)).andExpect(status().isCreated());
        // Make the original timestamps clearly older than "now".
        jdbc.update("UPDATE creator_profiles SET updated_at = updated_at - interval '1 hour'");
        Object before = jdbc.queryForObject("SELECT updated_at FROM creator_profiles", java.sql.Timestamp.class);

        update(mvc, session, body("davi", "Davi Silva", "Nova bio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("davi"))
                .andExpect(jsonPath("$.displayName").value("Davi Silva"))
                .andExpect(jsonPath("$.bio").value("Nova bio"));

        Object after = jdbc.queryForObject("SELECT updated_at FROM creator_profiles", java.sql.Timestamp.class);
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void changingHandleFreesTheOldOne() throws Exception {
        MockHttpSession session = signUp(mvc, emails, "davi@example.com", true);
        activate(mvc, session, body("davi", "Davi", null)).andExpect(status().isCreated());

        update(mvc, session, body("Davi-Novo", "Davi", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("davi-novo"));

        mvc.perform(get("/api/creators/davi"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
        mvc.perform(get("/api/creators/davi-novo")).andExpect(status().isOk());
    }

    @Test
    void handleOfAnotherCreatorIsTaken() throws Exception {
        MockHttpSession first = signUp(mvc, emails, "a@example.com", true);
        activate(mvc, first, body("alice", "Alice", null)).andExpect(status().isCreated());
        emails.clear();
        MockHttpSession second = signUp(mvc, emails, "b@example.com", true);
        activate(mvc, second, body("bruno", "Bruno", null)).andExpect(status().isCreated());

        update(mvc, second, body("ALICE", "Bruno", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDLE_TAKEN"))
                .andExpect(jsonPath("$.fields.handle").isNotEmpty());
    }

    @Test
    void invalidInputReturnsValidationError() throws Exception {
        MockHttpSession session = signUp(mvc, emails, "davi@example.com", true);
        activate(mvc, session, body("davi", "Davi", null)).andExpect(status().isCreated());

        update(mvc, session, body("-x", "", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.handle").isNotEmpty())
                .andExpect(jsonPath("$.fields.displayName").isNotEmpty());
    }
}
