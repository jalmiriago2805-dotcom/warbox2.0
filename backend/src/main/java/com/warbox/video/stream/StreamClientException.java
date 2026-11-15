package com.warbox.video.stream;

/** Any failure talking to Cloudflare Stream (network, non-2xx, missing configuration, unexpected payload). */
public class StreamClientException extends RuntimeException {

    public StreamClientException(String message) {
        super(message);
    }

    public StreamClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
