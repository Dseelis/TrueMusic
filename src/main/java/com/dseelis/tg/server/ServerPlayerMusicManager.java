package com.dseelis.tg.server;

import com.dseelis.tg.audio.*;
import com.dseelis.tg.network.packets.*;
import com.dseelis.tg.platform.PlatformHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Server-side manager for personal music player state.
// One entry per player UUID.
public class ServerPlayerMusicManager {
    private static ServerPlayerMusicManager instance;

    // Per-player state
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();

    private ServerPlayerMusicManager() {}

    public static ServerPlayerMusicManager getInstance() {
        if (instance == null) instance = new ServerPlayerMusicManager();
        return instance;
    }

    public static void reset() { instance = null; }

    private PlayerState getOrCreate(UUID uuid) {
        return states.computeIfAbsent(uuid, k -> new PlayerState());
    }

    public void remove(UUID uuid) {
        states.remove(uuid);
    }

    public void handleSeek(ServerPlayer player, long positionMs) {
        PlayerState st = getOrCreate(player.getUUID());
        if (st.playback.isStopped() || st.playback.resourceId() == null) return;

        long now = System.currentTimeMillis();
        st.playback = new PlaybackState(st.playback.resourceId(), now, positionMs, st.playback.speed());
        syncToPlayer(player, st);

        if (st.broadcasting) broadcastState(player, st);
    }

    public void handleControl(ServerPlayer player, PlayerControlPacket packet) {
        PlayerState st = getOrCreate(player.getUUID());
        long now = System.currentTimeMillis();

        switch (packet.action()) {
            case PLAY -> {
                PlaybackState current = st.playback;
                PlaybackState newState;

                if (current.isPaused() && current.resourceId() != null
                        && current.resourceId().equals(packet.resourceId())) {
                    newState = new PlaybackState(current.resourceId(), now,
                        current.positionAtAnchorMs(), 1.0f);
                } else {
                    newState = new PlaybackState(packet.resourceId(), now, 0, 1.0f);
                    // If a playlist exists, seek to this track
                    if (st.playlistId != null) {
                        PlaylistManager.getInstance().getPlaylist(st.playlistId)
                            .ifPresent(pl -> pl.seekToTrack(packet.resourceId()));
                    }
                }

                st.playback = newState;
                long dur = ServerSpeakerManager.getDurationMs(newState.resourceId());
                st.durationMs = dur;
            }
            case PAUSE -> {
                if (!st.playback.isPlaying()) return;
                long pos = st.playback.getCurrentPositionMs(now);
                st.playback = new PlaybackState(st.playback.resourceId(), now, pos, 0f);
            }
            case STOP -> {
                st.playback = PlaybackState.STOPPED;
                st.durationMs = -1;
            }
        }

        syncToPlayer(player, st);

        // If broadcasting, also make it audible to nearby players via a virtual speaker position
        if (st.broadcasting && st.playback.isPlaying()) {
            broadcastState(player, st);
        }
    }

    public void setBroadcast(ServerPlayer player, boolean broadcasting) {
        PlayerState st = getOrCreate(player.getUUID());
        st.broadcasting = broadcasting;
        syncToPlayer(player, st);

        if (!broadcasting) {
            // Stop broadcast for other players
            stopBroadcastForOthers(player);
        } else if (st.playback.isPlaying()) {
            broadcastState(player, st);
        }
    }

    public void handleSkip(ServerPlayer player, boolean forward) {
        PlayerState st = getOrCreate(player.getUUID());
        if (st.playback.isStopped() && st.playback.resourceId() == null) return;

        long now = System.currentTimeMillis();

        // Ensure playlist exists
        if (st.playlistId == null) {
            Playlist allTracks = PlaylistManager.getInstance().getOrCreatePlaylistFromAllResources("All Tracks");
            if (st.playback.resourceId() != null) allTracks.seekToTrack(st.playback.resourceId());
            st.playlistId = allTracks.getId();
        }

        PlaylistManager.getInstance().getPlaylist(st.playlistId).ifPresent(playlist -> {
            Optional<UUID> next = forward
                ? playlist.getNextTrack(st.playMode == PlayMode.SINGLE ? PlayMode.SEQUENTIAL : st.playMode)
                : playlist.getPreviousTrack();
            if (next.isPresent()) {
                st.playback = new PlaybackState(next.get(), now, 0, 1.0f);
                st.durationMs = ServerSpeakerManager.getDurationMs(next.get());
                st.lastAdvancedResourceId = null; // reset guard for new track
                syncToPlayer(player, st);
                if (st.broadcasting && st.playback.isPlaying()) broadcastState(player, st);
            }
        });
    }

    public void setPlayMode(ServerPlayer player, PlayMode mode) {
        PlayerState st = getOrCreate(player.getUUID());
        st.playMode = mode;
        syncToPlayer(player, st);
    }

    public void setPlaylist(ServerPlayer player, UUID playlistId) {
        getOrCreate(player.getUUID()).playlistId = playlistId;
    }

    /** Called every server tick to check if track finished. */
    public void tick(long tickCount) {
        if (tickCount % 20 != 0) return;

        long now = System.currentTimeMillis();
        // We don't have direct access to ServerPlayer from here,
        // so we store server ref and iterate. Track advancement is handled
        // when the client next sends a control packet or via explicit tick below.
        // For simplicity: auto-advance is done via SpeakerBlockEntity-style logic
        // but we'd need the server reference. We handle it in TrueMusic tick.
    }

    /**
     * Called from TrueMusic server tick with a reference to the server,
     * so we can advance tracks.
     */
    public void tick(net.minecraft.server.MinecraftServer server, long tickCount) {
        if (tickCount % 20 != 0) return;
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, PlayerState> entry : states.entrySet()) {
            PlayerState st = entry.getValue();
            if (!st.playback.isPlaying()) continue;
            if (st.durationMs <= 0) continue;

            UUID currentId = st.playback.resourceId();

            // Reset guard when a new track started
            if (!java.util.Objects.equals(currentId, st.lastAdvancedResourceId)) {
                st.lastAdvancedResourceId = null;
            }

            long pos = st.playback.getCurrentPositionMs(now);
            if (pos >= st.durationMs - 100 && st.lastAdvancedResourceId == null) {
                st.lastAdvancedResourceId = currentId; // mark before advancing
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    advanceTrack(player, st, now);
                }
            }
        }
    }

    private void advanceTrack(ServerPlayer player, PlayerState st, long now) {
        if (st.playlistId == null) {
            switch (st.playMode) {
                case LOOP, SHUFFLE -> {
                    // no playlist = single track, just restart
                    st.playback = new PlaybackState(st.playback.resourceId(), now, 0, 1.0f);
                    st.durationMs = ServerSpeakerManager.getDurationMs(st.playback.resourceId());
                }
                case SEQUENTIAL -> {
                    // Auto-create playlist from all server resources so sequential works
                    Playlist allTracks = PlaylistManager.getInstance()
                        .getOrCreatePlaylistFromAllResources("All Tracks");
                    if (st.playback.resourceId() != null)
                        allTracks.seekToTrack(st.playback.resourceId());
                    st.playlistId = allTracks.getId();
                    Optional<UUID> next = allTracks.getNextTrack(PlayMode.SEQUENTIAL);
                    if (next.isPresent()) {
                        st.playback = new PlaybackState(next.get(), now, 0, 1.0f);
                        st.durationMs = ServerSpeakerManager.getDurationMs(next.get());
                    } else {
                        st.playback = PlaybackState.STOPPED;
                        st.durationMs = -1;
                    }
                }
                default -> {
                    st.playback = PlaybackState.STOPPED;
                    st.durationMs = -1;
                }
            }
            syncToPlayer(player, st);
            return;
        }

        PlaylistManager.getInstance().getPlaylist(st.playlistId).ifPresentOrElse(
            playlist -> {
                Optional<UUID> next = playlist.getNextTrack(st.playMode);
                if (next.isPresent()) {
                    st.playback = new PlaybackState(next.get(), now, 0, 1.0f);
                    st.durationMs = ServerSpeakerManager.getDurationMs(next.get());
                } else {
                    st.playback = PlaybackState.STOPPED;
                    st.durationMs = -1;
                }
                syncToPlayer(player, st);
                if (st.broadcasting && st.playback.isPlaying()) broadcastState(player, st);
            },
            () -> {
                st.playback = PlaybackState.STOPPED;
                syncToPlayer(player, st);
            }
        );
    }

    private void syncToPlayer(ServerPlayer player, PlayerState st) {
        long now = System.currentTimeMillis();
        PacketDistributor.sendToPlayer(player, new SyncPlayerStatePacket(
            st.playback, st.volume, st.playMode, st.broadcasting, now
        ));
    }

    /**
     * Send a SyncSpeakerStatePacket to all nearby players using the
     * personal player's position as the "virtual speaker".
     * This reuses the existing speaker client-side infrastructure.
     */
    private void broadcastState(ServerPlayer owner, PlayerState st) {
        BlockPos virtualPos = owner.blockPosition();
        long now = System.currentTimeMillis();

        SyncSpeakerStatePacket packet = new SyncSpeakerStatePacket(
            virtualPos, st.playback, st.volume, st.playMode, now
        );

        // Send to all players within range (64 blocks)
        owner.serverLevel().players().forEach(p -> {
            if (p.distanceTo(owner) <= 64.0f) {
                PacketDistributor.sendToPlayer((ServerPlayer) p, packet);
            }
        });
    }

    private void stopBroadcastForOthers(ServerPlayer owner) {
        BlockPos virtualPos = owner.blockPosition();
        long now = System.currentTimeMillis();

        SyncSpeakerStatePacket stopPacket = new SyncSpeakerStatePacket(
            virtualPos, PlaybackState.STOPPED, 0f, PlayMode.SINGLE, now
        );

        owner.serverLevel().players().forEach(p -> {
            if (!p.getUUID().equals(owner.getUUID()) && p.distanceTo(owner) <= 64.0f) {
                PacketDistributor.sendToPlayer((ServerPlayer) p, stopPacket);
            }
        });
    }

    private static class PlayerState {
        PlaybackState playback = PlaybackState.STOPPED;
        float volume = 0.7f;
        PlayMode playMode = PlayMode.SEQUENTIAL;
        boolean broadcasting = false;
        UUID playlistId = null;
        long durationMs = -1;
        // guard: track UUID for which we already called advanceTrack, reset on new track
        UUID lastAdvancedResourceId = null;
    }
}
