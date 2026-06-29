package com.dseelis.tg.client;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.audio.PlaybackState;
import com.dseelis.tg.audio.PlayMode;
import com.dseelis.tg.client.audio.StreamingAudioStream;
import com.dseelis.tg.platform.PlatformHelper;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Client-side speaker manager.
// Tracks all active speakers and their playback state.

public class ClientSpeakerManager {
    private static ClientSpeakerManager instance;

    private final Map<BlockPos, PlaybackState> speakers = new ConcurrentHashMap<>();
    private final Map<BlockPos, Float> speakerVolumes = new ConcurrentHashMap<>();
    private final Map<BlockPos, PlayMode> playModes = new ConcurrentHashMap<>();
    private final Map<BlockPos, UUID> activeStreams = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRequests = ConcurrentHashMap.newKeySet();

    private ClientSpeakerManager() {}

    public static ClientSpeakerManager getInstance() {
        if (instance == null) {
            instance = new ClientSpeakerManager();
        }
        return instance;
    }

    public void updateSpeaker(BlockPos pos, PlaybackState playback, float volume, PlayMode playMode) {
        speakerVolumes.put(pos, volume);
        playModes.put(pos, playMode);
        PlaybackState oldState = speakers.get(pos);

        if (oldState != null && !java.util.Objects.equals(oldState.resourceId(), playback.resourceId())) {
            stopAndCleanup(pos, oldState);
        }

        if (playback.isPlaying()) {
            speakers.put(pos, playback);
            startPlayback(pos, playback, volume);
        } else if (playback.isPaused()) {
            speakers.put(pos, playback);
            stopAndCleanup(pos, oldState);
        } else {
            speakers.remove(pos);
            stopAndCleanup(pos, oldState);
        }
    }

    public void updateSpeaker(BlockPos pos, PlaybackState playback, float volume) {
        updateSpeaker(pos, playback, volume, PlayMode.SINGLE);
    }

    private void stopAndCleanup(BlockPos pos, PlaybackState state) {
        PlatformHelper.INSTANCE.stopAudio(pos);

        UUID streamResourceId = activeStreams.remove(pos);
        if (streamResourceId != null) {
            StreamingAudioManager.getInstance().releaseDownload(streamResourceId);
        }
    }

    private void startPlayback(BlockPos pos, PlaybackState playback, float volume) {
        UUID resourceId = playback.resourceId();
        if (resourceId == null) return;

        if (AudioCache.getInstance().isCached(resourceId)) {
            PlatformHelper.INSTANCE.playAudio(pos, playback, resourceId, volume);
            return;
        }

        long currentPositionMs = playback.getCurrentPositionMs(System.currentTimeMillis());
        boolean canCache = currentPositionMs == 0;

        if (StreamingAudioManager.getInstance().isReady(resourceId)) {
            startStreamingPlayback(pos, resourceId, currentPositionMs, volume);
            return;
        }

        if (AudioReceiver.getInstance().isTransferInProgress(resourceId)) {
            AudioReceiver.getInstance().onTransferComplete(resourceId, file -> {
                PlaybackState current = speakers.get(pos);
                float currentVolume = speakerVolumes.getOrDefault(pos, 0.5f);
                if (current != null && current.isPlaying() && resourceId.equals(current.resourceId())) {
                    PlatformHelper.INSTANCE.playAudio(pos, current, resourceId, currentVolume);
                }
            });
            return;
        }

        if (pendingRequests.contains(resourceId)) {
            addReadyCallback(pos, resourceId);
            return;
        }

        pendingRequests.add(resourceId);

        StreamingAudioManager.getInstance().getOrCreateDownload(resourceId, canCache);
        activeStreams.put(pos, resourceId);

        PlatformHelper.INSTANCE.requestAudioFromServer(resourceId, currentPositionMs);
        TrueMusic.LOGGER.info("Requesting streaming audio {} from position {}ms", resourceId, currentPositionMs);

        addReadyCallback(pos, resourceId);
    }

    private void addReadyCallback(BlockPos pos, UUID resourceId) {
        StreamingAudioManager.getInstance().addReadyCallback(resourceId, session -> {
            pendingRequests.remove(resourceId);

            PlaybackState current = speakers.get(pos);
            if (current == null || !current.isPlaying() || !resourceId.equals(current.resourceId())) {
                return;
            }

            float volume = speakerVolumes.getOrDefault(pos, 0.5f);
            long positionMs = current.getCurrentPositionMs(System.currentTimeMillis());
            startStreamingPlayback(pos, resourceId, positionMs, volume);
        });
    }

    private void startStreamingPlayback(BlockPos pos, UUID resourceId, long positionMs, float volume) {
        StreamingAudioStream stream = StreamingAudioManager.getInstance().createStream(resourceId, positionMs);
        if (stream == null) {
            TrueMusic.LOGGER.error("Failed to create stream for {}", resourceId);
            return;
        }

        UUID oldResourceId = activeStreams.put(pos, resourceId);
        if (oldResourceId != null && !oldResourceId.equals(resourceId)) {
            StreamingAudioManager.getInstance().releaseDownload(oldResourceId);
        }

        PlatformHelper.INSTANCE.playStreamingAudio(pos, stream, volume);
    }

    public void updateVolume(BlockPos pos, float volume) {
        speakerVolumes.put(pos, volume);
        PlatformHelper.INSTANCE.setAudioVolume(pos, volume);
    }

    public Optional<PlaybackState> getSpeakerState(BlockPos pos) {
        return Optional.ofNullable(speakers.get(pos));
    }

    public float getSpeakerVolume(BlockPos pos) {
        return speakerVolumes.getOrDefault(pos, 0.5f);
    }

    public PlayMode getSpeakerPlayMode(BlockPos pos) {
        return playModes.getOrDefault(pos, PlayMode.SINGLE);
    }

    public void removeSpeaker(BlockPos pos) {
        PlaybackState oldState = speakers.remove(pos);
        speakerVolumes.remove(pos);
        playModes.remove(pos);
        stopAndCleanup(pos, oldState);
    }

    public void clearPendingRequests() {
        pendingRequests.clear();
    }

    public void onResourcesReloaded() {
        for (Map.Entry<BlockPos, PlaybackState> entry : speakers.entrySet()) {
            BlockPos pos = entry.getKey();
            PlaybackState state = entry.getValue();

            if (state.isPlaying()) {
                UUID resourceId = state.resourceId();
                float volume = speakerVolumes.getOrDefault(pos, 0.5f);

                if (AudioCache.getInstance().isCached(resourceId)) {
                    PlatformHelper.INSTANCE.playAudio(pos, state, resourceId, volume);
                    TrueMusic.debugLog("Restored playback at {} after resource reload", pos);
                }
            }
        }
    }
}
