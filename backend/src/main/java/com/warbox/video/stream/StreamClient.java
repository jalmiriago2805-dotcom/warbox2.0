package com.warbox.video.stream;

/** Port to Cloudflare Stream. Every failure is reported as {@link StreamClientException}. */
public interface StreamClient {

    /** tus direct-creator-upload: returns the Cloudflare video uid and the URL the browser uploads to. */
    DirectUpload createDirectUpload(long sizeBytes, int maxDurationSeconds, boolean requireSignedUrls, String name);

    StreamVideoInfo getVideo(String uid);

    void setRequireSignedUrls(String uid, boolean value);

    /** Deletes the video; a video that no longer exists (404) is not an error. */
    void deleteVideo(String uid);

    record DirectUpload(String uid, String uploadUrl) {}

    /** Subset of the Cloudflare video object (API response or webhook body). */
    record StreamVideoInfo(String uid, String state, boolean readyToStream, Integer durationSeconds,
            String errorReasonCode) {}
}
