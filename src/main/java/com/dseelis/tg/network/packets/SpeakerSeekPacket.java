package com.dseelis.tg.network.packets;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.audio.PlaybackState;
import com.dseelis.tg.block.SpeakerBlockEntity;
import com.dseelis.tg.server.ServerSpeakerManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

public record SpeakerSeekPacket(
    BlockPos pos,
    long seekPositionMs
) implements CustomPacketPayload {

    public static final Type<SpeakerSeekPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrueMusic.MODID, "speaker_seek"));

    public static final StreamCodec<ByteBuf, SpeakerSeekPacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SpeakerSeekPacket::pos,
        ByteBufCodecs.VAR_LONG,
        SpeakerSeekPacket::seekPositionMs,
        SpeakerSeekPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpeakerSeekPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            var level = player.serverLevel();
            if (!(level.getBlockEntity(packet.pos) instanceof SpeakerBlockEntity speaker)) return;

            PlaybackState current = speaker.getPlayback();
            if (current.resourceId() == null) return;

            long serverTime = System.currentTimeMillis();

            PlaybackState newState = new PlaybackState(
                current.resourceId(),
                serverTime,
                packet.seekPositionMs,
                current.speed()
            );

            speaker.setPlayback(newState);

            if (newState.isPlaying()) {
                long durationMs = ServerSpeakerManager.getDurationMs(newState.resourceId());
                ServerSpeakerManager.getInstance().registerSpeaker(
                    level.dimension(), packet.pos, newState, durationMs);
            }

            PacketDistributor.sendToPlayersTrackingChunk(
                level,
                level.getChunkAt(packet.pos).getPos(),
                new SyncSpeakerStatePacket(packet.pos, newState, speaker.getVolume(), speaker.getPlayMode(), serverTime)
            );
        });
    }
}
