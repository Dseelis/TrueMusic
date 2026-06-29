package com.dseelis.tg.audio;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record PlaybackState(
    @Nullable UUID resourceId,
    long anchorTimeMs,
    long positionAtAnchorMs,
    float speed
) {
    public static final PlaybackState STOPPED = new PlaybackState(null, 0, 0, 0f);

    public long getCurrentPositionMs(long currentTimeMs) {
        if (resourceId == null) return 0;
        long elapsed = currentTimeMs - anchorTimeMs;
        return Math.max(0, positionAtAnchorMs + (long)(elapsed * speed));
    }

    public boolean isPlaying() {
        return resourceId != null && speed > 0.01f;
    }

    public boolean isPaused() {
        return resourceId != null && speed < 0.01f;
    }

    public boolean isStopped() {
        return resourceId == null;
    }
}
