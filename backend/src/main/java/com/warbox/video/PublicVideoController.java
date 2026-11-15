package com.warbox.video;

import com.warbox.video.dto.VideoDtos.PublicVideoResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicVideoController {

    private final VideoService videos;

    public PublicVideoController(VideoService videos) {
        this.videos = videos;
    }

    @GetMapping("/api/creators/{handle}/videos")
    public List<PublicVideoResponse> list(@PathVariable String handle) {
        return videos.listPublic(handle);
    }
}
