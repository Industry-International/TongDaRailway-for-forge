package com.hxzhitang.tongdarailway_for_forge.datagen;

import com.hxzhitang.tongdarailway_for_forge.Tongdarailway_for_forge;
import com.hxzhitang.tongdarailway_for_forge.event.FeatureRegistry;
import com.hxzhitang.tongdarailway_for_forge.worldgen.RailwayFeatureConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

public class ModFeatures {
    public static final ResourceKey<ConfiguredFeature<?, ?>> RAILWAY_CONFIGURED_FEATURE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, new ResourceLocation(Tongdarailway_for_forge.MODID, "railway_and_station"));

    public static void bootstrap(BootstapContext<ConfiguredFeature<?, ?>> pContext) {
        pContext.register(RAILWAY_CONFIGURED_FEATURE_KEY,
                new ConfiguredFeature<>(FeatureRegistry.RAILWAY_FEATURE.get(), new RailwayFeatureConfig(0)));
    }
}
