package com.warbox.video;

import com.warbox.security.AccountUserDetails;
import com.warbox.video.dto.VideoDtos.CreateVideoRequest;
import com.warbox.video.dto.VideoDtos.CreateVideoResponse;
import com.warbox.video.dto.VideoDtos.CreatorVideoResponse;
import com.warbox.video.dto.VideoDtos.UpdateVideoRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/creators/me/videos")
public class CreatorVideoController {

    private final VideoService videos;

    public CreatorVideoController(VideoService videos) {
        this.videos = videos;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateVideoResponse create(@AuthenticationPrincipal AccountUserDetails principal,
            @Valid @RequestBody CreateVideoRequest body) {
        return videos.create(principal.id(), body);
    }

    @GetMapping
    public List<CreatorVideoResponse> list(@AuthenticationPrincipal AccountUserDetails principal) {
        return videos.listMine(principal.id());
    }

    @GetMapping("/{id}")
    public CreatorVideoResponse get(@AuthenticationPrincipal AccountUserDetails principal, @PathVariable UUID id) {
        return videos.getMine(principal.id(), id);
    }

    @PutMapping("/{id}")
    public CreatorVideoResponse update(@AuthenticationPrincipal AccountUserDetails principal,
            @PathVariable UUID id, @Valid @RequestBody UpdateVideoRequest body) {
        return videos.update(principal.id(), id, body);
    }

    @PostMapping("/{id}/publish")
    public CreatorVideoResponse publish(@AuthenticationPrincipal AccountUserDetails principal,
            @PathVariable UUID id) {
        return videos.publish(principal.id(), id);
    }

    @PostMapping("/{id}/unpublish")
    public CreatorVideoResponse unpublish(@AuthenticationPrincipal AccountUserDetails principal,
            @PathVariable UUID id) {
        return videos.unpublish(principal.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AccountUserDetails principal, @PathVariable UUID id) {
        videos.delete(principal.id(), id);
    }
}
