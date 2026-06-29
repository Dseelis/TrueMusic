package com.dseelis.tg.network.packets;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.client.AudioReceiver;
import com.dseelis.tg.client.StreamingAudioManager;
import com.dseelis.tg.config.TrueMusicServerConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record AudioChunkPacket(
    UUID resourceId,
    int chunkIndex,
    int totalChunks,
    byte[] data
) implements CustomPacketPayload {

    public static int getChunkSize() {
        return TrueMusicServerConfig.getChunkSize();
    }

    public static final Type<AudioChunkPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrueMusic.MODID, "audio_chunk"));

    public static final StreamCodec<ByteBuf, AudioChunkPacket> CODEC = StreamCodec.composite(
        UUIDCodec.STREAM_CODEC,
        AudioChunkPacket::resourceId,
        ByteBufCodecs.VAR_INT,
        AudioChunkPacket::chunkIndex,
        ByteBufCodecs.VAR_INT,
        AudioChunkPacket::totalChunks,
        ByteBufCodecs.BYTE_ARRAY,
        AudioChunkPacket::data,
        AudioChunkPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AudioChunkPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (StreamingAudioManager.getInstance().hasDownload(packet.resourceId())) {
                StreamingAudioManager.getInstance().receiveChunk(
                    packet.resourceId(),
                    packet.data()
                );

                if (packet.isLastChunk()) {
                    StreamingAudioManager.getInstance().completeDownload(packet.resourceId());
                }
            } else {
                AudioReceiver.getInstance().receiveChunk(
                    packet.resourceId(),
                    packet.chunkIndex(),
                    packet.totalChunks(),
                    packet.data()
                );
            }
        });
    }

    public boolean isLastChunk() {
        return chunkIndex == totalChunks - 1;
    }
}
