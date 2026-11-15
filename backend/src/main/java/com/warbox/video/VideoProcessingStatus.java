package com.warbox.video;

import java.util.Locale;

public enum VideoProcessingStatus {
    PENDING_UPLOAD,
    PROCESSING,
    READY,
    ERROR;

    /** Maps Cloudflare {@code status.state} (+ {@code readyToStream}) to our status. */
    public static VideoProcessingStatus fromCloudflare(String state, boolean readyToStream) {
        if (readyToStream) {
            return READY;
        }
        String normalized = state == null ? "" : state.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ready" -> READY;
            case "error" -> ERROR;
            case "downloading", "queued", "inprogress" -> PROCESSING;
            default -> PENDING_UPLOAD;
        };
    }

    public boolean isFinal() {
        return this == READY || this == ERROR;
    }
}
