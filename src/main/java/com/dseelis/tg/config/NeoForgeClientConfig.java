package com.dseelis.tg.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class NeoForgeClientConfig {

    public static final NeoForgeClientConfig INSTANCE;
    public static final ModConfigSpec SPEC;

    static {
        Pair<NeoForgeClientConfig, ModConfigSpec> pair = new ModConfigSpec.Builder()
            .configure(NeoForgeClientConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    public final ModConfigSpec.ConfigValue<String> cacheDirectory;
    public final ModConfigSpec.IntValue maxCacheSizeMB;
    public final ModConfigSpec.DoubleValue maxAudioDistance;
    public final ModConfigSpec.BooleanValue enableDebugLogging;

    private NeoForgeClientConfig(ModConfigSpec.Builder builder) {
        builder.push("cache");

        cacheDirectory = builder
            .comment("Directory name for cached audio files (relative to game directory)")
            .define("directory", TrueMusicClientConfig.DEFAULT_CACHE_DIRECTORY);

        maxCacheSizeMB = builder
            .comment("Maximum cache size in megabytes")
            .comment("Set to 0 for unlimited (not recommended)")
            .comment("Oldest files are deleted when limit is exceeded")
            .defineInRange("maxSizeMB",
                TrueMusicClientConfig.DEFAULT_MAX_CACHE_SIZE_MB,
                0,
                10240);

        builder.pop();

        builder.push("audio");

        maxAudioDistance = builder
            .comment("Maximum distance (in blocks) at which audio can be heard")
            .comment("Audio volume becomes 0 beyond this distance")
            .defineInRange("maxDistance",
                TrueMusicClientConfig.DEFAULT_MAX_AUDIO_DISTANCE,
                16.0,
                256.0);

        builder.pop();

        builder.push("debug");

        enableDebugLogging = builder
            .comment("Enable verbose debug logging")
            .define("enableLogging", TrueMusicClientConfig.DEFAULT_ENABLE_DEBUG_LOGGING);

        builder.pop();
    }

    public static void bind() {
        TrueMusicClientConfig.setCacheDirectory(INSTANCE.cacheDirectory::get);
        TrueMusicClientConfig.setMaxCacheSizeMB(INSTANCE.maxCacheSizeMB::get);
        TrueMusicClientConfig.setMaxAudioDistance(INSTANCE.maxAudioDistance::get);
        TrueMusicClientConfig.setEnableDebugLogging(INSTANCE.enableDebugLogging::get);
    }
}
