package com.dseelis.tg.audio;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlaylistManager {
    private static final PlaylistManager instance = new PlaylistManager();
    private final Map<UUID, Playlist> playlists = new ConcurrentHashMap<>();

    private PlaylistManager() {}

    public static PlaylistManager getInstance() {
        return instance;
    }

    public Playlist createPlaylist(String name) {
        Playlist playlist = new Playlist(name);
        playlists.put(playlist.getId(), playlist);
        return playlist;
    }

    public void addPlaylist(Playlist playlist) {
        playlists.put(playlist.getId(), playlist);
    }

    public void removePlaylist(UUID playlistId) {
        playlists.remove(playlistId);
    }

    public Optional<Playlist> getPlaylist(UUID playlistId) {
        return Optional.ofNullable(playlists.get(playlistId));
    }

    public List<Playlist> getAllPlaylists() {
        return new ArrayList<>(playlists.values());
    }

    public void clear() {
        playlists.clear();
    }

    /**
     * Create a playlist from all available audio resources.
     */
    public Playlist createPlaylistFromAllResources(String name) {
        Playlist playlist = new Playlist(name);
        List<AudioResource> resources = AudioManager.getInstance().getAllResources();
        for (AudioResource resource : resources) {
            playlist.addTrack(resource.id());
        }
        playlists.put(playlist.getId(), playlist);
        return playlist;
    }

    /**
     * Create a playlist from a list of track names.
     */
    public Playlist createPlaylistFromNames(String playlistName, List<String> trackNames) {
        Playlist playlist = new Playlist(playlistName);
        AudioManager audioManager = AudioManager.getInstance();

        for (String trackName : trackNames) {
            audioManager.getResourceByName(trackName).ifPresent(resource ->
                playlist.addTrack(resource.id())
            );
        }

        playlists.put(playlist.getId(), playlist);
        return playlist;
    }
}
