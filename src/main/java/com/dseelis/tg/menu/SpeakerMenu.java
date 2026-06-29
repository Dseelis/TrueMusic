package com.dseelis.tg.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

public class SpeakerMenu extends AbstractContainerMenu {
    public static MenuType<SpeakerMenu> TYPE;

    private final BlockPos speakerPos;
    private final Level level;

    public SpeakerMenu(int containerId, Inventory playerInv, BlockPos speakerPos) {
        super(TYPE, containerId);
        this.speakerPos = speakerPos;
        this.level = playerInv.player.level();
    }

    public BlockPos getSpeakerPos() {
        return speakerPos;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(
            speakerPos.getX() + 0.5,
            speakerPos.getY() + 0.5,
            speakerPos.getZ() + 0.5
        ) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
