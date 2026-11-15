package com.warbox.video;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.warbox.creator.CreatorTestSupport.activate;
import static com.warbox.creator.CreatorTestSupport.body;
import static com.warbox.creator.CreatorTestSupport.signUp;
import static com.warbox.video.WireMockStreamSupport.SERVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.warbox.TestcontainersConfiguration;
import com.warbox.mail.RecordingEmailSender;
import com.warbox.mail.TestMailConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
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
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class, WireMockStreamSupport.Config.class})
class CreatorVideoFlowTest {

    private static final String UPLOAD_URL = "https://upload.cloudflarestream.com/tus/abc123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingEmailSender emails;

    @Autowired
    private ObjectMapper json;

    private MockHttpSession session;

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("TRUNCATE TABLE videos, creator_profiles, account_tokens, users");
        emails.clear();
        SERVER.resetAll();
        session = signUp(mvc, emails, "davi@example.com", true);
        activate(mvc, session, body("davi_silva", "Davi Silva", null)).andExpect(status().isCreated());
    }

    private static String createBody(String title, String access, Object size) {
        return "{\"title\":%s,\"description\":\"Uma descrição\",\"accessType\":\"%s\",\"sizeBytes\":%s}"
                .formatted(title == null ? "null" : "\"" + title + "\"", access, size);
    }

    private String createVideo(String uid, String access) throws Exception {
        WireMockStreamSupport.stubCreateUpload(uid, UPLOAD_URL);
        MvcResult result = mvc.perform(post("/api/creators/me/videos").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("Meu vídeo", access, 1000)))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("video").get("id").asText();
    }

    @Test
    void createSendsTusRequestAndReturnsUploadUrl() throws Exception {
        WireMockStreamSupport.stubCreateUpload("uid-free", UPLOAD_URL);

        mvc.perform(post("/api/creators/me/videos").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("Meu vídeo", "FREE", 5000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadUrl").value(UPLOAD_URL))
                .andExpect(jsonPath("$.video.title").value("Meu vídeo"))
                .andExpect(jsonPath("$.video.visibility").value("DRAFT"))
                .andExpect(jsonPath("$.video.processingStatus").value("PENDING_UPLOAD"))
                .andExpect(jsonPath("$.video.playerUrl").value((Object) null));

        List<LoggedRequest> calls = SERVER.findAll(postRequestedFor(urlPathEqualTo(WireMockStreamSupport.streamPath())));
        assertThat(calls).hasSize(1);
        LoggedRequest call = calls.get(0);
        assertThat(call.getUrl()).endsWith("?direct_user=true");
        assertThat(call.getHeader("Authorization")).isEqualTo("Bearer test-token");
        assertThat(call.getHeader("Tus-Resumable")).isEqualTo("1.0.0");
        assertThat(call.getHeader("Upload-Length")).isEqualTo("5000");
        String metadata = call.getHeader("Upload-Metadata");
        assertThat(metadata).contains("maxDurationSeconds " + b64("3600"));
        assertThat(metadata).contains("name " + b64("Meu vídeo"));
        assertThat(metadata).doesNotContain("requiresignedurls");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM videos", Integer.class)).isEqualTo(1);
    }

    @Test
    void exclusiveVideoRequiresSignedUrls() throws Exception {
        WireMockStreamSupport.stubCreateUpload("uid-excl", UPLOAD_URL);

        mvc.perform(post("/api/creators/me/videos").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("Premium", "EXCLUSIVE", 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.video.accessType").value("EXCLUSIVE"));

        LoggedRequest call = SERVER.findAll(postRequestedFor(urlPathEqualTo(WireMockStreamSupport.streamPath()))).get(0);
        assertThat(call.getHeader("Upload-Metadata")).endsWith(",requiresignedurls");
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(post("/api/creators/me/videos").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("x", "FREE", 10)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/creators/me/videos")).andExpect(status().isUnauthorized());
    }

    @Test
    void unverifiedEmailIsForbidden() throws Exception {
        MockHttpSession unverified = signUp(mvc, emails, "novo@example.com", false);

        mvc.perform(post("/api/creators/me/videos").with(csrf()).session(unverified)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("x", "FREE", 10)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void withoutCreatorProfileIsNotFound() throws Exception {
        MockHttpSession other = signUp(mvc, emails, "outro@example.com", true);

        mvc.perform(post("/api/creators/me/videos").with(csrf()).session(other)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("x", "FREE", 10)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_PROFILE_NOT_FOUND"));
    }

    @Test
    void invalidBodyReturnsValidationErrors() throws Exception {
        mvc.perform(post("/api/creators/me/videos").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("  ", "FREE", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.title").value("Informe o título."))
                .andExpect(jsonPath("$.fields.sizeBytes").value("Arquivo inválido ou maior que 30 GB."));

        mvc.perform(post("/api/creators/me/videos").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("t".repeat(101), "FREE", 32212254721L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.title").value("O título deve ter no máximo 100 caracteres."))
                .andExpect(jsonPath("$.fields.sizeBytes").value("Arquivo inválido ou maior que 30 GB."));

        mvc.perform(post("/api/creators/me/videos").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"ok\",\"sizeBytes\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.accessType").exists());
        assertThat(SERVER.getAllServeEvents()).isEmpty();
    }

    @Test
    void cloudflareFailureReturns502AndSavesNothing() throws Exception {
        WireMockStreamSupport.stubCreateUploadFailure(500);

        mvc.perform(post("/api/creators/me/videos").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("Meu vídeo", "FREE", 10)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("VIDEO_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message")
                        .value("Não foi possível falar com o serviço de vídeo. Tente novamente."));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM videos", Integer.class)).isZero();
    }

    @Test
    void updateChangingAccessTypeCallsCloudflare() throws Exception {
        String id = createVideo("uid-1", "FREE");
        WireMockStreamSupport.stubSetSigned("uid-1");

        mvc.perform(put("/api/creators/me/videos/{id}", id).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Novo título\",\"description\":\"   \",\"accessType\":\"EXCLUSIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Novo título"))
                .andExpect(jsonPath("$.description").value((Object) null))
                .andExpect(jsonPath("$.accessType").value("EXCLUSIVE"));

        SERVER.verify(1, postRequestedFor(urlEqualTo(WireMockStreamSupport.videoPath("uid-1")))
                .withRequestBody(equalToJson("{\"uid\":\"uid-1\",\"requireSignedURLs\":true}")));
    }

    @Test
    void updateWithoutAccessTypeChangeDoesNotCallCloudflare() throws Exception {
        String id = createVideo("uid-2", "FREE");

        mvc.perform(put("/api/creators/me/videos/{id}", id).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Outro\",\"accessType\":\"FREE\"}"))
                .andExpect(status().isOk());

        SERVER.verify(0, postRequestedFor(urlEqualTo(WireMockStreamSupport.videoPath("uid-2"))));
    }

    @Test
    void updateKeepsStateWhenCloudflareFails() throws Exception {
        String id = createVideo("uid-3", "FREE");
        SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo(WireMockStreamSupport.videoPath("uid-3")))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.serverError()));

        mvc.perform(put("/api/creators/me/videos/{id}", id).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Mudou\",\"accessType\":\"EXCLUSIVE\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("VIDEO_PROVIDER_UNAVAILABLE"));

        assertThat(jdbc.queryForObject("SELECT access_type FROM videos WHERE id = ?::uuid", String.class, id))
                .isEqualTo("FREE");
        assertThat(jdbc.queryForObject("SELECT title FROM videos WHERE id = ?::uuid", String.class, id))
                .isEqualTo("Meu vídeo");
    }

    @Test
    void publishRequiresReadyThenPublishAndUnpublishWork() throws Exception {
        String id = createVideo("uid-4", "FREE");

        mvc.perform(post("/api/creators/me/videos/{id}/publish", id).with(csrf()).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIDEO_NOT_READY"))
                .andExpect(jsonPath("$.message").value("O vídeo ainda está sendo processado."));

        jdbc.update("UPDATE videos SET processing_status = 'READY' WHERE id = ?::uuid", id);

        mvc.perform(post("/api/creators/me/videos/{id}/publish", id).with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());
        // Idempotent.
        mvc.perform(post("/api/creators/me/videos/{id}/publish", id).with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PUBLISHED"));

        mvc.perform(post("/api/creators/me/videos/{id}/unpublish", id).with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("DRAFT"))
                .andExpect(jsonPath("$.publishedAt").value((Object) null));
        mvc.perform(post("/api/creators/me/videos/{id}/unpublish", id).with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("DRAFT"));
    }

    @Test
    void anotherCreatorsVideoIsNotFound() throws Exception {
        String id = createVideo("uid-5", "FREE");
        MockHttpSession other = signUp(mvc, emails, "outro@example.com", true);
        activate(mvc, other, body("outro", "Outro", null)).andExpect(status().isCreated());

        mvc.perform(get("/api/creators/me/videos/{id}", id).session(other))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("VIDEO_NOT_FOUND"));
        mvc.perform(put("/api/creators/me/videos/{id}", id).with(csrf()).session(other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"accessType\":\"FREE\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("VIDEO_NOT_FOUND"));
        mvc.perform(post("/api/creators/me/videos/{id}/publish", id).with(csrf()).session(other))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/creators/me/videos/{id}", id).with(csrf()).session(other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/creators/me/videos/{id}", UUID.randomUUID()).session(session))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM videos", Integer.class)).isEqualTo(1);
    }

    @Test
    void deleteCallsCloudflareAndRemovesRow() throws Exception {
        String id = createVideo("uid-6", "FREE");
        WireMockStreamSupport.stubDelete("uid-6", 200);

        mvc.perform(delete("/api/creators/me/videos/{id}", id).with(csrf()).session(session))
                .andExpect(status().isNoContent());

        SERVER.verify(1, deleteRequestedFor(urlEqualTo(WireMockStreamSupport.videoPath("uid-6")))
                .withHeader("Authorization", equalTo("Bearer test-token")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM videos", Integer.class)).isZero();
    }

    @Test
    void deleteTreatsCloudflare404AsSuccess() throws Exception {
        String id = createVideo("uid-7", "FREE");
        WireMockStreamSupport.stubDelete("uid-7", 404);

        mvc.perform(delete("/api/creators/me/videos/{id}", id).with(csrf()).session(session))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM videos", Integer.class)).isZero();
    }

    @Test
    void deleteKeepsRowWhenCloudflareFails() throws Exception {
        String id = createVideo("uid-8", "FREE");
        WireMockStreamSupport.stubDelete("uid-8", 500);

        mvc.perform(delete("/api/creators/me/videos/{id}", id).with(csrf()).session(session))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("VIDEO_PROVIDER_UNAVAILABLE"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM videos", Integer.class)).isEqualTo(1);
    }

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
