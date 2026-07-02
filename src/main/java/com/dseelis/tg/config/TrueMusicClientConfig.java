package com.dseelis.tg.config;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class TrueMusicClientConfig {

    private TrueMusicClientConfig() {}

    public static final String DEFAULT_CACHE_DIRECTORY = "truemusic_cache";
    public static final int DEFAULT_MAX_CACHE_SIZE_MB = 512;
    public static final boolean DEFAULT_ENABLE_DEBUG_LOGGING = false;
    public static final double DEFAULT_MAX_AUDIO_DISTANCE = 64.0;
    public static final boolean DEFAULT_DUCK_MINECRAFT_MUSIC = false;
    public static final float DEFAULT_DUCK_VOLUME = 0.15f;

    private static Supplier<String> cacheDirectorySupplier = () -> DEFAULT_CACHE_DIRECTORY;
    private static IntSupplier maxCacheSizeMBSupplier = () -> DEFAULT_MAX_CACHE_SIZE_MB;
    private static DoubleSupplier maxAudioDistanceSupplier = () -> DEFAULT_MAX_AUDIO_DISTANCE;
    private static BooleanSupplier enableDebugLoggingSupplier = () -> DEFAULT_ENABLE_DEBUG_LOGGING;
    private static BooleanSupplier duckMinecraftMusicSupplier = () -> DEFAULT_DUCK_MINECRAFT_MUSIC;

    public static String getCacheDirectory() { return cacheDirectorySupplier.get(); }
    public static int getMaxCacheSizeMB() { return maxCacheSizeMBSupplier.getAsInt(); }
    public static double getMaxAudioDistance() { return maxAudioDistanceSupplier.getAsDouble(); }
    public static boolean isDebugLoggingEnabled() { return enableDebugLoggingSupplier.getAsBoolean(); }
    public static boolean isDuckMinecraftMusic() { return duckMinecraftMusicSupplier.getAsBoolean(); }

    public static void setCacheDirectory(Supplier<String> supplier) { cacheDirectorySupplier = supplier; }
    public static void setMaxCacheSizeMB(IntSupplier supplier) { maxCacheSizeMBSupplier = supplier; }
    public static void setMaxAudioDistance(DoubleSupplier supplier) { maxAudioDistanceSupplier = supplier; }
    public static void setEnableDebugLogging(BooleanSupplier supplier) { enableDebugLoggingSupplier = supplier; }
    public static void setDuckMinecraftMusic(BooleanSupplier supplier) { duckMinecraftMusicSupplier = supplier; }
}
