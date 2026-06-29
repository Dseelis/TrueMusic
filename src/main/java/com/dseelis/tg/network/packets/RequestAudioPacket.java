package com.dseelis.tg.network.packets;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.server.AudioTransferManager;
import com.dseelis.tg.server.ServerAudioStorage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record RequestAudioPacket(UUID resourceId, long startPositionMs) implements CustomPacketPayload {

    public RequestAudioPacket(UUID resourceId) {
        this(resourceId, 0);
    }

    public static final Type<RequestAudioPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrueMusic.MODID, "request_audio"));

    public static final StreamCodec<ByteBuf, RequestAudioPacket> CODEC = StreamCodec.composite(
        UUIDCodec.STREAM_CODEC,
        RequestAudioPacket::resourceId,
        ByteBufCodecs.VAR_LONG,
        RequestAudioPacket::startPositionMs,
        RequestAudioPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestAudioPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                if (ServerAudioStorage.getInstance().hasAudio(packet.resourceId)) {
                    AudioTransferManager.getInstance().queueTransfer(serverPlayer, packet.resourceId, packet.startPositionMs);
                    TrueMusic.debugLog("Player {} requested audio {} at position {}ms",
                        serverPlayer.getName().getString(), packet.resourceId, packet.startPositionMs);
                } else {
                    TrueMusic.LOGGER.warn("Player {} requested unavailable audio {}",
                        serverPlayer.getName().getString(), packet.resourceId);
                }
            }
        });
    }
}
