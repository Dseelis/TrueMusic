package com.dseelis.tg.server;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.audio.AudioManager;
import com.dseelis.tg.audio.AudioResource;
import com.dseelis.tg.audio.PlaybackState;
import com.dseelis.tg.block.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


// Server speaker manager.
// Tracks all active speakers and checks for song ends.

public class ServerSpeakerManager {
    private static ServerSpeakerManager instance;

    private final Map<SpeakerKey, SpeakerInfo> activeSpeakers = new ConcurrentHashMap<>();

    private SyncCallback syncCallback;

    @FunctionalInterface
    public interface SyncCallback {
        void onSpeakerSynced(ServerLevel level, BlockPos pos, SpeakerBlockEntity speaker);
    }

    private ServerSpeakerManager() {}

    public static ServerSpeakerManager getInstance() {
        if (instance == null) {
            instance = new ServerSpeakerManager();
        }
        return instance;
    }

    public static void reset() {
        instance = null;
    }

    public void setSyncCallback(SyncCallback callback) {
        this.syncCallback = callback;
    }

// Register active speaker.

    public void registerSpeaker(ResourceKey<Level> dimension, BlockPos pos, PlaybackState state, long durationMs) {
        if (!state.isPlaying() && !state.isPaused()) {
            activeSpeakers.remove(new SpeakerKey(dimension, pos));
            return;
        }
        activeSpeakers.put(new SpeakerKey(dimension, pos), new SpeakerInfo(state, durationMs));
    }

// Unregister speaker.

    public void unregisterSpeaker(ResourceKey<Level> dimension, BlockPos pos) {
        activeSpeakers.remove(new SpeakerKey(dimension, pos));
    }

// Tick every 20 ticks (1 second) to check playback end.

    public void tick(MinecraftServer server, long tickCount) {
        if (tickCount % 20 != 0) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<SpeakerKey, SpeakerInfo>> it = activeSpeakers.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<SpeakerKey, SpeakerInfo> entry = it.next();
            SpeakerKey key = entry.getKey();
            SpeakerInfo info = entry.getValue();

            if (info.state.isPaused()) continue;
            if (info.durationMs <= 0) continue;

            long currentPos = info.state.getCurrentPositionMs(now);
            if (currentPos >= info.durationMs) {
                it.remove();
                handlePlaybackFinished(server, key, info);
            }
        }
    }

    private void handlePlaybackFinished(MinecraftServer server, SpeakerKey key, SpeakerInfo info) {
        ServerLevel level = server.getLevel(key.dimension);
        if (level == null) return;

        if (!(level.getBlockEntity(key.pos) instanceof SpeakerBlockEntity speaker)) return;

        UUID oldResourceId = info.state.resourceId();
        speaker.checkAndAdvanceTrack(System.currentTimeMillis());

        PlaybackState newState = speaker.getPlayback();

        if (newState.isPlaying() && newState.resourceId() != null) {
            if (!newState.resourceId().equals(oldResourceId)) {
                // Track actually changed — re-register with new anchor and sync
                long duration = getDurationMs(newState.resourceId());
                registerSpeaker(key.dimension, key.pos, newState, duration);
                syncSpeakerState(level, key.pos, speaker);
            }
            // else: guard blocked advance — don't re-register with stale state
            // track will stop naturally on client
        }
        // stopped: setPlayback already called syncToClients
    }

    private void stopSpeaker(MinecraftServer server, SpeakerKey key) {
        ServerLevel level = server.getLevel(key.dimension);
        if (level == null) return;

        if (!(level.getBlockEntity(key.pos) instanceof SpeakerBlockEntity speaker)) return;

        speaker.setPlayback(PlaybackState.STOPPED);
        syncSpeakerState(level, key.pos, speaker);

        TrueMusic.debugLog("Auto-stopped speaker at {} in {}", key.pos, key.dimension.location());
    }

    public void fireSyncCallback(ServerLevel level, BlockPos pos, SpeakerBlockEntity speaker) {
        if (syncCallback != null) {
            syncCallback.onSpeakerSynced(level, pos, speaker);
        }
    }

    private void syncSpeakerState(ServerLevel level, BlockPos pos, SpeakerBlockEntity speaker) {
        if (syncCallback != null) {
            syncCallback.onSpeakerSynced(level, pos, speaker);
        }
    }

    public static long getDurationMs(java.util.UUID resourceId) {
        Optional<AudioResource> resource = AudioManager.getInstance().getResource(resourceId);
        return resource.map(AudioResource::durationMs).orElse(-1L);
    }

    public Map<BlockPos, SpeakerSyncData> getActiveSpeakersInDimension(ResourceKey<Level> dimension) {
        Map<BlockPos, SpeakerSyncData> result = new java.util.HashMap<>();
        long now = System.currentTimeMillis();

        for (var entry : activeSpeakers.entrySet()) {
            if (entry.getKey().dimension.equals(dimension)) {
                result.put(entry.getKey().pos, new SpeakerSyncData(entry.getValue().state, now));
            }
        }
        return result;
    }

    public record SpeakerSyncData(PlaybackState state, long serverTimeMs) {}

    private record SpeakerKey(ResourceKey<Level> dimension, BlockPos pos) {}

    private record SpeakerInfo(PlaybackState state, long durationMs) {}
}
