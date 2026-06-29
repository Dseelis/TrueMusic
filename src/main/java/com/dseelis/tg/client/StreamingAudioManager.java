package com.dseelis.tg.client;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.client.audio.SharedAudioBuffer;
import com.dseelis.tg.client.audio.StreamingAudioStream;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

// Manages audio downloads by resourceId.

public class StreamingAudioManager {
    private static final StreamingAudioManager instance = new StreamingAudioManager();
    private static final long CLEANUP_DELAY_MS = 5000;
    private static final int MIN_DATA_FOR_PLAYBACK = 16 * 1024;

    private final Map<UUID, AudioDownloadSession> downloads = new ConcurrentHashMap<>();
    private final Map<UUID, List<Consumer<AudioDownloadSession>>> readyCallbacks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "TrueMusic-StreamingCleanup");
        t.setDaemon(true);
        return t;
    });

    private Path cacheDir;

    public static StreamingAudioManager getInstance() {
        return instance;
    }

    public void setCacheDir(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    public AudioDownloadSession getOrCreateDownload(UUID resourceId, boolean canCache) {
        AudioDownloadSession session = downloads.computeIfAbsent(resourceId,
            id -> new AudioDownloadSession(id, canCache));
        session.addRef();
        return session;
    }

    public AudioDownloadSession getDownload(UUID resourceId) {
        return downloads.get(resourceId);
    }

    public boolean isReady(UUID resourceId) {
        AudioDownloadSession session = downloads.get(resourceId);
        return session != null && session.getBuffer().hasEnoughData(MIN_DATA_FOR_PLAYBACK);
    }

    public void receiveHeader(UUID resourceId, byte[] headerBytes, int sampleRate) {
        AudioDownloadSession session = downloads.get(resourceId);
        if (session == null) {
            TrueMusic.LOGGER.warn("Received header for unknown session: {}", resourceId);
            return;
        }

        session.receiveHeader(headerBytes, sampleRate);
    }

    public void receiveChunk(UUID resourceId, byte[] data) {
        AudioDownloadSession session = downloads.get(resourceId);
        if (session == null) return;

        session.receiveChunk(data);
        tryNotifyCallbacks(resourceId, session);
    }

    private void tryNotifyCallbacks(UUID resourceId, AudioDownloadSession session) {
        if (!session.getBuffer().hasEnoughData(MIN_DATA_FOR_PLAYBACK)) {
            return;
        }

        List<Consumer<AudioDownloadSession>> callbacks = readyCallbacks.remove(resourceId);
        if (callbacks != null) {
            for (Consumer<AudioDownloadSession> callback : callbacks) {
                try {
                    callback.accept(session);
                } catch (Exception e) {
                    TrueMusic.LOGGER.error("Error in ready callback", e);
                }
            }
        }
    }

    public void completeDownload(UUID resourceId) {
        AudioDownloadSession session = downloads.get(resourceId);
        if (session != null) {
            session.markComplete();

            if (cacheDir != null && session.canCache()) {
                session.saveToCache(cacheDir);
            }
        }
    }

    public StreamingAudioStream createStream(UUID resourceId, long startPositionMs) {
        AudioDownloadSession session = downloads.get(resourceId);
        if (session == null || !session.getBuffer().hasEnoughData(MIN_DATA_FOR_PLAYBACK)) {
            return null;
        }
        return new StreamingAudioStream(session.getBuffer(), startPositionMs);
    }

    public void releaseDownload(UUID resourceId) {
        AudioDownloadSession session = downloads.get(resourceId);
        if (session == null) return;

        int remaining = session.release();
        if (remaining <= 0) {
            scheduleCleanup(resourceId);
        }
    }

    private void scheduleCleanup(UUID resourceId) {
        scheduler.schedule(() -> {
            AudioDownloadSession session = downloads.get(resourceId);
            if (session != null && session.getRefCount() <= 0) {
                downloads.remove(resourceId);
                session.close();
                TrueMusic.debugLog("Cleaned up download session: {}", resourceId);
            }
        }, CLEANUP_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    public void addReadyCallback(UUID resourceId, Consumer<AudioDownloadSession> callback) {
        AudioDownloadSession session = downloads.get(resourceId);
        if (session != null && session.getBuffer().hasEnoughData(MIN_DATA_FOR_PLAYBACK)) {
            callback.accept(session);
            return;
        }

        readyCallbacks.computeIfAbsent(resourceId, k -> new ArrayList<>()).add(callback);
    }

    public boolean hasDownload(UUID resourceId) {
        return downloads.containsKey(resourceId);
    }

    public void endSession(UUID resourceId) {
        AudioDownloadSession session = downloads.remove(resourceId);
        if (session != null) {
            readyCallbacks.remove(resourceId);
            session.close();
        }
    }

    public void clear() {
        for (AudioDownloadSession session : downloads.values()) {
            session.close();
        }
        downloads.clear();
        readyCallbacks.clear();
    }
}
