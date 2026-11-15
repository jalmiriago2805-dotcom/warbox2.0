package com.warbox.account.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/** Request/response records of {@code /api/auth}. Validation messages are pt-BR (shown to the user). */
public final class AuthDtos {

    private AuthDtos() {}

    public record SignupRequest(
            @NotBlank(message = "Informe seu nome.")
            @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
            String name,

            @NotBlank(message = "Informe seu e-mail.")
            @Email(message = "Informe um e-mail válido.")
            @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
            String email,

            @NotNull(message = "Informe uma senha.")
            @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
            @MaxUtf8Bytes(72)
            String password,

            @NotNull(message = "Informe sua data de nascimento.")
            @Past(message = "A data de nascimento deve estar no passado.")
            @NotBefore1900
            LocalDate birthDate,

            boolean acceptedTerms) {

        public SignupRequest {
            name = trim(name);
            email = trim(email);
        }
    }

    public record LoginRequest(
            @NotBlank(message = "Informe seu e-mail.") String email,
            @NotBlank(message = "Informe sua senha.") String password) {

        public LoginRequest {
            email = trim(email);
        }
    }

    public record VerifyEmailRequest(String token) {}

    public record PasswordResetRequest(
            @NotBlank(message = "Informe seu e-mail.")
            @Email(message = "Informe um e-mail válido.")
            @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
            String email) {

        public PasswordResetRequest {
            email = trim(email);
        }
    }

    public record PasswordResetConfirmRequest(
            String token,

            @NotNull(message = "Informe a nova senha.")
            @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
            @MaxUtf8Bytes(72)
            String newPassword) {}

    public record MeResponse(UUID id, String name, String email, boolean emailVerified) {}

    /** Names and e-mails are validated and stored without surrounding whitespace. Passwords are never trimmed. */
    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /** BCrypt only uses the first 72 bytes of the password, so longer UTF-8 encodings are rejected. */
    @Documented
    @Constraint(validatedBy = MaxUtf8BytesValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MaxUtf8Bytes {
        int value();

        String message() default "A senha é longa demais. Use até 72 bytes (caracteres acentuados e emojis contam mais).";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static final class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {
        private int max;

        @Override
        public void initialize(MaxUtf8Bytes annotation) {
            this.max = annotation.value();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
        }
    }

    @Documented
    @Constraint(validatedBy = NotBefore1900Validator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NotBefore1900 {
        String message() default "Informe uma data de nascimento a partir de 01/01/1900.";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static final class NotBefore1900Validator implements ConstraintValidator<NotBefore1900, LocalDate> {
        private static final LocalDate MIN = LocalDate.of(1900, 1, 1);

        @Override
        public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
            return value == null || !value.isBefore(MIN);
        }
    }
}
