package com.dseelis.tg.platform;

import com.dseelis.tg.audio.PlaybackState;
import com.dseelis.tg.client.audio.StreamingAudioStream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;


//Platform abstraction layer.

public interface PlatformHelper {

    PlatformHelper INSTANCE = new NeoForgePlatformHelper();

    String getPlatformName();

    boolean isClient();

    boolean isServer();

    // Network
    void sendToClient(ServerPlayer player, Object packet);

    void sendToServer(Object packet);

    void sendToAllTracking(Level level, BlockPos pos, Object packet);

    // Client-side audio
    void playAudio(BlockPos pos, PlaybackState playback, UUID resourceId, float volume);

    void stopAudio(BlockPos pos);

    void setAudioVolume(BlockPos pos, float volume);

    void stopAllAudio();

    void playStreamingAudio(BlockPos pos, StreamingAudioStream stream, float volume);

    // Client-side utilities
    void runOnClient(Runnable task);

    void requestAudioFromServer(UUID resourceId, long startPositionMs);

    default void requestAudioFromServer(UUID resourceId) {
        requestAudioFromServer(resourceId, 0);
    }
}
