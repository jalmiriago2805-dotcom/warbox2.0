package com.warbox.video;

import static com.warbox.creator.CreatorTestSupport.activate;
import static com.warbox.creator.CreatorTestSupport.body;
import static com.warbox.creator.CreatorTestSupport.signUp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.warbox.TestcontainersConfiguration;
import com.warbox.mail.RecordingEmailSender;
import com.warbox.mail.TestMailConfiguration;
import java.time.Instant;
import java.util.UUID;
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
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class, WireMockStreamSupport.Config.class})
class PublicCreatorVideosTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingEmailSender emails;

    private UUID creatorId;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("TRUNCATE TABLE videos, creator_profiles, account_tokens, users");
        emails.clear();
        MockHttpSession session = signUp(mvc, emails, "davi@example.com", true);
        activate(mvc, session, body("davi_silva", "Davi Silva", null)).andExpect(status().isCreated());
        creatorId = VideoTestSupport.creatorId(jdbc, "davi_silva");
    }

    private UUID video(String uid, String access, String visibility, String processing, Instant publishedAt) {
        return VideoTestSupport.insertVideo(jdbc, creatorId, uid, "Título " + uid.substring(4), access, visibility, processing,
                publishedAt, Instant.now());
    }

    @Test
    void listsOnlyPublishedReadyNewestFirstAndRespectsLocking() throws Exception {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        video("uid-old", "FREE", "PUBLISHED", "READY", base);
        video("uid-excl", "EXCLUSIVE", "PUBLISHED", "READY", base.plusSeconds(3600));
        video("uid-new", "FREE", "PUBLISHED", "READY", base.plusSeconds(7200));
        video("uid-draft", "FREE", "DRAFT", "READY", null);
        video("uid-proc", "FREE", "PUBLISHED", "PROCESSING", base.plusSeconds(10));

        String response = mvc.perform(get("/api/creators/davi_silva/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("Título new"))
                .andExpect(jsonPath("$[0].locked").value(false))
                .andExpect(jsonPath("$[0].playerUrl")
                        .value("https://customer-testcode.cloudflarestream.com/uid-new/iframe"))
                .andExpect(jsonPath("$[0].thumbnailUrl")
                        .value("https://customer-testcode.cloudflarestream.com/uid-new/thumbnails/thumbnail.jpg"))
                .andExpect(jsonPath("$[0].durationSeconds").value(90))
                .andExpect(jsonPath("$[1].title").value("Título excl"))
                .andExpect(jsonPath("$[1].accessType").value("EXCLUSIVE"))
                .andExpect(jsonPath("$[1].locked").value(true))
                .andExpect(jsonPath("$[1].playerUrl").value((Object) null))
                .andExpect(jsonPath("$[1].thumbnailUrl").value((Object) null))
                .andExpect(jsonPath("$[2].title").value("Título old"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("uid-excl").doesNotContain("cloudflareUid").doesNotContain("uid-draft");
    }

    @Test
    void handleLookupIsCaseInsensitiveAndEmptyListIsOk() throws Exception {
        mvc.perform(get("/api/creators/DAVI_Silva/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unknownOrInvalidHandleIsNotFound() throws Exception {
        for (String handle : new String[] {"ninguem", "x"}) {
            mvc.perform(get("/api/creators/{handle}/videos", handle))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
        }
    }

    @Test
    void meVideosRequiresSession() throws Exception {
        mvc.perform(get("/api/creators/me/videos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/creators/me/videos/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
