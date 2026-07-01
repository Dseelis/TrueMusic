package com.dseelis.tg.item;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.audio.PlaybackState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Music Player item.
 *  - Right-click: open GUI (client-only playback, only the holder hears it)
 *  - The GUI has a "Broadcast" button that makes the server stream audio
 *    to all nearby players as if it were a speaker at the player's position.
 */
public class MusicPlayerItem extends Item {

    public MusicPlayerItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            openGui(player);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static java.util.function.Consumer<Player> guiOpener;

    public static void setGuiOpener(java.util.function.Consumer<Player> opener) {
        guiOpener = opener;
    }

    private void openGui(Player player) {
        if (guiOpener != null) {
            guiOpener.accept(player);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Right-click to open player").withStyle(
            net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.literal("Use Broadcast to share with others").withStyle(
            net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
