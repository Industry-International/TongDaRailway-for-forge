package com.hxzhitang.tongdarailway.event;

import com.hxzhitang.tongdarailway.Tongdarailway;
import com.hxzhitang.tongdarailway.worldgen.RailwayFeatureConfig;
import com.hxzhitang.tongdarailway.worldgen.RailwayFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class FeatureRegistry {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, Tongdarailway.MODID);

    public static final RegistryObject<RailwayFeature> RAILWAY_FEATURE =
            FEATURES.register("railway_and_station", () -> new RailwayFeature(RailwayFeatureConfig.CODEC));

    public static final ResourceKey<Feature<?>> RAILWAY_FEATURE_KEY = ResourceKey.create(Registries.FEATURE,
            new ResourceLocation(Tongdarailway.MODID, "railway_and_station"));

    private FeatureRegistry() {
    }

    public static void register(IEventBus eventBus) {
        FEATURES.register(eventBus);
    }
}
