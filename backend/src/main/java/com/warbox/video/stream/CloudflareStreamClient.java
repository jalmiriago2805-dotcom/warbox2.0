package com.warbox.video.stream;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class CloudflareStreamClient implements StreamClient {

    private final StreamProperties properties;
    private final RestClient rest;

    public CloudflareStreamClient(StreamProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.rest = RestClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public DirectUpload createDirectUpload(long sizeBytes, int maxDurationSeconds, boolean requireSignedUrls,
            String name) {
        requireConfigured();
        String metadata = "maxDurationSeconds " + b64(String.valueOf(maxDurationSeconds)) + ",name " + b64(name)
                + (requireSignedUrls ? ",requiresignedurls" : "");
        try {
            ResponseEntity<Void> response = rest.post()
                    .uri("/accounts/{acct}/stream?direct_user=true", properties.accountId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken())
                    .header("Tus-Resumable", "1.0.0")
                    .header("Upload-Length", String.valueOf(sizeBytes))
                    .header("Upload-Metadata", metadata)
                    .retrieve()
                    .toBodilessEntity();
            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            String uid = response.getHeaders().getFirst("stream-media-id");
            if (response.getStatusCode().value() != HttpStatus.CREATED.value()
                    || isBlank(location) || isBlank(uid)) {
                throw new StreamClientException("Unexpected Cloudflare response when creating an upload");
            }
            return new DirectUpload(uid, location);
        } catch (StreamClientException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new StreamClientException("Cloudflare create upload failed", ex);
        }
    }

    @Override
    public StreamVideoInfo getVideo(String uid) {
        requireConfigured();
        try {
            JsonNode body = rest.get()
                    .uri("/accounts/{acct}/stream/{uid}", properties.accountId(), uid)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode result = body == null ? null : body.get("result");
            if (result == null || result.isNull()) {
                throw new StreamClientException("Cloudflare returned no result for the video");
            }
            return parse(result, uid);
        } catch (StreamClientException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new StreamClientException("Cloudflare get video failed", ex);
        }
    }

    @Override
    public void setRequireSignedUrls(String uid, boolean value) {
        requireConfigured();
        try {
            rest.post()
                    .uri("/accounts/{acct}/stream/{uid}", properties.accountId(), uid)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("uid", uid, "requireSignedURLs", value))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            throw new StreamClientException("Cloudflare update video failed", ex);
        }
    }

    @Override
    public void deleteVideo(String uid) {
        requireConfigured();
        try {
            rest.delete()
                    .uri("/accounts/{acct}/stream/{uid}", properties.accountId(), uid)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ex) {
            // Already gone: nothing to delete.
        } catch (RuntimeException ex) {
            throw new StreamClientException("Cloudflare delete video failed", ex);
        }
    }

    /** Parses the Cloudflare video object (shared shape between the API result and the webhook body). */
    public static StreamVideoInfo parse(JsonNode node, String fallbackUid) {
        String uid = text(node.get("uid"));
        JsonNode status = node.get("status");
        String state = status == null ? null : text(status.get("state"));
        String errorCode = status == null ? null : text(status.get("errorReasonCode"));
        boolean ready = node.path("readyToStream").asBoolean(false);
        Integer duration = null;
        JsonNode durationNode = node.get("duration");
        if (durationNode != null && durationNode.isNumber() && durationNode.asDouble() > 0) {
            duration = (int) Math.round(durationNode.asDouble());
        }
        return new StreamVideoInfo(uid == null ? fallbackUid : uid, state, ready, duration, errorCode);
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() || node.asText().isEmpty() ? null : node.asText();
    }

    private void requireConfigured() {
        if (isBlank(properties.accountId()) || isBlank(properties.apiToken()) || isBlank(properties.apiBaseUrl())) {
            throw new StreamClientException("Cloudflare Stream is not configured");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
