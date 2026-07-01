package com.dseelis.tg.block;

import com.dseelis.tg.audio.*;
import com.dseelis.tg.client.ClientSpeakerManager;
import com.dseelis.tg.server.ServerSpeakerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class SpeakerBlockEntity extends BlockEntity {
    private static final float DEFAULT_VOLUME = 0.5f;

    private static Supplier<BlockEntityType<SpeakerBlockEntity>> typeSupplier;
    private PlaybackState playback = PlaybackState.STOPPED;
    private float volume = DEFAULT_VOLUME;
    private PlayMode playMode = PlayMode.SINGLE;
    private UUID playlistId = null;

    public static void setTypeSupplier(Supplier<BlockEntityType<SpeakerBlockEntity>> supplier) {
        typeSupplier = supplier;
    }

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(typeSupplier.get(), pos, state);
    }

    public PlaybackState getPlayback() {
        return playback;
    }

    public void setPlayback(PlaybackState playback) {
        this.playback = playback;
        setChanged();

        if (level != null) {
            BlockState state = getBlockState();
            boolean shouldPlay = playback.isPlaying();
            if (state.hasProperty(SpeakerBlock.PLAYING) && state.getValue(SpeakerBlock.PLAYING) != shouldPlay) {
                level.setBlock(worldPosition, state.setValue(SpeakerBlock.PLAYING, shouldPlay), 3);
            }
        }
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = volume;
        setChanged();
    }

    public PlayMode getPlayMode() {
        return playMode;
    }

    public void setPlayMode(PlayMode playMode) {
        this.playMode = playMode;
        setChanged();
    }

    public UUID getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(UUID playlistId) {
        this.playlistId = playlistId;
        setChanged();
    }

    public Optional<Playlist> getPlaylist() {
        if (playlistId == null) {
            return Optional.empty();
        }
        return PlaylistManager.getInstance().getPlaylist(playlistId);
    }

    // guard: UUID of the track for which we already called advanceToNextTrack
    private UUID lastAdvancedResourceId = null;

    public void checkAndAdvanceTrack(long currentTimeMs) {
        if (level == null || level.isClientSide) return;
        if (!playback.isPlaying()) return;

        UUID currentResourceId = playback.resourceId();
        if (currentResourceId == null) return;

        if (!currentResourceId.equals(lastAdvancedResourceId)) {
            lastAdvancedResourceId = null;
        }

        long durationMs = ServerSpeakerManager.getDurationMs(currentResourceId);
        if (durationMs <= 0) return;

        long currentPos = playback.getCurrentPositionMs(currentTimeMs);

        if (currentPos >= durationMs - 100 && lastAdvancedResourceId == null) {
            lastAdvancedResourceId = currentResourceId;
            advanceToNextTrack(currentTimeMs);
        }
    }

    private void advanceToNextTrack(long currentTimeMs) {
        // Ensure we have a valid playlist — playlistId may point to a stale
        // in-memory entry (e.g. after server restart). Recreate if missing.
        if (playlistId != null && PlaylistManager.getInstance().getPlaylist(playlistId).isEmpty()) {
            playlistId = null;
        }

        if (playlistId == null) {
            Playlist allTracks = PlaylistManager.getInstance().getOrCreatePlaylistFromAllResources("All Tracks");
            if (playback.resourceId() != null) allTracks.seekToTrack(playback.resourceId());
            playlistId = allTracks.getId();
            setChanged();
        }

        Playlist playlist = getPlaylist().orElse(null);
        if (playlist == null) {
            setPlayback(PlaybackState.STOPPED);
            return;
        }

        Optional<UUID> next = playlist.getNextTrack(playMode);
        if (next.isPresent()) {
            lastAdvancedResourceId = null;
            playback = new PlaybackState(next.get(), currentTimeMs, 0, 1.0f);
            setChanged();
            syncToClients();
        } else {
            setPlayback(PlaybackState.STOPPED);
        }
    }

    public void skipToNext() {
        if (level == null || level.isClientSide) return;
        if (playlistId != null && PlaylistManager.getInstance().getPlaylist(playlistId).isEmpty()) {
            playlistId = null;
        }
        if (playlistId == null) {
            Playlist allTracks = PlaylistManager.getInstance().getOrCreatePlaylistFromAllResources("All Tracks");
            if (playback.resourceId() != null) allTracks.seekToTrack(playback.resourceId());
            playlistId = allTracks.getId();
            setChanged();
        }
        getPlaylist().ifPresent(playlist -> {
            Optional<UUID> next = playlist.getNextTrack(PlayMode.SEQUENTIAL);
            if (next.isPresent()) {
                long now = System.currentTimeMillis();
                lastAdvancedResourceId = null;
                playback = new PlaybackState(next.get(), now, 0, 1.0f);
                setChanged();
                syncToClients();
            }
        });
    }

    public void skipToPrevious() {
        if (level == null || level.isClientSide) return;
        if (playlistId != null && PlaylistManager.getInstance().getPlaylist(playlistId).isEmpty()) {
            playlistId = null;
        }
        if (playlistId == null) {
            Playlist allTracks = PlaylistManager.getInstance().getOrCreatePlaylistFromAllResources("All Tracks");
            if (playback.resourceId() != null) allTracks.seekToTrack(playback.resourceId());
            playlistId = allTracks.getId();
            setChanged();
        }
        getPlaylist().ifPresent(playlist -> {
            Optional<UUID> prev = playlist.getPreviousTrack();
            if (prev.isPresent()) {
                long now = System.currentTimeMillis();
                lastAdvancedResourceId = null;
                playback = new PlaybackState(prev.get(), now, 0, 1.0f);
                setChanged();
                syncToClients();
            }
        });
    }

    private void syncToClients() {
        if (level == null || level.isClientSide) return;
        // Use ServerSpeakerManager callback so clients get a proper SyncSpeakerStatePacket
        // with the correct serverTimeMs anchor, not stale NBT data.
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            ServerSpeakerManager.getInstance().fireSyncCallback(serverLevel, worldPosition, this);
        }
    }

    @Override
    public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        if (level != null) {
            if (!level.isClientSide) {
                if (playback.isPlaying() || playback.isPaused()) {
                    ServerSpeakerManager.getInstance().registerSpeaker(
                        level.dimension(),
                        worldPosition,
                        playback,
                        ServerSpeakerManager.getDurationMs(playback.resourceId())
                    );
                }
            } else {
                ClientSpeakerManager.getInstance().updateSpeaker(worldPosition, playback, volume);
            }
        }
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            if (level.isClientSide) {
                ClientSpeakerManager.getInstance().removeSpeaker(worldPosition);
            } else {
                ServerSpeakerManager.getInstance()
                    .unregisterSpeaker(level.dimension(), worldPosition);
            }
        }
        super.setRemoved();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, provider);
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);

        tag.putFloat("volume", volume);
        tag.putString("playMode", playMode.name());

        if (playlistId != null) {
            tag.putUUID("playlistId", playlistId);
        }

        if (playback.resourceId() != null) {
            tag.putUUID("resourceId", playback.resourceId());
            long now = System.currentTimeMillis();
            long effectivePos = playback.getCurrentPositionMs(now);
            tag.putLong("position", effectivePos);
            tag.putFloat("speed", playback.speed());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);

        volume = tag.contains("volume") ? tag.getFloat("volume") : DEFAULT_VOLUME;

        if (tag.contains("playMode")) {
            try {
                playMode = PlayMode.valueOf(tag.getString("playMode"));
            } catch (Exception e) {
                playMode = PlayMode.SINGLE;
            }
        } else {
            playMode = PlayMode.SINGLE;
        }

        if (tag.contains("playlistId")) {
            try {
                playlistId = tag.getUUID("playlistId");
            } catch (Exception e) {
                playlistId = null;
            }
        } else {
            playlistId = null;
        }

        try {
            if (!tag.contains("resourceId")) {
                playback = PlaybackState.STOPPED;
                return;
            }

            UUID resourceId = tag.getUUID("resourceId");
            if (resourceId == null) {
                playback = PlaybackState.STOPPED;
                return;
            }

            long savedPos = tag.getLong("position");
            float speed = tag.contains("speed") ? tag.getFloat("speed") : 1.0f;

            long now = System.currentTimeMillis();
            playback = new PlaybackState(resourceId, now, savedPos, speed);

            if (level != null && level.isClientSide) {
                ClientSpeakerManager.getInstance().updateSpeaker(worldPosition, playback, volume);
            }
        } catch (Exception e) {
            playback = PlaybackState.STOPPED;
        }
    }
}
