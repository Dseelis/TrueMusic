package com.dseelis.tg.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * A small modal dialog for entering a folder name.
 * Shows on top of the parent screen.
 */
public class FolderNameDialog extends Screen {

    private static final int W = 220;
    private static final int H = 70;

    private final Screen parent;
    private final String initial;
    private final Consumer<String> onConfirm;

    private EditBox nameBox;

    public FolderNameDialog(Screen parent, String initial, Consumer<String> onConfirm) {
        super(Component.literal(initial.isEmpty() ? "New Folder" : "Rename Folder"));
        this.parent = parent;
        this.initial = initial;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int x = (width - W) / 2;
        int y = (height - H) / 2;

        nameBox = new EditBox(font, x + 8, y + 20, W - 16, 16, Component.literal("Folder name"));
        nameBox.setValue(initial);
        nameBox.setMaxLength(64);
        nameBox.setFocused(true);
        addRenderableWidget(nameBox);

        int half = (W - 24) / 2;
        addRenderableWidget(Button.builder(Component.literal("OK"), b -> confirm())
            .bounds(x + 8, y + H - 22, half, 16).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> cancel())
            .bounds(x + 16 + half, y + H - 22, half, 16).build());
    }

    private void confirm() {
        String value = nameBox.getValue().trim();
        if (!value.isEmpty()) {
            onConfirm.accept(value);
        }
        minecraft.setScreen(parent);
    }

    private void cancel() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) { // Enter / numpad Enter
            confirm();
            return true;
        }
        if (key == 256) { // Escape
            cancel();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        // Dim background
        renderTransparentBackground(g);

        int x = (width - W) / 2;
        int y = (height - H) / 2;

        // Dialog box
        g.fill(x, y, x + W, y + H, 0xF0101820);
        g.fill(x, y, x + W, y + 1, 0xFF1E2D3D);
        g.fill(x, y + H - 1, x + W, y + H, 0xFF1E2D3D);
        g.fill(x, y, x + 1, y + H, 0xFF1E2D3D);
        g.fill(x + W - 1, y, x + W, y + H, 0xFF1E2D3D);

        g.drawString(font, title, x + 8, y + 6, 0xFFFFFF);

        super.render(g, mx, my, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
