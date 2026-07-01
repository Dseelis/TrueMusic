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

// Client -> Server: skip to next or previous track on personal music player.
public record PlayerSkipPacket(boolean forward) implements CustomPacketPayload {
    public static final Type<PlayerSkipPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrueMusic.MODID, "player_skip"));

    public static final StreamCodec<ByteBuf, PlayerSkipPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, PlayerSkipPacket::forward,
        PlayerSkipPacket::new
    );

    @Override
    public Type<PlayerSkipPacket> type() { return TYPE; }

    public static void handle(PlayerSkipPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerPlayerMusicManager.getInstance().handleSkip(player, packet.forward());
        });
    }
}
