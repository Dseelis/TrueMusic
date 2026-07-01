package com.dseelis.tg.network.packets;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.audio.PlayMode;
import com.dseelis.tg.audio.PlaybackState;
import com.dseelis.tg.server.ServerPlayerMusicManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: control the personal music player.
 */
public record PlayerControlPacket(
    Action action,
    UUID resourceId    // null for STOP/PAUSE
) implements CustomPacketPayload {

    public enum Action { PLAY, PAUSE, STOP }

    public static final Type<PlayerControlPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrueMusic.MODID, "player_control"));

    private static final StreamCodec<ByteBuf, Action> ACTION_CODEC = StreamCodec.of(
        (buf, a) -> buf.writeByte(a.ordinal()),
        buf -> Action.values()[buf.readByte()]
    );

    public static final StreamCodec<ByteBuf, PlayerControlPacket> CODEC = StreamCodec.composite(
        ACTION_CODEC,            PlayerControlPacket::action,
        UUIDCodec.STREAM_CODEC,  PlayerControlPacket::resourceId,
        PlayerControlPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PlayerControlPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerPlayerMusicManager.getInstance().handleControl(player, packet);
        });
    }
}
