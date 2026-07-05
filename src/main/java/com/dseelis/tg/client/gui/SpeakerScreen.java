package com.dseelis.tg.client.gui;

import com.dseelis.tg.audio.*;
import com.dseelis.tg.client.ClientAudioManager;
import com.dseelis.tg.client.ClientSpeakerManager;
import com.dseelis.tg.client.FolderManager;
import com.dseelis.tg.menu.SpeakerMenu;
import com.dseelis.tg.network.packets.*;
import com.dseelis.tg.platform.PlatformHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SpeakerScreen extends AbstractContainerScreen<SpeakerMenu> {

    // Layout
    private static final int W        = 460;
    private static final int H        = 280;
    private static final int PAD      = 8;
    private static final int GAP      = 4;
    private static final int LEFT_W   = 262;
    private static final int BTN_H    = 18;
    private static final int ITEM_H   = 16;
    private static final int TAB_H    = 16;
    private static final int SEARCH_H = 16;
    private static final int HEADER_H = 10;
    private static final int CTRL_Y   = TAB_H + GAP;
    private static final int LIST_Y   = CTRL_Y + SEARCH_H + GAP + HEADER_H;

    private enum Tab { ALL_TRACKS, FOLDERS }
    private Tab activeTab = Tab.ALL_TRACKS;
    @Nullable private UUID openFolderId = null;

    private Button tabAllButton;
    private Button tabFoldersButton;
    private EditBox searchBox;
    private TrackListWidget trackList;
    private VolumeSlider volumeSlider;
    private ProgressSlider progressSlider;
    private Button playPauseButton;
    private Button stopButton;
    private Button prevButton;
    private Button nextButton;
    private Button playModeButton;
    private Button folderFilterButton;
    private Button duckButton;
    private Button newFolderButton;
    private Button backButton;
    private Button deleteFolderButton;

    @Nullable private AudioResource selectedTrack;
    private float currentVolume;
    @Nullable private UUID activeFilterFolderId;

    public SpeakerScreen(SpeakerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth  = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        currentVolume = ClientSpeakerManager.getInstance().getSpeakerVolume(menu.getSpeakerPos());
        FolderManager.getInstance().load();
        buildWidgets();
        refreshContent();
    }

    private void buildWidgets() {
        int lx = leftPos + PAD;
        int ly = topPos  + PAD;
        int rw = W - LEFT_W - PAD * 3;
        int rx = leftPos + LEFT_W + PAD * 2;
        int ry = ly;

        int tabW = (LEFT_W - GAP) / 2;
        tabAllButton = Button.builder(Component.literal("🎵 All Tracks"), b -> switchTab(Tab.ALL_TRACKS))
            .bounds(lx, ly, tabW, TAB_H).build();
        addRenderableWidget(tabAllButton);

        tabFoldersButton = Button.builder(Component.literal("📁 Folders"), b -> switchTab(Tab.FOLDERS))
            .bounds(lx + tabW + GAP, ly, tabW, TAB_H).build();
        addRenderableWidget(tabFoldersButton);

        int ctrlY = ly + CTRL_Y;
        searchBox = new EditBox(font, lx, ctrlY, LEFT_W, SEARCH_H, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search tracks..."));
        searchBox.setResponder(q -> refreshContent());
        searchBox.setMaxLength(100);
        addRenderableWidget(searchBox);

        backButton = Button.builder(Component.literal("⬅ Back"), b -> exitFolder())
            .bounds(lx, ctrlY, 56, SEARCH_H).build();
        backButton.visible = false;
        addRenderableWidget(backButton);

        deleteFolderButton = Button.builder(Component.literal("Delete"), b -> deleteOpenFolder())
            .bounds(lx + 60, ctrlY, 52, SEARCH_H).build();
        deleteFolderButton.visible = false;
        addRenderableWidget(deleteFolderButton);

        int listTop = ly + LIST_Y;
        int listH   = H - LIST_Y - PAD * 2 - BTN_H - GAP;
        trackList = new TrackListWidget(
            minecraft, LEFT_W, listH, listTop, ITEM_H,
            this::onTrackSelected, this::onTrackDoubleClicked
        );
        trackList.setX(lx);
        addRenderableWidget(trackList);

        newFolderButton = Button.builder(Component.literal("➕ New Folder"), b -> promptCreateFolder())
            .bounds(lx, topPos + H - PAD - BTN_H, LEFT_W, BTN_H).build();
        newFolderButton.visible = false;
        addRenderableWidget(newFolderButton);

        ry += 22;

        progressSlider = new ProgressSlider(rx, ry, rw, 22, this::onSeek);
        addRenderableWidget(progressSlider);
        ry += 22 + GAP;

        volumeSlider = new VolumeSlider(rx, ry, rw, BTN_H, currentVolume,
            this::onVolumeChanged, this::onVolumeCommit);
        addRenderableWidget(volumeSlider);
        ry += BTN_H + GAP + 2;

        // One row of 4 equal transport buttons
        int btnW4 = (rw - GAP * 3) / 4;
        PlaybackState state = getCurrentState();

        prevButton = Button.builder(Component.literal("⏮"), b -> skipTrack(false))
            .bounds(rx, ry, btnW4, BTN_H).build();
        addRenderableWidget(prevButton);

        playPauseButton = Button.builder(Component.literal(playLabel(state)), b -> togglePlayPause())
            .bounds(rx + btnW4 + GAP, ry, btnW4, BTN_H).build();
        addRenderableWidget(playPauseButton);

        stopButton = Button.builder(Component.literal("⏹ Stop"), b -> stopPlayback())
            .bounds(rx + (btnW4 + GAP) * 2, ry, btnW4, BTN_H).build();
        addRenderableWidget(stopButton);

        nextButton = Button.builder(Component.literal("⏭"), b -> skipTrack(true))
            .bounds(rx + (btnW4 + GAP) * 3, ry, btnW4, BTN_H).build();
        addRenderableWidget(nextButton);
        ry += BTN_H + GAP;

        PlayMode mode = ClientSpeakerManager.getInstance().getSpeakerPlayMode(menu.getSpeakerPos());
        playModeButton = Button.builder(Component.literal(modeLabel(mode)), b -> togglePlayMode())
            .bounds(rx, ry, rw, BTN_H).build();
        addRenderableWidget(playModeButton);
        ry += BTN_H + GAP;

        folderFilterButton = Button.builder(Component.literal(filterLabel()), b -> toggleFolderFilter())
            .bounds(rx, ry, rw, BTN_H).build();
        addRenderableWidget(folderFilterButton);
        ry += BTN_H + GAP;

        boolean duck = com.dseelis.tg.config.TrueMusicClientConfig.isDuckMinecraftMusic();
        duckButton = Button.builder(Component.literal(duckLabel(duck)), b -> toggleDuck())
            .bounds(rx, ry, rw, BTN_H).build();
        addRenderableWidget(duckButton);
    }

    private void switchTab(Tab tab) {
        activeTab    = tab;
        openFolderId = null;
        refreshContent();
    }

    private void updateWidgetVisibility() {
        boolean isAll          = activeTab == Tab.ALL_TRACKS;
        boolean isFolderList   = activeTab == Tab.FOLDERS && openFolderId == null;
        boolean isFolderInside = activeTab == Tab.FOLDERS && openFolderId != null;

        searchBox.visible          = isAll;
        newFolderButton.visible    = isFolderList;
        backButton.visible         = isFolderInside;
        deleteFolderButton.visible = isFolderInside;
    }

    private void refreshContent() {
        updateWidgetVisibility();

        if (activeTab == Tab.ALL_TRACKS) {
            String q = searchBox != null ? searchBox.getValue() : "";
            List<AudioResource> res = new ArrayList<>(q.isEmpty()
                ? ClientAudioManager.getInstance().getAllResources()
                : ClientAudioManager.getInstance().searchResources(q));
            res.sort(Comparator.comparing(AudioResource::name, String.CASE_INSENSITIVE_ORDER));
            trackList.updateEntries(res);
            // Right-click -> open folder selection screen
            trackList.setRightClickHandler((track, mx, my) ->
                minecraft.setScreen(new FolderSelectScreen(this, track, this::refreshContent)));

        } else if (openFolderId == null) {
            trackList.updateFolderEntries(FolderManager.getInstance().getAllFolders(), this::openFolder);

        } else {
            List<AudioResource> tracks = FolderManager.getInstance().getTracksInFolder(openFolderId);
            tracks.sort(Comparator.comparing(AudioResource::name, String.CASE_INSENSITIVE_ORDER));
            trackList.updateEntriesWithRemove(tracks,
                this::onTrackSelected, this::onTrackDoubleClicked,
                this::removeTrackFromOpenFolder);
        }
    }

    private void openFolder(UUID id)  { openFolderId = id;   refreshContent(); }
    private void exitFolder()         { openFolderId = null; refreshContent(); }

    private void promptCreateFolder() {
        minecraft.setScreen(new FolderNameDialog(this, "", name -> {
            if (!name.isBlank()) {
                FolderManager.getInstance().createFolder(name.trim());
                refreshContent();
            }
        }));
    }

    private void deleteOpenFolder() {
        if (openFolderId == null) return;
        if (openFolderId.equals(activeFilterFolderId)) {
            activeFilterFolderId = null;
            sendPlaylistPacket(null);
        }
        FolderManager.getInstance().removeFolder(openFolderId);
        openFolderId = null;
        refreshContent();
    }

    private void removeTrackFromOpenFolder(AudioResource track) {
        if (openFolderId == null) return;
        FolderManager.getInstance().removeTrackFromFolder(openFolderId, track.id());
        refreshContent();
    }

    private void onTrackSelected(AudioResource track)     { selectedTrack = track; playPauseButton.active = true; }
    private void onTrackDoubleClicked(AudioResource track) { selectedTrack = track; playTrack(track); }

    private void togglePlayPause() {
        PlaybackState state = getCurrentState();
        if (state.isPlaying()) {
            PlatformHelper.INSTANCE.sendToServer(
                new SpeakerControlPacket(menu.getSpeakerPos(), SpeakerControlPacket.Action.PAUSE, state.resourceId()));
        } else if (state.isPaused()) {
            PlatformHelper.INSTANCE.sendToServer(
                new SpeakerControlPacket(menu.getSpeakerPos(), SpeakerControlPacket.Action.PLAY, state.resourceId()));
        } else if (selectedTrack != null) {
            playTrack(selectedTrack);
        }
    }

    private void playTrack(AudioResource track) {
        PlatformHelper.INSTANCE.sendToServer(
            new SpeakerControlPacket(menu.getSpeakerPos(), SpeakerControlPacket.Action.PLAY, track.id()));
    }

    private void stopPlayback() {
        PlatformHelper.INSTANCE.sendToServer(
            new SpeakerControlPacket(menu.getSpeakerPos(), SpeakerControlPacket.Action.STOP, null));
    }

    private void skipTrack(boolean forward) {
        PlatformHelper.INSTANCE.sendToServer(new SpeakerSkipPacket(menu.getSpeakerPos(), forward));
    }

    private void togglePlayMode() {
        PlayMode cur  = ClientSpeakerManager.getInstance().getSpeakerPlayMode(menu.getSpeakerPos());
        PlayMode next = cur.next();
        ClientSpeakerManager.getInstance().updateSpeaker(menu.getSpeakerPos(), getCurrentState(), currentVolume, next);
        playModeButton.setMessage(Component.literal(modeLabel(next)));
        PlatformHelper.INSTANCE.sendToServer(new SpeakerPlayModePacket(menu.getSpeakerPos(), next));
    }

    private void toggleFolderFilter() {
        if (activeFilterFolderId != null) {
            activeFilterFolderId = null;
            sendPlaylistPacket(null);
        } else {
            if (openFolderId != null && activeTab == Tab.FOLDERS) {
                activeFilterFolderId = openFolderId;
                ensureServerPlaylistForFolder(activeFilterFolderId);
            } else {
                folderFilterButton.setMessage(Component.literal("Open a folder first"));
                return;
            }
        }
        folderFilterButton.setMessage(Component.literal(filterLabel()));
    }

    private void toggleDuck() {
        boolean cur  = com.dseelis.tg.config.TrueMusicClientConfig.isDuckMinecraftMusic();
        boolean next = !cur;
        // Store supplier that returns the new value
        boolean[] val = { next };
        com.dseelis.tg.config.TrueMusicClientConfig.setDuckMinecraftMusic(() -> val[0]);
        duckButton.setMessage(Component.literal(duckLabel(next)));
        // Apply immediately
        PlaybackState s = getCurrentState();
        com.dseelis.tg.client.MusicDuckingManager.getInstance().refresh(s.isPlaying());
    }

    private void ensureServerPlaylistForFolder(UUID folderId) {
        FolderManager.getInstance().getFolder(folderId).ifPresent(folder -> {
            String name = "folder:" + folder.getName();
            PlaylistManager mgr = PlaylistManager.getInstance();
            Playlist pl = mgr.getAllPlaylists().stream()
                .filter(p -> p.getName().equals(name)).findFirst()
                .orElseGet(() -> mgr.createPlaylistFromNames(name,
                    FolderManager.getInstance().getTracksInFolder(folderId)
                        .stream().map(AudioResource::name).toList()));
            sendPlaylistPacket(pl.getId());
        });
    }

    private void sendPlaylistPacket(@Nullable UUID id) {
        PlatformHelper.INSTANCE.sendToServer(new SpeakerSetPlaylistPacket(menu.getSpeakerPos(), id));
    }

    private void onVolumeChanged(float v) {
        currentVolume = v;
        ClientSpeakerManager.getInstance().updateVolume(menu.getSpeakerPos(), v);
    }

    private void onVolumeCommit() {
        PlatformHelper.INSTANCE.sendToServer(new SpeakerVolumePacket(menu.getSpeakerPos(), currentVolume));
    }

    private void onSeek(float progress) {
        PlaybackState state = getCurrentState();
        if (state.isStopped()) return;
        ClientAudioManager.getInstance().getResource(state.resourceId()).ifPresent(res -> {
            if (res.durationMs() <= 0) return;
            long ms = (long) (progress * res.durationMs());
            PlatformHelper.INSTANCE.sendToServer(new SpeakerSeekPacket(menu.getSpeakerPos(), ms));
        });
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        PlaybackState state = getCurrentState();

        if (!state.isStopped()) {
            long dur = ClientAudioManager.getInstance().getResource(state.resourceId())
                .map(AudioResource::durationMs).orElse(-1L);
            progressSlider.update(state.getCurrentPositionMs(System.currentTimeMillis()), dur);
        } else {
            progressSlider.update(0, -1);
        }

        playPauseButton.setMessage(Component.literal(playLabel(state)));
        playPauseButton.active = state.isPlaying() || state.isPaused() || selectedTrack != null;
        stopButton.active      = !state.isStopped();

        prevButton.active = !state.isStopped();
        nextButton.active = !state.isStopped();

        PlayMode mode = ClientSpeakerManager.getInstance().getSpeakerPlayMode(menu.getSpeakerPos());
        playModeButton.setMessage(Component.literal(modeLabel(mode)));
        folderFilterButton.setMessage(Component.literal(filterLabel()));

        boolean duck = com.dseelis.tg.config.TrueMusicClientConfig.isDuckMinecraftMusic();
        duckButton.setMessage(Component.literal(duckLabel(duck)));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        // Main background
        g.fill(leftPos, topPos, leftPos + W, topPos + H, 0xF0050810);

        // Neon cyan border
        int bc = 0xFF00FFFF;
        g.fill(leftPos,         topPos,         leftPos + W,     topPos + 2,     bc);
        g.fill(leftPos,         topPos + H - 2, leftPos + W,     topPos + H,     bc);
        g.fill(leftPos,         topPos,         leftPos + 2,     topPos + H,     bc);
        g.fill(leftPos + W - 2, topPos,         leftPos + W,     topPos + H,     bc);

        // Magenta divider (2px)
        int divX = leftPos + LEFT_W + PAD + PAD / 2;
        g.fill(divX, topPos + PAD, divX + 2, topPos + H - PAD, 0x80FF00FF);

        // Tab underline — cyan
        int lx   = leftPos + PAD;
        int ty   = topPos  + PAD;
        int tabW = (LEFT_W - GAP) / 2;
        if (activeTab == Tab.ALL_TRACKS) {
            g.fill(lx, ty + TAB_H, lx + tabW, ty + TAB_H + 2, 0xFF00FFFF);
        } else {
            g.fill(lx + tabW + GAP, ty + TAB_H, lx + LEFT_W, ty + TAB_H + 2, 0xFF00FFFF);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);

        int lx      = leftPos + PAD;
        int headerY = topPos + PAD + LIST_Y - HEADER_H;
        g.drawString(font, getLeftHeader(), lx, headerY, 0xFF6688BB);

        int rx = leftPos + LEFT_W + PAD * 2;
        renderRightPanel(g, rx, topPos + PAD);
    }

    private String getLeftHeader() {
        if (activeTab == Tab.ALL_TRACKS) {
            int total = ClientAudioManager.getInstance().getAllResources().size();
            return "All Tracks (" + total + ")  [RMB = add to folder]";
        } else if (openFolderId != null) {
            return FolderManager.getInstance().getFolder(openFolderId)
                .map(f -> f.getName() + " (" + f.size() + " tracks)").orElse("Folder");
        } else {
            return "Folders (" + FolderManager.getInstance().getAllFolders().size() + ")";
        }
    }

    private void renderRightPanel(GuiGraphics g, int x, int y) {
        g.drawString(font, "◈ NOW PLAYING", x, y, 0xFF00FFFF);
        y += 10;
        int rw = W - LEFT_W - PAD * 3;
        g.fill(x, y, x + rw, y + 1, 0x6000FFFF);
        y += 5;

        PlaybackState state = getCurrentState();

        if (state.isStopped()) {
            g.drawString(font, "Nothing playing", x, y, 0xFF334455);
        } else {
            String name = ClientAudioManager.getInstance().getResource(state.resourceId())
                .map(AudioResource::name).orElse("Unknown");
            if (font.width(name) > rw) name = font.plainSubstrByWidth(name, rw - 8) + "...";
            int col = state.isPlaying() ? 0xFF00FF66 : 0xFFFF6600;
            g.drawString(font, name, x, y, col);
            if (state.isPaused()) g.drawString(font, "(Paused)", x, y + 10, 0xFF445566);
        }

        if (activeFilterFolderId != null) {
            String fname = FolderManager.getInstance().getFolder(activeFilterFolderId)
                .map(TrackFolder::getName).orElse("?");
            String label = "◈ " + fname;
            g.drawString(font, label, x + rw - font.width(label), topPos + H - PAD - BTN_H - 6, 0xFF00CCFF);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Intercept right-click on track list before AbstractContainerScreen eats it
        if (btn == 1 && trackList != null && trackList.isMouseOver(mx, my)) {
            return trackList.mouseClicked(mx, my, btn);
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (searchBox != null && searchBox.isFocused()) {
            if (key == 256) { searchBox.setFocused(false); return true; }
            return searchBox.keyPressed(key, scan, mods);
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (searchBox != null && searchBox.isFocused()) return searchBox.charTyped(c, mods);
        return super.charTyped(c, mods);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (getFocused() != null && isDragging() && btn == 0)
            return getFocused().mouseDragged(mx, my, btn, dx, dy);
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    private PlaybackState getCurrentState() {
        return ClientSpeakerManager.getInstance()
            .getSpeakerState(menu.getSpeakerPos())
            .orElse(PlaybackState.STOPPED);
    }

    private String playLabel(PlaybackState s) { return s.isPlaying() ? "Pause" : "Play"; }

    private String modeLabel(PlayMode m) { return "Mode: " + m.getDisplayName(); }

    private String duckLabel(boolean on) { return on ? "Duck MC Music: ON" : "Duck MC Music: OFF"; }

    private String filterLabel() {
        if (activeFilterFolderId != null) {
            String name = FolderManager.getInstance().getFolder(activeFilterFolderId)
                .map(TrackFolder::getName).orElse("?");
            return "Folder: " + name + " [clear]";
        }
        return "Play from open folder";
    }
}
