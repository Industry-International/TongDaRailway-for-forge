package com.hxzhitang.tongdarailway_for_forge.blocks;

import com.hxzhitang.tongdarailway_for_forge.Tongdarailway_for_forge;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Tongdarailway_for_forge.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Tongdarailway_for_forge.MODID);

    public static final RegistryObject<Block> TRACK_SPAWNER = BLOCKS.register("track_spawner",
            () -> new TrackSpawnerBlock(BlockBehaviour.Properties.of()
                    .strength(0.5f)
                    .sound(SoundType.COPPER)
                    .lightLevel(l -> 10)
                    .noOcclusion()
            ));

    public static final RegistryObject<Item> TRACK_SPAWNER_ITEM = ITEMS.register("track_spawner",
            () -> new BlockItem(TRACK_SPAWNER.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
