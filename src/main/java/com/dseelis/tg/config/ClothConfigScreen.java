package com.dseelis.tg.config;

import com.dseelis.tg.TrueMusic;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClothConfigScreen {

    public static Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("config.truemusic.title"))
            .setSavingRunnable(() -> {
                NeoForgeClientConfig.bind();
                NeoForgeServerConfig.bind();
                TrueMusic.LOGGER.info("TrueMusic config saved");
            });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // AUDIO CATEGORY
        ConfigCategory audioCategory = builder.getOrCreateCategory(
            Component.translatable("config.truemusic.category.audio")
        );

        audioCategory.addEntry(entryBuilder.startDoubleField(
                Component.translatable("config.truemusic.audio.maxDistance"),
                TrueMusicClientConfig.getMaxAudioDistance()
            )
            .setDefaultValue(TrueMusicClientConfig.DEFAULT_MAX_AUDIO_DISTANCE)
            .setMin(16.0)
            .setMax(256.0)
            .setTooltip(Component.translatable("config.truemusic.audio.maxDistance.tooltip"))
            .setSaveConsumer(value -> NeoForgeClientConfig.INSTANCE.maxAudioDistance.set(value))
            .build()
        );

        // CACHE CATEGORY
        ConfigCategory cacheCategory = builder.getOrCreateCategory(
            Component.translatable("config.truemusic.category.cache")
        );

        cacheCategory.addEntry(entryBuilder.startStrField(
                Component.translatable("config.truemusic.cache.directory"),
                TrueMusicClientConfig.getCacheDirectory()
            )
            .setDefaultValue(TrueMusicClientConfig.DEFAULT_CACHE_DIRECTORY)
            .setTooltip(Component.translatable("config.truemusic.cache.directory.tooltip"))
            .setSaveConsumer(value -> NeoForgeClientConfig.INSTANCE.cacheDirectory.set(value))
            .build()
        );

        cacheCategory.addEntry(entryBuilder.startIntSlider(
                Component.translatable("config.truemusic.cache.maxSizeMB"),
                TrueMusicClientConfig.getMaxCacheSizeMB(),
                0,
                10240
            )
            .setDefaultValue(TrueMusicClientConfig.DEFAULT_MAX_CACHE_SIZE_MB)
            .setTooltip(Component.translatable("config.truemusic.cache.maxSizeMB.tooltip"))
            .setSaveConsumer(value -> NeoForgeClientConfig.INSTANCE.maxCacheSizeMB.set(value))
            .build()
        );

        // DEBUG CATEGORY
        ConfigCategory debugCategory = builder.getOrCreateCategory(
            Component.translatable("config.truemusic.category.debug")
        );

        debugCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.truemusic.debug.enableLogging"),
                TrueMusicClientConfig.isDebugLoggingEnabled()
            )
            .setDefaultValue(TrueMusicClientConfig.DEFAULT_ENABLE_DEBUG_LOGGING)
            .setTooltip(Component.translatable("config.truemusic.debug.enableLogging.tooltip"))
            .setSaveConsumer(value -> NeoForgeClientConfig.INSTANCE.enableDebugLogging.set(value))
            .build()
        );

        // TRANSFER CATEGORY (Server)
        ConfigCategory transferCategory = builder.getOrCreateCategory(
            Component.translatable("config.truemusic.category.transfer")
        );

        transferCategory.addEntry(entryBuilder.startIntSlider(
                Component.translatable("config.truemusic.transfer.chunkSize"),
                TrueMusicServerConfig.getChunkSize(),
                64 * 1024,
                1024 * 1024
            )
            .setDefaultValue(TrueMusicServerConfig.DEFAULT_CHUNK_SIZE)
            .setTooltip(Component.translatable("config.truemusic.transfer.chunkSize.tooltip"))
            .setSaveConsumer(value -> { if (NeoForgeServerConfig.INSTANCE != null) NeoForgeServerConfig.INSTANCE.chunkSize.set(value); })
            .setTextGetter(value -> Component.literal(formatBytes(value)))
            .build()
        );

        transferCategory.addEntry(entryBuilder.startIntSlider(
                Component.translatable("config.truemusic.transfer.maxBytesPerTick"),
                TrueMusicServerConfig.getMaxBytesPerTick(),
                64 * 1024,
                4 * 1024 * 1024
            )
            .setDefaultValue(TrueMusicServerConfig.DEFAULT_MAX_BYTES_PER_TICK)
            .setTooltip(Component.translatable("config.truemusic.transfer.maxBytesPerTick.tooltip"))
            .setSaveConsumer(value -> { if (NeoForgeServerConfig.INSTANCE != null) NeoForgeServerConfig.INSTANCE.maxBytesPerTick.set(value); })
            .setTextGetter(value -> Component.literal(formatBytes(value)))
            .build()
        );

        transferCategory.addEntry(entryBuilder.startIntSlider(
                Component.translatable("config.truemusic.transfer.maxBytesPerPlayerPerTick"),
                TrueMusicServerConfig.getMaxBytesPerPlayerPerTick(),
                64 * 1024,
                2 * 1024 * 1024
            )
            .setDefaultValue(TrueMusicServerConfig.DEFAULT_MAX_BYTES_PER_PLAYER_PER_TICK)
            .setTooltip(Component.translatable("config.truemusic.transfer.maxBytesPerPlayerPerTick.tooltip"))
            .setSaveConsumer(value -> { if (NeoForgeServerConfig.INSTANCE != null) NeoForgeServerConfig.INSTANCE.maxBytesPerPlayerPerTick.set(value); })
            .setTextGetter(value -> Component.literal(formatBytes(value)))
            .build()
        );

        // DOWNLOAD CATEGORY (Server)
        ConfigCategory downloadCategory = builder.getOrCreateCategory(
            Component.translatable("config.truemusic.category.download")
        );

        downloadCategory.addEntry(entryBuilder.startIntSlider(
                Component.translatable("config.truemusic.download.connectTimeoutSeconds"),
                TrueMusicServerConfig.getDownloadConnectTimeoutSeconds(),
                5,
                120
            )
            .setDefaultValue(TrueMusicServerConfig.DEFAULT_DOWNLOAD_CONNECT_TIMEOUT)
            .setTooltip(Component.translatable("config.truemusic.download.connectTimeoutSeconds.tooltip"))
            .setSaveConsumer(value -> { if (NeoForgeServerConfig.INSTANCE != null) NeoForgeServerConfig.INSTANCE.downloadConnectTimeoutSeconds.set(value); })
            .setTextGetter(value -> Component.literal(value + "s"))
            .build()
        );

        downloadCategory.addEntry(entryBuilder.startIntSlider(
                Component.translatable("config.truemusic.download.readTimeoutSeconds"),
                TrueMusicServerConfig.getDownloadReadTimeoutSeconds(),
                30,
                600
            )
            .setDefaultValue(TrueMusicServerConfig.DEFAULT_DOWNLOAD_READ_TIMEOUT)
            .setTooltip(Component.translatable("config.truemusic.download.readTimeoutSeconds.tooltip"))
            .setSaveConsumer(value -> { if (NeoForgeServerConfig.INSTANCE != null) NeoForgeServerConfig.INSTANCE.downloadReadTimeoutSeconds.set(value); })
            .setTextGetter(value -> Component.literal(value + "s"))
            .build()
        );

        return builder.build();
    }

    private static String formatBytes(int bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        } else {
            return (bytes / (1024 * 1024)) + " MB";
        }
    }
}
