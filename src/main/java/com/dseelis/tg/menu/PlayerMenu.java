package com.dseelis.tg.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Menu for the Music Player item.
 * Unlike SpeakerMenu, this is tied to the player (not a block position).
 * The player UUID is used to identify the "virtual speaker" for the item.
 */
public class PlayerMenu extends AbstractContainerMenu {
    public static MenuType<PlayerMenu> TYPE;

    private final UUID playerUuid;
    private final Player player;

    public PlayerMenu(int containerId, Inventory playerInv) {
        super(TYPE, containerId);
        this.player = playerInv.player;
        this.playerUuid = player.getUUID();
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean stillValid(Player player) {
        // Valid as long as the player holds the Music Player item
        return player.getUUID().equals(playerUuid);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
