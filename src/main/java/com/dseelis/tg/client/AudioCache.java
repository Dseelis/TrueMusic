package com.dseelis.tg.client;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.config.TrueMusicClientConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Client-side audio cache.
// Stores OGG files received via packet transfer.

public class AudioCache {

    private static final AudioCache instance = new AudioCache();
    private final Map<UUID, Path> cache = new ConcurrentHashMap<>();
    private Path cacheDir;

    private AudioCache() {}

    public static AudioCache getInstance() {
        return instance;
    }

    public void initialize(Path gameDir) {
        this.cacheDir = gameDir.resolve(TrueMusicClientConfig.getCacheDirectory());
        try {
            Files.createDirectories(cacheDir);
            loadCacheIndex();
        } catch (IOException e) {
            TrueMusic.LOGGER.error("Failed to initialize audio cache", e);
        }
    }

    private void loadCacheIndex() {
        try {
            if (Files.exists(cacheDir)) {
                Files.list(cacheDir)
                    .filter(p -> p.toString().endsWith(".ogg"))
                    .forEach(p -> {
                        try {
                            String filename = p.getFileName().toString();
                            UUID id = UUID.fromString(filename.replace(".ogg", ""));
                            cache.put(id, p);
                        } catch (IllegalArgumentException e) {
                            // Skip files with invalid UUID names
                        }
                    });
                TrueMusic.LOGGER.info("Loaded {} cached audio files", cache.size());
            }
        } catch (IOException e) {
            TrueMusic.LOGGER.error("Failed to load cache index", e);
        }
    }

    public Optional<Path> getCachedAudio(UUID resourceId) {
        return Optional.ofNullable(cache.get(resourceId));
    }

    public void registerCachedFile(UUID resourceId, Path file) {
        cache.put(resourceId, file);
    }

    public boolean isCached(UUID resourceId) {
        return cache.containsKey(resourceId);
    }

    public Path getCacheDir() {
        return cacheDir;
    }
}
