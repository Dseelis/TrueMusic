package com.dseelis.tg.network.packets;

import com.dseelis.tg.block.SpeakerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.dseelis.tg.TrueMusic.MODID;

// Packet for speaker playlist skip controls (next/previous track).

public record SpeakerSkipPacket(BlockPos pos, boolean forward) implements CustomPacketPayload {
    public static final Type<SpeakerSkipPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "speaker_skip"));

    public static final StreamCodec<ByteBuf, SpeakerSkipPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SpeakerSkipPacket::pos,
        ByteBufCodecs.BOOL, SpeakerSkipPacket::forward,
        SpeakerSkipPacket::new
    );

    @Override
    public Type<SpeakerSkipPacket> type() {
        return TYPE;
    }

    public static void handle(SpeakerSkipPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (!(be instanceof SpeakerBlockEntity speaker)) return;

            if (packet.forward) {
                speaker.skipToNext();
            } else {
                speaker.skipToPrevious();
            }
        });
    }
}
