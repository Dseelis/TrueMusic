package com.dseelis.tg.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;


public class ProgressSlider extends AbstractWidget {
    private static final int BAR_HEIGHT = 6;
    private static final int KNOB_SIZE  = 8;

    private final Consumer<Float> onSeek;
    private float progress = 0f;
    private long durationMs = -1;
    private long positionMs = 0;
    private boolean dragging = false;

    public ProgressSlider(int x, int y, int width, int height, Consumer<Float> onSeek) {
        super(x, y, width, height, Component.empty());
        this.onSeek = onSeek;
    }

    public void update(long positionMs, long durationMs) {
        if (!dragging) {
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.progress = durationMs > 0 ? Math.min(1f, (float) positionMs / durationMs) : 0f;
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int barY = getY() + (height - BAR_HEIGHT) / 2;

        // Track background
        graphics.fill(getX(), barY, getX() + width, barY + BAR_HEIGHT, 0x3000FFFF);
        // Thin neon border for the track
        graphics.fill(getX(), barY, getX() + width, barY + 1, 0x6000FFFF);
        graphics.fill(getX(), barY + BAR_HEIGHT - 1, getX() + width, barY + BAR_HEIGHT, 0x6000FFFF);

        if (durationMs > 0) {
            int progressWidth = (int) (width * progress);
            // Filled portion — cyan
            graphics.fill(getX(), barY, getX() + progressWidth, barY + BAR_HEIGHT, 0xFF00FFFF);

            // Knob — magenta diamond shape
            int knobCx = getX() + progressWidth;
            int knobCy = getY() + height / 2;
            int ks = KNOB_SIZE / 2;
            boolean hot = dragging || isHovered;
            int knobColor = hot ? 0xFFFF66FF : 0xFFFF00FF;
            // Draw diamond: fill four triangles around center
            for (int dy = -ks; dy <= ks; dy++) {
                int hw = ks - Math.abs(dy);
                graphics.fill(knobCx - hw, knobCy + dy, knobCx + hw + 1, knobCy + dy + 1, knobColor);
            }
        }

        // Time text — cyan
        String timeText = formatTime(positionMs) + " / " + (durationMs > 0 ? formatTime(durationMs) : "--:--");
        int textWidth = net.minecraft.client.Minecraft.getInstance().font.width(timeText);
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
            timeText, getX() + (width - textWidth) / 2, getY() + height - 8, 0xFF00CCCC);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && durationMs > 0 && isMouseOver(mouseX, mouseY)) {
            dragging = true;
            updateFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            updateFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            onSeek.accept(progress);
            return true;
        }
        return false;
    }

    private void updateFromMouse(double mouseX) {
        float newProgress = (float) Math.max(0, Math.min(1, (mouseX - getX()) / width));
        this.progress = newProgress;
        this.positionMs = (long) (newProgress * durationMs);
    }

    private String formatTime(long ms) {
        if (ms < 0) return "--:--";
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
