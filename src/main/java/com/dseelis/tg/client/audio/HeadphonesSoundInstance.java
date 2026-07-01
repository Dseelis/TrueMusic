package com.dseelis.tg.client.audio;

import com.dseelis.tg.TrueMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Sound instance for the Music Player item (headphones mode).
 * Plays at full volume regardless of distance — only the local player hears it.
 * Stops automatically when playback is paused/stopped or the player closes the GUI.
 */
public class HeadphonesSoundInstance extends AbstractTickableSoundInstance {

    private static final ResourceLocation DUMMY_SOUND_LOCATION =
        ResourceLocation.fromNamespaceAndPath(TrueMusic.MODID, "player");
    private static final Sound DUMMY_SOUND = new Sound(
        DUMMY_SOUND_LOCATION,
        ConstantFloat.of(1.0f),
        ConstantFloat.of(1.0f),
        1,
        Sound.Type.FILE,
        true,
        false,
        0
    );

    private final AudioStream stream;
    private final UUID ownerUuid;
    private float baseVolume;
    private boolean shouldStop = false;

    public HeadphonesSoundInstance(AudioStream stream, UUID ownerUuid, float volume) {
        super(
            SoundEvent.createVariableRangeEvent(DUMMY_SOUND_LOCATION),
            SoundSource.RECORDS,
            SoundInstance.createUnseededRandom()
        );
        this.stream = stream;
        this.ownerUuid = ownerUuid;
        this.baseVolume = volume;

        // Position at the listener (self) — will be updated each tick
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            this.x = mc.player.getX();
            this.y = mc.player.getY();
            this.z = mc.player.getZ();
        }

        // No distance attenuation
        this.attenuation = Attenuation.NONE;
        this.volume = volume;
    }

    @Override
    public Sound getSound() {
        return DUMMY_SOUND;
    }

    @Override
    public WeighedSoundEvents resolve(net.minecraft.client.sounds.SoundManager soundManager) {
        this.sound = DUMMY_SOUND;
        return new WeighedSoundEvents(DUMMY_SOUND_LOCATION, null);
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return CompletableFuture.completedFuture(this.stream);
    }

    @Override
    public void tick() {
        if (shouldStop) {
            super.stop();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.getUUID().equals(ownerUuid)) {
            super.stop();
            return;
        }

        // Keep position at the listener so it's always full volume
        this.x = mc.player.getX();
        this.y = mc.player.getY();
        this.z = mc.player.getZ();
        this.volume = baseVolume;
    }

    public void setVolume(float v) {
        this.baseVolume = v;
        this.volume = v;
    }

    public void requestStop() {
        this.shouldStop = true;
    }
}
