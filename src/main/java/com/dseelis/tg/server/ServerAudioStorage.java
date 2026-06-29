package com.dseelis.tg.server;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.audio.AudioStreamInfo;
import com.dseelis.tg.audio.OggPageScanner;
import com.dseelis.tg.config.TrueMusicServerConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Server-side audio file storage.
// Downloads and stores OGG files for distribution via packets.

public class ServerAudioStorage {

    private static ServerAudioStorage instance;

    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(2);
    private final Map<UUID, AudioStreamInfo> streamInfoCache = new ConcurrentHashMap<>();

    private Path storageDir;

    private ServerAudioStorage() {}

    public static ServerAudioStorage getInstance() {
        if (instance == null) {
            instance = new ServerAudioStorage();
        }
        return instance;
    }

    public void initialize(Path worldDir) {
        this.storageDir = worldDir.resolve("truemusic_audio");
        try {
            Files.createDirectories(storageDir);
            scanExistingAudioFiles();
            TrueMusic.LOGGER.info("Server audio storage initialized at {}", storageDir);
        } catch (IOException e) {
            TrueMusic.LOGGER.error("Failed to create audio storage directory", e);
        }
    }

    private void scanExistingAudioFiles() {
        try (var files = Files.list(storageDir)) {
            files.filter(p -> p.toString().endsWith(".ogg"))
                .forEach(this::scanAndCacheFromPath);
        } catch (IOException e) {
            TrueMusic.LOGGER.error("Failed to scan existing audio files", e);
        }
    }

    private void scanAndCacheFromPath(Path audioPath) {
        String fileName = audioPath.getFileName().toString();
        String uuidStr = fileName.substring(0, fileName.length() - 4);
        try {
            UUID resourceId = UUID.fromString(uuidStr);
            scanAndCacheStreamInfo(resourceId, audioPath);
        } catch (IllegalArgumentException e) {
            TrueMusic.LOGGER.warn("Skipping invalid audio file name: {}", fileName);
        }
    }

    private void scanAndCacheStreamInfo(UUID resourceId, Path audioPath) {
        OggPageScanner.OggScanResult result = OggPageScanner.scan(audioPath);
        if (result != null) {
            streamInfoCache.put(resourceId, new AudioStreamInfo(
                result.headerBytes(),
                result.seekTable(),
                result.sampleRate()
            ));
        }
    }

    public Optional<AudioStreamInfo> getStreamInfo(UUID resourceId) {
        return Optional.ofNullable(streamInfoCache.get(resourceId));
    }

    public CompletableFuture<Boolean> downloadAndStore(UUID resourceId, String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path targetFile = storageDir.resolve(resourceId + ".ogg");
                TrueMusic.LOGGER.info("Processing audio: {}", url);

                boolean success = false;

                // Use yt-dlp (supports YouTube/Bilibili/any URL)
                if (FFmpegHelper.isYtDlpAvailable()) {
                    success = FFmpegHelper.downloadAndConvert(url, targetFile);
                }

                // If yt-dlp fails/unavailable and URL is direct .ogg link, use HTTP download
                if (!success && isDirectOggUrl(url)) {
                    TrueMusic.LOGGER.info("Fallback to direct HTTP download");
                    success = directDownload(url, targetFile);
                }

                if (success && Files.exists(targetFile)) {
                    scanAndCacheStreamInfo(resourceId, targetFile);
                    long size = Files.size(targetFile);
                    TrueMusic.LOGGER.info("Stored audio {} ({} bytes)", resourceId, size);
                    return true;
                } else {
                    TrueMusic.LOGGER.error("Failed to download/convert audio");
                    return false;
                }
            } catch (Exception e) {
                TrueMusic.LOGGER.error("Failed to store audio {}", resourceId, e);
                return false;
            }
        }, downloadExecutor);
    }

    private boolean isDirectOggUrl(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".ogg") || lower.contains(".ogg?");
    }

    private boolean directDownload(String url, Path targetFile) {
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TrueMusicServerConfig.getDownloadConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(TrueMusicServerConfig.getDownloadReadTimeoutSeconds()))
                .build();

            HttpResponse<InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() == 200) {
                Files.copy(response.body(), targetFile, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } else {
                TrueMusic.LOGGER.error("HTTP download failed: {}", response.statusCode());
                return false;
            }
        } catch (Exception e) {
            TrueMusic.LOGGER.error("Direct download failed", e);
            return false;
        }
    }

    public boolean hasAudio(UUID resourceId) {
        if (storageDir == null) return false;
        return Files.exists(storageDir.resolve(resourceId + ".ogg"));
    }

    public Optional<Path> getAudioPath(UUID resourceId) {
        if (storageDir == null) return Optional.empty();
        Path file = storageDir.resolve(resourceId + ".ogg");
        return Files.exists(file) ? Optional.of(file) : Optional.empty();
    }

    public Optional<byte[]> readAudio(UUID resourceId) {
        return getAudioPath(resourceId).flatMap(path -> {
            try {
                return Optional.of(Files.readAllBytes(path));
            } catch (IOException e) {
                TrueMusic.LOGGER.error("Failed to read audio file {}", resourceId, e);
                return Optional.empty();
            }
        });
    }

    public long getAudioSize(UUID resourceId) {
        return getAudioPath(resourceId).map(path -> {
            try {
                return Files.size(path);
            } catch (IOException e) {
                return 0L;
            }
        }).orElse(0L);
    }

    public int calculateChunkCount(UUID resourceId) {
        long size = getAudioSize(resourceId);
        return (int) Math.ceil((double) size / TrueMusicServerConfig.getChunkSize());
    }

    public static int getChunkSize() {
        return TrueMusicServerConfig.getChunkSize();
    }

    public long getDurationMs(UUID resourceId) {
        return getAudioPath(resourceId)
            .flatMap(FFmpegHelper::getDurationMs)
            .orElse(-1L);
    }

    public boolean deleteAudio(UUID resourceId) {
        streamInfoCache.remove(resourceId);
        return getAudioPath(resourceId).map(path -> {
            try {
                Files.deleteIfExists(path);
                return true;
            } catch (IOException e) {
                TrueMusic.LOGGER.error("Failed to delete audio file {}", resourceId, e);
                return false;
            }
        }).orElse(false);
    }

    public void shutdown() {
        downloadExecutor.shutdown();
    }
}
