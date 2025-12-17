package com.hxzhitang.tongdarailway_for_forge.datagen;

import com.hxzhitang.tongdarailway_for_forge.Tongdarailway_for_forge;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = Tongdarailway_for_forge.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModDataGen {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        // Language files
        generator.addProvider(
                event.includeClient(),
                new ModLanguageProviderENUS(packOutput)
        );
        generator.addProvider(
                event.includeClient(),
                new ModLanguageProviderZHCN(packOutput)
        );

        // Block states
        generator.addProvider(
                event.includeClient(),
                new ModBlockStateProvider(packOutput, Tongdarailway_for_forge.MODID, existingFileHelper)
        );

        // Loot tables
        generator.addProvider(
                event.includeServer(),
                new LootTableProvider(
                        packOutput,
                        Collections.emptySet(),
                        List.of(
                                new LootTableProvider.SubProviderEntry(ModBlockLootProvider::new, LootContextParamSets.BLOCK)
                        )
                )
        );

        // World generation data
        generator.addProvider(
                event.includeServer(),
                new ModWorldGenProvider(packOutput, lookupProvider)
        );
    }
}
