package com.dseelis.tg.client;

import com.dseelis.tg.config.TrueMusicClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

// Applies/removes Minecraft music ducking while TrueMusic is playing.
// Saves the original MUSIC and RECORD volumes and restores them on stop.
public class MusicDuckingManager {

    private static final MusicDuckingManager instance = new MusicDuckingManager();

    private boolean ducked = false;
    private float savedMusicVolume  = -1f;
    private float savedRecordVolume = -1f;

    private MusicDuckingManager() {}

    public static MusicDuckingManager getInstance() { return instance; }

// Call when TrueMusic starts playing.
    public void onPlaybackStarted() {
        if (!TrueMusicClientConfig.isDuckMinecraftMusic()) return;
        if (ducked) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;

        savedMusicVolume  = mc.options.getSoundSourceVolume(SoundSource.MUSIC);
        savedRecordVolume = mc.options.getSoundSourceVolume(SoundSource.RECORDS);

        setVolume(mc, SoundSource.MUSIC,   TrueMusicClientConfig.DEFAULT_DUCK_VOLUME);
        setVolume(mc, SoundSource.RECORDS, TrueMusicClientConfig.DEFAULT_DUCK_VOLUME);

        ducked = true;
    }

// Call when TrueMusic stops/pauses.
    public void onPlaybackStopped() {
        if (!ducked) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;

        if (savedMusicVolume  >= 0) setVolume(mc, SoundSource.MUSIC,  savedMusicVolume);
        if (savedRecordVolume >= 0) setVolume(mc, SoundSource.RECORDS, savedRecordVolume);

        savedMusicVolume  = -1f;
        savedRecordVolume = -1f;
        ducked = false;
    }

    public void refresh(boolean isPlaying) {
        if (isPlaying) {
            if (TrueMusicClientConfig.isDuckMinecraftMusic()) {
                if (!ducked) onPlaybackStarted();
            } else {
                if (ducked) onPlaybackStopped();
            }
        } else {
            if (ducked) onPlaybackStopped();
        }
    }

    public boolean isDucked() { return ducked; }

    private static void setVolume(Minecraft mc, SoundSource source, float volume) {
        mc.options.getSoundSourceOptionInstance(source).set((double) volume);
    }
}
