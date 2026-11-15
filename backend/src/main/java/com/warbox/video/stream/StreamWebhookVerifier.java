package com.warbox.video.stream;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Verifies the Cloudflare {@code Webhook-Signature} header: {@code time=<unix seconds>,sig1=<hex>} where
 * sig1 = HMAC-SHA256(secret, time + "." + rawBody). Comparison is constant-time and the timestamp must be
 * within the configured tolerance of the {@link Clock}.
 */
@Component
public class StreamWebhookVerifier {

    private static final String HMAC = "HmacSHA256";

    private final StreamProperties properties;
    private final Clock clock;

    public StreamWebhookVerifier(StreamProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean isValid(String signatureHeader, String rawBody) {
        String secret = properties.webhookSecret();
        if (secret == null || secret.isBlank() || signatureHeader == null || rawBody == null) {
            return false;
        }
        String time = null;
        String signature = null;
        for (String part : signatureHeader.split(",")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if (key.equals("time")) {
                time = value;
            } else if (key.equals("sig1")) {
                signature = value;
            }
        }
        if (time == null || signature == null) {
            return false;
        }
        long seconds;
        try {
            seconds = Long.parseLong(time);
        } catch (NumberFormatException ex) {
            return false;
        }
        Duration tolerance = properties.webhookTolerance() == null ? Duration.ofMinutes(5)
                : properties.webhookTolerance();
        long skew = Math.abs(Instant.now(clock).getEpochSecond() - seconds);
        if (skew > tolerance.toSeconds()) {
            return false;
        }
        String expected = hmacHex(secret, time + "." + rawBody);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    static String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
