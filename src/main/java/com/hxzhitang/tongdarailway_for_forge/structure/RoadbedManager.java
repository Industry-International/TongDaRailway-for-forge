package com.hxzhitang.tongdarailway_for_forge.structure;

import com.hxzhitang.tongdarailway_for_forge.Tongdarailway_for_forge;
import com.hxzhitang.tongdarailway_for_forge.util.MyRandom;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

@Mod.EventBusSubscriber
public class RoadbedManager {
    private static final Map<ResourceLocation, CompoundTag> LOADED_STRUCTURES = new HashMap<>();

    // Road/ground structures
    public static final Map<Integer, RailwayTemplate> ground = new HashMap<>();
    // Tunnels
    public static final Map<Integer, RailwayTemplate> tunnel = new HashMap<>();
    // Bridges
    public static final Map<Integer, RailwayTemplate> bridge = new HashMap<>();

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new NBTResourceReloadListener());
    }

    public static RailwayTemplate getRandomGround(long seed) {
        if (ground.isEmpty()) {
            return null;
        }

        return MyRandom.getRandomValueFromMap(ground, 84_270 + seed * 10000);
    }

    public static RailwayTemplate getRandomTunnel(long seed) {
        if (tunnel.isEmpty()) {
            return null;
        }

        return MyRandom.getRandomValueFromMap(tunnel, 71_1553 + seed * 10000);
    }

    public static RailwayTemplate getRandomBridge(long seed) {
        if (bridge.isEmpty()) {
            return null;
        }

        return MyRandom.getRandomValueFromMap(bridge, 90_318 + seed * 10000);
    }

    private static class NBTResourceReloadListener extends SimplePreparableReloadListener<Map<ResourceLocation, CompoundTag>> {
        @Override
        protected Map<ResourceLocation, CompoundTag> prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
            Map<ResourceLocation, CompoundTag> nbtData = new HashMap<>();
            resourceManager.listResources("structure/railway", location ->
                    location.getPath().endsWith(".nbt")
            ).forEach((location, resource) -> {
                try {
                    InputStream resourceStream = resourceManager
                            .getResource(location)
                            .orElseThrow()
                            .open();
                    try (DataInputStream stream = new DataInputStream(new BufferedInputStream(
                            new GZIPInputStream(resourceStream)))) {
                        CompoundTag rootTag = NbtIo.read(stream);
                        nbtData.put(location, rootTag);
                    } catch (Exception e) {
                        Tongdarailway_for_forge.LOGGER.error(e.getMessage());
                    }
                } catch (Exception e) {
                    Tongdarailway_for_forge.LOGGER.error(e.getMessage());
                }
            });

            return nbtData;
        }

        @Override
        protected void apply(Map<ResourceLocation, CompoundTag> prepared, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
            LOADED_STRUCTURES.clear();
            LOADED_STRUCTURES.putAll(prepared);
            init();
            Tongdarailway_for_forge.LOGGER.info("Loaded {} NBT structures from resources", LOADED_STRUCTURES.size());
        }

        private void init() {
            LOADED_STRUCTURES.forEach((location, compoundTag) -> {
                Tongdarailway_for_forge.LOGGER.info("Loading railway template: {}", location.getPath());
                int id = location.getPath().hashCode();
                String[] subString = location.getPath().split("structure/railway/");
                if (subString.length == 2) {
                    switch (subString[1].split("/")[0]) {
                        case "ground":
                            ground.put(id, new RailwayTemplate(compoundTag));
                            break;
                        case "tunnel":
                            tunnel.put(id, new RailwayTemplate(compoundTag));
                            break;
                        case "bridge":
                            bridge.put(id, new RailwayTemplate(compoundTag));
                            break;
                    }
                }
            });
        }
    }
}
