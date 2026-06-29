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

    public void checkAndAdvanceTrack(long currentTimeMs) {
        if (level == null || level.isClientSide) return;
        if (!playback.isPlaying()) return;

        // Get current track duration
        UUID currentResourceId = playback.resourceId();
        if (currentResourceId == null) return;

        long durationMs = ServerSpeakerManager.getDurationMs(currentResourceId);
        if (durationMs <= 0) return;

        long currentPos = playback.getCurrentPositionMs(currentTimeMs);

        // Check if track finished (with 100ms tolerance)
        if (currentPos >= durationMs - 100) {
            advanceToNextTrack(currentTimeMs);
        }
    }

    private void advanceToNextTrack(long currentTimeMs) {
        Optional<Playlist> playlistOpt = getPlaylist();

        if (playlistOpt.isEmpty()) {
            // No playlist, handle simple modes
            if (playMode == PlayMode.LOOP) {
                // Restart current track
                playback = new PlaybackState(playback.resourceId(), currentTimeMs, 0, 1.0f);
                setChanged();
                syncToClients();
            } else {
                // Stop playback
                setPlayback(PlaybackState.STOPPED);
            }
            return;
        }

        Playlist playlist = playlistOpt.get();
        Optional<UUID> nextTrackId = playlist.getNextTrack(playMode);

        if (nextTrackId.isPresent()) {
            // Start next track
            playback = new PlaybackState(nextTrackId.get(), currentTimeMs, 0, 1.0f);
            setChanged();
            syncToClients();
        } else {
            // End of playlist
            setPlayback(PlaybackState.STOPPED);
        }
    }


    public void skipToNext() {
        if (level == null || level.isClientSide) return;
        advanceToNextTrack(System.currentTimeMillis());
    }


    public void skipToPrevious() {
        if (level == null || level.isClientSide) return;

        Optional<Playlist> playlistOpt = getPlaylist();
        if (playlistOpt.isEmpty()) return;

        Playlist playlist = playlistOpt.get();
        Optional<UUID> prevTrackId = playlist.getPreviousTrack();

        if (prevTrackId.isPresent()) {
            long currentTimeMs = System.currentTimeMillis();
            playback = new PlaybackState(prevTrackId.get(), currentTimeMs, 0, 1.0f);
            setChanged();
            syncToClients();
        }
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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
