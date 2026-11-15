package com.warbox.creator;

import static com.warbox.creator.CreatorTestSupport.activate;
import static com.warbox.creator.CreatorTestSupport.body;
import static com.warbox.creator.CreatorTestSupport.signUp;
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
class PublicCreatorProfileTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingEmailSender emails;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("TRUNCATE TABLE creator_profiles, account_tokens, users");
        emails.clear();
        MockHttpSession session = signUp(mvc, emails, "davi@example.com", true);
        activate(mvc, session, body("davi_silva", "Davi Silva", "Criador de conteúdo"))
                .andExpect(status().isCreated());
    }

    @Test
    void publicProfileIsVisibleWithoutSessionAndHidesInternalFields() throws Exception {
        mvc.perform(get("/api/creators/davi_silva"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("davi_silva"))
                .andExpect(jsonPath("$.displayName").value("Davi Silva"))
                .andExpect(jsonPath("$.bio").value("Criador de conteúdo"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void uppercaseHandleInPathResolves() throws Exception {
        mvc.perform(get("/api/creators/DAVI_Silva"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("davi_silva"));
    }

    @Test
    void unknownOrInvalidHandleReturns404() throws Exception {
        for (String handle : new String[] {"ninguem", "x", "-abc", "joão"}) {
            mvc.perform(get("/api/creators/{handle}", handle))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Criador não encontrado."));
        }
    }

    @Test
    void meWithoutSessionStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/creators/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
