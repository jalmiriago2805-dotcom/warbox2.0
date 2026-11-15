package com.warbox.account;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code warbox.auth.*}: lifetimes of e-mail tokens and the minimum interval between e-mails of the same kind. */
@ConfigurationProperties("warbox.auth")
public record AuthProperties(Duration emailVerificationTtl, Duration passwordResetTtl, Duration emailCooldown) {

    public Duration ttlFor(TokenPurpose purpose) {
        return switch (purpose) {
            case EMAIL_VERIFICATION -> emailVerificationTtl;
            case PASSWORD_RESET -> passwordResetTtl;
        };
    }
}
