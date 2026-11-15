package com.warbox.video;

import static org.assertj.core.api.Assertions.assertThat;

import com.warbox.video.stream.StreamProperties;
import com.warbox.video.stream.StreamWebhookVerifier;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StreamWebhookVerifierTest {

    private static final Instant NOW = Instant.parse("2026-01-10T12:00:00Z");
    private static final String BODY = "{\"uid\":\"abc\"}";

    private static StreamWebhookVerifier verifier(String secret) {
        StreamProperties props = new StreamProperties("http://x", "acct", "token", "code", secret, 3600,
                Duration.ofMinutes(5));
        return new StreamWebhookVerifier(props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void acceptsValidSignature() {
        assertThat(verifier("s3cret").isValid(WireMockStreamSupport.sign("s3cret", BODY, NOW.getEpochSecond()), BODY))
                .isTrue();
    }

    @Test
    void rejectsWrongSignatureOrBody() {
        StreamWebhookVerifier verifier = verifier("s3cret");
        assertThat(verifier.isValid(WireMockStreamSupport.sign("other", BODY, NOW.getEpochSecond()), BODY)).isFalse();
        assertThat(verifier.isValid(WireMockStreamSupport.sign("s3cret", BODY, NOW.getEpochSecond()), BODY + "x"))
                .isFalse();
    }

    @Test
    void enforcesTimestampTolerance() {
        StreamWebhookVerifier verifier = verifier("s3cret");
        long edge = NOW.getEpochSecond() - 300;
        assertThat(verifier.isValid(WireMockStreamSupport.sign("s3cret", BODY, edge), BODY)).isTrue();
        assertThat(verifier.isValid(WireMockStreamSupport.sign("s3cret", BODY, edge - 1), BODY)).isFalse();
        assertThat(verifier.isValid(WireMockStreamSupport.sign("s3cret", BODY, NOW.getEpochSecond() + 301), BODY))
                .isFalse();
    }

    @Test
    void rejectsMalformedHeaders() {
        StreamWebhookVerifier verifier = verifier("s3cret");
        assertThat(verifier.isValid(null, BODY)).isFalse();
        assertThat(verifier.isValid("", BODY)).isFalse();
        assertThat(verifier.isValid("garbage", BODY)).isFalse();
        assertThat(verifier.isValid("time=abc,sig1=00", BODY)).isFalse();
        assertThat(verifier.isValid("time=" + NOW.getEpochSecond(), BODY)).isFalse();
        assertThat(verifier.isValid("sig1=00", BODY)).isFalse();
    }

    @Test
    void blankSecretNeverValidates() {
        assertThat(verifier("").isValid("time=" + NOW.getEpochSecond() + ",sig1=00", BODY)).isFalse();
        assertThat(verifier(null).isValid(WireMockStreamSupport.sign("x", BODY, NOW.getEpochSecond()), BODY)).isFalse();
    }
}
