package com.warbox.video;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Fake Cloudflare Stream API for tests: one WireMock server per JVM (dynamic port) and helpers to stub the
 * endpoints used by {@code CloudflareStreamClient}. Import {@link Config} so the cached Spring context points at it.
 */
public final class WireMockStreamSupport {

    public static final String ACCOUNT = "test-account";
    public static final String TOKEN = "test-token";
    public static final String CUSTOMER_CODE = "testcode";
    public static final String WEBHOOK_SECRET = "test-secret";

    public static final WireMockServer SERVER = new WireMockServer(options().dynamicPort());

    static {
        SERVER.start();
        Runtime.getRuntime().addShutdownHook(new Thread(SERVER::stop));
    }

    private WireMockStreamSupport() {}

    /** Sets {@code warbox.cloudflare.*} to point at the WireMock server. */
    public static void register(DynamicPropertyRegistry registry) {
        registry.add("warbox.cloudflare.api-base-url", SERVER::baseUrl);
        registry.add("warbox.cloudflare.account-id", () -> ACCOUNT);
        registry.add("warbox.cloudflare.api-token", () -> TOKEN);
        registry.add("warbox.cloudflare.stream-customer-code", () -> CUSTOMER_CODE);
        registry.add("warbox.cloudflare.webhook-secret", () -> WEBHOOK_SECRET);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        static DynamicPropertyRegistrar wireMockStreamProperties() {
            return WireMockStreamSupport::register;
        }
    }

    public static String streamPath() {
        return "/accounts/" + ACCOUNT + "/stream";
    }

    public static String videoPath(String uid) {
        return streamPath() + "/" + uid;
    }

    public static void stubCreateUpload(String uid, String location) {
        SERVER.stubFor(post(urlPathEqualTo(streamPath()))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Location", location)
                        .withHeader("stream-media-id", uid)
                        .withHeader("Tus-Resumable", "1.0.0")));
    }

    public static void stubCreateUploadFailure(int status) {
        SERVER.stubFor(post(urlPathEqualTo(streamPath()))
                .willReturn(aResponse().withStatus(status).withBody("{\"success\":false}")));
    }

    public static void stubGetVideo(String uid, String state, boolean ready, Number duration) {
        String body = """
                {"success":true,"result":{"uid":"%s","readyToStream":%s,"status":{"state":"%s"},"duration":%s}}
                """.formatted(uid, ready, state, duration);
        SERVER.stubFor(get(urlEqualTo(videoPath(uid)))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));
    }

    public static void stubGetVideoFailure(String uid, int status) {
        SERVER.stubFor(get(urlEqualTo(videoPath(uid))).willReturn(aResponse().withStatus(status)));
    }

    public static void stubSetSigned(String uid) {
        SERVER.stubFor(post(urlEqualTo(videoPath(uid)))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"result\":{\"uid\":\"" + uid + "\"}}")));
    }

    public static void stubDelete(String uid, int status) {
        SERVER.stubFor(delete(urlEqualTo(videoPath(uid))).willReturn(aResponse().withStatus(status)));
    }

    /** {@code Webhook-Signature} header value for the given raw body and unix-seconds timestamp. */
    public static String sign(String body, long timeSeconds) {
        return sign(WEBHOOK_SECRET, body, timeSeconds);
    }

    public static String sign(String secret, String body, long timeSeconds) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String sig = HexFormat.of().formatHex(mac.doFinal((timeSeconds + "." + body).getBytes(StandardCharsets.UTF_8)));
            return "time=" + timeSeconds + ",sig1=" + sig;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
