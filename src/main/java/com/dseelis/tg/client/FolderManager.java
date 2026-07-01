package com.dseelis.tg.client;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.audio.AudioResource;
import com.dseelis.tg.audio.TrackFolder;
import com.google.gson.*;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

// Client-side manager for track folders.
// Folders are stored locally in .minecraft/config/truemusic_folders.json
public class FolderManager {
    private static FolderManager instance;
    private final List<TrackFolder> folders = new CopyOnWriteArrayList<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private FolderManager() {}

    public static FolderManager getInstance() {
        if (instance == null) {
            instance = new FolderManager();
        }
        return instance;
    }

    public TrackFolder createFolder(String name) {
        TrackFolder folder = new TrackFolder(name);
        folders.add(folder);
        save();
        return folder;
    }

    public void removeFolder(UUID id) {
        folders.removeIf(f -> f.getId().equals(id));
        save();
    }

    public Optional<TrackFolder> getFolder(UUID id) {
        return folders.stream().filter(f -> f.getId().equals(id)).findFirst();
    }

    public List<TrackFolder> getAllFolders() {
        return Collections.unmodifiableList(folders);
    }

    public void addTrackToFolder(UUID folderId, UUID trackId) {
        getFolder(folderId).ifPresent(f -> {
            f.addTrack(trackId);
            save();
        });
    }

    public void removeTrackFromFolder(UUID folderId, UUID trackId) {
        getFolder(folderId).ifPresent(f -> {
            f.removeTrack(trackId);
            save();
        });
    }

    public void renameFolder(UUID folderId, String newName) {
        getFolder(folderId).ifPresent(f -> {
            f.setName(newName);
            save();
        });
    }

    /**
     * Get all AudioResources in a folder, resolved against ClientAudioManager.
     */
    public List<AudioResource> getTracksInFolder(UUID folderId) {
        return getFolder(folderId).map(folder -> {
            List<AudioResource> result = new ArrayList<>();
            for (UUID trackId : folder.getTrackIds()) {
                ClientAudioManager.getInstance().getResource(trackId).ifPresent(result::add);
            }
            return result;
        }).orElse(Collections.emptyList());
    }

    private Path getSavePath() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath()
            .resolve("config")
            .resolve("truemusic_folders.json");
    }

    public void save() {
        try {
            Path path = getSavePath();
            Files.createDirectories(path.getParent());

            JsonArray array = new JsonArray();
            for (TrackFolder folder : folders) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", folder.getId().toString());
                obj.addProperty("name", folder.getName());
                JsonArray tracks = new JsonArray();
                for (UUID trackId : folder.getTrackIds()) {
                    tracks.add(trackId.toString());
                }
                obj.add("tracks", tracks);
                array.add(obj);
            }

            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(array, w);
            }
        } catch (Exception e) {
            TrueMusic.LOGGER.error("Failed to save folders", e);
        }
    }

    public void load() {
        try {
            Path path = getSavePath();
            if (!Files.exists(path)) return;

            try (Reader r = Files.newBufferedReader(path)) {
                JsonArray array = GSON.fromJson(r, JsonArray.class);
                if (array == null) return;

                folders.clear();
                for (JsonElement el : array) {
                    JsonObject obj = el.getAsJsonObject();
                    UUID id = UUID.fromString(obj.get("id").getAsString());
                    String name = obj.get("name").getAsString();
                    List<UUID> trackIds = new ArrayList<>();
                    JsonArray tracks = obj.getAsJsonArray("tracks");
                    if (tracks != null) {
                        for (JsonElement t : tracks) {
                            trackIds.add(UUID.fromString(t.getAsString()));
                        }
                    }
                    folders.add(new TrackFolder(id, name, trackIds));
                }
            }
        } catch (Exception e) {
            TrueMusic.LOGGER.error("Failed to load folders", e);
        }
    }
}
