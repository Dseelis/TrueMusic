package com.dseelis.tg;

import com.dseelis.tg.audio.AudioManager;
import com.dseelis.tg.audio.AudioPersistence;
import com.dseelis.tg.audio.AudioResource;
import com.dseelis.tg.block.SpeakerBlock;
import com.dseelis.tg.block.SpeakerBlockEntity;
import com.dseelis.tg.menu.SpeakerMenu;
import com.dseelis.tg.command.TrueMusicCommand;
import com.dseelis.tg.config.NeoForgeClientConfig;
import com.dseelis.tg.config.NeoForgeServerConfig;
import com.dseelis.tg.network.TrueMusicNetwork;
import com.dseelis.tg.network.packets.SyncAudioResourcesPacket;
import com.dseelis.tg.network.packets.SyncSpeakerStatePacket;
import com.dseelis.tg.server.BinaryManager;
import com.dseelis.tg.server.AudioTransferManager;
import com.dseelis.tg.server.FFmpegHelper;
import com.dseelis.tg.server.ServerAudioStorage;
import com.dseelis.tg.server.ServerSpeakerManager;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

@Mod(TrueMusic.MODID)
public class TrueMusic {
    public static final String MODID = "truemusic";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Log a debug message only if debug logging is enabled in config.
// Use this instead of LOGGER.debug() directly.
public static void debugLog(String message, Object... args) {
        if (com.dseelis.tg.config.TrueMusicClientConfig.isDebugLoggingEnabled()) {
            LOGGER.info("[DEBUG] " + message, args);
        }
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Speaker registration
    public static final DeferredBlock<Block> SPEAKER_BLOCK = BLOCKS.register("speaker", SpeakerBlock::new);
    public static final DeferredItem<Item> SPEAKER_BLOCK_ITEM = ITEMS.register("speaker", () ->
        new BlockItem(SPEAKER_BLOCK.get(), new Item.Properties())
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpeakerBlockEntity>> SPEAKER_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("speaker", () ->
            BlockEntityType.Builder.of(SpeakerBlockEntity::new, SPEAKER_BLOCK.get()).build(null)
        );

    public static final Supplier<MenuType<SpeakerMenu>> SPEAKER_MENU =
        MENU_TYPES.register("speaker", () ->
            new MenuType<>((id, inv) -> new SpeakerMenu(id, inv, BlockPos.ZERO), FeatureFlags.DEFAULT_FLAGS)
        );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MUSIC_TAB = CREATIVE_MODE_TABS.register("music_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.truemusic"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> SPEAKER_BLOCK_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(SPEAKER_BLOCK_ITEM.get());
            }).build());

    private long serverTickCount = 0;

    public TrueMusic(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::onRegisterComplete);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);



        NeoForge.EVENT_BUS.register(this);
        modEventBus.addListener(TrueMusicNetwork::register);
        modEventBus.addListener(this::onConfigLoad);

        modContainer.registerConfig(ModConfig.Type.SERVER, NeoForgeServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, NeoForgeClientConfig.SPEC);
    }

    private void onRegisterComplete(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SpeakerBlockEntity.setTypeSupplier(SPEAKER_BLOCK_ENTITY::get);
            SpeakerMenu.TYPE = SPEAKER_MENU.get();
        });
        LOGGER.info("TrueMusic Common Setup Initializing");
        AudioManager.getInstance();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        TrueMusicCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        Path worldDir = event.getServer().getWorldPath(LevelResource.ROOT);

        // Initialize binary manager to download ffmpeg and yt-dlp if needed
        // Binaries will be placed in the game root directory
        Path gameDir = event.getServer().getServerDirectory();
        BinaryManager.initialize(gameDir);

        ServerAudioStorage storage = ServerAudioStorage.getInstance();
        storage.initialize(worldDir);

        Path dataFile = worldDir.resolve("truemusic_audio.json");
        AudioManager manager = AudioManager.getInstance();
        manager.loadResources(AudioPersistence.load(dataFile));

        repairMissingDurations(manager, storage);

        serverTickCount = 0;

        ServerSpeakerManager.getInstance().setSyncCallback((level, pos, speaker) -> {
            var packet = new SyncSpeakerStatePacket(
                pos, speaker.getPlayback(), speaker.getVolume(), speaker.getPlayMode(), System.currentTimeMillis()
            );
            // Send to tracking players
            PacketDistributor.sendToPlayersTrackingChunk(
                level,
                level.getChunkAt(pos).getPos(),
                packet
            );
        });

        LOGGER.info("Loaded {} audio resources", manager.getAllResources().size());
    }

    private void repairMissingDurations(AudioManager manager, ServerAudioStorage storage) {
        if (!FFmpegHelper.isAvailable()) {
            return;
        }

        int repaired = 0;
        for (AudioResource resource : manager.getAllResources()) {
            if (resource.durationMs() <= 0 && storage.hasAudio(resource.id())) {
                long duration = storage.getDurationMs(resource.id());
                if (duration > 0) {
                    manager.updateResource(new AudioResource(
                        resource.id(), resource.name(), resource.url(), duration, resource.sizeBytes()
                    ));
                    repaired++;
                }
            }
        }
        if (repaired > 0) {
            LOGGER.info("Repaired duration for {} audio resources", repaired);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        Path dataFile = event.getServer().getWorldPath(LevelResource.ROOT).resolve("truemusic_audio.json");

        try {
            AudioPersistence.save(dataFile, AudioManager.getInstance().getAllResources());
            LOGGER.info("Saved audio resources");
        } catch (Exception e) {
            LOGGER.error("Failed to save audio resources", e);
        }

        ServerAudioStorage.getInstance().shutdown();
        AudioTransferManager.getInstance().shutdown();
        ServerSpeakerManager.reset();
    }

    @SubscribeEvent
    public void onPlayerJoin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var resources = AudioManager.getInstance().getAllResources();
            var packet = new SyncAudioResourcesPacket(resources);
            PacketDistributor.sendToPlayer(player, packet);
            LOGGER.info("Synced {} audio resources to {}", resources.size(), player.getName().getString());
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        serverTickCount++;
        AudioTransferManager.getInstance().tick(event.getServer());
        ServerSpeakerManager.getInstance().tick(event.getServer(), serverTickCount);
    }

    private void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == NeoForgeServerConfig.SPEC) {
            NeoForgeServerConfig.bind();
            LOGGER.info("Server config loaded/reloaded");
        } else if (event.getConfig().getSpec() == NeoForgeClientConfig.SPEC) {
            NeoForgeClientConfig.bind();
            LOGGER.info("Client config loaded/reloaded");
        }
    }
}
