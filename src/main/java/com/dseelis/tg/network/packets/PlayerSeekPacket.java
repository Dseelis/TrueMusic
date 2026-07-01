package com.dseelis.tg.network.packets;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.server.ServerPlayerMusicManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: seek the personal music player to a position.
 */
public record PlayerSeekPacket(long positionMs) implements CustomPacketPayload {

    public static final Type<PlayerSeekPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrueMusic.MODID, "player_seek"));

    public static final StreamCodec<ByteBuf, PlayerSeekPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, PlayerSeekPacket::positionMs,
        PlayerSeekPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PlayerSeekPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerPlayerMusicManager.getInstance().handleSeek(player, packet.positionMs());
        });
    }
}
