package com.warbox.video;

import com.warbox.account.AccountService;
import com.warbox.common.ApiException;
import com.warbox.creator.CreatorProfile;
import com.warbox.creator.CreatorProfileRepository;
import com.warbox.creator.HandlePolicy;
import com.warbox.video.dto.VideoDtos.CreateVideoRequest;
import com.warbox.video.dto.VideoDtos.CreateVideoResponse;
import com.warbox.video.dto.VideoDtos.CreatorVideoResponse;
import com.warbox.video.dto.VideoDtos.PublicVideoResponse;
import com.warbox.video.dto.VideoDtos.UpdateVideoRequest;
import com.warbox.video.stream.StreamClient;
import com.warbox.video.stream.StreamClient.DirectUpload;
import com.warbox.video.stream.StreamClient.StreamVideoInfo;
import com.warbox.video.stream.StreamClientException;
import com.warbox.video.stream.StreamProperties;
import com.warbox.video.stream.StreamUrls;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    private static final int ERROR_REASON_MAX = 100;

    private final VideoRepository videos;
    private final CreatorProfileRepository profiles;
    private final AccountService accountService;
    private final StreamClient stream;
    private final StreamUrls urls;
    private final StreamProperties properties;
    private final Clock clock;

    public VideoService(VideoRepository videos, CreatorProfileRepository profiles, AccountService accountService,
            StreamClient stream, StreamUrls urls, StreamProperties properties, Clock clock) {
        this.videos = videos;
        this.profiles = profiles;
        this.accountService = accountService;
        this.stream = stream;
        this.urls = urls;
        this.properties = properties;
        this.clock = clock;
    }

    public CreateVideoResponse create(UUID userId, CreateVideoRequest request) {
        accountService.requireVerifiedEmail(userId);
        CreatorProfile creator = requireProfile(userId);
        DirectUpload upload;
        try {
            upload = stream.createDirectUpload(request.sizeBytes(), properties.maxDurationSeconds(),
                    request.accessType() == VideoAccessType.EXCLUSIVE, request.title());
        } catch (StreamClientException ex) {
            throw providerUnavailable(ex);
        }
        Video video = new Video(creator.getId(), upload.uid(), request.title(), request.description(),
                request.accessType(), Instant.now(clock));
        videos.saveAndFlush(video);
        return new CreateVideoResponse(toCreatorResponse(video), upload.uploadUrl());
    }

    public List<CreatorVideoResponse> listMine(UUID userId) {
        CreatorProfile creator = requireProfile(userId);
        List<Video> mine = videos.findByCreatorIdOrderByCreatedAtDesc(creator.getId());
        mine.forEach(this::syncIfNotFinal);
        return mine.stream().map(this::toCreatorResponse).toList();
    }

    public CreatorVideoResponse getMine(UUID userId, UUID id) {
        Video video = requireOwned(userId, id);
        syncIfNotFinal(video);
        return toCreatorResponse(video);
    }

    public CreatorVideoResponse update(UUID userId, UUID id, UpdateVideoRequest request) {
        accountService.requireVerifiedEmail(userId);
        Video video = requireOwned(userId, id);
        if (video.getAccessType() != request.accessType()) {
            try {
                stream.setRequireSignedUrls(video.getCloudflareUid(), request.accessType() == VideoAccessType.EXCLUSIVE);
            } catch (StreamClientException ex) {
                throw providerUnavailable(ex);
            }
        }
        video.updateMetadata(request.title(), request.description(), request.accessType(), Instant.now(clock));
        videos.saveAndFlush(video);
        return toCreatorResponse(video);
    }

    public CreatorVideoResponse publish(UUID userId, UUID id) {
        accountService.requireVerifiedEmail(userId);
        Video video = requireOwned(userId, id);
        if (video.getVisibility() == VideoVisibility.PUBLISHED) {
            return toCreatorResponse(video);
        }
        if (video.getProcessingStatus() != VideoProcessingStatus.READY) {
            throw new ApiException(HttpStatus.CONFLICT, "VIDEO_NOT_READY", "O vídeo ainda está sendo processado.");
        }
        video.publish(Instant.now(clock));
        videos.saveAndFlush(video);
        return toCreatorResponse(video);
    }

    public CreatorVideoResponse unpublish(UUID userId, UUID id) {
        accountService.requireVerifiedEmail(userId);
        Video video = requireOwned(userId, id);
        video.unpublish(Instant.now(clock));
        videos.saveAndFlush(video);
        return toCreatorResponse(video);
    }

    public void delete(UUID userId, UUID id) {
        accountService.requireVerifiedEmail(userId);
        Video video = requireOwned(userId, id);
        try {
            stream.deleteVideo(video.getCloudflareUid());
        } catch (StreamClientException ex) {
            throw providerUnavailable(ex);
        }
        videos.delete(video);
        videos.flush();
    }

    @Transactional(readOnly = true)
    public List<PublicVideoResponse> listPublic(String rawHandle) {
        String handle = HandlePolicy.normalize(rawHandle);
        if (!HandlePolicy.isValidFormat(handle)) {
            throw creatorNotFound();
        }
        CreatorProfile creator = profiles.findByHandle(handle).orElseThrow(VideoService::creatorNotFound);
        return videos.findByCreatorIdAndVisibilityAndProcessingStatusOrderByPublishedAtDesc(
                        creator.getId(), VideoVisibility.PUBLISHED, VideoProcessingStatus.READY)
                .stream().map(this::toPublicResponse).toList();
    }

    /** Webhook entry point: updates the matching video; an unknown uid is ignored. */
    public void applyStreamStatus(StreamVideoInfo info) {
        if (info == null || info.uid() == null) {
            return;
        }
        videos.findByCloudflareUid(info.uid()).ifPresentOrElse(video -> {
            apply(video, info);
            videos.saveAndFlush(video);
        }, () -> log.debug("Ignoring Stream webhook for unknown uid"));
    }

    /** Lazy polling (dev): refreshes a non-final video from Cloudflare; failures are logged and ignored. */
    private void syncIfNotFinal(Video video) {
        if (video.isFinal()) {
            return;
        }
        try {
            StreamVideoInfo info = stream.getVideo(video.getCloudflareUid());
            apply(video, info);
            videos.saveAndFlush(video);
        } catch (StreamClientException ex) {
            log.warn("Could not sync video {} with Cloudflare Stream: {}", video.getId(), ex.getMessage());
        }
    }

    private void apply(Video video, StreamVideoInfo info) {
        VideoProcessingStatus status = VideoProcessingStatus.fromCloudflare(info.state(), info.readyToStream());
        String reason = info.errorReasonCode();
        if (reason != null && reason.length() > ERROR_REASON_MAX) {
            reason = reason.substring(0, ERROR_REASON_MAX);
        }
        video.applyStatus(status, info.durationSeconds(), reason, Instant.now(clock));
    }

    private CreatorProfile requireProfile(UUID userId) {
        return profiles.findByUserId(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "CREATOR_PROFILE_NOT_FOUND", "Você ainda não tem um perfil de criador."));
    }

    private Video requireOwned(UUID userId, UUID id) {
        CreatorProfile creator = requireProfile(userId);
        return videos.findByIdAndCreatorId(id, creator.getId()).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND", "Vídeo não encontrado."));
    }

    private static ApiException creatorNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "CREATOR_NOT_FOUND", "Criador não encontrado.");
    }

    private static ApiException providerUnavailable(StreamClientException ex) {
        log.warn("Cloudflare Stream call failed: {}", ex.getMessage(), ex);
        return new ApiException(HttpStatus.BAD_GATEWAY, "VIDEO_PROVIDER_UNAVAILABLE",
                "Não foi possível falar com o serviço de vídeo. Tente novamente.");
    }

    private boolean playable(Video video) {
        return video.getAccessType() == VideoAccessType.FREE
                && video.getProcessingStatus() == VideoProcessingStatus.READY;
    }

    private CreatorVideoResponse toCreatorResponse(Video v) {
        boolean playable = playable(v);
        return new CreatorVideoResponse(v.getId(), v.getTitle(), v.getDescription(), v.getAccessType(),
                v.getVisibility(), v.getProcessingStatus(), v.getDurationSeconds(),
                playable ? urls.thumbnailUrl(v.getCloudflareUid()) : null,
                playable ? urls.playerUrl(v.getCloudflareUid()) : null,
                v.getCreatedAt(), v.getUpdatedAt(), v.getPublishedAt());
    }

    private PublicVideoResponse toPublicResponse(Video v) {
        boolean locked = v.getAccessType() == VideoAccessType.EXCLUSIVE;
        return new PublicVideoResponse(v.getId(), v.getTitle(), v.getDescription(), v.getAccessType(),
                v.getDurationSeconds(), v.getPublishedAt(), locked,
                locked ? null : urls.thumbnailUrl(v.getCloudflareUid()),
                locked ? null : urls.playerUrl(v.getCloudflareUid()));
    }
}
