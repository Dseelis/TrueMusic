package com.dseelis.tg.network.packets;

import com.dseelis.tg.audio.Playlist;
import com.dseelis.tg.audio.PlaylistManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.dseelis.tg.TrueMusic.MODID;

// Packet to sync playlists from server to client.

public record SyncPlaylistsPacket(List<PlaylistData> playlists) implements CustomPacketPayload {
    public static final Type<SyncPlaylistsPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "sync_playlists"));

    public static final StreamCodec<ByteBuf, SyncPlaylistsPacket> STREAM_CODEC = StreamCodec.composite(
        PlaylistData.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncPlaylistsPacket::playlists,
        SyncPlaylistsPacket::new
    );

    @Override
    public Type<SyncPlaylistsPacket> type() {
        return TYPE;
    }

    public static void handle(SyncPlaylistsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().isClientSide) {
                PlaylistManager manager = PlaylistManager.getInstance();
                manager.clear();

                for (PlaylistData data : packet.playlists) {
                    Playlist playlist = new Playlist(data.id, data.name, data.trackIds, data.currentIndex);
                    manager.addPlaylist(playlist);
                }
            }
        });
    }

    public record PlaylistData(UUID id, String name, List<UUID> trackIds, int currentIndex) {
        public static final StreamCodec<ByteBuf, PlaylistData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(net.minecraft.core.UUIDUtil.CODEC), PlaylistData::id,
            ByteBufCodecs.STRING_UTF8, PlaylistData::name,
            ByteBufCodecs.fromCodec(net.minecraft.core.UUIDUtil.CODEC).apply(ByteBufCodecs.list()), PlaylistData::trackIds,
            ByteBufCodecs.VAR_INT, PlaylistData::currentIndex,
            PlaylistData::new
        );

        public static PlaylistData fromPlaylist(Playlist playlist) {
            return new PlaylistData(
                playlist.getId(),
                playlist.getName(),
                new ArrayList<>(playlist.getTrackIds()),
                playlist.getCurrentIndex()
            );
        }
    }
}
