package com.dseelis.tg.network.packets;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.audio.PlaybackState;
import com.dseelis.tg.block.SpeakerBlockEntity;
import com.dseelis.tg.server.ServerSpeakerManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;


// Client -> Server: Control speaker (play/pause/stop).

public record SpeakerControlPacket(
    BlockPos pos,
    Action action,
    UUID resourceId
) implements CustomPacketPayload {

    public enum Action {
        PLAY,
        PAUSE,
        STOP
    }

    public static final Type<SpeakerControlPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrueMusic.MODID, "speaker_control"));

    private static final StreamCodec<ByteBuf, Action> ACTION_CODEC = StreamCodec.of(
        (buf, action) -> buf.writeByte(action.ordinal()),
        buf -> Action.values()[buf.readByte()]
    );

    public static final StreamCodec<ByteBuf, SpeakerControlPacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SpeakerControlPacket::pos,
        ACTION_CODEC,
        SpeakerControlPacket::action,
        UUIDCodec.STREAM_CODEC,
        SpeakerControlPacket::resourceId,
        SpeakerControlPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpeakerControlPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            var level = player.serverLevel();
            if (!(level.getBlockEntity(packet.pos) instanceof SpeakerBlockEntity speaker)) return;

            long serverTime = System.currentTimeMillis();
            PlaybackState current = speaker.getPlayback();
            PlaybackState newState;

            switch (packet.action) {
                case PLAY -> {
                    if (current.isPaused() && current.resourceId() != null
                        && current.resourceId().equals(packet.resourceId)) {
                        long pausedPos = current.positionAtAnchorMs();
                        newState = new PlaybackState(current.resourceId(), serverTime, pausedPos, 1.0f);
                    } else {
                        newState = new PlaybackState(packet.resourceId, serverTime, 0, 1.0f);
                    }

                    speaker.setPlayback(newState);
                    long durationMs = ServerSpeakerManager.getDurationMs(newState.resourceId());
                    ServerSpeakerManager.getInstance().registerSpeaker(
                        level.dimension(), packet.pos, newState, durationMs);
                }
                case PAUSE -> {
                    if (!current.isPlaying() || current.resourceId() == null) return;

                    long currentPos = current.getCurrentPositionMs(serverTime);
                    newState = new PlaybackState(current.resourceId(), serverTime, currentPos, 0f);

                    speaker.setPlayback(newState);
                    ServerSpeakerManager.getInstance().registerSpeaker(
                        level.dimension(), packet.pos, newState, -1);
                }
                case STOP -> {
                    newState = PlaybackState.STOPPED;
                    speaker.setPlayback(newState);
                    ServerSpeakerManager.getInstance().unregisterSpeaker(level.dimension(), packet.pos);
                }
                default -> {
                    return;
                }
            }

            PacketDistributor.sendToPlayersTrackingChunk(
                level,
                level.getChunkAt(packet.pos).getPos(),
                new SyncSpeakerStatePacket(packet.pos, newState, speaker.getVolume(), speaker.getPlayMode(), serverTime)
            );
        });
    }
}
