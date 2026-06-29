package com.dseelis.tg.server;

import com.dseelis.tg.TrueMusic;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// Manages automatic download of yt-dlp and ffmpeg binaries.
//
// On first server start, downloads the latest releases into:
//   <gameDir>

public class BinaryManager {

    // Binaries will be placed directly in the game root directory
    private static final String BIN_DIR_NAME = "";

    // yt-dlp release URLs
    private static final String YTDLP_WINDOWS = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe";
    private static final String YTDLP_LINUX   = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp";
    private static final String YTDLP_MAC     = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos";

    // ffmpeg static build — Windows x64 (BtbN builds, GPL)
    private static final String FFMPEG_WIN_ZIP =
        "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip";
    // ffmpeg for Linux
    private static final String FFMPEG_LINUX_ZIP =
        "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz";

    private static Path binDir;

    // Cached binary paths
    private static String ytDlpPath;
    private static String ffmpegPath;
    private static String ffprobePath;

    private BinaryManager() {}


// Initialize the binary manager with the game directory.
// Downloads binaries if not present.
// Returns quickly - downloads happen asynchronously.

    public static void initialize(Path gameDir) {
        binDir = gameDir.resolve(BIN_DIR_NAME);
        try {
            Files.createDirectories(binDir);
        } catch (IOException e) {
            TrueMusic.LOGGER.error("Failed to create bin directory: {}", binDir, e);
            return;
        }

        // Resolve paths using system-installed tools first, then fall back to bundled
        ytDlpPath = resolveExecutable("yt-dlp", getYtDlpFileName());
        ffmpegPath = resolveExecutable("ffmpeg", "ffmpeg" + exeSuffix());
        ffprobePath = resolveExecutable("ffprobe", "ffprobe" + exeSuffix());

        boolean needsYtDlp = ytDlpPath == null;
        boolean needsFFmpeg = ffmpegPath == null || ffprobePath == null;

        if (!needsYtDlp && !needsFFmpeg) {
            TrueMusic.LOGGER.info("All binaries found. yt-dlp: {}, ffmpeg: {}", ytDlpPath, ffmpegPath);
            return;
        }

        TrueMusic.LOGGER.info("Some binaries missing — downloading to {}", binDir);
        TrueMusic.LOGGER.info("  yt-dlp: {}", needsYtDlp ? "MISSING" : "OK");
        TrueMusic.LOGGER.info("  ffmpeg: {}", needsFFmpeg ? "MISSING" : "OK");

        // Download asynchronously to not block server startup
        Thread downloadThread = new Thread(() -> {
            if (needsYtDlp) {
                downloadYtDlp();
            }
            if (needsFFmpeg) {
                downloadFFmpeg();
            }
            TrueMusic.LOGGER.info("Binary download complete. yt-dlp: {}, ffmpeg: {}",
                ytDlpPath != null ? ytDlpPath : "FAILED",
                ffmpegPath != null ? ffmpegPath : "FAILED");
        }, "TrueMusic-BinaryDownload");
        downloadThread.setDaemon(true);
        downloadThread.start();
    }

// Resolve an executable: first check PATH, then check our bin dir.

    private static String resolveExecutable(String systemName, String bundledName) {
        // Check system PATH first
        if (isCommandAvailable(systemName)) {
            return systemName;
        }
        // Check our bundled bin directory
        Path bundled = binDir.resolve(bundledName);
        if (Files.exists(bundled) && Files.isRegularFile(bundled)) {
            TrueMusic.LOGGER.info("Using bundled {}: {}", bundledName, bundled);
            return bundled.toAbsolutePath().toString();
        }
        return null;
    }

    private static boolean isCommandAvailable(String cmd) {
        try {
            Process p = new ProcessBuilder(cmd, "--version")
                .redirectErrorStream(true)
                .start();
            // Drain output
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            return p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // yt-dlp

    private static void downloadYtDlp() {
        String url = getYtDlpUrl();
        String fileName = getYtDlpFileName();
        Path target = binDir.resolve(fileName);

        TrueMusic.LOGGER.info("Downloading yt-dlp from: {}", url);
        try {
            downloadFile(url, target);
            if (!isWindows()) {
                target.toFile().setExecutable(true, false);
            }
            ytDlpPath = target.toAbsolutePath().toString();
            TrueMusic.LOGGER.info("yt-dlp downloaded to: {}", ytDlpPath);
        } catch (Exception e) {
            TrueMusic.LOGGER.error("Failed to download yt-dlp", e);
        }
    }

    private static String getYtDlpUrl() {
        if (isWindows()) return YTDLP_WINDOWS;
        if (isMac()) return YTDLP_MAC;
        return YTDLP_LINUX;
    }

    private static String getYtDlpFileName() {
        if (isWindows()) return "yt-dlp.exe";
        if (isMac()) return "yt-dlp";
        return "yt-dlp";
    }

    // ffmpeg

    private static void downloadFFmpeg() {
        if (isWindows()) {
            downloadFFmpegWindows();
        } else if (isLinux()) {
            downloadFFmpegLinux();
        } else {
            TrueMusic.LOGGER.warn("Automatic ffmpeg download not supported on macOS. " +
                "Please install ffmpeg via Homebrew: brew install ffmpeg");
        }
    }

    private static void downloadFFmpegWindows() {
        TrueMusic.LOGGER.info("Downloading ffmpeg for Windows from GitHub...");
        Path zipPath = binDir.resolve("ffmpeg-win.zip");

        try {
            downloadFile(FFMPEG_WIN_ZIP, zipPath);
            TrueMusic.LOGGER.info("Extracting ffmpeg.exe and ffprobe.exe...");
            extractFFmpegFromZip(zipPath);
            Files.deleteIfExists(zipPath);
        } catch (Exception e) {
            TrueMusic.LOGGER.error("Failed to download/extract ffmpeg for Windows", e);
        }
    }

    private static void extractFFmpegFromZip(Path zipFile) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // Extract ffmpeg.exe and ffprobe.exe (they're inside a subdirectory in the zip)
                boolean isFfmpeg = name.endsWith("/ffmpeg.exe") || name.equals("ffmpeg.exe");
                boolean isFfprobe = name.endsWith("/ffprobe.exe") || name.equals("ffprobe.exe");

                if (isFfmpeg || isFfprobe) {
                    String outName = isFfmpeg ? "ffmpeg.exe" : "ffprobe.exe";
                    Path outPath = binDir.resolve(outName);
                    TrueMusic.LOGGER.info("Extracting: {} -> {}", name, outPath);

                    try (OutputStream out = Files.newOutputStream(outPath)) {
                        zis.transferTo(out);
                    }

                    if (isFfmpeg) ffmpegPath = outPath.toAbsolutePath().toString();
                    if (isFfprobe) ffprobePath = outPath.toAbsolutePath().toString();
                }

                zis.closeEntry();
            }
        }

        if (ffmpegPath == null || ffprobePath == null) {
            TrueMusic.LOGGER.error("ffmpeg.exe or ffprobe.exe not found inside ZIP!");
        }
    }

    private static void downloadFFmpegLinux() {
        TrueMusic.LOGGER.warn("Automatic ffmpeg download for Linux is not implemented. " +
            "Please install via your package manager: sudo apt install ffmpeg");
    }

    // HTTP download

    private static void downloadFile(String url, Path target) throws Exception {
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMinutes(10))
            .GET()
            .build();

        TrueMusic.LOGGER.info("Downloading {} -> {}", url, target.getFileName());

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for: " + url);
        }

        long contentLength = response.headers().firstValueAsLong("content-length").orElse(-1);

        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(target)) {

            byte[] buf = new byte[64 * 1024];
            long downloaded = 0;
            long lastLog = System.currentTimeMillis();
            int n;

            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                downloaded += n;

                long now = System.currentTimeMillis();
                if (now - lastLog > 5000) {
                    if (contentLength > 0) {
                        TrueMusic.LOGGER.info("Downloading {}... {}/{} MB ({:.0f}%)",
                            target.getFileName(),
                            downloaded / (1024 * 1024),
                            contentLength / (1024 * 1024),
                            100.0 * downloaded / contentLength);
                    } else {
                        TrueMusic.LOGGER.info("Downloading {}... {} MB",
                            target.getFileName(), downloaded / (1024 * 1024));
                    }
                    lastLog = now;
                }
            }

            TrueMusic.LOGGER.info("Download complete: {} ({} MB)",
                target.getFileName(), downloaded / (1024 * 1024));
        }
    }

    // Public API

    // Returns the yt-dlp executable path (may be name-only if on PATH).
    public static Optional<String> getYtDlpExecutable() {
        if (ytDlpPath == null) {
            // Try re-resolving in case it was downloaded since init
            ytDlpPath = resolveExecutable("yt-dlp", getYtDlpFileName());
        }
        return Optional.ofNullable(ytDlpPath);
    }

    // Returns the ffmpeg executable path.
    public static Optional<String> getFfmpegExecutable() {
        if (ffmpegPath == null) {
            ffmpegPath = resolveExecutable("ffmpeg", "ffmpeg" + exeSuffix());
        }
        return Optional.ofNullable(ffmpegPath);
    }

    // Returns the ffprobe executable path.
    public static Optional<String> getFfprobeExecutable() {
        if (ffprobePath == null) {
            ffprobePath = resolveExecutable("ffprobe", "ffprobe" + exeSuffix());
        }
        return Optional.ofNullable(ffprobePath);
    }

    // Check if all required binaries are ready.
    public static boolean isReady() {
        return getYtDlpExecutable().isPresent()
            && getFfmpegExecutable().isPresent()
            && getFfprobeExecutable().isPresent();
    }

    // Wait until binaries are downloaded (max waitMs milliseconds).
    public static boolean waitUntilReady(long waitMs) {
        long deadline = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < deadline) {
            if (isReady()) return true;
            try { Thread.sleep(500); } catch (InterruptedException e) { break; }
        }
        return isReady();
    }

    // OS detection

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    public static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    public static boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac") || os.contains("darwin");
    }

    private static String exeSuffix() {
        return isWindows() ? ".exe" : "";
    }
}
