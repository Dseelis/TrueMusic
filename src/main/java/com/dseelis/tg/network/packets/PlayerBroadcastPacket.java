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
 * Client -> Server: toggle broadcast mode for the music player.
 * When enabled, audio is streamed to all nearby players as a "virtual speaker"
 * at the player's position.
 */
public record PlayerBroadcastPacket(boolean broadcasting) implements CustomPacketPayload {

    public static final Type<PlayerBroadcastPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrueMusic.MODID, "player_broadcast"));

    public static final StreamCodec<ByteBuf, PlayerBroadcastPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, PlayerBroadcastPacket::broadcasting,
        PlayerBroadcastPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PlayerBroadcastPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerPlayerMusicManager.getInstance().setBroadcast(player, packet.broadcasting());
        });
    }
}
