package com.warbox.account;

import com.warbox.account.dto.AuthDtos.MeResponse;
import com.warbox.account.dto.AuthDtos.SignupRequest;
import com.warbox.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountService {

    private final UserRepository users;
    private final AccountTokenService tokens;
    private final AccountMailer mailer;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountService(UserRepository users, AccountTokenService tokens, AccountMailer mailer,
            PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.mailer = mailer;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Creates the account (terms -> age -> unique e-mail) and sends the verification e-mail. Does not log in. */
    public User signup(SignupRequest request) {
        if (!request.acceptedTerms()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TERMS_NOT_ACCEPTED",
                    "Você precisa aceitar os Termos de Uso e a Política de Privacidade.");
        }
        if (!AgePolicy.isAdult(request.birthDate(), LocalDate.now(clock))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNDERAGE",
                    "Você precisa ter pelo menos 18 anos para criar uma conta.");
        }
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        // Defensive: DTO validation already enforces this (BCrypt ignores bytes past 72).
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "A senha é longa demais.");
        }

        Instant now = clock.instant();
        User user;
        try {
            user = users.saveAndFlush(new User(request.name().trim(), email,
                    passwordEncoder.encode(request.password()), request.birthDate(), now));
        } catch (DataIntegrityViolationException ex) {
            // Concurrent signup with the same e-mail won the race for the unique constraint.
            throw emailTaken();
        }
        String rawToken = tokens.issue(user, TokenPurpose.EMAIL_VERIFICATION);
        mailer.sendVerification(user, rawToken);
        return user;
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(UUID userId) {
        return users.findById(userId).map(AccountService::toMe).orElseThrow(AccountService::unauthenticated);
    }

    public void verifyEmail(String rawToken) {
        User user = tokens.consume(rawToken, TokenPurpose.EMAIL_VERIFICATION);
        user.markEmailVerified(clock.instant());
    }

    public void resendVerification(UUID userId) {
        User user = users.findById(userId).orElseThrow(AccountService::unauthenticated);
        if (user.isEmailVerified()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_VERIFIED", "Seu e-mail já está confirmado.");
        }
        if (tokens.issuedWithinCooldown(userId, TokenPurpose.EMAIL_VERIFICATION)) {
            throw tooManyRequests();
        }
        mailer.sendVerification(user, tokens.issue(user, TokenPurpose.EMAIL_VERIFICATION));
    }

    /** Silent by design: never reveals whether the e-mail has an account. */
    public void requestPasswordReset(String email) {
        users.findByEmail(normalizeEmail(email))
                .filter(user -> !tokens.issuedWithinCooldown(user.getId(), TokenPurpose.PASSWORD_RESET))
                .ifPresent(user -> mailer.sendPasswordReset(user, tokens.issue(user, TokenPurpose.PASSWORD_RESET)));
    }

    /** Sets the new password, burns every reset token of the user and marks the e-mail verified. Does not log in. */
    public void resetPassword(String rawToken, String newPassword) {
        User user = tokens.consume(rawToken, TokenPurpose.PASSWORD_RESET);
        Instant now = clock.instant();
        user.changePasswordHash(passwordEncoder.encode(newPassword), now);
        // Receiving the reset link proves ownership of the mailbox.
        user.markEmailVerified(now);
        tokens.invalidatePending(user.getId(), TokenPurpose.PASSWORD_RESET);
    }

    /**
     * Guard for features that require a confirmed mailbox (e.g. activating the creator profile).
     *
     * @throws ApiException 403 EMAIL_NOT_VERIFIED, or 401 UNAUTHENTICATED if the user no longer exists
     */
    @Transactional(readOnly = true)
    public void requireVerifiedEmail(UUID userId) {
        User user = users.findById(userId).orElseThrow(AccountService::unauthenticated);
        if (!user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                    "Confirme seu e-mail para continuar.");
        }
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static MeResponse toMe(User user) {
        return new MeResponse(user.getId(), user.getName(), user.getEmail(), user.isEmailVerified());
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Já existe uma conta com este e-mail.");
    }

    private static ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Faça login para continuar.");
    }

    private static ApiException tooManyRequests() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                "Aguarde um minuto antes de pedir um novo e-mail.");
    }
}
