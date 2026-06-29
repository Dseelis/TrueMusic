package com.dseelis.tg.server;

import com.dseelis.tg.TrueMusic;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

// FFmpeg/FFprobe wrapper for audio metadata extraction.
// Also provides yt-dlp integration for downloading from various sources.
// Uses BinaryManager to automatically download required binaries if not present.
public class FFmpegHelper {

    public static boolean isAvailable() {
        return isFFmpegAvailable();
    }

    public static boolean isFFmpegAvailable() {
        return BinaryManager.getFfmpegExecutable().isPresent()
            && BinaryManager.getFfprobeExecutable().isPresent();
    }

    public static boolean isYtDlpAvailable() {
        return BinaryManager.getYtDlpExecutable().isPresent();
    }

    private static boolean checkCommand(String cmd, String arg) {
        try {
            Process process = new ProcessBuilder(cmd, arg)
                .redirectErrorStream(true)
                .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean downloadAndConvert(String url, Path targetFile) {
        if (!isYtDlpAvailable() || !isFFmpegAvailable()) {
            return false;
        }

        String ytDlpExe = BinaryManager.getYtDlpExecutable().orElse(null);
        if (ytDlpExe == null) {
            TrueMusic.LOGGER.error("yt-dlp executable not found");
            return false;
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("truemusic_dl_");

            ProcessBuilder pb = new ProcessBuilder(
                ytDlpExe,
                "-f", "bestaudio",
                "-o", "source.%(ext)s",
                "--no-playlist",
                "--no-update",
                url
            );
            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);

            TrueMusic.LOGGER.info("Downloading audio with yt-dlp: {}", url);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    TrueMusic.LOGGER.info("[yt-dlp] {}", line);
                }
            }

            if (!process.waitFor(300, TimeUnit.SECONDS) || process.exitValue() != 0) {
                TrueMusic.LOGGER.error("yt-dlp download failed");
                return false;
            }

            Path sourceFile = Files.list(tempDir)
                .filter(p -> !p.toString().endsWith(".ogg"))
                .findFirst()
                .orElse(null);

            if (sourceFile == null) {
                TrueMusic.LOGGER.error("yt-dlp produced no audio file");
                return false;
            }

            return convertToOgg(sourceFile, targetFile);

        } catch (Exception e) {
            TrueMusic.LOGGER.error("yt-dlp download failed", e);
        } finally {
            if (tempDir != null) {
                try (Stream<Path> walk = Files.walk(tempDir)) {
                    walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                } catch (Exception e) {}
            }
        }
        return false;
    }

    public static Optional<Long> getDurationMs(Path audioFile) {
        if (!isFFmpegAvailable()) {
            return Optional.empty();
        }

        String ffprobeExe = BinaryManager.getFfprobeExecutable().orElse(null);
        if (ffprobeExe == null) {
            return Optional.empty();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                ffprobeExe,
                "-v", "quiet",
                "-show_entries", "format=duration",
                "-of", "csv=p=0",
                audioFile.toAbsolutePath().toString()
            );

            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.readLine();
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                TrueMusic.LOGGER.warn("FFprobe timeout for {}", audioFile);
                return Optional.empty();
            }

            if (process.exitValue() != 0 || output == null || output.isBlank()) {
                TrueMusic.LOGGER.warn("FFprobe failed for {}", audioFile);
                return Optional.empty();
            }

            double seconds = Double.parseDouble(output.trim());
            return Optional.of((long) (seconds * 1000));

        } catch (Exception e) {
            TrueMusic.LOGGER.error("Failed to get duration for {}: {}", audioFile, e.getMessage());
            return Optional.empty();
        }
    }

    public static boolean convertToOgg(Path sourceFile, Path targetFile) {
        if (!isFFmpegAvailable()) {
            TrueMusic.LOGGER.error("FFmpeg is not available");
            return false;
        }

        String ffmpegExe = BinaryManager.getFfmpegExecutable().orElse(null);
        if (ffmpegExe == null) {
            TrueMusic.LOGGER.error("FFmpeg executable not found");
            return false;
        }

        if (!Files.exists(sourceFile)) {
            TrueMusic.LOGGER.error("Source file does not exist: {}", sourceFile);
            return false;
        }

        try {
            Files.createDirectories(targetFile.getParent());

            ProcessBuilder pb = new ProcessBuilder(
                ffmpegExe,
                "-i", sourceFile.toAbsolutePath().toString(),
                "-vn",
                "-c:a", "libvorbis",
                "-q:a", "5",
                "-y",
                targetFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);

            TrueMusic.LOGGER.info("Converting {} to OGG", sourceFile.getFileName());
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    TrueMusic.debugLog("[ffmpeg] {}", line);
                }
            }

            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                TrueMusic.LOGGER.error("FFmpeg timeout for {}", sourceFile);
                return false;
            }

            if (process.exitValue() == 0 && Files.exists(targetFile)) {
                TrueMusic.LOGGER.info("Successfully converted to: {}", targetFile);
                return true;
            } else {
                TrueMusic.LOGGER.error("FFmpeg conversion failed with exit code: {}", process.exitValue());
                return false;
            }

        } catch (Exception e) {
            TrueMusic.LOGGER.error("Failed to convert file: {}", sourceFile, e);
            return false;
        }
    }

    public static Optional<AudioMetadata> getMetadata(Path audioFile) {
        if (!isFFmpegAvailable()) {
            return Optional.empty();
        }

        String ffprobeExe = BinaryManager.getFfprobeExecutable().orElse(null);
        if (ffprobeExe == null) {
            return Optional.empty();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                ffprobeExe,
                "-v", "quiet",
                "-show_entries", "format_tags=title,artist,album:format=duration",
                "-of", "json",
                audioFile.toAbsolutePath().toString()
            );

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Optional.empty();
            }

            if (process.exitValue() != 0) {
                return Optional.empty();
            }

            String json = output.toString();
            String title = extractJsonValue(json, "title");
            String artist = extractJsonValue(json, "artist");
            String album = extractJsonValue(json, "album");
            String durationStr = extractJsonValue(json, "duration");

            long durationMs = 0;
            if (durationStr != null && !durationStr.isEmpty()) {
                try {
                    durationMs = (long) (Double.parseDouble(durationStr) * 1000);
                } catch (NumberFormatException e) {}
            }

            return Optional.of(new AudioMetadata(title, artist, album, durationMs));

        } catch (Exception e) {
            TrueMusic.LOGGER.error("Failed to get metadata for {}", audioFile, e);
            return Optional.empty();
        }
    }

    private static String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;

        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) return null;

        int startQuote = json.indexOf("\"", colonIndex);
        if (startQuote == -1) return null;

        int endQuote = json.indexOf("\"", startQuote + 1);
        if (endQuote == -1) return null;

        return json.substring(startQuote + 1, endQuote);
    }

    public record AudioMetadata(String title, String artist, String album, long durationMs) {}
}
