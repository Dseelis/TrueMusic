package com.dseelis.tg.client.gui;

import com.dseelis.tg.audio.AudioResource;
import com.dseelis.tg.audio.TrackFolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

// Reusable list widget supporting three modes:
//  - Normal track list                    (updateEntries)
//  - Track list with per-row remove btn   (updateEntriesWithRemove)
//  - Folder list                          (updateFolderEntries)
public class TrackListWidget extends ObjectSelectionList<TrackListWidget.BaseEntry> {

    // Default callbacks set at construction, used by updateEntries
    private Consumer<AudioResource> defaultOnSelect;
    private Consumer<AudioResource> defaultOnPlay;

    public TrackListWidget(Minecraft mc, int width, int height, int y, int itemHeight,
                           Consumer<AudioResource> onSelect, Consumer<AudioResource> onPlay) {
        super(mc, width, height, y, itemHeight);
        this.defaultOnSelect = onSelect;
        this.defaultOnPlay = onPlay;
    }

    // ---- Population ----

    // Normal list — uses the default callbacks set at construction.
    public void updateEntries(List<AudioResource> resources) {
        clearEntries();
        for (AudioResource r : resources) {
            addEntry(new TrackEntry(this, r, defaultOnSelect, defaultOnPlay, null));
        }
    }

    // List with a per-row remove (x) button; explicit callbacks override defaults.
    public void updateEntriesWithRemove(List<AudioResource> resources,
                                        Consumer<AudioResource> onSelect,
                                        Consumer<AudioResource> onPlay,
                                        Consumer<AudioResource> onRemove) {
        clearEntries();
        for (AudioResource r : resources) {
            addEntry(new TrackEntry(this, r, onSelect, onPlay, onRemove));
        }
    }

    // Folder list — each row opens the folder on click.
    public void updateFolderEntries(List<TrackFolder> folders, Consumer<UUID> onOpen) {
        clearEntries();
        for (TrackFolder f : folders) {
            addEntry(new FolderEntry(this, f, onOpen));
        }
    }

    @Override
    public int getRowWidth() { return this.width - 12; }

    @Override
    protected int getScrollbarPosition() { return this.getX() + this.width - 6; }

    // =========================================================
    //  Base entry
    // =========================================================

    public abstract static class BaseEntry extends ObjectSelectionList.Entry<BaseEntry> {
        protected final TrackListWidget list;

        protected BaseEntry(TrackListWidget list) {
            this.list = list;
        }

        protected boolean isSelected() {
            return list.getSelected() == this;
        }

        @Override
        public Component getNarration() { return Component.empty(); }
    }

    // =========================================================
    //  Track entry
    // =========================================================

    public static class TrackEntry extends BaseEntry {
        private static final int BTN_W = 14;
        private static final int BTN_H = 12;

        private final AudioResource resource;
        private final Consumer<AudioResource> onSelect;
        private final Consumer<AudioResource> onPlay;
        final Consumer<AudioResource> onRemove; // null = no remove button
        private long lastClickTime;

        // Right-click context: "add to folder" popup
        @org.jetbrains.annotations.Nullable
        private RightClickCallback onRightClick;

        public TrackEntry(TrackListWidget list,
                          AudioResource resource,
                          Consumer<AudioResource> onSelect,
                          Consumer<AudioResource> onPlay,
                          Consumer<AudioResource> onRemove) {
            super(list);
            this.resource = resource;
            this.onSelect = onSelect;
            this.onPlay = onPlay;
            this.onRemove = onRemove;
        }

        public void setOnRightClick(RightClickCallback cb) {
            this.onRightClick = cb;
        }

        @FunctionalInterface
        public interface RightClickCallback {
            void accept(AudioResource resource, int mouseX, int mouseY);
        }

        public AudioResource getResource() { return resource; }

        @Override
        public Component getNarration() { return Component.literal(resource.name()); }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            boolean selected = isSelected();
            if (hovered || selected) {
                g.fill(left, top, left + width, top + height,
                    selected ? 0x50FFFFFF : 0x25FFFFFF);
            }

            Minecraft mc = Minecraft.getInstance();
            int textColor = selected ? 0xFFFFFF : (hovered ? 0xE0E0E0 : 0xAAAAAA);

            // Duration label
            String dur = formatDuration(resource.durationMs());
            int durW = mc.font.width(dur);

            // Width budget for name
            int removeBudget = (onRemove != null) ? BTN_W + 4 : 0;
            int nameMaxW = width - durW - 8 - removeBudget;

            String name = resource.name();
            if (mc.font.width(name) > nameMaxW) {
                name = mc.font.plainSubstrByWidth(name, nameMaxW - 6) + "...";
            }
            g.drawString(mc.font, name, left + 3, top + (height - 8) / 2, textColor);

            // Duration (right-aligned, before remove btn)
            int durX = left + width - durW - 4 - removeBudget;
            g.drawString(mc.font, dur, durX, top + (height - 8) / 2, 0x666666);

            // Remove (x) button
            if (onRemove != null) {
                int bx = left + width - BTN_W - 1;
                int by = top + (height - BTN_H) / 2;
                boolean btnHov = mouseX >= bx && mouseX < bx + BTN_W
                    && mouseY >= by && mouseY < by + BTN_H;
                g.fill(bx, by, bx + BTN_W, by + BTN_H, btnHov ? 0xBBCC3333 : 0x66993333);
                g.drawString(mc.font, "x", bx + 3, by + 2, 0xFFCCCC);
            }
        }

        // True if the click X falls within the remove button column.
        public boolean isRemoveClick(double mouseX, int left, int width) {
            if (onRemove == null) return false;
            int bx = left + width - BTN_W - 1;
            return mouseX >= bx && mouseX < bx + BTN_W;
        }

        public void fireRemove() {
            if (onRemove != null) onRemove.accept(resource);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                list.setSelected(this);
                if (onSelect != null) onSelect.accept(resource);

                long now = System.currentTimeMillis();
                if (now - lastClickTime < 400 && onPlay != null) {
                    onPlay.accept(resource);
                }
                lastClickTime = now;
                return true;
            }
            if (button == 1) {
                list.setSelected(this);
                if (onRightClick != null) onRightClick.accept(resource, (int)mouseX, (int)mouseY);
                return true;
            }
            return false;
        }

        private String formatDuration(long ms) {
            if (ms < 0) return "--:--";
            long s = ms / 1000;
            return String.format("%d:%02d", s / 60, s % 60);
        }
    }

    // =========================================================
    //  Folder entry
    // =========================================================

    public static class FolderEntry extends BaseEntry {
        private final TrackFolder folder;
        private final Consumer<UUID> onOpen;

        public FolderEntry(TrackListWidget list, TrackFolder folder, Consumer<UUID> onOpen) {
            super(list);
            this.folder = folder;
            this.onOpen = onOpen;
        }

        @Override
        public Component getNarration() { return Component.literal(folder.getName()); }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            boolean selected = isSelected();
            if (hovered || selected) {
                g.fill(left, top, left + width, top + height,
                    selected ? 0x50FFFFFF : 0x25FFFFFF);
            }

            Minecraft mc = Minecraft.getInstance();
            int col = selected ? 0xFFFFFF : (hovered ? 0xFFE860 : 0xCCA830);

            String label = "> " + folder.getName();
            g.drawString(mc.font, label, left + 3, top + (height - 8) / 2, col);

            String count = folder.size() + " tracks >";
            int cw = mc.font.width(count);
            g.drawString(mc.font, count, left + width - cw - 4, top + (height - 8) / 2, 0x888866);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                list.setSelected(this);
                onOpen.accept(folder.getId());
                return true;
            }
            return false;
        }
    }

    // =========================================================
    //  Intercept remove-button clicks before row selection fires
    // =========================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Intercept remove-button clicks before row selection fires
            int rowW = getRowWidth();
            int lx   = getRowLeft();
            for (int i = 0; i < children().size(); i++) {
                BaseEntry entry = children().get(i);
                if (!(entry instanceof TrackEntry te) || te.onRemove == null) continue;

                int rowTop = getRowTop(i);
                int rowBot = rowTop + itemHeight;
                if (mouseY < rowTop || mouseY >= rowBot) continue;

                if (te.isRemoveClick(mouseX, lx, rowW)) {
                    te.fireRemove();
                    return true;
                }
            }
        }

        if (button == 1) {
            // Forward right-click directly to the hovered entry
            for (int i = 0; i < children().size(); i++) {
                BaseEntry entry = children().get(i);
                int rowTop = getRowTop(i);
                int rowBot = rowTop + itemHeight;
                if (mouseY >= rowTop && mouseY < rowBot
                        && mouseX >= getRowLeft() && mouseX < getRowLeft() + getRowWidth()) {
                    return entry.mouseClicked(mouseX, mouseY, button);
                }
            }
            return false;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    // Allow right-click context callback to be set on all current track entries
    public void setRightClickHandler(TrackEntry.RightClickCallback cb) {
        for (BaseEntry entry : children()) {
            if (entry instanceof TrackEntry te) {
                te.setOnRightClick(cb);
            }
        }
    }
}
