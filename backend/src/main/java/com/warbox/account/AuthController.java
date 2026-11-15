package com.warbox.account;

import com.warbox.account.dto.AuthDtos.LoginRequest;
import com.warbox.account.dto.AuthDtos.MeResponse;
import com.warbox.account.dto.AuthDtos.PasswordResetConfirmRequest;
import com.warbox.account.dto.AuthDtos.PasswordResetRequest;
import com.warbox.account.dto.AuthDtos.SignupRequest;
import com.warbox.account.dto.AuthDtos.VerifyEmailRequest;
import com.warbox.common.ApiException;
import com.warbox.security.AccountUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Account endpoints. Logout is handled by Spring Security's LogoutFilter (POST /api/auth/logout). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accountService;
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public AuthController(AccountService accountService, AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository) {
        this.accountService = accountService;
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
    }

    /** Forces the XSRF-TOKEN cookie to be issued. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/signup")
    public ResponseEntity<MeResponse> signup(@Valid @RequestBody SignupRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        User user = accountService.signup(body);
        Authentication authentication = authenticate(user.getEmail(), body.password());
        establishSession(authentication, request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountService.toMe(user));
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication = authenticate(body.email(), body.password());
        establishSession(authentication, request, response);
        return accountService.getMe(((AccountUserDetails) authentication.getPrincipal()).id());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AccountUserDetails principal) {
        return accountService.getMe(principal.id());
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestBody VerifyEmailRequest body) {
        accountService.verifyEmail(body == null ? null : body.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email/resend")
    public ResponseEntity<Void> resendVerification(@AuthenticationPrincipal AccountUserDetails principal) {
        accountService.resendVerification(principal.id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest body) {
        accountService.requestPasswordReset(body.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest body) {
        accountService.resetPassword(body.token(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    private Authentication authenticate(String email, String password) {
        try {
            return authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(AccountService.normalizeEmail(email), password));
        } catch (AuthenticationException ex) {
            // Same answer for unknown e-mail and wrong password.
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "E-mail ou senha incorretos.");
        }
    }

    /** Session fixation protection + CSRF rotation, then persists the SecurityContext in the HTTP session. */
    private void establishSession(Authentication authentication, HttpServletRequest request,
            HttpServletResponse response) {
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
