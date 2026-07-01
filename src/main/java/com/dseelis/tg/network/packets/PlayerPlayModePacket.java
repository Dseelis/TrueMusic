package com.dseelis.tg.network.packets;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.audio.PlayMode;
import com.dseelis.tg.server.ServerPlayerMusicManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: change play mode for the personal music player.
 */
public record PlayerPlayModePacket(PlayMode playMode) implements CustomPacketPayload {

    public static final Type<PlayerPlayModePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrueMusic.MODID, "player_play_mode"));

    private static final StreamCodec<ByteBuf, PlayMode> PLAY_MODE_CODEC = StreamCodec.of(
        (buf, m) -> buf.writeByte(m.ordinal()),
        buf -> PlayMode.values()[buf.readByte()]
    );

    public static final StreamCodec<ByteBuf, PlayerPlayModePacket> CODEC = StreamCodec.composite(
        PLAY_MODE_CODEC, PlayerPlayModePacket::playMode,
        PlayerPlayModePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PlayerPlayModePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerPlayerMusicManager.getInstance().setPlayMode(player, packet.playMode());
        });
    }
}
