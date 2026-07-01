package com.dseelis.tg.client.gui;

import com.dseelis.tg.audio.AudioResource;
import com.dseelis.tg.audio.TrackFolder;
import com.dseelis.tg.client.FolderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// A screen to select a folder for a given AudioResource.
// Opens from SpeakerScreen/PlayerScreen on right-click on a track.
public class FolderSelectScreen extends Screen {

    private static final int W = 250;
    private static final int H = 200;
    private static final int PAD = 8;
    private static final int ITEM_H = 16;
    private static final int BTN_H = 18;
    private static final int TITLE_H = 20;
    private static final int NEW_FOLDER_BTN_Y_OFFSET = 2;

    private final Screen parentScreen;
    private final AudioResource trackToAdd;
    // Called after a track is successfully added to a folder, before returning to parent.
    private final @Nullable Runnable onDone;

    private TrackListWidget folderListWidget;
    private Button backButton;
    private Button newFolderButton;

    public FolderSelectScreen(Screen parentScreen, AudioResource trackToAdd) {
        this(parentScreen, trackToAdd, null);
    }

    public FolderSelectScreen(Screen parentScreen, AudioResource trackToAdd, @Nullable Runnable onDone) {
        super(Component.literal("Select Folder for " + trackToAdd.name()));
        this.parentScreen = parentScreen;
        this.trackToAdd = trackToAdd;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        super.init();

        int x = (width - W) / 2;
        int y = (height - H) / 2;

        // Title (track name)
        Component titleComponent = Component.literal(trackToAdd.name()).withStyle(
            net.minecraft.ChatFormatting.YELLOW);
        int titleWidth = font.width(titleComponent);
        addRenderableWidget(new StaticLabel(
            Component.literal("📂 Add to folder: ").withStyle(net.minecraft.ChatFormatting.WHITE)
                .append(titleComponent),
            x + PAD + (W - PAD * 2 - titleWidth - font.width("📂 Add to folder: ")) / 2,
            y + PAD + (TITLE_H - 8) / 2, 0xFFFFFF));

        int listY = y + PAD + TITLE_H;
        int listHeight = H - TITLE_H - BTN_H - PAD * 3 - NEW_FOLDER_BTN_Y_OFFSET;

        folderListWidget = new TrackListWidget(
            minecraft, W - PAD * 2, listHeight, listY, ITEM_H,
            r -> {}, r -> {}); // callbacks unused; folder selection via updateFolderEntries
        folderListWidget.setX(x + PAD);
        addRenderableWidget(folderListWidget);

        // Buttons at the bottom
        int btnY = y + H - PAD - BTN_H;
        int halfW = (W - PAD * 3) / 2;

        newFolderButton = Button.builder(Component.literal("➕ New Folder"), b -> promptCreateFolder())
            .bounds(x + PAD, btnY, halfW, BTN_H).build();
        addRenderableWidget(newFolderButton);

        backButton = Button.builder(Component.literal("⬅ Back"), b -> onClose())
            .bounds(x + PAD + halfW + PAD, btnY, halfW, BTN_H).build();
        addRenderableWidget(backButton);

        refreshFolderList();
    }

    private void refreshFolderList() {
        List<TrackFolder> folders = new ArrayList<>(FolderManager.getInstance().getAllFolders());
        folders.sort(Comparator.comparing(TrackFolder::getName, String.CASE_INSENSITIVE_ORDER));
        folderListWidget.updateFolderEntries(folders, this::onFolderSelected);
    }

    private void onFolderSelected(UUID folderId) {
        FolderManager.getInstance().addTrackToFolder(folderId, trackToAdd.id());
        if (onDone != null) onDone.run();
        minecraft.setScreen(parentScreen);
    }

    private void promptCreateFolder() {
        minecraft.setScreen(new FolderNameDialog(this, "", newName -> {
            if (!newName.isBlank()) {
                TrackFolder newFolder = FolderManager.getInstance().createFolder(newName.trim());
                FolderManager.getInstance().addTrackToFolder(newFolder.getId(), trackToAdd.id());
                if (onDone != null) onDone.run();
                minecraft.setScreen(parentScreen);
            }
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);

        int x = (width - W) / 2;
        int y = (height - H) / 2;

        // Dialog background
        graphics.fill(x, y, x + W, y + H, 0xF0101820);
        // Border
        int borderColor = 0xFF1E2D3D;
        graphics.fill(x, y, x + W, y + 1, borderColor);
        graphics.fill(x, y + H - 1, x + W, y + H, borderColor);
        graphics.fill(x, y, x + 1, y + H, borderColor);
        graphics.fill(x + W - 1, y, x + W, y + H, borderColor);

        // Title background
        graphics.fill(x, y + PAD, x + W, y + PAD + TITLE_H, 0xFF1A2A3A);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Helper for non-interactive labels
    private static class StaticLabel extends AbstractWidget {
        private final Component text;

        public StaticLabel(Component text, int x, int y, int color) {
            super(x, y, Minecraft.getInstance().font.width(text), Minecraft.getInstance().font.lineHeight, text);
            this.text = text;
            this.active = false;
            this.setAlpha(0.0f);
            this.setMessage(text);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.drawString(Minecraft.getInstance().font, text, getX(), getY(), 0xFFFFFF, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            // No narration needed for a static label
        }
    }
}
