package com.dseelis.tg.client;

import com.dseelis.tg.audio.PlaybackState;
import com.dseelis.tg.audio.PlayMode;
import com.dseelis.tg.client.audio.HeadphonesSoundInstance;
import com.dseelis.tg.client.audio.StreamingAudioStream;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/**
 * Client-side state manager for the Music Player item.
 * Tracks playback state, volume, play mode and whether broadcast is active.
 * Keyed by the local player UUID (there is only one local player).
 */
public class ClientPlayerMusicManager {
    private static ClientPlayerMusicManager instance;

    private PlaybackState state = PlaybackState.STOPPED;
    private float volume = 0.7f;
    private PlayMode playMode = PlayMode.SEQUENTIAL;
    private boolean broadcasting = false;

    // The currently playing headphones sound instance (null when stopped)
    private HeadphonesSoundInstance currentSound;

    private ClientPlayerMusicManager() {}

    public static ClientPlayerMusicManager getInstance() {
        if (instance == null) instance = new ClientPlayerMusicManager();
        return instance;
    }

    // ---- State accessors ----

    public PlaybackState getState() { return state; }
    public float getVolume() { return volume; }
    public PlayMode getPlayMode() { return playMode; }
    public boolean isBroadcasting() { return broadcasting; }

    public void setState(PlaybackState s) { this.state = s; }
    public void setVolume(float v) {
        this.volume = v;
        if (currentSound != null) currentSound.setVolume(v);
    }
    public void setPlayMode(PlayMode m) { this.playMode = m; }
    public void setBroadcasting(boolean b) { this.broadcasting = b; }

    // ---- Playback ----

    public void playStreaming(StreamingAudioStream stream) {
        stopLocal();
        UUID uuid = Minecraft.getInstance().player != null
            ? Minecraft.getInstance().player.getUUID() : null;
        if (uuid == null) return;

        currentSound = new HeadphonesSoundInstance(stream, uuid, volume);
        Minecraft.getInstance().getSoundManager().play(currentSound);
    }

    public void stopLocal() {
        if (currentSound != null) {
            currentSound.requestStop();
            currentSound = null;
        }
    }

    public void updateVolume(float v) {
        this.volume = v;
        if (currentSound != null) currentSound.setVolume(v);
    }

    public boolean isPlayingLocally() {
        return currentSound != null;
    }

    /** Called on world disconnect — reset everything. */
    public void reset() {
        stopLocal();
        state = PlaybackState.STOPPED;
        broadcasting = false;
    }
}
