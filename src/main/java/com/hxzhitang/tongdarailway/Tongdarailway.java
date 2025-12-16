package com.hxzhitang.tongdarailway;

import com.hxzhitang.tongdarailway.blocks.ModBlockEntities;
import com.hxzhitang.tongdarailway.blocks.ModBlocks;
import com.hxzhitang.tongdarailway.blocks.TrackSpawnerBlockRenderer;
import com.hxzhitang.tongdarailway.command.TongdaTestCommand;
import com.hxzhitang.tongdarailway.event.FeatureRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * TongDaRailway - Auto-generates realistic railway networks
 * 1. Auto-generated railway routes
 * 2. Roadbed system (bridges, tunnels)
 * 3. Station structures
 * 4. Region-based planning
 * 5. Station connection planning
 */
@Mod(Tongdarailway.MODID)
public class Tongdarailway {
    public static final String MODID = "tongdarailway";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Mod constants
    public static final int CHUNK_GROUP_SIZE = 128;  // Size of a railway planning region in chunks
    public static final int HEIGHT_MAX_INCREMENT = 100;  // Maximum height above sea level for railways

    public Tongdarailway() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register Feature
        FeatureRegistry.register(modEventBus);

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register blocks and block entities
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);

        // Register ourselves for server and other game events
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ModConfigSpec
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Common setup code
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Server starting code
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        TongdaTestCommand.register(event.getDispatcher());
        LOGGER.info("TongDaRailway test commands registered");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                // Register block entity renderer
                BlockEntityRenderers.register(ModBlockEntities.TRACK_SPAWNER.get(), TrackSpawnerBlockRenderer::new);
            });
        }
    }
}
