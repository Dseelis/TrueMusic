package com.dseelis.tg.network.packets;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.audio.Playlist;
import com.dseelis.tg.audio.PlaylistManager;
import com.dseelis.tg.block.SpeakerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Client -> Server: Assign (or clear) a playlist on a speaker.
 * playlistId == null means "play all tracks" (clear playlist filter).
 */
public record SpeakerSetPlaylistPacket(
    BlockPos pos,
    @Nullable UUID playlistId
) implements CustomPacketPayload {

    public static final Type<SpeakerSetPlaylistPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrueMusic.MODID, "speaker_set_playlist"));

    public static final StreamCodec<ByteBuf, SpeakerSetPlaylistPacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SpeakerSetPlaylistPacket::pos,
        UUIDCodec.STREAM_CODEC,
        SpeakerSetPlaylistPacket::playlistId,
        SpeakerSetPlaylistPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpeakerSetPlaylistPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            var level = player.serverLevel();
            if (!(level.getBlockEntity(packet.pos) instanceof SpeakerBlockEntity speaker)) return;

            speaker.setPlaylistId(packet.playlistId);

            // If a playlist is assigned, seek it to the current track if possible
            if (packet.playlistId != null) {
                Optional<Playlist> playlist = PlaylistManager.getInstance().getPlaylist(packet.playlistId);
                if (playlist.isPresent() && speaker.getPlayback().resourceId() != null) {
                    playlist.get().seekToTrack(speaker.getPlayback().resourceId());
                }
            }

            long serverTime = System.currentTimeMillis();
            PacketDistributor.sendToPlayersTrackingChunk(
                level,
                level.getChunkAt(packet.pos).getPos(),
                new SyncSpeakerStatePacket(
                    packet.pos,
                    speaker.getPlayback(),
                    speaker.getVolume(),
                    speaker.getPlayMode(),
                    serverTime
                )
            );
        });
    }
}
