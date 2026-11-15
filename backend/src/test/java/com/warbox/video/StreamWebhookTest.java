package com.warbox.video;

import static com.warbox.creator.CreatorTestSupport.activate;
import static com.warbox.creator.CreatorTestSupport.body;
import static com.warbox.creator.CreatorTestSupport.signUp;
import static com.warbox.video.WireMockStreamSupport.sign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.warbox.TestcontainersConfiguration;
import com.warbox.mail.RecordingEmailSender;
import com.warbox.mail.TestMailConfiguration;
import java.time.Instant;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class, WireMockStreamSupport.Config.class})
class StreamWebhookTest {

    private static final String URL = "/api/webhooks/cloudflare/stream";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingEmailSender emails;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("TRUNCATE TABLE videos, creator_profiles, account_tokens, users");
        emails.clear();
        MockHttpSession session = signUp(mvc, emails, "davi@example.com", true);
        activate(mvc, session, body("davi_silva", "Davi Silva", null)).andExpect(status().isCreated());
        VideoTestSupport.insertVideo(jdbc, VideoTestSupport.creatorId(jdbc, "davi_silva"), "uid-w", "Vídeo", "FREE",
                "DRAFT", "PROCESSING", null, Instant.now());
        jdbc.update("UPDATE videos SET duration_seconds = NULL");
    }

    private ResultActions send(String payload, String signature) throws Exception {
        var request = post(URL).contentType(MediaType.APPLICATION_JSON).content(payload);
        if (signature != null) {
            request.header("Webhook-Signature", signature);
        }
        return mvc.perform(request); // no CSRF token on purpose
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }

    private String processingStatus() {
        return jdbc.queryForObject("SELECT processing_status FROM videos", String.class);
    }

    @Test
    void validSignatureMarksVideoReady() throws Exception {
        String payload = "{\"uid\":\"uid-w\",\"readyToStream\":true,\"status\":{\"state\":\"ready\"},\"duration\":61.2}";

        send(payload, sign(payload, now())).andExpect(status().isNoContent());

        assertThat(processingStatus()).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT duration_seconds FROM videos", Integer.class)).isEqualTo(61);
    }

    @Test
    void validSignatureMarksVideoAsError() throws Exception {
        String payload = "{\"uid\":\"uid-w\",\"readyToStream\":false,"
                + "\"status\":{\"state\":\"error\",\"errorReasonCode\":\"ERR_DURATION_EXCEED_CONSTRAINT\"},\"duration\":-1}";

        send(payload, sign(payload, now())).andExpect(status().isNoContent());

        assertThat(processingStatus()).isEqualTo("ERROR");
        assertThat(jdbc.queryForObject("SELECT error_reason FROM videos", String.class))
                .isEqualTo("ERR_DURATION_EXCEED_CONSTRAINT");
        assertThat(jdbc.queryForObject("SELECT duration_seconds FROM videos", Integer.class)).isNull();
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        String payload = "{\"uid\":\"uid-w\",\"readyToStream\":true,\"status\":{\"state\":\"ready\"}}";

        send(payload, sign("wrong-secret", payload, now()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
        send(payload + " ", sign(payload, now()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));

        assertThat(processingStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void missingHeaderIsRejected() throws Exception {
        send("{\"uid\":\"uid-w\",\"readyToStream\":true}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
        assertThat(processingStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void staleTimestampIsRejected() throws Exception {
        String payload = "{\"uid\":\"uid-w\",\"readyToStream\":true,\"status\":{\"state\":\"ready\"}}";

        send(payload, sign(payload, now() - 10 * 60))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
        assertThat(processingStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void unknownUidIsIgnored() throws Exception {
        String payload = "{\"uid\":\"other\",\"readyToStream\":true,\"status\":{\"state\":\"ready\"}}";

        send(payload, sign(payload, now())).andExpect(status().isNoContent());

        assertThat(processingStatus()).isEqualTo("PROCESSING");
        assertThat(Map.of("count", jdbc.queryForObject("SELECT count(*) FROM videos", Integer.class)))
                .containsEntry("count", 1);
    }
}
