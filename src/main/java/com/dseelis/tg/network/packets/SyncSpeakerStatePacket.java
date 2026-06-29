package com.dseelis.tg.network.packets;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.audio.PlaybackState;
import com.dseelis.tg.audio.PlayMode;
import com.dseelis.tg.client.ClientSpeakerManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncSpeakerStatePacket(
    BlockPos pos,
    PlaybackState playback,
    float volume,
    PlayMode playMode,
    long serverTimeMs
) implements CustomPacketPayload {

    public static final Type<SyncSpeakerStatePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrueMusic.MODID, "sync_speaker_state"));

    private static final StreamCodec<ByteBuf, PlayMode> PLAY_MODE_CODEC = StreamCodec.of(
        (buf, mode) -> buf.writeByte(mode.ordinal()),
        buf -> PlayMode.values()[buf.readByte()]
    );

    public static final StreamCodec<ByteBuf, SyncSpeakerStatePacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SyncSpeakerStatePacket::pos,
        StreamCodec.composite(
            UUIDCodec.STREAM_CODEC,
            PlaybackState::resourceId,
            ByteBufCodecs.VAR_LONG,
            PlaybackState::anchorTimeMs,
            ByteBufCodecs.VAR_LONG,
            PlaybackState::positionAtAnchorMs,
            ByteBufCodecs.FLOAT,
            PlaybackState::speed,
            PlaybackState::new
        ),
        SyncSpeakerStatePacket::playback,
        ByteBufCodecs.FLOAT,
        SyncSpeakerStatePacket::volume,
        PLAY_MODE_CODEC,
        SyncSpeakerStatePacket::playMode,
        ByteBufCodecs.VAR_LONG,
        SyncSpeakerStatePacket::serverTimeMs,
        SyncSpeakerStatePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncSpeakerStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            long clientTimeMs = System.currentTimeMillis();
            long clockOffset = packet.serverTimeMs - clientTimeMs;

            PlaybackState adjusted = new PlaybackState(
                packet.playback.resourceId(),
                packet.playback.anchorTimeMs() - clockOffset,
                packet.playback.positionAtAnchorMs(),
                packet.playback.speed()
            );

            ClientSpeakerManager.getInstance().updateSpeaker(packet.pos, adjusted, packet.volume, packet.playMode);
        });
    }
}
