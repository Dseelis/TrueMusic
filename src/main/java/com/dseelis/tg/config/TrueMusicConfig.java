package com.dseelis.tg.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "truemusic")
public class TrueMusicConfig implements ConfigData {

    @ConfigEntry.Category("audio")
    @ConfigEntry.Gui.TransitiveObject
    public AudioSettings audio = new AudioSettings();

    @ConfigEntry.Category("cache")
    @ConfigEntry.Gui.TransitiveObject
    public CacheSettings cache = new CacheSettings();

    @ConfigEntry.Category("streaming")
    @ConfigEntry.Gui.TransitiveObject
    public StreamingSettings streaming = new StreamingSettings();

    public static class AudioSettings {
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 16, max = 256)
        public int maxAudioDistance = 64;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int defaultVolume = 50;

        @ConfigEntry.Gui.Tooltip
        public boolean enableOcclusion = true;

        @ConfigEntry.Gui.Tooltip
        public boolean enable3DSound = true;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 10)
        public int occlusionRayCount = 5;
    }

    public static class CacheSettings {
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 128, max = 4096)
        public int maxCacheSizeMB = 512;

        @ConfigEntry.Gui.Tooltip
        public boolean autoClearOldCache = true;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 90)
        public int cacheExpirationDays = 30;

        @ConfigEntry.Gui.Tooltip
        public String cacheDirectory = "truemusic_cache";
    }

    public static class StreamingSettings {
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 16, max = 256)
        public int bufferSizeKB = 64;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 10)
        public int maxConcurrentDownloads = 3;

        @ConfigEntry.Gui.Tooltip
        public boolean enableStreamingPlayback = true;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 60)
        public int downloadTimeoutSeconds = 30;
    }

    @Override
    public void validatePostLoad() throws ValidationException {
        if (audio.maxAudioDistance < 16 || audio.maxAudioDistance > 256) {
            throw new ValidationException("Audio distance must be between 16 and 256 blocks");
        }
        if (cache.maxCacheSizeMB < 128) {
            throw new ValidationException("Cache size must be at least 128 MB");
        }
    }
}
