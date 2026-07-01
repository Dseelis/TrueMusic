package com.dseelis.tg.audio;

import java.util.*;

/**
 * A client-side folder grouping a subset of audio resources.
 * Folders are stored locally (not synced to server).
 */
public class TrackFolder {
    private final UUID id;
    private String name;
    private final List<UUID> trackIds;

    public TrackFolder(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.trackIds = new ArrayList<>();
    }

    public TrackFolder(UUID id, String name, List<UUID> trackIds) {
        this.id = id;
        this.name = name;
        this.trackIds = new ArrayList<>(trackIds);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<UUID> getTrackIds() {
        return Collections.unmodifiableList(trackIds);
    }

    public void addTrack(UUID trackId) {
        if (!trackIds.contains(trackId)) {
            trackIds.add(trackId);
        }
    }

    public void removeTrack(UUID trackId) {
        trackIds.remove(trackId);
    }

    public boolean contains(UUID trackId) {
        return trackIds.contains(trackId);
    }

    public boolean isEmpty() {
        return trackIds.isEmpty();
    }

    public int size() {
        return trackIds.size();
    }
}
