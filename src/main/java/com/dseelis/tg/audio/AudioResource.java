package com.dseelis.tg.audio;

import java.util.UUID;


public record AudioResource(
    UUID id,
    String name,
    String url,
    long durationMs,
    long sizeBytes
) {
    // 4-parameter constructor for backward compatibility
    public AudioResource(UUID id, String name, String url, long durationMs) {
        this(id, name, url, durationMs, 0L);
    }

    public AudioResource(String name, String url, long durationMs) {
        this(UUID.randomUUID(), name, url, durationMs, 0L);
    }

    public AudioResource(String name, String url) {
        this(UUID.randomUUID(), name, url, -1L, 0L);
    }
}
