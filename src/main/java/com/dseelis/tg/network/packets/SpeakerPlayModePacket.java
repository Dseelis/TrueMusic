package com.dseelis.tg.network.packets;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.audio.PlayMode;
import com.dseelis.tg.block.SpeakerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

public record SpeakerPlayModePacket(
    BlockPos pos,
    PlayMode playMode
) implements CustomPacketPayload {

    public static final Type<SpeakerPlayModePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrueMusic.MODID, "speaker_play_mode"));

    private static final StreamCodec<ByteBuf, PlayMode> PLAY_MODE_CODEC = StreamCodec.of(
        (buf, mode) -> buf.writeByte(mode.ordinal()),
        buf -> PlayMode.values()[buf.readByte()]
    );

    public static final StreamCodec<ByteBuf, SpeakerPlayModePacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SpeakerPlayModePacket::pos,
        PLAY_MODE_CODEC,
        SpeakerPlayModePacket::playMode,
        SpeakerPlayModePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpeakerPlayModePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            var level = player.serverLevel();
            if (!(level.getBlockEntity(packet.pos) instanceof SpeakerBlockEntity speaker)) return;

            speaker.setPlayMode(packet.playMode);

            PacketDistributor.sendToPlayersTrackingChunk(
                level,
                level.getChunkAt(packet.pos).getPos(),
                new SyncSpeakerStatePacket(packet.pos, speaker.getPlayback(), speaker.getVolume(), speaker.getPlayMode(), System.currentTimeMillis())
            );
        });
    }
}
