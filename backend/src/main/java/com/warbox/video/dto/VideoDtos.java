package com.warbox.video.dto;

import com.warbox.video.VideoAccessType;
import com.warbox.video.VideoProcessingStatus;
import com.warbox.video.VideoVisibility;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class VideoDtos {

    public static final long MAX_SIZE_BYTES = 32_212_254_720L; // 30 GiB

    private static final String TITLE_REQUIRED = "Informe o título.";
    private static final String TITLE_TOO_LONG = "O título deve ter no máximo 100 caracteres.";
    private static final String DESCRIPTION_TOO_LONG = "A descrição deve ter no máximo 5000 caracteres.";
    private static final String ACCESS_REQUIRED = "Informe o tipo de acesso.";
    private static final String SIZE_INVALID = "Arquivo inválido ou maior que 30 GB.";

    private VideoDtos() {}

    public record CreateVideoRequest(
            @NotBlank(message = TITLE_REQUIRED) @Size(max = 100, message = TITLE_TOO_LONG) String title,
            @Size(max = 5000, message = DESCRIPTION_TOO_LONG) String description,
            @NotNull(message = ACCESS_REQUIRED) VideoAccessType accessType,
            @NotNull(message = SIZE_INVALID) @Min(value = 1, message = SIZE_INVALID)
            @Max(value = MAX_SIZE_BYTES, message = SIZE_INVALID) Long sizeBytes) {

        public CreateVideoRequest {
            title = trimToNull(title);
            description = trimToNull(description);
        }
    }

    public record UpdateVideoRequest(
            @NotBlank(message = TITLE_REQUIRED) @Size(max = 100, message = TITLE_TOO_LONG) String title,
            @Size(max = 5000, message = DESCRIPTION_TOO_LONG) String description,
            @NotNull(message = ACCESS_REQUIRED) VideoAccessType accessType) {

        public UpdateVideoRequest {
            title = trimToNull(title);
            description = trimToNull(description);
        }
    }

    public record CreatorVideoResponse(
            UUID id,
            String title,
            String description,
            VideoAccessType accessType,
            VideoVisibility visibility,
            VideoProcessingStatus processingStatus,
            Integer durationSeconds,
            String thumbnailUrl,
            String playerUrl,
            Instant createdAt,
            Instant updatedAt,
            Instant publishedAt) {}

    public record CreateVideoResponse(CreatorVideoResponse video, String uploadUrl) {}

    public record PublicVideoResponse(
            UUID id,
            String title,
            String description,
            VideoAccessType accessType,
            Integer durationSeconds,
            Instant publishedAt,
            boolean locked,
            String thumbnailUrl,
            String playerUrl) {}

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
