package com.warbox.video.stream;

import org.springframework.stereotype.Component;

/** Builds public playback URLs from the Stream customer code. */
@Component
public class StreamUrls {

    private final StreamProperties properties;

    public StreamUrls(StreamProperties properties) {
        this.properties = properties;
    }

    public String playerUrl(String uid) {
        return base() + "/" + uid + "/iframe";
    }

    public String thumbnailUrl(String uid) {
        return base() + "/" + uid + "/thumbnails/thumbnail.jpg";
    }

    private String base() {
        return "https://customer-" + properties.streamCustomerCode() + ".cloudflarestream.com";
    }
}
