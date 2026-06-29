package com.dseelis.tg.config;

import com.dseelis.tg.TrueMusic;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

// Config manager using Cloth Config API.
public class TrueMusicConfigManager {
    private static TrueMusicConfig config;

    public static void register() {
        AutoConfig.register(TrueMusicConfig.class, GsonConfigSerializer::new);
        config = AutoConfig.getConfigHolder(TrueMusicConfig.class).getConfig();

        TrueMusic.LOGGER.info("TrueMusic config loaded");
        updateClientConfig();
    }

    public static TrueMusicConfig getConfig() {
        if (config == null) {
            register();
        }
        return config;
    }

    private static void updateClientConfig() {
        TrueMusicClientConfig.setMaxAudioDistance(() -> config.audio.maxAudioDistance);
        TrueMusicClientConfig.setMaxCacheSizeMB(() -> config.cache.maxCacheSizeMB);
        TrueMusicClientConfig.setCacheDirectory(() -> config.cache.cacheDirectory);
    }

    // Convenience getters
    public static int getMaxAudioDistance() {
        return getConfig().audio.maxAudioDistance;
    }

    public static int getDefaultVolume() {
        return getConfig().audio.defaultVolume;
    }

    public static boolean isOcclusionEnabled() {
        return getConfig().audio.enableOcclusion;
    }

    public static boolean is3DSoundEnabled() {
        return getConfig().audio.enable3DSound;
    }

    public static int getOcclusionRayCount() {
        return getConfig().audio.occlusionRayCount;
    }

    public static int getMaxCacheSizeMB() {
        return getConfig().cache.maxCacheSizeMB;
    }

    public static boolean isAutoClearCacheEnabled() {
        return getConfig().cache.autoClearOldCache;
    }

    public static int getCacheExpirationDays() {
        return getConfig().cache.cacheExpirationDays;
    }

    public static String getCacheDirectory() {
        return getConfig().cache.cacheDirectory;
    }

    public static int getBufferSizeKB() {
        return getConfig().streaming.bufferSizeKB;
    }

    public static int getMaxConcurrentDownloads() {
        return getConfig().streaming.maxConcurrentDownloads;
    }

    public static boolean isStreamingPlaybackEnabled() {
        return getConfig().streaming.enableStreamingPlayback;
    }

    public static int getDownloadTimeoutSeconds() {
        return getConfig().streaming.downloadTimeoutSeconds;
    }
}
