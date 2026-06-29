package com.dseelis.tg.network.packets;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.client.StreamingAudioManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record AudioStreamStartPacket(
    UUID resourceId,
    byte[] headerBytes,
    int sampleRate,
    int startOffset,
    long startPositionMs
) implements CustomPacketPayload {

    public static final Type<AudioStreamStartPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrueMusic.MODID, "audio_stream_start"));

    public static final StreamCodec<ByteBuf, AudioStreamStartPacket> CODEC = StreamCodec.composite(
        UUIDCodec.STREAM_CODEC,
        AudioStreamStartPacket::resourceId,
        ByteBufCodecs.BYTE_ARRAY,
        AudioStreamStartPacket::headerBytes,
        ByteBufCodecs.VAR_INT,
        AudioStreamStartPacket::sampleRate,
        ByteBufCodecs.VAR_INT,
        AudioStreamStartPacket::startOffset,
        ByteBufCodecs.VAR_LONG,
        AudioStreamStartPacket::startPositionMs,
        AudioStreamStartPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AudioStreamStartPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StreamingAudioManager.getInstance().receiveHeader(
                packet.resourceId(),
                packet.headerBytes(),
                packet.sampleRate()
            );
        });
    }
}
