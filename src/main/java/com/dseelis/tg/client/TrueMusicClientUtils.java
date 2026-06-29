package com.dseelis.tg.client;

import com.dseelis.tg.TrueMusic;
import com.dseelis.tg.config.TrueMusicConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = TrueMusic.MODID, value = Dist.CLIENT)
public class TrueMusicClientUtils {


    public static void openConfigScreen() {
        Minecraft mc = Minecraft.getInstance();
        Screen configScreen = AutoConfig.getConfigScreen(TrueMusicConfig.class, mc.screen).get();
        mc.setScreen(configScreen);
    }

    public static Screen getConfigScreen(Screen parent) {
        return AutoConfig.getConfigScreen(TrueMusicConfig.class, parent).get();
    }

    @SubscribeEvent
    public static void onClientJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        TrueMusic.LOGGER.info("TrueMusic client joined, config ready");
    }
}
