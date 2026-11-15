package com.warbox.account;

import com.warbox.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountTokenService {

    public static final String INVALID_OR_EXPIRED_TOKEN = "INVALID_OR_EXPIRED_TOKEN";

    private static final int TOKEN_BYTES = 32;
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final AccountTokenRepository tokens;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AccountTokenService(AccountTokenRepository tokens, AuthProperties properties, Clock clock) {
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    /** Invalidates pending tokens of the same purpose and returns a fresh raw token (never persisted). */
    public String issue(User user, TokenPurpose purpose) {
        Instant now = clock.instant();
        tokens.invalidatePending(user.getId(), purpose, now);

        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawToken = TOKEN_ENCODER.encodeToString(bytes);

        tokens.save(new AccountToken(user, purpose, sha256Hex(rawToken), now, now.plus(properties.ttlFor(purpose))));
        return rawToken;
    }

    /**
     * Atomically consumes a token: the row is locked, so two concurrent calls cannot both succeed.
     *
     * @throws ApiException 400 INVALID_OR_EXPIRED_TOKEN when blank, unknown, expired or already used
     */
    public User consume(String rawToken, TokenPurpose purpose) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        Instant now = clock.instant();
        AccountToken token = tokens.findByTokenHashAndPurpose(sha256Hex(rawToken.trim()), purpose)
                .orElseThrow(AccountTokenService::invalidToken);
        if (!token.isUsableAt(now)) {
            throw invalidToken();
        }
        token.consume(now);
        return token.getUser();
    }

    /** Whether a token of this purpose was issued to the user less than {@code warbox.auth.email-cooldown} ago. */
    @Transactional(readOnly = true)
    public boolean issuedWithinCooldown(UUID userId, TokenPurpose purpose) {
        Instant threshold = clock.instant().minus(properties.emailCooldown());
        return tokens.findLatestCreatedAt(userId, purpose).map(threshold::isBefore).orElse(false);
    }

    public void invalidatePending(UUID userId, TokenPurpose purpose) {
        tokens.invalidatePending(userId, purpose, clock.instant());
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, INVALID_OR_EXPIRED_TOKEN,
                "Este link é inválido ou expirou. Solicite um novo.");
    }
}
