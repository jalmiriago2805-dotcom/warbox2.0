package com.warbox.video;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.warbox.creator.CreatorTestSupport.activate;
import static com.warbox.creator.CreatorTestSupport.body;
import static com.warbox.creator.CreatorTestSupport.signUp;
import static com.warbox.video.WireMockStreamSupport.SERVER;
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
class VideoStatusSyncTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingEmailSender emails;

    private MockHttpSession session;
    private UUID creatorId;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("TRUNCATE TABLE videos, creator_profiles, account_tokens, users");
        emails.clear();
        SERVER.resetAll();
        session = signUp(mvc, emails, "davi@example.com", true);
        activate(mvc, session, body("davi_silva", "Davi Silva", null)).andExpect(status().isCreated());
        creatorId = VideoTestSupport.creatorId(jdbc, "davi_silva");
    }

    private UUID video(String uid, String access, String statusValue) {
        return VideoTestSupport.insertVideo(jdbc, creatorId, uid, "Vídeo " + uid, access, "DRAFT", statusValue, null,
                Instant.now());
    }

    @Test
    void getSyncsNonFinalVideoAndExposesFreeUrls() throws Exception {
        UUID id = video("uid-a", "FREE", "PENDING_UPLOAD");
        jdbc.update("UPDATE videos SET duration_seconds = NULL WHERE id = ?", id);
        WireMockStreamSupport.stubGetVideo("uid-a", "ready", true, 125.4);

        mvc.perform(get("/api/creators/me/videos/{id}", id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("READY"))
                .andExpect(jsonPath("$.durationSeconds").value(125))
                .andExpect(jsonPath("$.playerUrl").value("https://customer-testcode.cloudflarestream.com/uid-a/iframe"))
                .andExpect(jsonPath("$.thumbnailUrl")
                        .value("https://customer-testcode.cloudflarestream.com/uid-a/thumbnails/thumbnail.jpg"));

        assertThat(jdbc.queryForObject("SELECT processing_status FROM videos WHERE id = ?", String.class, id))
                .isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT duration_seconds FROM videos WHERE id = ?", Integer.class, id))
                .isEqualTo(125);
    }

    @Test
    void exclusiveVideoGetsNoUrlsEvenWhenReady() throws Exception {
        UUID id = video("uid-b", "EXCLUSIVE", "PROCESSING");
        WireMockStreamSupport.stubGetVideo("uid-b", "ready", true, 60);

        mvc.perform(get("/api/creators/me/videos/{id}", id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("READY"))
                .andExpect(jsonPath("$.playerUrl").value((Object) null))
                .andExpect(jsonPath("$.thumbnailUrl").value((Object) null));
    }

    @Test
    void listSyncsProcessingVideosAndMapsStates() throws Exception {
        UUID processing = video("uid-c", "FREE", "PENDING_UPLOAD");
        WireMockStreamSupport.stubGetVideo("uid-c", "inprogress", false, -1);

        mvc.perform(get("/api/creators/me/videos").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].processingStatus").value("PROCESSING"))
                .andExpect(jsonPath("$[0].durationSeconds").value(90));

        WireMockStreamSupport.stubGetVideo("uid-c", "error", false, 0);
        mvc.perform(get("/api/creators/me/videos").session(session))
                .andExpect(jsonPath("$[0].processingStatus").value("ERROR"));
        assertThat(processing).isNotNull();
    }

    @Test
    void syncFailureDoesNotBreakTheResponse() throws Exception {
        UUID id = video("uid-d", "FREE", "PROCESSING");
        WireMockStreamSupport.stubGetVideoFailure("uid-d", 500);

        mvc.perform(get("/api/creators/me/videos/{id}", id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("PROCESSING"));
        mvc.perform(get("/api/creators/me/videos").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].processingStatus").value("PROCESSING"));
    }

    @Test
    void finalVideosDoNotCallCloudflare() throws Exception {
        UUID ready = video("uid-e", "FREE", "READY");
        video("uid-f", "FREE", "ERROR");

        mvc.perform(get("/api/creators/me/videos/{id}", ready).session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/creators/me/videos").session(session)).andExpect(status().isOk());

        SERVER.verify(0, getRequestedFor(urlEqualTo(WireMockStreamSupport.videoPath("uid-e"))));
        SERVER.verify(0, getRequestedFor(urlEqualTo(WireMockStreamSupport.videoPath("uid-f"))));
        assertThat(SERVER.getAllServeEvents()).isEmpty();
    }
}
