package com.warbox.video.stream;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code warbox.cloudflare.*}: Cloudflare Stream credentials and limits (values come from env vars). */
@ConfigurationProperties("warbox.cloudflare")
public record StreamProperties(
        String apiBaseUrl,
        String accountId,
        String apiToken,
        String streamCustomerCode,
        String webhookSecret,
        int maxDurationSeconds,
        Duration webhookTolerance) {}
