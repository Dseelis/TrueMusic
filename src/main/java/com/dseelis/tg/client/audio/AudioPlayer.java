package com.dseelis.tg.client.audio;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.audio.PlaybackState;
import com.dseelis.tg.client.AudioCache;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AudioPlayer {
    private static AudioPlayer instance;
    private final Map<BlockPos, SpeakerSoundInstance> playingSounds = new ConcurrentHashMap<>();

    private AudioPlayer() {}

    public static AudioPlayer getInstance() {
        if (instance == null) {
            instance = new AudioPlayer();
        }
        return instance;
    }

    public void play(BlockPos pos, PlaybackState playback, UUID resourceId, float volume) {
        Path cachedAudio = AudioCache.getInstance().getCachedAudio(resourceId).orElse(null);
        if (cachedAudio == null || !Files.exists(cachedAudio)) {
            TrueMusic.LOGGER.error("Audio {} not cached (caller should have downloaded first)", resourceId);
            return;
        }

        stop(pos);

        try {
            long currentTime = System.currentTimeMillis();
            long playbackPosition = playback.getCurrentPositionMs(currentTime);

            TrueMusicAudioStream stream = new TrueMusicAudioStream(cachedAudio);

            if (playbackPosition > 0) {
                stream.seekMs(playbackPosition);
            }

            SpeakerSoundInstance sound = new SpeakerSoundInstance(stream, pos, volume);
            Minecraft.getInstance().getSoundManager().play(sound);

            playingSounds.put(pos, sound);

            TrueMusic.LOGGER.info("Started playing audio {} at {} (seek {}ms)",
                resourceId, pos, playbackPosition);

        } catch (Exception e) {
            TrueMusic.LOGGER.error("Failed to play audio at {}", pos, e);
        }
    }

    public void playStreaming(BlockPos pos, StreamingAudioStream stream, float volume) {
        stop(pos);

        SpeakerSoundInstance sound = new SpeakerSoundInstance(stream, pos, volume);
        Minecraft.getInstance().getSoundManager().play(sound);
        playingSounds.put(pos, sound);

        TrueMusic.LOGGER.info("Started streaming audio at {}", pos);
    }

    public void stop(BlockPos pos) {
        SpeakerSoundInstance sound = playingSounds.remove(pos);
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().stop(sound);
            TrueMusic.debugLog("Stopped audio at {}", pos);
        }
    }

    public void setVolume(BlockPos pos, float volume) {
        SpeakerSoundInstance sound = playingSounds.get(pos);
        if (sound != null) {
            sound.setVolume(volume);
        }
    }

    public boolean isPlaying(BlockPos pos) {
        return playingSounds.containsKey(pos);
    }

    public void stopAll() {
        for (SpeakerSoundInstance sound : playingSounds.values()) {
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
        playingSounds.clear();
        TrueMusic.LOGGER.info("Stopped all audio playback");
    }
}
