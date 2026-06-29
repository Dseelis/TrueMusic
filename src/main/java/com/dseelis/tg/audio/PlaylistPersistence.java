package com.dseelis.tg.audio;

import com.dseelis.tg.TrueMusic;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class PlaylistPersistence {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PLAYLIST_FILE = "playlists.json";


    public static void savePlaylists(MinecraftServer server) {
        try {
            Path playlistsFile = getPlaylistsFile(server);
            Files.createDirectories(playlistsFile.getParent());

            List<Playlist> playlists = PlaylistManager.getInstance().getAllPlaylists();
            JsonArray array = new JsonArray();

            for (Playlist playlist : playlists) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", playlist.getId().toString());
                obj.addProperty("name", playlist.getName());
                obj.addProperty("currentIndex", playlist.getCurrentIndex());

                JsonArray tracks = new JsonArray();
                for (UUID trackId : playlist.getTrackIds()) {
                    tracks.add(trackId.toString());
                }
                obj.add("tracks", tracks);

                array.add(obj);
            }

            try (Writer writer = Files.newBufferedWriter(playlistsFile)) {
                GSON.toJson(array, writer);
            }

            TrueMusic.LOGGER.info("Saved {} playlists", playlists.size());

        } catch (Exception e) {
            TrueMusic.LOGGER.error("Failed to save playlists", e);
        }
    }


    public static void loadPlaylists(MinecraftServer server) {
        try {
            Path playlistsFile = getPlaylistsFile(server);

            if (!Files.exists(playlistsFile)) {
                TrueMusic.LOGGER.info("No playlists file found, starting fresh");
                return;
            }

            try (Reader reader = Files.newBufferedReader(playlistsFile)) {
                JsonArray array = GSON.fromJson(reader, JsonArray.class);
                if (array == null) return;

                PlaylistManager manager = PlaylistManager.getInstance();
                manager.clear();

                for (JsonElement element : array) {
                    JsonObject obj = element.getAsJsonObject();

                    UUID id = UUID.fromString(obj.get("id").getAsString());
                    String name = obj.get("name").getAsString();
                    int currentIndex = obj.get("currentIndex").getAsInt();

                    List<UUID> trackIds = new ArrayList<>();
                    JsonArray tracks = obj.getAsJsonArray("tracks");
                    for (JsonElement trackElement : tracks) {
                        trackIds.add(UUID.fromString(trackElement.getAsString()));
                    }

                    Playlist playlist = new Playlist(id, name, trackIds, currentIndex);
                    manager.addPlaylist(playlist);
                }

                TrueMusic.LOGGER.info("Loaded {} playlists", array.size());
            }

        } catch (Exception e) {
            TrueMusic.LOGGER.error("Failed to load playlists", e);
        }
    }

    private static Path getPlaylistsFile(MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("data")
            .resolve("truemusic")
            .resolve(PLAYLIST_FILE);
    }
}
