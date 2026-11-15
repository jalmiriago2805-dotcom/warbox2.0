package com.warbox.video.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warbox.common.ApiException;
import com.warbox.video.VideoService;
import com.warbox.video.stream.StreamClient.StreamVideoInfo;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cloudflare Stream webhook: public route, authenticated by the HMAC signature instead of a session/CSRF. */
@RestController
@RequestMapping("/api/webhooks/cloudflare/stream")
public class StreamWebhookController {

    private final StreamWebhookVerifier verifier;
    private final VideoService videos;
    private final ObjectMapper objectMapper;

    public StreamWebhookController(StreamWebhookVerifier verifier, VideoService videos, ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.videos = videos;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receive(@RequestHeader(value = "Webhook-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        if (!verifier.isValid(signature, rawBody)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEBHOOK_SIGNATURE_INVALID", "Assinatura inválida.");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Não foi possível ler os dados enviados.");
        }
        StreamVideoInfo info = node == null ? null : CloudflareStreamClient.parse(node, null);
        if (info == null || info.uid() == null) {
            return;
        }
        videos.applyStreamStatus(info);
    }
}
