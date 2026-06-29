package com.dseelis.tg.network.packets;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.block.SpeakerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

public record SpeakerVolumePacket(
    BlockPos pos,
    float volume
) implements CustomPacketPayload {

    public static final Type<SpeakerVolumePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrueMusic.MODID, "speaker_volume"));

    public static final StreamCodec<ByteBuf, SpeakerVolumePacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SpeakerVolumePacket::pos,
        ByteBufCodecs.FLOAT,
        SpeakerVolumePacket::volume,
        SpeakerVolumePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpeakerVolumePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            var level = player.serverLevel();
            if (!(level.getBlockEntity(packet.pos) instanceof SpeakerBlockEntity speaker)) return;

            speaker.setVolume(packet.volume);

            PacketDistributor.sendToPlayersTrackingChunk(
                level,
                level.getChunkAt(packet.pos).getPos(),
                new SyncSpeakerVolumePacket(packet.pos, packet.volume)
            );
        });
    }
}
