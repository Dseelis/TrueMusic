package com.dseelis.tg.network.packets;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.audio.PlayMode;
import com.dseelis.tg.audio.PlaybackState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: sync the player's personal music player state.
 */
public record SyncPlayerStatePacket(
    PlaybackState playback,
    float volume,
    PlayMode playMode,
    boolean broadcasting,
    long serverTimeMs
) implements CustomPacketPayload {

    public static final Type<SyncPlayerStatePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrueMusic.MODID, "sync_player_state"));

    private static final StreamCodec<ByteBuf, PlayMode> PLAY_MODE_CODEC = StreamCodec.of(
        (buf, m) -> buf.writeByte(m.ordinal()),
        buf -> PlayMode.values()[buf.readByte()]
    );

    public static final StreamCodec<ByteBuf, SyncPlayerStatePacket> CODEC = StreamCodec.composite(
        StreamCodec.composite(
            UUIDCodec.STREAM_CODEC,      PlaybackState::resourceId,
            ByteBufCodecs.VAR_LONG,      PlaybackState::anchorTimeMs,
            ByteBufCodecs.VAR_LONG,      PlaybackState::positionAtAnchorMs,
            ByteBufCodecs.FLOAT,         PlaybackState::speed,
            PlaybackState::new
        ),
        SyncPlayerStatePacket::playback,
        ByteBufCodecs.FLOAT,
        SyncPlayerStatePacket::volume,
        PLAY_MODE_CODEC,
        SyncPlayerStatePacket::playMode,
        ByteBufCodecs.BOOL,
        SyncPlayerStatePacket::broadcasting,
        ByteBufCodecs.VAR_LONG,
        SyncPlayerStatePacket::serverTimeMs,
        SyncPlayerStatePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncPlayerStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;

            long clientTime = System.currentTimeMillis();
            long offset = packet.serverTimeMs() - clientTime;

            PlaybackState adjusted = new PlaybackState(
                packet.playback().resourceId(),
                packet.playback().anchorTimeMs() - offset,
                packet.playback().positionAtAnchorMs(),
                packet.playback().speed()
            );

            var mgr = com.dseelis.tg.client.ClientPlayerMusicManager.getInstance();
            mgr.setState(adjusted);
            mgr.setVolume(packet.volume());
            mgr.setPlayMode(packet.playMode());
            mgr.setBroadcasting(packet.broadcasting());

            // Use a virtual BlockPos derived from the player's UUID for headphones audio.
            // This delegates to the existing ClientSpeakerManager streaming pipeline.
            net.minecraft.core.BlockPos virtualPos = getVirtualPos(mc.player.getUUID());
            com.dseelis.tg.client.ClientSpeakerManager.getInstance()
                .updateSpeaker(virtualPos, adjusted, packet.volume(), packet.playMode());
        });
    }

    // Derives a deterministic virtual BlockPos from a player UUID.
    // Used as a key for headphones audio in ClientSpeakerManager.
    // Y = -65535 so it never collides with real blocks.
    public static net.minecraft.core.BlockPos getVirtualPos(java.util.UUID uuid) {
        int x = (int) (uuid.getMostSignificantBits()  & 0x7FFF);
        int z = (int) (uuid.getLeastSignificantBits() & 0x7FFF);
        return new net.minecraft.core.BlockPos(x, -65535, z);
    }
}
