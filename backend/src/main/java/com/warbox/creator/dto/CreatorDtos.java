package com.warbox.creator.dto;

import com.warbox.creator.HandlePolicy;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Instant;
import java.util.UUID;

public final class CreatorDtos {

    public static final String HANDLE_FORMAT_MESSAGE = "Use de 3 a 30 caracteres: letras minúsculas, números, "
            + "'-' ou '_', começando e terminando com letra ou número.";

    private CreatorDtos() {}

    public record CreatorProfileRequest(
            @NotBlank(message = "Informe um nome de usuário.")
            // Empty passes the pattern so that only @NotBlank reports it.
            @Pattern(regexp = "^$|" + HandlePolicy.REGEX, message = HANDLE_FORMAT_MESSAGE)
            @NotReservedHandle
            String handle,
            @NotBlank(message = "Informe o nome de exibição.")
            @Size(max = 60, message = "O nome de exibição deve ter no máximo 60 caracteres.")
            String displayName,
            @Size(max = 500, message = "A bio deve ter no máximo 500 caracteres.")
            String bio) {

        public CreatorProfileRequest {
            handle = HandlePolicy.normalize(handle);
            displayName = displayName == null ? null : displayName.trim();
            bio = bio == null || bio.isBlank() ? null : bio.trim();
        }
    }

    public record CreatorProfileResponse(UUID id, String handle, String displayName, String bio,
            Instant createdAt, Instant updatedAt) {}

    public record PublicCreatorResponse(String handle, String displayName, String bio, Instant createdAt) {}

    @Documented
    @Constraint(validatedBy = NotReservedHandleValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NotReservedHandle {
        String message() default "Este nome de usuário não está disponível.";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class NotReservedHandleValidator implements ConstraintValidator<NotReservedHandle, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return !HandlePolicy.isReserved(value);
        }
    }
}
