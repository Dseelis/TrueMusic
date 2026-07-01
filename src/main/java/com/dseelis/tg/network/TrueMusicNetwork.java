package com.dseelis.tg.network;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.network.packets.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// Network registration for TrueMusic.

public class TrueMusicNetwork {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TrueMusic.MODID)
            .versioned("1.0.0")
            .optional();

        // Server -> Client packets
        registrar.playToClient(
            SyncAudioResourcesPacket.TYPE,
            SyncAudioResourcesPacket.CODEC,
            SyncAudioResourcesPacket::handle
        );

        registrar.playToClient(
            SyncSpeakerStatePacket.TYPE,
            SyncSpeakerStatePacket.CODEC,
            SyncSpeakerStatePacket::handle
        );

        registrar.playToClient(
            AudioChunkPacket.TYPE,
            AudioChunkPacket.CODEC,
            AudioChunkPacket::handle
        );

        registrar.playToClient(
            SyncSpeakerVolumePacket.TYPE,
            SyncSpeakerVolumePacket.CODEC,
            SyncSpeakerVolumePacket::handle
        );

        registrar.playToClient(
            AudioStreamStartPacket.TYPE,
            AudioStreamStartPacket.CODEC,
            AudioStreamStartPacket::handle
        );

        registrar.playToClient(
            SyncPlaylistsPacket.TYPE,
            SyncPlaylistsPacket.STREAM_CODEC,
            SyncPlaylistsPacket::handle
        );

        // Client -> Server packets
        registrar.playToServer(
            RequestAudioPacket.TYPE,
            RequestAudioPacket.CODEC,
            RequestAudioPacket::handle
        );

        registrar.playToServer(
            SpeakerControlPacket.TYPE,
            SpeakerControlPacket.CODEC,
            SpeakerControlPacket::handle
        );

        registrar.playToServer(
            SpeakerSeekPacket.TYPE,
            SpeakerSeekPacket.CODEC,
            SpeakerSeekPacket::handle
        );

        registrar.playToServer(
            SpeakerVolumePacket.TYPE,
            SpeakerVolumePacket.CODEC,
            SpeakerVolumePacket::handle
        );

        registrar.playToServer(
            SpeakerPlayModePacket.TYPE,
            SpeakerPlayModePacket.CODEC,
            SpeakerPlayModePacket::handle
        );

        registrar.playToServer(
            SpeakerSkipPacket.TYPE,
            SpeakerSkipPacket.STREAM_CODEC,
            SpeakerSkipPacket::handle
        );

        registrar.playToServer(
            SpeakerSetPlaylistPacket.TYPE,
            SpeakerSetPlaylistPacket.CODEC,
            SpeakerSetPlaylistPacket::handle
        );

        // Music Player item packets
        registrar.playToClient(
            SyncPlayerStatePacket.TYPE,
            SyncPlayerStatePacket.CODEC,
            SyncPlayerStatePacket::handle
        );

        registrar.playToServer(
            PlayerControlPacket.TYPE,
            PlayerControlPacket.CODEC,
            PlayerControlPacket::handle
        );

        registrar.playToServer(
            PlayerPlayModePacket.TYPE,
            PlayerPlayModePacket.CODEC,
            PlayerPlayModePacket::handle
        );

        registrar.playToServer(
            PlayerBroadcastPacket.TYPE,
            PlayerBroadcastPacket.CODEC,
            PlayerBroadcastPacket::handle
        );

        registrar.playToServer(
            PlayerSeekPacket.TYPE,
            PlayerSeekPacket.CODEC,
            PlayerSeekPacket::handle
        );

        registrar.playToServer(
            PlayerSkipPacket.TYPE,
            PlayerSkipPacket.CODEC,
            PlayerSkipPacket::handle
        );
    }
}
