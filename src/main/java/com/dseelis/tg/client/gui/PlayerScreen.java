package com.dseelis.tg.client.gui;

import com.dseelis.tg.audio.*;
import com.dseelis.tg.client.ClientAudioManager;
import com.dseelis.tg.client.ClientPlayerMusicManager;
import com.dseelis.tg.client.FolderManager;
import com.dseelis.tg.menu.PlayerMenu;
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

public class PlayerScreen extends AbstractContainerScreen<PlayerMenu> {

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
    private static final int CTRL_Y   = TAB_H + GAP;
    private static final int LIST_Y   = CTRL_Y + SEARCH_H + GAP;

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
    private Button broadcastButton;
    private Button newFolderButton;
    private Button backButton;
    private Button deleteFolderButton;

    @Nullable private AudioResource selectedTrack;

    public PlayerScreen(PlayerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth  = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
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
        trackList = new TrackListWidget(minecraft, LEFT_W, listH, listTop, ITEM_H,
            this::onTrackSelected, this::onTrackDoubleClicked);
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

        float vol = ClientPlayerMusicManager.getInstance().getVolume();
        volumeSlider = new VolumeSlider(rx, ry, rw, BTN_H, vol,
            v -> ClientPlayerMusicManager.getInstance().updateVolume(v),
            this::onVolumeCommit);
        addRenderableWidget(volumeSlider);
        ry += BTN_H + GAP + 2;

        int half = (rw - GAP) / 2;
        PlaybackState state = ClientPlayerMusicManager.getInstance().getState();

        playPauseButton = Button.builder(Component.literal(playLabel(state)), b -> togglePlayPause())
            .bounds(rx, ry, half, BTN_H).build();
        addRenderableWidget(playPauseButton);

        stopButton = Button.builder(Component.literal("⏹ Stop"), b -> stopPlayback())
            .bounds(rx + half + GAP, ry, half, BTN_H).build();
        addRenderableWidget(stopButton);
        ry += BTN_H + GAP;

        prevButton = Button.builder(Component.literal("⏮ Prev"), b -> skip(false))
            .bounds(rx, ry, half, BTN_H).build();
        addRenderableWidget(prevButton);

        nextButton = Button.builder(Component.literal("Next ⏭"), b -> skip(true))
            .bounds(rx + half + GAP, ry, half, BTN_H).build();
        addRenderableWidget(nextButton);
        ry += BTN_H + GAP;

        PlayMode mode = ClientPlayerMusicManager.getInstance().getPlayMode();
        playModeButton = Button.builder(Component.literal(modeLabel(mode)), b -> toggleMode())
            .bounds(rx, ry, rw, BTN_H).build();
        addRenderableWidget(playModeButton);
        ry += BTN_H + GAP;

        boolean bc = ClientPlayerMusicManager.getInstance().isBroadcasting();
        broadcastButton = Button.builder(Component.literal(broadcastLabel(bc)), b -> toggleBroadcast())
            .bounds(rx, ry, rw, BTN_H).build();
        addRenderableWidget(broadcastButton);
    }

    // ---- Navigation ----

    private void switchTab(Tab tab) {
        activeTab    = tab;
        openFolderId = null;
        refreshContent();
    }

    private void updateVisibility() {
        boolean isAll          = activeTab == Tab.ALL_TRACKS;
        boolean isFolderList   = activeTab == Tab.FOLDERS && openFolderId == null;
        boolean isFolderInside = activeTab == Tab.FOLDERS && openFolderId != null;

        searchBox.visible          = isAll;
        newFolderButton.visible    = isFolderList;
        backButton.visible         = isFolderInside;
        deleteFolderButton.visible = isFolderInside;
    }

    private void refreshContent() {
        updateVisibility();

        if (activeTab == Tab.ALL_TRACKS) {
            String q = searchBox != null ? searchBox.getValue() : "";
            List<AudioResource> res = q.isEmpty()
                ? ClientAudioManager.getInstance().getAllResources()
                : ClientAudioManager.getInstance().searchResources(q);
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

    // ---- Folder management ----

    private void promptCreateFolder() {
        minecraft.setScreen(new FolderNameDialog(this, "", name -> {
            if (!name.isBlank()) {
                FolderManager.getInstance().createFolder(name.trim());
                refreshContent();
            }
        }));
    }

    private void deleteOpenFolder() {
        if (openFolderId != null) {
            FolderManager.getInstance().removeFolder(openFolderId);
            openFolderId = null;
            refreshContent();
        }
    }

    private void removeTrackFromOpenFolder(AudioResource track) {
        if (openFolderId == null) return;
        FolderManager.getInstance().removeTrackFromFolder(openFolderId, track.id());
        refreshContent();
    }

    // ---- Playback ----

    private void onTrackSelected(AudioResource t)      { selectedTrack = t; playPauseButton.active = true; }
    private void onTrackDoubleClicked(AudioResource t)  { selectedTrack = t; playTrack(t); }

    private void togglePlayPause() {
        PlaybackState s = ClientPlayerMusicManager.getInstance().getState();
        if (s.isPlaying()) {
            PlatformHelper.INSTANCE.sendToServer(new PlayerControlPacket(PlayerControlPacket.Action.PAUSE, s.resourceId()));
        } else if (s.isPaused()) {
            PlatformHelper.INSTANCE.sendToServer(new PlayerControlPacket(PlayerControlPacket.Action.PLAY, s.resourceId()));
        } else if (selectedTrack != null) {
            playTrack(selectedTrack);
        }
    }

    private void playTrack(AudioResource t) {
        PlatformHelper.INSTANCE.sendToServer(new PlayerControlPacket(PlayerControlPacket.Action.PLAY, t.id()));
    }

    private void stopPlayback() {
        PlatformHelper.INSTANCE.sendToServer(new PlayerControlPacket(PlayerControlPacket.Action.STOP, null));
    }

    private void skip(boolean forward) {
        PlatformHelper.INSTANCE.sendToServer(new com.dseelis.tg.network.packets.PlayerSkipPacket(forward));
    }

    private void toggleMode() {
        PlayMode cur  = ClientPlayerMusicManager.getInstance().getPlayMode();
        PlayMode next = cur.next();
        ClientPlayerMusicManager.getInstance().setPlayMode(next);
        playModeButton.setMessage(Component.literal(modeLabel(next)));
        PlatformHelper.INSTANCE.sendToServer(new PlayerPlayModePacket(next));
    }

    private void toggleBroadcast() {
        boolean cur  = ClientPlayerMusicManager.getInstance().isBroadcasting();
        boolean next = !cur;
        ClientPlayerMusicManager.getInstance().setBroadcasting(next);
        broadcastButton.setMessage(Component.literal(broadcastLabel(next)));
        PlatformHelper.INSTANCE.sendToServer(new PlayerBroadcastPacket(next));
    }

    private void onVolumeCommit() { /* headphones-only, no server packet */ }

    private void onSeek(float progress) {
        PlaybackState s = ClientPlayerMusicManager.getInstance().getState();
        if (s.isStopped()) return;
        ClientAudioManager.getInstance().getResource(s.resourceId()).ifPresent(res -> {
            if (res.durationMs() <= 0) return;
            long ms = (long) (progress * res.durationMs());
            PlatformHelper.INSTANCE.sendToServer(new PlayerSeekPacket(ms));
        });
    }

    // ---- Tick ----

    @Override
    protected void containerTick() {
        super.containerTick();
        PlaybackState s = ClientPlayerMusicManager.getInstance().getState();

        if (!s.isStopped()) {
            long dur = ClientAudioManager.getInstance().getResource(s.resourceId())
                .map(AudioResource::durationMs).orElse(-1L);
            progressSlider.update(s.getCurrentPositionMs(System.currentTimeMillis()), dur);
        } else {
            progressSlider.update(0, -1);
        }

        playPauseButton.setMessage(Component.literal(playLabel(s)));
        playPauseButton.active = s.isPlaying() || s.isPaused() || selectedTrack != null;
        stopButton.active      = !s.isStopped();

        PlayMode mode = ClientPlayerMusicManager.getInstance().getPlayMode();
        playModeButton.setMessage(Component.literal(modeLabel(mode)));

        boolean bc = ClientPlayerMusicManager.getInstance().isBroadcasting();
        broadcastButton.setMessage(Component.literal(broadcastLabel(bc)));
    }

    // ---- Rendering ----

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        g.fill(leftPos, topPos, leftPos + W, topPos + H, 0xEE0D1520);

        int bc = 0xFF1E2D3D;
        g.fill(leftPos,         topPos,         leftPos + W,     topPos + 1,     bc);
        g.fill(leftPos,         topPos + H - 1, leftPos + W,     topPos + H,     bc);
        g.fill(leftPos,         topPos,         leftPos + 1,     topPos + H,     bc);
        g.fill(leftPos + W - 1, topPos,         leftPos + W,     topPos + H,     bc);

        int divX = leftPos + LEFT_W + PAD + PAD / 2;
        g.fill(divX, topPos + PAD, divX + 1, topPos + H - PAD, 0x30FFFFFF);

        int lx   = leftPos + PAD;
        int ty   = topPos  + PAD;
        int tabW = (LEFT_W - GAP) / 2;
        if (activeTab == Tab.ALL_TRACKS) {
            g.fill(lx, ty + TAB_H, lx + tabW, ty + TAB_H + 2, 0xFF40AAFF);
        } else {
            g.fill(lx + tabW + GAP, ty + TAB_H, lx + LEFT_W, ty + TAB_H + 2, 0xFF40AAFF);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);

        int lx      = leftPos + PAD;
        int headerY = topPos + PAD + LIST_Y - 2;
        g.drawString(font, getLeftHeader(), lx, headerY, 0x888888);

        int rx = leftPos + LEFT_W + PAD * 2;
        renderRightPanel(g, rx, topPos + PAD);
    }

    private String getLeftHeader() {
        if (activeTab == Tab.ALL_TRACKS) {
            int total = ClientAudioManager.getInstance().getAllResources().size();
            return "All Tracks (" + total + ")";
        } else if (openFolderId != null) {
            return FolderManager.getInstance().getFolder(openFolderId)
                .map(f -> f.getName() + " (" + f.size() + " tracks)").orElse("Folder");
        } else {
            return "Folders (" + FolderManager.getInstance().getAllFolders().size() + ")";
        }
    }

    private void renderRightPanel(GuiGraphics g, int x, int y) {
        boolean bc = ClientPlayerMusicManager.getInstance().isBroadcasting();
        g.drawString(font, bc ? "Broadcasting" : "Now Playing", x, y, bc ? 0xFF8855 : 0xFFFFFF);
        y += 12;

        PlaybackState s = ClientPlayerMusicManager.getInstance().getState();
        int rw = W - LEFT_W - PAD * 3;

        if (s.isStopped()) {
            g.drawString(font, "Nothing playing", x, y, 0x555555);
        } else {
            String name = ClientAudioManager.getInstance().getResource(s.resourceId())
                .map(AudioResource::name).orElse("Unknown");
            if (font.width(name) > rw) name = font.plainSubstrByWidth(name, rw - 8) + "...";
            int col = s.isPlaying() ? (bc ? 0xFF8855 : 0x55FF55) : 0xFFFF55;
            g.drawString(font, name, x, y, col);
            if (s.isPaused()) g.drawString(font, "(Paused)", x, y + 10, 0x888888);
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

    // ---- Helpers ----

    private String playLabel(PlaybackState s)  { return s.isPlaying() ? "Pause" : "Play"; }
    private String modeLabel(PlayMode m) { return "Mode: " + m.getDisplayName(); }
    private String broadcastLabel(boolean on)  { return on ? "Broadcast: ON" : "Broadcast: OFF"; }
}
