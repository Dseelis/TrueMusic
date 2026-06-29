package com.dseelis.tg.audio;

import java.util.*;

// Represents a playlist of audio tracks.

public class Playlist {
    private final UUID id;
    private String name;
    private final List<UUID> trackIds;
    private int currentIndex;
    private final Random random;

    public Playlist(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.trackIds = new ArrayList<>();
        this.currentIndex = 0;
        this.random = new Random();
    }

    public Playlist(UUID id, String name, List<UUID> trackIds, int currentIndex) {
        this.id = id;
        this.name = name;
        this.trackIds = new ArrayList<>(trackIds);
        this.currentIndex = currentIndex;
        this.random = new Random();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<UUID> getTrackIds() {
        return Collections.unmodifiableList(trackIds);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int index) {
        if (index >= 0 && index < trackIds.size()) {
            this.currentIndex = index;
        }
    }

    public void addTrack(UUID trackId) {
        trackIds.add(trackId);
    }

    public void addTrack(int index, UUID trackId) {
        if (index >= 0 && index <= trackIds.size()) {
            trackIds.add(index, trackId);
        }
    }

    public void removeTrack(int index) {
        if (index >= 0 && index < trackIds.size()) {
            trackIds.remove(index);
            if (currentIndex >= trackIds.size() && !trackIds.isEmpty()) {
                currentIndex = trackIds.size() - 1;
            }
        }
    }

    public void removeTrack(UUID trackId) {
        trackIds.remove(trackId);
        if (currentIndex >= trackIds.size() && !trackIds.isEmpty()) {
            currentIndex = trackIds.size() - 1;
        }
    }

    public void clear() {
        trackIds.clear();
        currentIndex = 0;
    }

    public boolean isEmpty() {
        return trackIds.isEmpty();
    }

    public int size() {
        return trackIds.size();
    }

    public Optional<UUID> getCurrentTrack() {
        if (currentIndex >= 0 && currentIndex < trackIds.size()) {
            return Optional.of(trackIds.get(currentIndex));
        }
        return Optional.empty();
    }

    /**
     * Get the next track based on the play mode.
     */
    public Optional<UUID> getNextTrack(PlayMode mode) {
        if (trackIds.isEmpty()) {
            return Optional.empty();
        }

        return switch (mode) {
            case SINGLE -> Optional.empty(); // Don't advance
            case LOOP -> getCurrentTrack(); // Repeat current
            case SEQUENTIAL -> {
                if (currentIndex + 1 < trackIds.size()) {
                    currentIndex++;
                    yield Optional.of(trackIds.get(currentIndex));
                }
                yield Optional.empty(); // End of playlist
            }
            case SHUFFLE -> {
                if (trackIds.size() == 1) {
                    yield Optional.of(trackIds.get(0));
                }
                // Pick a random track different from current
                int nextIndex;
                do {
                    nextIndex = random.nextInt(trackIds.size());
                } while (nextIndex == currentIndex && trackIds.size() > 1);
                currentIndex = nextIndex;
                yield Optional.of(trackIds.get(currentIndex));
            }
        };
    }

    /**
     * Get the previous track.
     */
    public Optional<UUID> getPreviousTrack() {
        if (trackIds.isEmpty()) {
            return Optional.empty();
        }
        if (currentIndex > 0) {
            currentIndex--;
            return Optional.of(trackIds.get(currentIndex));
        }
        return Optional.empty();
    }

    /**
     * Move to a specific track by its UUID.
     */
    public boolean seekToTrack(UUID trackId) {
        int index = trackIds.indexOf(trackId);
        if (index >= 0) {
            currentIndex = index;
            return true;
        }
        return false;
    }

    /**
     * Shuffle the playlist order.
     */
    public void shuffle() {
        if (trackIds.isEmpty()) return;

        UUID currentTrack = trackIds.get(currentIndex);
        Collections.shuffle(trackIds, random);
        currentIndex = trackIds.indexOf(currentTrack);
    }
}
