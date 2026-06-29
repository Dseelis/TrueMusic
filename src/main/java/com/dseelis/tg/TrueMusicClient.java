package com.dseelis.tg;

import com.dseelis.tg.client.AudioCache;
import com.dseelis.tg.client.AudioReceiver;
import com.dseelis.tg.client.ClientSpeakerManager;
import com.dseelis.tg.client.gui.SpeakerScreen;
import com.dseelis.tg.config.ClothConfigScreen;
import com.dseelis.tg.menu.SpeakerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = TrueMusic.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = TrueMusic.MODID, value = Dist.CLIENT)
public class TrueMusicClient {

    public TrueMusicClient(ModContainer container) {
        // Register Cloth Config screen
        container.registerExtensionPoint(IConfigScreenFactory.class,
            (client, parent) -> ClothConfigScreen.createConfigScreen(parent));
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            var gameDir = Minecraft.getInstance().gameDirectory.toPath();
            AudioCache.getInstance().initialize(gameDir);
            AudioReceiver.getInstance().initialize(gameDir);

            // Register GUI opener
            com.dseelis.tg.block.SpeakerBlock.setGuiOpener(pos -> {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(new SpeakerScreen(
                    new SpeakerMenu(0, mc.player.getInventory(), pos),
                    mc.player.getInventory(),
                    net.minecraft.network.chat.Component.literal("Speaker")
                ));
            });
        });
    }

    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Unit>() {
            @Override
            protected Unit prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return Unit.INSTANCE;
            }

            @Override
            protected void apply(Unit result, ResourceManager resourceManager, ProfilerFiller profiler) {
                ClientSpeakerManager.getInstance().onResourcesReloaded();
                TrueMusic.debugLog("Resource reload detected, restoring speaker playback");
            }
        });
    }
}